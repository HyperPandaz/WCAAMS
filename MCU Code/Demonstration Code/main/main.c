
#include "main.h"

static const char *tag = "NimBLE_BLE_HeartRate";

static TimerHandle_t mtu_timer;

static bool indicate_state;

// Semaphore to limit concurrent indications
static SemaphoreHandle_t indication_semaphore;

static uint16_t conn_handle;
uint16_t max_tx_octets;
uint16_t max_tx_time;

static const char *device_name = "blehr_sensor_1.0";
static const char *TAG = "FileSystem";
static const char *TAG2 = "FILE TRANSFER";

static int blehr_gap_event(struct ble_gap_event *event, void *arg);

static uint8_t blehr_addr_type;

void spiffs_init(void)
{

    esp_vfs_spiffs_conf_t config = {
        .base_path = "/storage",
        .partition_label = NULL,
        .max_files = 5,
        .format_if_mount_failed = true};

    esp_err_t result = esp_vfs_spiffs_register(&config);

    if (result != ESP_OK)
    {
        ESP_LOGE(TAG, "Failed to initialise SPIFFS (%s)", esp_err_to_name(result));
        return;
    }
}

static void ble_app_set_security(void)
{
    ble_hs_cfg.sm_io_cap = BLE_SM_IO_CAP_NO_IO; // No input/output capabilities
    ble_hs_cfg.sm_bonding = 1;                  // Enable bonding
    ble_hs_cfg.sm_mitm = 1;                     // Enable Man-in-the-Middle protection
    ble_hs_cfg.sm_sc = 1;                       // Enable Secure Connections
}

/*
 * Enables advertising with parameters:
 *     o General discoverable mode
 *     o Undirected connectable mode
 */
static void
blehr_advertise(void)
{
    struct ble_gap_adv_params adv_params;
    struct ble_hs_adv_fields fields;
    int rc;

    /*
     *  Set the advertisement data included in our advertisements:
     *     o Flags (indicates advertisement type and other general info)
     *     o Advertising tx power
     *     o Device name
     */
    memset(&fields, 0, sizeof(fields));

    /*
     * Advertise two flags:
     *      o Discoverability in forthcoming advertisement (general)
     *      o BLE-only (BR/EDR unsupported)
     */
    fields.flags = BLE_HS_ADV_F_DISC_GEN |
                   BLE_HS_ADV_F_BREDR_UNSUP;

    /*
     * Indicate that the TX power level field should be included; have the
     * stack fill this value automatically.  This is done by assigning the
     * special value BLE_HS_ADV_TX_PWR_LVL_AUTO.
     */
    fields.tx_pwr_lvl_is_present = 1;
    fields.tx_pwr_lvl = BLE_HS_ADV_TX_PWR_LVL_AUTO;

    fields.name = (uint8_t *)device_name;
    fields.name_len = strlen(device_name);
    fields.name_is_complete = 1;

    rc = ble_gap_adv_set_fields(&fields);
    if (rc != 0)
    {
        MODLOG_DFLT(ERROR, "error setting advertisement data; rc=%d\n", rc);
        return;
    }

    /* Begin advertising */
    memset(&adv_params, 0, sizeof(adv_params));
    adv_params.conn_mode = BLE_GAP_CONN_MODE_UND;
    adv_params.disc_mode = BLE_GAP_DISC_MODE_GEN;
    rc = ble_gap_adv_start(blehr_addr_type, NULL, BLE_HS_FOREVER,
                           &adv_params, blehr_gap_event, NULL);
    if (rc != 0)
    {
        MODLOG_DFLT(ERROR, "error enabling advertisement; rc=%d\n", rc);
        return;
    }
}

// Function to send a file in chunks
// void send_file_over_ble(TimerHandle_t th)
void vTransferFiles()
{
    ESP_LOGI("TASK", "Startting File Transfer");

    size_t mtu = ble_att_mtu(conn_handle) - 3; // MTU size minus BLE ATT overhead

    DIR *dir;
    struct dirent *entry;

    // Open the directory
    dir = opendir("/storage/");
    if (dir == NULL)
    {
        ESP_LOGI("ERROR", "Unable to open directory");
        vTaskDelete(NULL);
        return;
    }

    while ((entry = readdir(dir)) != NULL)
    {
        vTaskDelay(100 / portTICK_PERIOD_MS);
        if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0)
        {
            continue;
        }

        if (mode != 4 && (strcmp(entry->d_name, "2000-02-02-02-02-02.wav") == 0 || strcmp(entry->d_name, "1000-01-01-01-01-01.wav") == 0))
        {
            continue;
        }

        char filepath[265];
        char fileName[265];
        snprintf(filepath, sizeof(filepath), "/storage/%s", entry->d_name);
        snprintf(fileName, sizeof(fileName), "%s", entry->d_name);

        ESP_LOGI("FILE", "Sending %s", fileName);

    retry:
        FILE *file = fopen(filepath, "rb");
        if (!file)
        {
            ESP_LOGE(TAG, "Failed to open file");
            vTaskDelete(NULL);
            return;
        }

        // Get file size
        fseek(file, 0, SEEK_END);
        size_t file_size = ftell(file);
        fseek(file, 0, SEEK_SET);
        ESP_LOGI(TAG, "File size: %d bytes", file_size);

        // Buffer to hold each chunk
        uint8_t chunk[mtu];
        size_t bytes_remaining = file_size;
        size_t chunk_number = 0;
        // TODO: Circular array

        // Wait for the semaphore to limit concurrent indications
        if (xSemaphoreTake(indication_semaphore, pdMS_TO_TICKS(1000)) != pdTRUE)
        {
            ESP_LOGE(TAG2, "Timeout waiting for semaphore");
            vTaskDelete(NULL);
            return;
        }

        // Create an mbuf from the chunk
        struct os_mbuf *om = ble_hs_mbuf_from_flat(fileName, strlen(fileName));
        if (!om)
        {
            ESP_LOGE(TAG2, "Failed to create mbuf for chunk %d", chunk_number);
            xSemaphoreGive(indication_semaphore); // Release the semaphore
            vTaskDelete(NULL);
            return;
        }

        // Send the chunk as an indication
        int rc = ble_gatts_indicate_custom(conn_handle, ots_object_data_handle, om);
        vTaskDelay(50 / portTICK_PERIOD_MS);

        if (rc != 0)
        {
            ESP_LOGE(TAG2, "Failed to send chunk %d, error: %d", chunk_number, rc);
            // os_mbuf_free_chain(om); // Free the mbuf
            xSemaphoreGive(indication_semaphore); // Release the semaphore
            fclose(file);
            goto retry;
        }

        while (bytes_remaining > 0 && indicate_state == 1)
        {
            // Read a chunk of the file
            size_t chunk_size = (bytes_remaining > mtu) ? mtu : bytes_remaining;
            size_t bytes_read = fread(chunk, 1, chunk_size, file);
            if (bytes_read != chunk_size)
            {
                ESP_LOGE(TAG2, "Failed to read file chunk");
                break;
            }

            // Wait for the semaphore to limit concurrent indications
            if (xSemaphoreTake(indication_semaphore, pdMS_TO_TICKS(1000)) != pdTRUE)
            {
                ESP_LOGE(TAG2, "Timeout waiting for semaphore");
                break;
            }

            om = ble_hs_mbuf_from_flat(chunk, chunk_size);
            if (!om)
            {
                ESP_LOGE(TAG2, "Failed to create mbuf for chunk %d", chunk_number);
                xSemaphoreGive(indication_semaphore); // Release the semaphore
                break;
            }

            // Send the chunk as an indication
            int rc = ble_gatts_indicate_custom(conn_handle, ots_object_data_handle, om);

            vTaskDelay(50 / portTICK_PERIOD_MS);

            if (rc != 0)
            {
                ESP_LOGE(TAG2, "Failed to send chunk %d, error: %d", chunk_number, rc);
                // os_mbuf_free_chain(om); // Free the mbuf
                xSemaphoreGive(indication_semaphore); // Release the semaphore
                uint8_t done[] = {1};
                // struct os_mbuf *om = ble_hs_mbuf_from_flat(done, 1);
                om = ble_hs_mbuf_from_flat(done, 1);
                ble_gatts_indicate_custom(conn_handle, ots_object_data_handle, om);
                ESP_LOGI(TAG2, "Sent done flag");
                goto retry;
            }

            bytes_remaining -= chunk_size;
            chunk_number++;
        }

        if (xSemaphoreTake(indication_semaphore, pdMS_TO_TICKS(1000)) != pdTRUE)
        {
        }
        uint8_t done[] = {1};
        om = ble_hs_mbuf_from_flat(done, 1);
        ble_gatts_indicate_custom(conn_handle, ots_object_data_handle, om);
        ESP_LOGI(TAG2, "Sent done flag");

        fclose(file);
        ESP_LOGI(TAG2, "File transfer complete/terminated");
        if (strcmp(filepath, "/storage/2000-02-02-02-02-02.wav") != 0 && strcmp(filepath, "/storage/1000-01-01-01-01-01.wav") != 0)
        {
            if (remove(filepath) == 0)
            {
                ESP_LOGI(TAG2, "File deleted successfully.\n");
            }
            else
            {
                ESP_LOGI(TAG2, "Error: Unable to delete the file.\n");
            }
        }
    }

    if (mode > 1)
    {
        xTaskCreate(vRecord, "record", 4096, NULL, 10, NULL);
    }
    vTaskDelete(NULL);

    return;
}

// Initialize the semaphore
void init_ble_file_transfer()
{
    // Allow up to 4 concurrent indications
    indication_semaphore = xSemaphoreCreateCounting(4, 4);
    if (!indication_semaphore)
    {
        ESP_LOGE(TAG, "Failed to create semaphore");
    }
}

void request_mtu()
{
    int rc = ble_gattc_exchange_mtu(conn_handle, NULL, NULL);
    if (rc == 0)
    {
        ESP_LOGI(TAG, "MTU exchange request sent successfully.");
    }
    else
    {
        ESP_LOGE(TAG, "Failed to send MTU exchange request: %d", rc);
    }
}

static int
blehr_gap_event(struct ble_gap_event *event, void *arg)
{
    switch (event->type)
    {
    case BLE_GAP_EVENT_CONNECT:
        // New connection was established or a connection attempt failed
        MODLOG_DFLT(INFO, "connection %s; status=%d\n",
                    event->connect.status == 0 ? "established" : "failed",
                    event->connect.status);

        if (event->connect.status != 0)
        {
            // Connection failed; resume advertising
            blehr_advertise();
        }
        conn_handle = event->connect.conn_handle;
        ble_gattc_exchange_mtu(conn_handle, NULL, NULL);

        break;

    case BLE_GAP_EVENT_DISCONNECT:
        MODLOG_DFLT(INFO, "disconnect; reason=%d\n", event->disconnect.reason);
        indicate_state = 0;

        // Connection terminated; resume advertising
        blehr_advertise();
        break;

    case BLE_GAP_EVENT_ADV_COMPLETE:
        MODLOG_DFLT(INFO, "adv complete\n");
        blehr_advertise();
        break;

    case BLE_GAP_EVENT_SUBSCRIBE:
        MODLOG_DFLT(INFO, "subscribe event; cur_notify=%d\n value handle; "
                          "val_handle=%d\n",
                    event->subscribe.cur_notify, event->subscribe.attr_handle);
        if (event->subscribe.attr_handle == ots_object_data_handle)
        {
            indicate_state = event->subscribe.cur_indicate;
            ESP_LOGI("NOTE", "indicate_state:%d", indicate_state);
        }
        else
        {
            indicate_state = 0;
        }
        ESP_LOGI("BLE_GAP_SUBSCRIBE_EVENT", "conn_handle from subscribe=%d", conn_handle);
        break;

    case BLE_GAP_EVENT_MTU:
        MODLOG_DFLT(INFO, "mtu update event; conn_handle=%d mtu=%d\n",
                    event->mtu.conn_handle,
                    event->mtu.value);
        ESP_LOGI("MTU", "mtu=%d", ble_att_mtu(conn_handle));
        break;

    case BLE_GAP_EVENT_NOTIFY_RX:
        ESP_LOGI("GAP EVENT", "NOTIFY_RX");
        break;

    case BLE_GAP_EVENT_NOTIFY_TX:
        if (event->notify_tx.indication)
        {
            // Indication acknowledgment received
            if (event->notify_tx.status == 0)
            {
                // ESP_LOGI(TAG2, "Indication sent successfully");
            }
            else if (event->notify_tx.status == 14)
            {
                // ESP_LOGI(TAG2, "ACK");
                xSemaphoreGive(indication_semaphore); // Release the semaphore
            }
            else
            {
                ESP_LOGE(TAG2, "Indication failed, status: %d", event->notify_tx.status);
            }
        }
        break;

    case BLE_GAP_EVENT_CONN_UPDATE_REQ:
        ESP_LOGI("GAP", "Connection update request received:");
        ESP_LOGI("GAP", "  Min Interval: %d (%.2f ms)",
                 event->conn_update_req.peer_params->itvl_min,
                 event->conn_update_req.peer_params->itvl_min * 1.25);
        ESP_LOGI("GAP", "  Max Interval: %d (%.2f ms)",
                 event->conn_update_req.peer_params->itvl_max,
                 event->conn_update_req.peer_params->itvl_max * 1.25);
        ESP_LOGI("GAP", "  Latency: %d", event->conn_update_req.peer_params->latency);
        ESP_LOGI("GAP", "  Timeout: %d (%.2f ms)",
                 event->conn_update_req.peer_params->supervision_timeout,
                 event->conn_update_req.peer_params->supervision_timeout * 10.0);
        break;

    case BLE_GAP_EVENT_CONN_UPDATE:
        // Connection parameters updated
        ESP_LOGI(TAG, "Connection parameters update status: %d", event->conn_update.status);
        break;
    default:
        ESP_LOGI("GAP EVENT", "Unhandled event: %d", event->type);
    }

    return 0;
}

static void
blehr_on_sync(void)
{
    int rc;

    rc = ble_hs_id_infer_auto(0, &blehr_addr_type);
    assert(rc == 0);

    uint8_t addr_val[6] = {0};
    rc = ble_hs_id_copy_addr(blehr_addr_type, addr_val, NULL);

    // Begin advertising
    blehr_advertise();
}

static void
blehr_on_reset(int reason)
{
    MODLOG_DFLT(ERROR, "Resetting state; reason=%d\n", reason);
}

void blehr_host_task(void *param)
{
    ESP_LOGI(tag, "BLE Host Task Started");
    // This function will return only when nimble_port_stop() is executed
    nimble_port_run();

    nimble_port_freertos_deinit();
}

void app_main(void)
{

    spiffs_init();
    // adc1_config_width(ADC_BITWIDTH_12);                       // set the ADC to 12 bits
    // adc1_config_channel_atten(ADC_CHANNEL_6, ADC_ATTEN_DB_12); // set attenuation to 12 dB and channel to 6
    adc_dma_init();

    int rc;

    // Initialize NVS — it is used to store PHY calibration data
    esp_err_t ret = nvs_flash_init();
    if (ret == ESP_ERR_NVS_NO_FREE_PAGES || ret == ESP_ERR_NVS_NEW_VERSION_FOUND)
    {
        ESP_ERROR_CHECK(nvs_flash_erase());
        ret = nvs_flash_init();
    }
    ESP_ERROR_CHECK(ret);

    ret = nimble_port_init();
    if (ret != ESP_OK)
    {
        MODLOG_DFLT(ERROR, "Failed to init nimble %d \n", ret);
        return;
    }
    ble_app_set_security();
    // Initialize the NimBLE host configuration
    ble_hs_cfg.sync_cb = blehr_on_sync;
    ble_hs_cfg.reset_cb = blehr_on_reset;

    // name, period/time,  auto reload, timer ID, callback
    mtu_timer = xTimerCreate("mtu_timer", pdMS_TO_TICKS(50), pdFALSE, (void *)0, request_mtu);

    rc = gatt_svr_init();
    assert(rc == 0);

    // Set the default device name
    rc = ble_svc_gap_device_name_set(device_name);
    assert(rc == 0);

    // Start the task
    nimble_port_freertos_init(blehr_host_task);

    init_ble_file_transfer();
}

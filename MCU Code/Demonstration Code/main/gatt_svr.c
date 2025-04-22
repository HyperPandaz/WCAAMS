
#include "gatt_srv.h"

// 1 STANDBY
// 2 LOW
// 3 Continuous
// 4 DEMO
uint8_t mode = 1; // STANDBY
bool flag = false;

static const struct ble_gatt_svc_def gatt_svr_svcs[] = {
    {//
     .type = BLE_GATT_SVC_TYPE_PRIMARY,
     .uuid = BLE_UUID16_DECLARE(GATT_OTS_UUID),
     .characteristics = (struct ble_gatt_chr_def[]){
         {
             /* Object Data Characteristic with Indication support */
             .uuid = BLE_UUID16_DECLARE(GATT_OTS_OBJECT_DATA_UUID),
             .access_cb = gatt_svr_chr_access_ots_data, // Callback for data access
             .val_handle = &ots_object_data_handle,     // Store the handle for later use
             .flags = BLE_GATT_CHR_F_INDICATE,
         },
         {
             0, /* No more characteristics in this service */
         },
     }},

    {/* Time Synchronisation*/
     .type = BLE_GATT_SVC_TYPE_PRIMARY,
     .uuid = BLE_UUID128_DECLARE(GATT_TIME_SYNC_UUID),
     .characteristics = (struct ble_gatt_chr_def[]){
         {
             /* Time Update */
             .uuid = BLE_UUID128_DECLARE(GATT_TIME_UPDATE_UUID),
             .access_cb = gatt_svr_chr_access_time_update, // Callback for data access
             .flags = BLE_GATT_CHR_F_READ | BLE_GATT_CHR_F_WRITE,
         },
         {
             0, /* No more characteristics in this service */
         },
     }},

    {//
     .type = BLE_GATT_SVC_TYPE_PRIMARY,
     .uuid = BLE_UUID128_DECLARE(GATT_MODE_UUID),
     .characteristics = (struct ble_gatt_chr_def[]){
         {
             /* Mode Update */
             .uuid = BLE_UUID128_DECLARE(GATT_MODE_UPDATE_UUID),
             .access_cb = gatt_svr_chr_access_mode_update, // Callback for data access
             .flags = BLE_GATT_CHR_F_READ | BLE_GATT_CHR_F_WRITE,
         },
         {
             0, /* No more characteristics in this service */
         },
     }},

    {
        0, /* No more services */
    },
};

static int
gatt_svr_chr_access_ots_data(uint16_t conn_handle, uint16_t attr_handle, struct ble_gatt_access_ctxt *ctxt, void *arg)
{
    if (ctxt->op == BLE_GATT_ACCESS_OP_READ_CHR)
    {
        // Example: Return "Object Data" when the characteristic is read
        uint8_t example_data[] = "Object Data Example";
        return os_mbuf_append(ctxt->om, example_data, sizeof(example_data));
    }

    return 0; // Default if no specific read/write operations are required
}

static int
gatt_svr_chr_access_time_update(uint16_t conn_handle, uint16_t attr_handle, struct ble_gatt_access_ctxt *ctxt, void *arg)
{

    if (ctxt->op == BLE_GATT_ACCESS_OP_WRITE_CHR)
    {
        // Extract the data written by the client
        uint8_t *data = ctxt->om->om_data;
        uint16_t len = ctxt->om->om_len;

        ESP_LOGI("TIME", "data %s\n", data);
        ESP_LOGI("TIME", "length %d\n", len);

        struct timeval tv;
        tv.tv_sec = 0;
        tv.tv_usec = 0;
        for (int i = 0; i < len; i++)
        {
            tv.tv_sec *= 10;
            tv.tv_sec += ((int)data[i] - 48);
        }
        ESP_LOGI("TIME", "%llu\n", tv.tv_sec);

        tv.tv_sec /= 1000;
        settimeofday(&tv, NULL);

        gettimeofday(&tv, NULL); // Get the current time

        // Convert the time to a human-readable format
        struct tm *timeinfo = localtime(&tv.tv_sec);
        char buffer[50];
        strftime(buffer, sizeof(buffer), "%Y-%m-%d %H:%M:%S", timeinfo);

        ESP_LOGI("RTC", "Current time: %s", buffer);
    }
    else if (ctxt->op == BLE_GATT_ACCESS_OP_READ_CHR)
    {
        struct timeval tv;
        gettimeofday(&tv, NULL);
        struct tm *timeinfo = localtime(&tv.tv_sec);
        char buffer[] = "YYYY-MM-DD-HH-MM-SS";
        strftime(buffer, sizeof(buffer), "%Y-%m-%d-%H-%M-%S", timeinfo);
        ESP_LOGI("RTC", "Current time: %s", buffer);
        return os_mbuf_append(ctxt->om, buffer, sizeof(buffer));
    }

    return 0;
}

static int
gatt_svr_chr_access_mode_update(uint16_t conn_handle, uint16_t attr_handle, struct ble_gatt_access_ctxt *ctxt, void *arg)
{
    if (ctxt->op == BLE_GATT_ACCESS_OP_WRITE_CHR)
    {
        uint8_t *data = ctxt->om->om_data;
        ESP_LOGI("INFO", "Writing mode value %d", data[0]);

        if (mode == 1 && (data[0] != 1))
        {
            xTaskCreate(vRecord, "record", 4096, NULL, 10, NULL);
        }
        mode = data[0];

        // if(mode == 4){
        //     flag = true;
        // }
    }
    else if (ctxt->op == BLE_GATT_ACCESS_OP_READ_CHR)
    {
        return os_mbuf_append(ctxt->om, &mode, sizeof(mode));
    }

    return 0;
}

void gatt_svr_register_cb(struct ble_gatt_register_ctxt *ctxt, void *arg)
{
    char buf[BLE_UUID_STR_LEN];

    switch (ctxt->op)
    {
    case BLE_GATT_REGISTER_OP_SVC:
        MODLOG_DFLT(DEBUG, "registered service %s with handle=%d\n",
                    ble_uuid_to_str(ctxt->svc.svc_def->uuid, buf),
                    ctxt->svc.handle);
        break;

    case BLE_GATT_REGISTER_OP_CHR:
        MODLOG_DFLT(DEBUG, "registering characteristic %s with "
                           "def_handle=%d val_handle=%d\n",
                    ble_uuid_to_str(ctxt->chr.chr_def->uuid, buf),
                    ctxt->chr.def_handle,
                    ctxt->chr.val_handle);
        break;

    case BLE_GATT_REGISTER_OP_DSC:
        MODLOG_DFLT(DEBUG, "registering descriptor %s with handle=%d\n",
                    ble_uuid_to_str(ctxt->dsc.dsc_def->uuid, buf),
                    ctxt->dsc.handle);
        break;

    default:
        assert(0);
        break;
    }
}

int gatt_svr_init(void)
{
    int rc;

    ble_svc_gap_init();
    ble_svc_gatt_init();

    rc = ble_gatts_count_cfg(gatt_svr_svcs);
    if (rc != 0)
    {
        return rc;
    }

    rc = ble_gatts_add_svcs(gatt_svr_svcs);
    if (rc != 0)
    {
        return rc;
    }

    return 0;
}

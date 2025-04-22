#ifndef MAIN_H
#define MAIN_H

#include "record.h"
#include "blehr_sens.h"

#include "esp_log.h"
#include "esp_spiffs.h"
#include "nvs_flash.h"
#include "freertos/FreeRTOS.h"
/* BLE */
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "host/ble_hs.h"
#include "host/util/util.h"


#include "services/gap/ble_svc_gap.h"

#include "driver/adc.h"
// #include "esp_adc/adc_oneshot.h"
#include <dirent.h>



void vTransferFiles();

#endif
#ifndef RECORD_H
#define RECORD_H

#include "main.h"
#include "filtering.h"

#include "stdio.h"
#include "stdint.h"
#include "stdlib.h"
#include "string.h"

// #include "driver/adc.h"
#include "esp_adc/adc_oneshot.h"
#include "esp_adc/adc_cali.h"
#include "esp_adc/adc_cali_scheme.h"
#include "esp_timer.h"
#include "driver/gpio.h"

#include "sys/time.h"
#include "esp_spiffs.h"

#include "freertos/FreeRTOS.h"
#include "driver/gpio.h"
#include "esp_log.h"

extern bool flag;

void vRecord();
void writeWavFile(int16_t *output, char *filePath);
void adc_dma_init();

#endif
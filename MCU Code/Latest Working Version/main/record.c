#include "record.h"

#define sampling_frequency 1000 // Hz
#define run_time 10 // seconds
#define length (sampling_frequency * run_time)
// #define for_norm 32767
// #define max_boost 21.35f

int_least16_t *output1;
adc_oneshot_unit_handle_t adc1_handle;
adc_cali_handle_t adc_cali_handle = NULL;
bool adc_calibrated = false;

void adc_dma_init()
{
    adc_oneshot_unit_init_cfg_t init_config = {
        .unit_id = ADC_UNIT_1,
    };
    adc_oneshot_new_unit(&init_config, &adc1_handle);

    adc_oneshot_chan_cfg_t config = {
        .bitwidth = ADC_BITWIDTH_12,
        .atten = ADC_ATTEN_DB_12};
    adc_oneshot_config_channel(adc1_handle, ADC_CHANNEL_6, &config);

    adc_cali_curve_fitting_config_t cali_config = {
        .unit_id = ADC_UNIT_1,
        .chan = ADC_CHANNEL_6,
        .atten = ADC_ATTEN_DB_12,
        .bitwidth = ADC_BITWIDTH_12};
    if (adc_cali_create_scheme_curve_fitting(&cali_config, &adc_cali_handle) == ESP_OK)
    {
        int test_val = 0, mv = 0;
        if (adc_oneshot_read(adc1_handle, ADC_CHANNEL_6, &test_val) == ESP_OK &&
            adc_cali_raw_to_voltage(adc_cali_handle, test_val, &mv) == ESP_OK)
        {
            adc_calibrated = true;
        }
    }
}
// FreeRTOS task for rcording raw data from the MEMS
void vRecord()
{
  ESP_LOGI("RECORD", "staring");
  // Allocate memory for data
  output1 = (int_least16_t *)calloc(length, sizeof(int_least16_t));

  vTaskDelay(50 / portTICK_PERIOD_MS);
  if (flag)
  {
    vTaskDelete(NULL);
    ;
  }

  // Get the current time
  struct timeval tv;
  gettimeofday(&tv, NULL);
  struct tm *timeinfo = localtime(&tv.tv_sec);

  // Convert time into file name
  char fileName[] = "YYYY-MM-DD-HH-MM-SS.wav";
  strftime(fileName, sizeof(fileName), "%Y-%m-%d-%H-%M-%S.wav", timeinfo);

  // Create filepath for currnet file
  char file_path[256] = "/storage/";
  snprintf(file_path, sizeof(file_path), "/storage/%s", fileName);
  ESP_LOGI("FILEPATH", "%s", file_path);
  // uint64_t start_time=esp_timer_get_time();

  // Record data
  for (int i = 0; i < length; i++)
  {
    // Get MEMS output
    int raw = 0, mv = 0;
    float sample = 0;
    if (adc_oneshot_read(adc1_handle, ADC_CHANNEL_6, &raw) == ESP_OK)
    {
        if (adc_calibrated && adc_cali_raw_to_voltage(adc_cali_handle, raw, &mv) == ESP_OK)
        {
          sample = (((float)mv / 1000.0f) - 1.0f)*32767.0f;
        }
        else
        {
          sample = (((float)raw * 0.0004885198f) - 1.0f)*32767.0f;
        }
    }
    if (sample>32767.0f)
      sample=32767.0f;
    if (sample<-32768.0f)
      sample=-32768.0f;
    output1[i]=(int_least16_t)sample;
    // Wait based on sapling frequency
    vTaskDelay(((sampling_frequency / 1000) / portTICK_PERIOD_MS));
  }
  // ESP_LOGW("sample", "sampling time for 5s: %lld", esp_timer_get_time()-start_time);

  writeWavFile(output1, file_path);

  free(output1);

  size_t total = 0, used = 0;
  esp_err_t result = esp_spiffs_info(NULL, &total, &used);
  if (result != ESP_OK)
  {
    ESP_LOGE("SPIFFS", "Failed to get partition info (%s)", esp_err_to_name(result));
  }
  else
  {
    ESP_LOGI("SPIFFS", "Partition size: total: %d, used: %d", total, used);
  }
  vTaskDelay(500 / portTICK_PERIOD_MS);

  xTaskCreate(vTransferFiles, "send_file", 4096, NULL, 5, NULL); // schedule file transfer
  vTaskDelete(NULL); // return
}

// Create Wav Struct
// https://docs.fileformat.com/audio/wav/
struct wav_header
{
  char riff[4];           /* "RIFF"                                  */
  int32_t flength;        /* file length in bytes                    */
  char wave[4];           /* "WAVE"                                  */
  char fmt[4];            /* "fmt "                                  */
  int32_t chunk_size;     /* size of FMT chunk in bytes (usually 16) */
  int16_t format_tag;     /* 1=PCM, 257=Mu-Law, 258=A-Law, 259=ADPCM */
  int16_t num_chans;      /* 1=mono, 2=stereo                        */
  int32_t srate;          /* Sampling rate in samples per second     */
  int32_t bytes_per_sec;  /* bytes per second = srate*bytes_per_samp */
  int16_t bytes_per_samp; /* 2=16-bit mono, 4=16-bit stereo          */
  int16_t bits_per_samp;  /* Number of bits per sample               */
  char data[4];           /* "data"                                  */
  int32_t dlength;        /* data length in bytes (filelength - 44)  */
};

// Populate Wav Struct

struct wav_header wavh;
const int header_length = sizeof(struct wav_header);

void writeWavFile(int_least16_t *output, char *filePath)
{
  memcpy(wavh.riff, "RIFF", 4);
  memcpy(wavh.wave, "WAVE", 4);
  memcpy(wavh.fmt, "fmt ", 4);
  memcpy(wavh.data, "data", 4);

  wavh.chunk_size = 16;
  wavh.format_tag = 1;
  wavh.num_chans = 1;
  wavh.srate = sampling_frequency;
  wavh.bits_per_samp = 16;
  wavh.bytes_per_sec = wavh.srate * wavh.bits_per_samp / 8 * wavh.num_chans;
  wavh.bytes_per_samp = wavh.bits_per_samp / 8 * wavh.num_chans;

  wavh.dlength = length * wavh.bytes_per_samp;
  wavh.flength = wavh.dlength + header_length;

  // Writing Wav File to Disk
  FILE *fp = fopen(filePath, "w");
  fwrite(&wavh, 1, header_length, fp);
  fwrite(output, 2, length, fp);

  fclose(fp);
  return;
}

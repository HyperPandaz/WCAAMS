#include "record.h"
#include "coeffs.h"

// ----------------------------------------------------------------
#define RLS_LEN 14
#define sampling_frequency 1000
#define run_time 10
#define length (sampling_frequency * run_time)

float sos_states[NUM_SECTIONS][4] = {0};
float conv_buffer[H_INV_LEN] = {0};
int conv_idx = 0;

float rls_weights[RLS_LEN] = {0};
float rls_buffer[RLS_LEN] = {0};
float S[RLS_LEN][RLS_LEN];
const float lambda = 0.55f;
float delta = 1e-4f;
float error_avg = 0.0f;

float gain_factor = 20.0f;
const float clip_threshold = 30000.0f;
const float quiet_threshold = 2000.0f;

int16_t end[length];
adc_oneshot_unit_handle_t adc1_handle;
adc_cali_handle_t adc_cali_handle = NULL;
bool adc_calibrated = false;

uint64_t sampling_end, sampling_start;

// ----------------------------------------------------------------
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
  // output1 = (int_least16_t *)calloc(length, sizeof(int_least16_t));

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

  // Initialize RLS matrix
  for (int r = 0; r < RLS_LEN; r++)
    for (int c = 0; c < RLS_LEN; c++)
      S[r][c] = (r == c) ? 1.0f / delta : 0.0f;
  // Record data
  sampling_start = esp_timer_get_time();
  static int rls_idx = 0;

  for (int t = 0; t < length; t++)
  {
    int raw = 0, mv = 0;
    float sample = 0;
    if (adc_oneshot_read(adc1_handle, ADC_CHANNEL_6, &raw) == ESP_OK)
    {
      if (adc_calibrated && adc_cali_raw_to_voltage(adc_cali_handle, raw, &mv) == ESP_OK)
        sample = ((float)mv - 1387.0f) * gain_factor;
      else
        sample = ((float)raw - 1720.0f) * gain_factor;
    }

    float abs_sample = fabsf(sample);
    gain_factor *= (abs_sample > clip_threshold) ? 0.99f : (abs_sample < quiet_threshold) ? 1.01f
                                                                                          : 1.0f;
    gain_factor = fminf(fmaxf(gain_factor, 10.0f), 50.0f);

    float stage_in = fmaxf(fminf(sample, 32767.0f), -32768.0f), stage_out = 0;
    for (int s = 0; s < NUM_SECTIONS; s++)
    {
      dsps_biquad_f32(&stage_in, &stage_out, 1, sos_coeffs[s], sos_states[s]);
      stage_in = stage_out;
    }

    float filtered = stage_out;
    conv_buffer[conv_idx] = filtered;
    float desired_f = 0.0f;
    for (int h = 0; h < H_INV_LEN; h++)
    {
      int idx = (conv_idx - h + H_INV_LEN) % H_INV_LEN;
      desired_f += conv_buffer[idx] * h_inv_skin[h];
    }
    conv_idx = (conv_idx + 1) % H_INV_LEN;

    rls_buffer[rls_idx] = filtered;
    float x[RLS_LEN];
    for (int i = 0; i < RLS_LEN; i++)
    {
      int idx = (rls_idx - i + RLS_LEN) % RLS_LEN;
      x[i] = rls_buffer[idx];
    }
    rls_idx = (rls_idx + 1) % RLS_LEN;

    float y_pred = 0.0f;
    dsps_dotprod_f32(x, rls_weights, &y_pred, RLS_LEN);
    float error = desired_f - y_pred;

    error_avg = 0.98f * error_avg + 0.02f * error * error;

    float s_vec[RLS_LEN] = {0};
    for (int m = 0; m < RLS_LEN; m++)
      for (int n = 0; n < RLS_LEN; n++)
        s_vec[m] += x[n] * S[n][m];

    float dot = 0.0f;
    dsps_dotprod_f32(s_vec, x, &dot, RLS_LEN);
    float kappa = lambda + dot;

    float K[RLS_LEN] = {0};
    for (int m = 0; m < RLS_LEN; m++)
      for (int n = 0; n < RLS_LEN; n++)
        K[m] += S[m][n] * x[n];
    for (int m = 0; m < RLS_LEN; m++)
      K[m] /= kappa;

    for (int m = 0; m < RLS_LEN; m++)
    {
      rls_weights[m] += K[m] * error;
      rls_weights[m] = fminf(fmaxf(rls_weights[m], -100.0f), 100.0f);
    }

    for (int m = 0; m < RLS_LEN; m++)
      for (int n = 0; n < RLS_LEN; n++)
        S[m][n] = (S[m][n] - K[m] * s_vec[n]) / lambda;

    float y_rls = y_pred;

    float y_out = fminf(fmaxf(y_rls * 10, -32768.0f), 32767.0f);
    end[t] = (int16_t)y_out;

    if (t % 1000 == 0)
    {
      ESP_LOGI("RLS", "err_rms=%.2f, w0=%.3f",
               sqrtf(error_avg), rls_weights[0]);
    }

    // Wait based on sapling frequency
    vTaskDelay(((sampling_frequency / 1000) / portTICK_PERIOD_MS));
  }
  ESP_LOGW("sampol", "time to sample and filter 10s %lld", esp_timer_get_time() - sampling_start);

  writeWavFile(end, file_path);

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
  vTaskDelete(NULL);                                             // return
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

void writeWavFile(int16_t *output, char *filePath)
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
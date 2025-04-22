#ifndef FILTERING_h
#define FILTERING_h

#include <stdint.h>
#include <string.h>
#include <math.h>
#include "dsps_dotprod.h"
#include "dsps_fir.h"
#include "esp_dsp.h"
// #include "all_the_lib_imports.h"

#define FILTER_ORDER 32
#define FRAME_SIZE 2500
#define MU 0.00001f   // LMS Step size
#define LAMBDA 0.99f  // RLS Forgetting factor
#define EPSILON 1e-6f // Small constant for numerical stability

// LMS Algorithm -----------------------------------------------------------
void LMS_filter(int_least16_t *input, const char *file_path, size_t length);

// Normalized LMS (NLMS) Algorithm -----------------------------------------
void NLMS_filter(int_least16_t *input, const char *file_path, size_t length);

// Recursive Least Squares (RLS) Algorithm ---------------------------------
void RLS_filter(int_least16_t *input, const char *file_path, size_t length);

// Constant Modulus Algorithm (CMA) -----------------------------------------
void CMA_filter(int_least16_t *input, const char *file_path, size_t length);

#endif
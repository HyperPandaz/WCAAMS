
#include "filtering.h"


#define N_FIR_B 52

float b_fir[] = { 0.0032511f, -0.0012797f, -0.0024579f, 0.0114320f, -0.0037276f, -0.0090202f, -0.0045552f, -0.0148918f, 0.0038334f, -0.0087262f, 0.0052015f, 0.0108462f, 0.0012680f, 0.0339721f, 0.0002465f, 0.0346675f, 0.0085586f, -0.0041827f, 0.0191665f, -0.0686362f, 0.0061634f, -0.1125181f, -0.0654401f, -0.0688912f, -0.2668036f, 0.4925213f, 0.4925213f, -0.2668036f, -0.0688912f, -0.0654401f, -0.1125181f, 0.0061634f, -0.0686362f, 0.0191665f, -0.0041827f, 0.0085586f, 0.0346675f, 0.0002465f, 0.0339721f, 0.0012680f, 0.0108462f, 0.0052015f, -0.0087262f, 0.0038334f, -0.0148918f, -0.0045552f, -0.0090202f, -0.0037276f, 0.0114320f, -0.0024579f, -0.0012797f, 0.0032511f };

#define N_FIR_A 1

float a_fir[] = { 1.0000000f };

#define for_norm 32767


// void vFilter(){
//     int_least16_t *output1 = (int_least16_t *)calloc(length, sizeof(int_least16_t));


// }


// LMS Algorithm
void LMS_filter(int_least16_t *input, const char *file_path, size_t length)
{
    float weights[FILTER_ORDER] = {0};
    float buffer[FILTER_ORDER] = {0};
    FILE *file = fopen(file_path, "a");
    float *desired = (float *)input;

    for (size_t n = 0; n < length; n++)
    {
        memmove(&buffer[1], buffer, (FILTER_ORDER - 1) * sizeof(float));
        buffer[0] = input[n] / for_norm;

        float y = 0;
        dsps_dotprod_f32(weights, buffer, &y, FILTER_ORDER);

        float e = desired[n] - y;
        for (int i = 0; i < FILTER_ORDER; i++)
        {
            weights[i] += MU * e * buffer[i];
            if (weights[i] > 10)
                weights[i] = 10;
            if (weights[i] < -10)
                weights[i] = -10;
        }
        fprintf(file, "%f\n", y);
        if (isnan(y) || isinf(y))
        {
            ESP_LOGE("LMS", "NaN detected, resetting weights");
            memset(weights, 0, sizeof(weights));
        }
    }
    fclose(file);
}



// Normalized LMS (NLMS) Algorithm
void NLMS_filter(int_least16_t *input, const char *file_path, size_t length)
{
    float weights[FILTER_ORDER] = {0};
    float buffer[FILTER_ORDER] = {0};
    float *desired = (float *)input;
    FILE *file = fopen(file_path, "a");
    for (size_t n = 0; n < length; n++)
    {
        memmove(&buffer[1], buffer, (FILTER_ORDER - 1) * sizeof(float));
        buffer[0] = input[n] / for_norm;

        float y = 0;
        dsps_dotprod_f32(weights, buffer, &y, FILTER_ORDER);

        float e = desired[n] - y;
        float norm_factor = EPSILON;
        dsps_dotprod_f32(buffer, buffer, &norm_factor, FILTER_ORDER);
        norm_factor += EPSILON;

        for (int i = 0; i < FILTER_ORDER; i++)
            weights[i] += (MU * e / norm_factor) * buffer[i];

        fprintf(file, "%f\n", y);
    }
    fclose(file);
}

// Recursive Least Squares (RLS) Algorithm
void RLS_filter(int_least16_t *input, const char *file_path, size_t length)
{
    FILE *file = fopen(file_path, "a");
    float *desired = (float *)input;
    float weights[FILTER_ORDER] = {0};
    float P[FILTER_ORDER][FILTER_ORDER] = {{0}};
    float buffer[FILTER_ORDER] = {0};

    for (int i = 0; i < FILTER_ORDER; i++)
        P[i][i] = 1.0f; // Initialize P matrix

    for (size_t n = 0; n < length; n++)
    {
        memmove(&buffer[1], buffer, (FILTER_ORDER - 1) * sizeof(float));
        buffer[0] = input[n] / for_norm;

        float y = 0;
        dsps_dotprod_f32(weights, buffer, &y, FILTER_ORDER);

        float K[FILTER_ORDER] = {0};
        float alpha = 1.0f / (LAMBDA + buffer[0] * P[0][0]);
        for (int i = 0; i < FILTER_ORDER; i++)
            K[i] = P[i][0] * alpha;

        float e = desired[n] - y;
        for (int i = 0; i < FILTER_ORDER; i++)
            weights[i] += K[i] * e;

        for (int i = 0; i < FILTER_ORDER; i++)
        {
            for (int j = 0; j < FILTER_ORDER; j++)
            {
                P[i][j] -= K[i] * buffer[j] * P[0][j];
                P[i][j] /= LAMBDA;
            }
        }

        fprintf(file, "%f\n", y);
    }
    fclose(file);
}


// Constant Modulus Algorithm (CMA)
void CMA_filter(int_least16_t *input, const char *file_path, size_t length)
{
    FILE *file = fopen(file_path, "a");

    float weights[FILTER_ORDER] = {0};
    float buffer[FILTER_ORDER] = {0};

    for (size_t n = 0; n < length; n++)
    {
        memmove(&buffer[1], buffer, (FILTER_ORDER - 1) * sizeof(float));
        buffer[0] = input[n] / for_norm;

        float y = 0;
        dsps_dotprod_f32(weights, buffer, &y, FILTER_ORDER);

        float e = y * (y * y - 1);
        for (int i = 0; i < FILTER_ORDER; i++)
            weights[i] -= MU * e * buffer[i];

        fprintf(file, "%f\n", y);
    }
    fclose(file);
}

void fir_bandpass_filter()
{
}

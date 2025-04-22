#ifndef GATT_SERVER_H
#define GATT_SERVER_H

#include "blehr_sens.h"
#include "record.h"

#include <sys/time.h>
#include "host/ble_hs.h"
#include "host/ble_uuid.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"

uint16_t ots_object_data_handle;

static int gatt_svr_chr_access_ots_data(uint16_t conn_handle, uint16_t attr_handle, struct ble_gatt_access_ctxt *ctxt, void *arg);

static int gatt_svr_chr_access_time_update(uint16_t conn_handle, uint16_t attr_handle, struct ble_gatt_access_ctxt *ctxt, void *arg);

static int gatt_svr_chr_access_mode_update(uint16_t conn_handle, uint16_t attr_handle, struct ble_gatt_access_ctxt *ctxt, void *arg);

#endif

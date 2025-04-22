idf_component_register(SRCS "main.c" "gatt_svr.c" "record.c" "filtering.c"
                    INCLUDE_DIRS ".")
spiffs_create_partition_image(storage ../storage FLASH_IN_PROJECT)

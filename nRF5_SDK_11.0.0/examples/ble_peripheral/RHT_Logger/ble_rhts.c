
#include "ble_rhts.h"
#include "ble_srv_common.h"
#include "sdk_common.h"
#include "stdio.h"


extern void measurement_handler(uint8_t command, uint8_t received_measurement_interval_in_minutes);

						uint8_t data[4]={0,0, 0, 0};

/**@brief Function for handling the Connect event.
 *
 * @param[in] p_rhts      RHT Service structure.
 * @param[in] p_ble_evt  Event received from the BLE stack.
 */
static void on_connect(ble_rhts_t * p_rhts, ble_evt_t * p_ble_evt)
{
    p_rhts->conn_handle = p_ble_evt->evt.gap_evt.conn_handle;
}


/**@brief Function for handling the Disconnect event.
 *
 * @param[in] p_rhts      LED Button Service structure.
 * @param[in] p_ble_evt  Event received from the BLE stack.
 */
static void on_disconnect(ble_rhts_t * p_rhts, ble_evt_t * p_ble_evt)
{
    UNUSED_PARAMETER(p_ble_evt);
    p_rhts->conn_handle = BLE_CONN_HANDLE_INVALID;
		p_rhts->command_notif_enabled = false;
	
}


/**@brief Function for handling the Write event.
 *
 * @param[in] p_rhts     RHT Service structure.
 * @param[in] p_ble_evt  Event received from the BLE stack.
 */
static void on_write(ble_rhts_t * p_rhts, ble_evt_t * p_ble_evt)
{

    ble_gatts_evt_write_t * p_evt_write = &p_ble_evt->evt.gatts_evt.params.write;

	uint8_t received_measurement_interval_in_minutes =	1;
	uint8_t command=0;

	 if(p_ble_evt->evt.gatts_evt.params.write.handle ==  p_rhts->command_char_handles.value_handle)
    {
			// Get data
			//uint8_t data[4]={0,0, 0, 0};
			
		for (uint32_t i = 0; i < p_evt_write->len; i++)
		{
				data[i] = p_evt_write->data[i];
		}
			//we can use only one byte because the Android app allows only measurement period from 1 to 240 min
			received_measurement_interval_in_minutes = (uint16_t)data[1];	
			command = data[0];		
			//printf("rd=%u %u %u %u\n\r", data[0], data[1], data[2], data[3]);
			measurement_handler(command, received_measurement_interval_in_minutes );
		}					
	
}



void ble_rhts_on_ble_evt(ble_rhts_t * p_rhts, ble_evt_t * p_ble_evt)
{
    switch (p_ble_evt->header.evt_id)
    {
        case BLE_GAP_EVT_CONNECTED:
            on_connect(p_rhts, p_ble_evt);
            break;

        case BLE_GAP_EVT_DISCONNECTED:
            on_disconnect(p_rhts, p_ble_evt);
            break;
            
        case BLE_GATTS_EVT_WRITE:
            on_write(p_rhts, p_ble_evt);
            break;

        default:
            // No implementation needed.
            break;
    }
}


static uint32_t status_char_add(ble_rhts_t * p_rhts)
{
    ble_gatts_char_md_t char_md;
    ble_gatts_attr_t    attr_char_value;
    ble_uuid_t          ble_uuid;
    ble_gatts_attr_md_t attr_md;

    memset(&char_md, 0, sizeof(char_md));

    char_md.char_props.read   = 1;
    char_md.char_props.write  = 1;
		//char_md.char_props.notify = 1;
    char_md.p_char_user_desc  = NULL;
    char_md.p_char_pf         = NULL;
    char_md.p_user_desc_md    = NULL;
    char_md.p_cccd_md         = NULL;
    char_md.p_sccd_md         = NULL;

    ble_uuid.type = p_rhts->uuid_type;
    ble_uuid.uuid = RHTS_UUID_STATUS_CHAR;
    
    memset(&attr_md, 0, sizeof(attr_md));

    BLE_GAP_CONN_SEC_MODE_SET_OPEN(&attr_md.read_perm);
    BLE_GAP_CONN_SEC_MODE_SET_OPEN(&attr_md.write_perm);
    attr_md.vloc       = BLE_GATTS_VLOC_STACK;
    attr_md.rd_auth    = 0;
    attr_md.wr_auth    = 0;
    attr_md.vlen       = 0;
    
    memset(&attr_char_value, 0, sizeof(attr_char_value));

    attr_char_value.p_uuid       = &ble_uuid;
    attr_char_value.p_attr_md    = &attr_md;
    attr_char_value.init_len     = sizeof(uint32_t);
    attr_char_value.init_offs    = 0;
    attr_char_value.max_len      = sizeof(uint32_t);
    attr_char_value.p_value      = NULL;

    return sd_ble_gatts_characteristic_add(p_rhts->service_handle,
                                           &char_md,
                                           &attr_char_value,
                                           &p_rhts->status_char_handles);
}

static uint32_t measurement_char_add(ble_rhts_t * p_rhts)
{
    ble_gatts_char_md_t char_md;
    ble_gatts_attr_t    attr_char_value;
    ble_uuid_t          ble_uuid;
    ble_gatts_attr_md_t attr_md;

    memset(&char_md, 0, sizeof(char_md));

    char_md.char_props.read   = 1;
    char_md.char_props.write  = 1;
		//char_md.char_props.notify = 1;
    char_md.p_char_user_desc  = NULL;
    char_md.p_char_pf         = NULL;
    char_md.p_user_desc_md    = NULL;
    char_md.p_cccd_md         = NULL;
    char_md.p_sccd_md         = NULL;

    ble_uuid.type = p_rhts->uuid_type;
    ble_uuid.uuid = RHTS_UUID_MEASUREMENT_CHAR;
    
    memset(&attr_md, 0, sizeof(attr_md));

    BLE_GAP_CONN_SEC_MODE_SET_OPEN(&attr_md.read_perm);
    BLE_GAP_CONN_SEC_MODE_SET_OPEN(&attr_md.write_perm);
    attr_md.vloc       = BLE_GATTS_VLOC_STACK;
    attr_md.rd_auth    = 0;
    attr_md.wr_auth    = 0;
    attr_md.vlen       = 0;
    
    memset(&attr_char_value, 0, sizeof(attr_char_value));

    attr_char_value.p_uuid       = &ble_uuid;
    attr_char_value.p_attr_md    = &attr_md;
    attr_char_value.init_len     = sizeof(uint32_t);
    attr_char_value.init_offs    = 0;
    attr_char_value.max_len      = sizeof(uint32_t);
    attr_char_value.p_value      = NULL;

    return sd_ble_gatts_characteristic_add(p_rhts->service_handle,
                                           &char_md,
                                           &attr_char_value,
                                           &p_rhts->measurement_char_handles);
}

static uint32_t command_char_add(ble_rhts_t * p_rhts)
{
    ble_gatts_char_md_t char_md;
    ble_gatts_attr_t    attr_char_value;
    ble_uuid_t          ble_uuid;
    ble_gatts_attr_md_t attr_md;

    memset(&char_md, 0, sizeof(char_md));

    char_md.char_props.read   = 1;
    char_md.char_props.write  = 1;
	char_md.char_props.notify = 1;
    char_md.p_char_user_desc  = NULL;
    char_md.p_char_pf         = NULL;
    char_md.p_user_desc_md    = NULL;
    char_md.p_cccd_md         = NULL;
    char_md.p_sccd_md         = NULL;

    ble_uuid.type = p_rhts->uuid_type;
    ble_uuid.uuid = RHTS_UUID_COMMAND_CHAR;
    
    memset(&attr_md, 0, sizeof(attr_md));

    BLE_GAP_CONN_SEC_MODE_SET_OPEN(&attr_md.read_perm);
    BLE_GAP_CONN_SEC_MODE_SET_OPEN(&attr_md.write_perm);
    attr_md.vloc       = BLE_GATTS_VLOC_STACK;
    attr_md.rd_auth    = 1;
    attr_md.wr_auth    = 1;
    attr_md.vlen       = 0;
    
    memset(&attr_char_value, 0, sizeof(attr_char_value));

    attr_char_value.p_uuid       = &ble_uuid;
    attr_char_value.p_attr_md    = &attr_md;
    attr_char_value.init_len     = sizeof(uint32_t);
    attr_char_value.init_offs    = 0;
    attr_char_value.max_len      = sizeof(uint32_t);
    attr_char_value.p_value      = NULL;

    return sd_ble_gatts_characteristic_add(p_rhts->service_handle,
                                           &char_md,
                                           &attr_char_value,
                                           &p_rhts->command_char_handles);
}

uint32_t ble_rhts_init(ble_rhts_t * p_rhts)
{
    uint32_t   err_code;
    ble_uuid_t ble_uuid;

    // Initialize service structure.
    p_rhts->conn_handle       = BLE_CONN_HANDLE_INVALID;

    // Add service.
    ble_uuid128_t base_uuid = {RHTS_UUID_BASE};
    err_code = sd_ble_uuid_vs_add(&base_uuid, &p_rhts->uuid_type);
    VERIFY_SUCCESS(err_code);

    ble_uuid.type = p_rhts->uuid_type;
    ble_uuid.uuid = RHTS_UUID_SERVICE;

    err_code = sd_ble_gatts_service_add(BLE_GATTS_SRVC_TYPE_PRIMARY, &ble_uuid, &p_rhts->service_handle);
    VERIFY_SUCCESS(err_code);

    // Add characteristics.

	err_code = status_char_add(p_rhts);
    VERIFY_SUCCESS(err_code);
		
	err_code = measurement_char_add(p_rhts);
    VERIFY_SUCCESS(err_code);
		
	err_code = command_char_add(p_rhts);
    VERIFY_SUCCESS(err_code);
		

    return NRF_SUCCESS;
}


uint32_t ble_rhts_status_char_set(ble_rhts_t * p_rhts, uint32_t value)
{
	ble_gatts_value_t new_value;

	memset(&new_value, 0, sizeof(new_value));
	new_value.len     = sizeof(value);
	new_value.offset  = 0;
	new_value.p_value = (uint8_t*) &value;
	
	return sd_ble_gatts_value_set(p_rhts->conn_handle, p_rhts->status_char_handles.value_handle, &new_value);

}



uint32_t ble_rhts_measurement_char_set(ble_rhts_t * p_rhts, uint32_t value)
{
	ble_gatts_value_t new_value;

	memset(&new_value, 0, sizeof(new_value));
	new_value.len     = sizeof(value);
	new_value.offset  = 0;
	new_value.p_value = (uint8_t*) &value;
	
	return sd_ble_gatts_value_set(p_rhts->conn_handle, p_rhts->measurement_char_handles.value_handle, &new_value);

}




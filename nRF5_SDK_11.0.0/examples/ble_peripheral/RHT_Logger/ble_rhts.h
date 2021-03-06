
#ifndef BLE_RHTS_H__
#define BLE_RHTS_H__

#include <stdint.h>
#include <stdbool.h>
#include "ble.h"
#include "ble_srv_common.h"

#define RHTS_UUID_BASE        {0x65, 0x50, 0x15, 0x20, 0x5F, 0x78, 0x23, 0x15, \
                              0xDE, 0xEF, 0x12, 0x12, 0x15, 0x00, 0x00, 0x00}
#define RHTS_UUID_SERVICE     0x1521
#define RHTS_UUID_STATUS_CHAR 0x1522
#define RHTS_UUID_MEASUREMENT_CHAR 0x1523
#define RHTS_UUID_COMMAND_CHAR 0x1524


												



// Forward declaration of the ble_rhts_t type. 
typedef struct ble_rhts_s ble_rhts_t;



/**@brief LED Button Service structure. This structure contains various status information for the service. */
struct ble_rhts_s
{
  uint16_t                  service_handle;      /**< Handle of RHT Service (as provided by the BLE stack). */
	ble_gatts_char_handles_t  status_char_handles;    /**< Handles related to the rht Characteristic. */
	ble_gatts_char_handles_t  measurement_char_handles;    /**< Handles related to the temp Characteristic. */
	ble_gatts_char_handles_t  command_char_handles;    /**< Handles related to the id Characteristic. */
	uint8_t                   uuid_type;           /**< UUID type for the RHT Service. */
	uint16_t                  conn_handle;         /**< Handle of the current connection (as provided by the BLE stack). BLE_CONN_HANDLE_INVALID if not in a connection. */
	bool                   	  command_notif_enabled; /**< Variable to indicate if the peer has enabled notification of the characteristic.*/
	
};

/**@brief Function for initializing the RHT Service.
 *
 * @param[out] p_rhts      RHT Service structure. This structure must be supplied by
 *                        the application. It is initialized by this function and will later
 *                        be used to identify this particular service instance.
 *
 * @retval NRF_SUCCESS If the service was initialized successfully. Otherwise, an error code is returned.
 */
uint32_t ble_rhts_init(ble_rhts_t * p_rhts);

/**@brief Function for handling the application's BLE stack events.
 *
 * @details This function handles all events from the BLE stack that are of interest to the LED Button Service.
 *
 * @param[in] p_rhts      LED Button Service structure.
 * @param[in] p_ble_evt  Event received from the BLE stack.
 */
void ble_rhts_on_ble_evt(ble_rhts_t * p_rhts, ble_evt_t * p_ble_evt);

uint32_t ble_rhts_status_char_set(ble_rhts_t * p_rhts, uint32_t value);
uint32_t ble_rhts_measurement_char_set(ble_rhts_t * p_rhts, uint32_t value);

#endif // BLE_RHTS_H__

/** @} */


/** 
 * @brief RHT Logger Application main file.
 *
 * This file contains the source code for a sample server application using the RHT service.
 */

#include <stdint.h>
#include <string.h>
#include "nordic_common.h"
#include "nrf.h"
#include "app_error.h"
#include "ble.h"
#include "ble_hci.h"
#include "ble_srv_common.h"
#include "ble_advdata.h"
#include "ble_conn_params.h"
#include "softdevice_handler.h"
#include "app_timer.h"
#include "ble_rhts.h"
#include "bsp.h"
#include "ble_gap.h"
#include "nrf_soc.h"
#include "nrf_nvic.h"
#include "app_util_platform.h"
#include "nrf_drv_timer.h"
#include <math.h>
#include "twi_master.h"
#include "htu21d.h"
#include "app_util.h"
#include "nrf_delay.h"


#define CENTRAL_LINK_COUNT              0                                           /**< Number of central links used by the application. When changing this number remember to adjust the RAM settings*/
#define PERIPHERAL_LINK_COUNT           1                                           /**< Number of peripheral links used by the application. When changing this number remember to adjust the RAM settings*/

#define ADVERTISING_LED_PIN             BSP_LED_0_MASK                              /**< Is on when device is advertising. */
#define CONNECTED_LED_PIN               BSP_LED_1_MASK                              /**< Is on when device has connected. */


#define DEVICE_NAME                     "RHT_Sensor"                             /**< Name of device. Will be included in the advertising data. */

#define APP_ADV_INTERVAL                1000                                          /**< The advertising interval (in units of 0.625 ms; this value corresponds to 40 ms). */
#define APP_ADV_TIMEOUT_IN_SECONDS      BLE_GAP_ADV_TIMEOUT_GENERAL_UNLIMITED       /**< The advertising time-out (in units of seconds). When set to 0, we will never time out. */

#define APP_TIMER_PRESCALER             0                                           /**< Value of the RTC1 PRESCALER register. */
#define APP_TIMER_MAX_TIMERS            6                                           /**< Maximum number of simultaneously created timers. */
#define APP_TIMER_OP_QUEUE_SIZE         4                                           /**< Size of timer operation queues. */

#define MIN_CONN_INTERVAL               MSEC_TO_UNITS(100, UNIT_1_25_MS)            /**< Minimum acceptable connection interval (0.125 seconds). */
#define MAX_CONN_INTERVAL               MSEC_TO_UNITS(200, UNIT_1_25_MS)            /**< Maximum acceptable connection interval (0.25 second). */
#define SLAVE_LATENCY                   0                                           /**< Slave latency. */
#define CONN_SUP_TIMEOUT                MSEC_TO_UNITS(4000, UNIT_10_MS)             /**< Connection supervisory time-out (4 seconds). */
#define FIRST_CONN_PARAMS_UPDATE_DELAY  APP_TIMER_TICKS(20000, APP_TIMER_PRESCALER) /**< Time from initiating event (connect or start of notification) to first time sd_ble_gap_conn_param_update is called (15 seconds). */
#define NEXT_CONN_PARAMS_UPDATE_DELAY   APP_TIMER_TICKS(5000, APP_TIMER_PRESCALER)  /**< Time between each call to sd_ble_gap_conn_param_update after the first call (5 seconds). */
#define MAX_CONN_PARAMS_UPDATE_COUNT    3                                           /**< Number of attempts before giving up the connection parameter negotiation. */

#define APP_GPIOTE_MAX_USERS            1                                           /**< Maximum number of users of the GPIOTE handler. */

#define DEAD_BEEF                       0xDEADBEEF                                  /**< Value used as error code on stack dump, can be used to identify stack location on stack unwind. */

static uint16_t                         m_conn_handle = BLE_CONN_HANDLE_INVALID;    /**< Handle of the current connection. */
static ble_rhts_t                        m_rhts;                                      /**< LED Button Service instance. */


#define CURRENT_MEASUREMENTS 1U
#define CURRENT_MEASUREMENTS_RECEIVED 2U
#define MEASUREMENTS_HISTORY 3U 
#define HISTORY_MEASUREMENTS_RECEIVED 4U
#define DELETE_HISTORY 5U
#define HISTORY_DELETED 6U
#define CHANGE_INTERVAL 7U
#define INTERVAL_CHANGED 8U
#define CONNECTED 9U

#define ARRAY_SIZE 200

APP_TIMER_DEF(m_connection_event_timer_id);
#define CONNECTION_EVENT_TIMER_INTERVAL     APP_TIMER_TICKS(20, APP_TIMER_PRESCALER) // 25 ms intervals

const nrf_drv_timer_t TIMER_RHT_MEASUREMENT = NRF_DRV_TIMER_INSTANCE(1);


volatile uint32_t timer_overflow_count=0;

static int16_t current_humidity_measurement=0;
static int16_t current_temperature_measurement=0;

volatile uint32_t connection_counter=0;
#define MAX_CONN_EVENTS 1000000U

volatile uint32_t interval=60; 

volatile uint8_t status;
volatile uint16_t send_counter=0;
volatile uint8_t current_measurement_wait_counter=0;
extern uint16_t measurement_interval_in_minutes;
extern uint8_t command;
extern uint8_t status_received;

typedef enum
{
	ERROR=0U,
	READY=1U,
	BUSY=2U,
	COMPLETE=3U
}NRF_STATE_t;

typedef struct
{
	int16_t temperature_value_array[ARRAY_SIZE];
	uint16_t time_array[ARRAY_SIZE];
	int16_t humidity_value_array[ARRAY_SIZE];
	uint16_t next_measurement_position;
	uint16_t number_of_elements_existing;
}Measurements_History_t;

static Measurements_History_t History;
Measurements_History_t* History_p;

NRF_STATE_t nRF_State;


uint32_t parameters_merge(uint16_t parameter1, uint16_t parameter2)
{
	uint32_t result=0;
	result=65536*parameter1 | parameter2;

	return result;
}

uint32_t command_parameters_merge(uint16_t parameter1, uint8_t parameter2, uint8_t parameter3)
{
	uint32_t result=0;
	result= (uint32_t)65536*parameter1 | (parameter2<<8) | parameter3;

	return result;
}


static uint32_t end_connection(void)
{
	uint32_t err_code;
	
	  if (m_conn_handle != BLE_CONN_HANDLE_INVALID)
    {
        err_code = sd_ble_gap_disconnect(m_conn_handle,  BLE_HCI_REMOTE_USER_TERMINATED_CONNECTION);
        if (err_code != NRF_SUCCESS) return err_code;
    }
		
	return NRF_SUCCESS;
}

void history_struct_init(void)
{
	History_p = &History;

	memset((void*)History_p, 0, sizeof(Measurements_History_t)); 
}

void measurements_history_add_element_to_array(int16_t new_temperature_measurement, int16_t new_humidity_measurement, uint16_t measurement_period)
{
	static uint16_t position = 0; 
	static uint16_t existing_elements;
	int16_t loop_counter = 0;
	
	
	if(History_p->number_of_elements_existing > ARRAY_SIZE)
	{
		History_p->number_of_elements_existing = ARRAY_SIZE + 1;
	}
	else
	{
		History_p->number_of_elements_existing++;
	}
	
	History_p->next_measurement_position++;
	
	if(History_p->next_measurement_position >= ARRAY_SIZE)
	{
		History_p->next_measurement_position=0;
	}
	
	if(History_p->next_measurement_position>=1)
	{
		position = History_p->next_measurement_position-1;
	}
	else
	{
		position = ARRAY_SIZE - 1; 
	}
	existing_elements = History_p->number_of_elements_existing;
	
	History_p->temperature_value_array[position] = new_temperature_measurement;
	History_p->humidity_value_array[position] = new_humidity_measurement;
	History_p->time_array[position] = 0;
	
	if(existing_elements >= ARRAY_SIZE)
	{
	
		if(position>=1)
		{
			for(loop_counter = (position-1); loop_counter >= 0; loop_counter--)
			{
				History_p->time_array[loop_counter] += measurement_period;		
			}	
						
			for(loop_counter = (ARRAY_SIZE - 1); loop_counter >= (position + 1); loop_counter--)
			{
				History_p->time_array[loop_counter] += measurement_period;		
			}
		}
		else
		{
			for(loop_counter = (ARRAY_SIZE - 1); loop_counter >= 1; loop_counter--)
			{
				History_p->time_array[loop_counter] += measurement_period;		
			}
		}
	}
	else
	{
		for(loop_counter = (position-1); loop_counter >= 0; loop_counter--)
		{
			History_p->time_array[loop_counter] += measurement_period;		
		}		
	}	
	
}

uint16_t measurements_history_get_position_from_array(uint16_t sent_element_counter)
{
	static uint16_t position = 0; 
	static uint16_t existing_elements;
	uint16_t element_to_send;
	
	existing_elements = History_p->number_of_elements_existing;
	position = History_p->next_measurement_position-1;
	
	if(existing_elements <= ARRAY_SIZE)
	{
		element_to_send = position - sent_element_counter; 
	}
	else
	{
		if((int16_t)(position - sent_element_counter) >= 0)
		{
			 element_to_send = position - sent_element_counter; 
		}
		else
		{
			element_to_send = ARRAY_SIZE - sent_element_counter + position; 
		}				
	}

	return element_to_send; 
}

void erase_measurements_history(void)
{
		memset((void*)History_p, 0, sizeof(Measurements_History_t)); 
}

/**@brief Function for assert macro callback.
 *
 * @details This function will be called in case of an assert in the SoftDevice.
 *
 * @warning This handler is an example only and does not fit a final product. You need to analyze 
 *          how your product is supposed to react in case of Assert.
 * @warning On assert from the SoftDevice, the system can only recover on reset.
 *
 * @param[in] line_num    Line number of the failing ASSERT call.
 * @param[in] p_file_name File name of the failing ASSERT call.
 */
void assert_nrf_callback(uint16_t line_num, const uint8_t * p_file_name)
{
    app_error_handler(DEAD_BEEF, line_num, p_file_name);
}


/**@brief Function for the LEDs initialization.
 *
 * @details Initializes all LEDs used by the application.
 */
static void leds_init(void)
{
    LEDS_CONFIGURE(ADVERTISING_LED_PIN | CONNECTED_LED_PIN);
    LEDS_OFF(ADVERTISING_LED_PIN | CONNECTED_LED_PIN);
}




/**@brief Function for the GAP initialization.
 *
 * @details This function sets up all the necessary GAP (Generic Access Profile) parameters of the
 *          device including the device name, appearance, and the preferred connection parameters.
 */
static void gap_params_init(void)
{
    uint32_t                err_code;
    ble_gap_conn_params_t   gap_conn_params;
    ble_gap_conn_sec_mode_t sec_mode;

    BLE_GAP_CONN_SEC_MODE_SET_OPEN(&sec_mode);

    err_code = sd_ble_gap_device_name_set(&sec_mode,
                                          (const uint8_t *)DEVICE_NAME,
                                          strlen(DEVICE_NAME));
    APP_ERROR_CHECK(err_code);

    memset(&gap_conn_params, 0, sizeof(gap_conn_params));

    gap_conn_params.min_conn_interval = MIN_CONN_INTERVAL;
    gap_conn_params.max_conn_interval = MAX_CONN_INTERVAL;
    gap_conn_params.slave_latency     = SLAVE_LATENCY;
    gap_conn_params.conn_sup_timeout  = CONN_SUP_TIMEOUT;

    err_code = sd_ble_gap_ppcp_set(&gap_conn_params);
    APP_ERROR_CHECK(err_code);
}


/**@brief Function for initializing the Advertising functionality.
 *
 * @details Encodes the required advertising data and passes it to the stack.
 *          Also builds a structure to be passed to the stack when starting advertising.
 */
static void advertising_init(void)
{
    uint32_t      err_code;
    ble_advdata_t advdata;
    ble_advdata_t scanrsp;

    ble_uuid_t adv_uuids[] = {{RHTS_UUID_SERVICE, m_rhts.uuid_type}};

    // Build and set advertising data
    memset(&advdata, 0, sizeof(advdata));

    advdata.name_type          = BLE_ADVDATA_FULL_NAME;
    advdata.include_appearance = true;
    advdata.flags              = BLE_GAP_ADV_FLAGS_LE_ONLY_GENERAL_DISC_MODE;


    memset(&scanrsp, 0, sizeof(scanrsp));
    scanrsp.uuids_complete.uuid_cnt = sizeof(adv_uuids) / sizeof(adv_uuids[0]);
    scanrsp.uuids_complete.p_uuids  = adv_uuids;

    err_code = ble_advdata_set(&advdata, &scanrsp);
    APP_ERROR_CHECK(err_code);
}


/**@brief Function for initializing services that will be used by the application.
 */
static void services_init(void)
{
    uint32_t       err_code;

    err_code = ble_rhts_init(&m_rhts);
    APP_ERROR_CHECK(err_code);
}


/**@brief Function for handling the Connection Parameters Module.
 *
 * @details This function will be called for all events in the Connection Parameters Module that
 *          are passed to the application.
 *
 * @note All this function does is to disconnect. This could have been done by simply
 *       setting the disconnect_on_fail config parameter, but instead we use the event
 *       handler mechanism to demonstrate its use.
 *
 * @param[in] p_evt  Event received from the Connection Parameters Module.
 */
static void on_conn_params_evt(ble_conn_params_evt_t * p_evt)
{
    uint32_t err_code;

    if (p_evt->evt_type == BLE_CONN_PARAMS_EVT_FAILED)
    {
        err_code = sd_ble_gap_disconnect(m_conn_handle, BLE_HCI_CONN_INTERVAL_UNACCEPTABLE);
        APP_ERROR_CHECK(err_code);
    }
}


/**@brief Function for handling a Connection Parameters error.
 *
 * @param[in] nrf_error  Error code containing information about what went wrong.
 */
static void conn_params_error_handler(uint32_t nrf_error)
{
    APP_ERROR_HANDLER(nrf_error);
}


/**@brief Function for initializing the Connection Parameters module.
 */
static void conn_params_init(void)
{
    uint32_t               err_code;
    ble_conn_params_init_t cp_init;

    memset(&cp_init, 0, sizeof(cp_init));

    cp_init.p_conn_params                  = NULL;
    cp_init.first_conn_params_update_delay = FIRST_CONN_PARAMS_UPDATE_DELAY;
    cp_init.next_conn_params_update_delay  = NEXT_CONN_PARAMS_UPDATE_DELAY;
    cp_init.max_conn_params_update_count   = MAX_CONN_PARAMS_UPDATE_COUNT;
    cp_init.start_on_notify_cccd_handle    = BLE_GATT_HANDLE_INVALID;
    cp_init.disconnect_on_fail             = false;
    cp_init.evt_handler                    = on_conn_params_evt;
    cp_init.error_handler                  = conn_params_error_handler;

    err_code = ble_conn_params_init(&cp_init);
    APP_ERROR_CHECK(err_code);
}


/**@brief Function for starting advertising.
 */
static void advertising_start(void)
{
    uint32_t             err_code;
    ble_gap_adv_params_t adv_params;

    // Start advertising
    memset(&adv_params, 0, sizeof(adv_params));

    adv_params.type        = BLE_GAP_ADV_TYPE_ADV_IND;
    adv_params.p_peer_addr = NULL;
    adv_params.fp          = BLE_GAP_ADV_FP_ANY;
    adv_params.interval    = APP_ADV_INTERVAL;
    adv_params.timeout     = APP_ADV_TIMEOUT_IN_SECONDS;

    err_code = sd_ble_gap_adv_start(&adv_params);
    APP_ERROR_CHECK(err_code);
    LEDS_ON(ADVERTISING_LED_PIN);
}


/**@brief Function for handling the Application's BLE stack events.
 *
 * @param[in] p_ble_evt  Bluetooth stack event.
 */
static void on_ble_evt(ble_evt_t * p_ble_evt)
{
    uint32_t err_code;

    switch (p_ble_evt->header.evt_id)
    {
        case BLE_GAP_EVT_CONNECTED:
            LEDS_ON(CONNECTED_LED_PIN);
            LEDS_OFF(ADVERTISING_LED_PIN);
            m_conn_handle = p_ble_evt->evt.gap_evt.conn_handle;
				    app_timer_start(m_connection_event_timer_id, CONNECTION_EVENT_TIMER_INTERVAL, NULL);
						connection_counter=0;
						command = 0;
            break;

        case BLE_GAP_EVT_DISCONNECTED:
            LEDS_OFF(CONNECTED_LED_PIN);
            m_conn_handle = BLE_CONN_HANDLE_INVALID;
						app_timer_stop(m_connection_event_timer_id);
						connection_counter=0;
						current_measurement_wait_counter=0;
						send_counter = 0; 
						command = 0;
            advertising_start();
            break;

        case BLE_GAP_EVT_SEC_PARAMS_REQUEST:
            // Pairing not supported
            err_code = sd_ble_gap_sec_params_reply(m_conn_handle,
                                                   BLE_GAP_SEC_STATUS_PAIRING_NOT_SUPP,
                                                   NULL,
                                                   NULL);
            APP_ERROR_CHECK(err_code);
            break;

        case BLE_GATTS_EVT_SYS_ATTR_MISSING:
            // No system attributes have been stored.
            err_code = sd_ble_gatts_sys_attr_set(m_conn_handle, NULL, 0, 0);
            APP_ERROR_CHECK(err_code);
            break;

        default:
            // No implementation needed.
            break;
    }
}


/**@brief Function for dispatching a BLE stack event to all modules with a BLE stack event handler.
 *
 * @details This function is called from the scheduler in the main loop after a BLE stack
 *          event has been received.
 *
 * @param[in] p_ble_evt  Bluetooth stack event.
 */
static void ble_evt_dispatch(ble_evt_t * p_ble_evt)
{
    on_ble_evt(p_ble_evt);
    ble_conn_params_on_ble_evt(p_ble_evt);
    ble_rhts_on_ble_evt(&m_rhts, p_ble_evt);
}


/**@brief Function for initializing the BLE stack.
 *
 * @details Initializes the SoftDevice and the BLE event interrupt.
 */
static void ble_stack_init(void)
{
    uint32_t err_code;
    
    nrf_clock_lf_cfg_t clock_lf_cfg = NRF_CLOCK_LFCLKSRC;
    
    // Initialize the SoftDevice handler module.
    SOFTDEVICE_HANDLER_INIT(&clock_lf_cfg, NULL);
    
    ble_enable_params_t ble_enable_params;
    err_code = softdevice_enable_get_default_config(CENTRAL_LINK_COUNT,
                                                    PERIPHERAL_LINK_COUNT,
                                                    &ble_enable_params);
    APP_ERROR_CHECK(err_code);
    
    //Check the ram settings against the used number of links
    CHECK_RAM_START_ADDR(CENTRAL_LINK_COUNT,PERIPHERAL_LINK_COUNT);
    
    // Enable BLE stack.
    err_code = softdevice_enable(&ble_enable_params);
    APP_ERROR_CHECK(err_code);

    ble_gap_addr_t addr;

    err_code = sd_ble_gap_address_get(&addr);
    APP_ERROR_CHECK(err_code);
    err_code = sd_ble_gap_address_set(BLE_GAP_ADDR_CYCLE_MODE_NONE, &addr);
    APP_ERROR_CHECK(err_code);

    // Subscribe for BLE events.
    err_code = softdevice_ble_evt_handler_set(ble_evt_dispatch);
    APP_ERROR_CHECK(err_code);
}


static void timer_timeout_handler(void * p_context)
{	
		static uint32_t packet_temperature=0;
		static uint32_t packet_humidity=0;
		static uint32_t packet_command=0;
		static uint16_t RHT_step=0;

		switch(command)
		{
			case CURRENT_MEASUREMENTS:
				
				nRF_State = BUSY; 
				status = (uint8_t)nRF_State;
			
					if(current_measurement_wait_counter==1)
					{
							i2c_temp_read(); 
							i2c_temp_conv();
					}
					else if(current_measurement_wait_counter==5)
					{
							current_temperature_measurement=i2c_temp_read();
							i2c_RH_conv(); 
					}
					else if(current_measurement_wait_counter==9)
					{
							current_humidity_measurement=i2c_RH_read(); 
					}	
					else if(current_measurement_wait_counter>=10)
					{
						nRF_State = COMPLETE; 
						status = (uint8_t)nRF_State;
						packet_temperature=parameters_merge(0U, (uint16_t)current_temperature_measurement);
						packet_humidity=parameters_merge(0U, (uint16_t)current_temperature_measurement);
						
						ble_rhts_temperature_char_update(&m_rhts, packet_temperature);		
						ble_rhts_humidity_char_update(&m_rhts, packet_humidity);	
						current_measurement_wait_counter=10;
					}
					
					packet_command=command_parameters_merge(measurement_interval_in_minutes, status, command);						
					ble_rhts_command_char_update(&m_rhts, packet_command);						

					current_measurement_wait_counter++;
			
			break;
			
			case CURRENT_MEASUREMENTS_RECEIVED: 

				nRF_State = READY;		
				status = (uint8_t)nRF_State;			
				packet_command=command_parameters_merge(measurement_interval_in_minutes, status, command);						
				ble_rhts_command_char_update(&m_rhts, packet_command);	
				current_measurement_wait_counter=0;
			
			break;
			
			case MEASUREMENTS_HISTORY:
						
				if(send_counter < ARRAY_SIZE && History_p->number_of_elements_existing >= send_counter)
				{
					nRF_State = BUSY; 
					status = (uint8_t)nRF_State;
					RHT_step = measurements_history_get_position_from_array(send_counter);
					packet_temperature=parameters_merge((uint16_t)(History_p->time_array[RHT_step]),
																							(uint16_t)(History_p->temperature_value_array[RHT_step]));
					packet_humidity=parameters_merge((uint16_t)(History_p->time_array[RHT_step]),
																							(uint16_t)(History_p->humidity_value_array[RHT_step]));
					packet_command=command_parameters_merge(measurement_interval_in_minutes, status, command);						
					ble_rhts_command_char_update(&m_rhts, packet_command);						
					ble_rhts_temperature_char_update(&m_rhts, packet_temperature);		
					ble_rhts_humidity_char_update(&m_rhts, packet_humidity);	
					send_counter++;
				}
				else 
				{
					nRF_State = COMPLETE; 
					status = (uint8_t)nRF_State;
					packet_temperature=parameters_merge(0U, 0U);
					packet_humidity=parameters_merge(0U, 0U);
					packet_command=command_parameters_merge(measurement_interval_in_minutes, status, command);											
					ble_rhts_temperature_char_update(&m_rhts, packet_temperature);		
					ble_rhts_humidity_char_update(&m_rhts, packet_humidity);	
				}
				
				packet_command=command_parameters_merge(measurement_interval_in_minutes, status, command);						
				ble_rhts_command_char_update(&m_rhts, packet_command);						
	
			break;
			
			case HISTORY_MEASUREMENTS_RECEIVED: 

				nRF_State = READY;	
				status = (uint8_t)nRF_State;
				packet_command=command_parameters_merge(measurement_interval_in_minutes, status, command);						
				ble_rhts_command_char_update(&m_rhts, packet_command);	
				send_counter = 0;
			
			break;
			
			
			case DELETE_HISTORY:
			
				nRF_State = BUSY; 
				erase_measurements_history();
				nRF_State = COMPLETE;
				status = (uint8_t)nRF_State;
				packet_command=command_parameters_merge(measurement_interval_in_minutes, status, command);						
				ble_rhts_command_char_update(&m_rhts, packet_command);						

			
			break;
			
			case HISTORY_DELETED: 

				nRF_State = READY;	
				status = (uint8_t)nRF_State;
				packet_command=command_parameters_merge(measurement_interval_in_minutes, status, command);						
				ble_rhts_command_char_update(&m_rhts, packet_command);	
			
			break;
			
			case CHANGE_INTERVAL:
				
				nRF_State = BUSY; 
			
				if(measurement_interval_in_minutes <=1)
				{
					measurement_interval_in_minutes=1;
				}
				else if(measurement_interval_in_minutes >=240)
				{
					measurement_interval_in_minutes=240;
				}
				interval = (measurement_interval_in_minutes*60);
				
				nRF_State = COMPLETE;
				status = (uint8_t)nRF_State;
				packet_command=command_parameters_merge(measurement_interval_in_minutes, status, command);						
				
				ble_rhts_command_char_update(&m_rhts, packet_command);						
				ble_rhts_temperature_char_update(&m_rhts, packet_temperature);		
				ble_rhts_humidity_char_update(&m_rhts, packet_humidity);	
			
			break;
				
			case INTERVAL_CHANGED:

				nRF_State = READY;	
				status = (uint8_t)nRF_State;
				packet_command=command_parameters_merge(measurement_interval_in_minutes, status, command);						
				ble_rhts_command_char_update(&m_rhts, packet_command);	
			
			break;
			
			
			case CONNECTED:
			
				nRF_State = READY; 
				status = (uint8_t)nRF_State;
				packet_command=command_parameters_merge(measurement_interval_in_minutes, status, command);						
				
				ble_rhts_command_char_update(&m_rhts, packet_command);						
				ble_rhts_temperature_char_update(&m_rhts, packet_temperature);		
				ble_rhts_humidity_char_update(&m_rhts, packet_humidity);	
				send_counter=0;
				current_measurement_wait_counter = 0; 
			
			break;

			
			default:
			//this should not happen
			break;
			
		}

											
		connection_counter++;

		if(connection_counter>=MAX_CONN_EVENTS)
		{
			end_connection();
		}				
			
}


void timer_rht_measurement_event_handler(nrf_timer_event_t event_type, void* p_context)
{
    
    switch(event_type)
    {
        case NRF_TIMER_EVENT_COMPARE0:
					
					NRF_WDT->RR[0] = WDT_RR_RR_Reload;  //reset  watchdog
					timer_overflow_count++;

					if(timer_overflow_count==1)
					{
							i2c_temp_read(); 
							i2c_temp_conv();
					}
					else if(timer_overflow_count==2)
					{
							current_temperature_measurement=i2c_temp_read();
							i2c_RH_conv(); 
					}
					else if(timer_overflow_count==3)
					{
							current_humidity_measurement=i2c_RH_read(); 
					}
					else if(timer_overflow_count==4)
					{
							measurements_history_add_element_to_array(current_temperature_measurement, current_humidity_measurement, measurement_interval_in_minutes);
					}
					else if(timer_overflow_count==interval)
					{
						timer_overflow_count=0;
					}
					else
					{
						;
					}					
				
				break;
				
				default:
					break;
			}
}


/**@brief Function for the Timer initialization.
 *
 * @details Initializes the timer module.
 */
static void timers_init(void)
{
	ret_code_t err_code;

	uint32_t time_ms = 1000; //Time(in miliseconds) between consecutive compare events.
    uint32_t time_ticks;
	//co sekunde
    // Initialize timer module, making it use the scheduler
    APP_TIMER_INIT(APP_TIMER_PRESCALER, APP_TIMER_OP_QUEUE_SIZE, false);
	    // Initiate our timer
    app_timer_create(&m_connection_event_timer_id, APP_TIMER_MODE_REPEATED, timer_timeout_handler);
	
	err_code = sd_nvic_SetPriority(TIMER1_IRQn, APP_IRQ_PRIORITY_LOW);
	APP_ERROR_CHECK(err_code);

	err_code = sd_nvic_EnableIRQ(TIMER1_IRQn);
	APP_ERROR_CHECK(err_code);
	
	// Set up counter with new configuration
    nrf_drv_timer_config_t counter_config = NRF_DRV_TIMER_DEFAULT_CONFIG(1);
    //counter_config.mode = NRF_TIMER_MODE_COUNTER;
	counter_config.frequency = NRF_TIMER_FREQ_62500Hz;
	counter_config.bit_width = NRF_TIMER_BIT_WIDTH_16;
	
	
	//Configure TIMER_RHT_MEASUREMENT.
    err_code = nrf_drv_timer_init(&TIMER_RHT_MEASUREMENT, &counter_config, timer_rht_measurement_event_handler);

    APP_ERROR_CHECK(err_code);
    time_ticks = nrf_drv_timer_ms_to_ticks(&TIMER_RHT_MEASUREMENT, time_ms);
    
    nrf_drv_timer_extended_compare(
         &TIMER_RHT_MEASUREMENT, NRF_TIMER_CC_CHANNEL0, time_ticks, NRF_TIMER_SHORT_COMPARE0_CLEAR_MASK, true);
    
    nrf_drv_timer_enable(&TIMER_RHT_MEASUREMENT);
}

void wdt_init(void)
{
	NRF_WDT->CONFIG = (WDT_CONFIG_HALT_Pause << WDT_CONFIG_HALT_Pos) | ( WDT_CONFIG_SLEEP_Run << WDT_CONFIG_SLEEP_Pos);
	NRF_WDT->CRV = 300*32768;   //ca 300 sek. timeout
	NRF_WDT->RREN |= WDT_RREN_RR0_Msk;  //Enable reload register 0
	NRF_WDT->TASKS_START = 1;
}




/**@brief Function for the Power Manager.
 */
static void power_manage(void)
{
    uint32_t err_code = sd_app_evt_wait();

    APP_ERROR_CHECK(err_code);
}


/**@brief Function for application main entry.
 */
int main(void)
{
    // Initialize.
    leds_init();
		history_struct_init();
    timers_init();
		wdt_init();
    ble_stack_init();
    gap_params_init();
    services_init();
    advertising_init();
    conn_params_init();
		

    // Start execution.
    advertising_start();

    // Enter main loop.
    for (;;)
    {
        power_manage();
    }
}


/**
 * @}
 */

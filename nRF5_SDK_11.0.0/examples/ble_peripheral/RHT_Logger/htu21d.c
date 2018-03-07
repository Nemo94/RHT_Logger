
 
#include "htu21d.h"
#include "twi_master.h"
#include "nrf_delay.h"



static uint8_t m_device_address=0x80; //!< Device address in bits [7:1] albo 0x80

const uint8_t command_start_convert_temp = 0xF3; //!< Initiates temperature conversion. //zmienione
const uint8_t command_start_convert_RH = 0xF5; //!< Initiates temperature conversion. //zmienione
const uint8_t command_access_config      = 0xE6; //!< Reads or writes configuration data to configuration register 
const uint8_t read_config      = 0xE7; //!< Reads or writes configuration data to configuration register 

//uint8_t command_resolution  = 0x81; //T=11 bit RH=11 bit pomiar RH 8 ms, a temp 7 ms
uint8_t command_resolution  = 0x80; //T=13 bit RH=10 bit pomiar RH 5 ms, a temp 25 ms

//zmienne do pomiaru
volatile float odczyt_temperatury =0; 
volatile float odczyt_RH =0; 
uint16_t St=0;
uint16_t SRH=0;

bool htu21d_start_temp_conversion(void)
{
  return twi_master_transfer(m_device_address, (uint8_t*)&command_start_convert_temp, 1, TWI_ISSUE_STOP);
}

bool htu21d_is_temp_conversion_done(void)
{
		return true; 
}

static uint8_t htu21d_config_read(void)
{
    uint8_t config = 0;

    // Write: command protocol
    if (twi_master_transfer(m_device_address, (uint8_t*)&read_config, 1, TWI_DONT_ISSUE_STOP))
    {
        if (twi_master_transfer(m_device_address | TWI_READ_BIT, &config, 1, TWI_ISSUE_STOP)) // Read: current configuration
        {
            // Read succeeded, configuration stored to variable "config"
        }
        else
        {
            // Read failed
            config = 0;
        }
    }

    return config;
}

uint8_t htu21d_init(void)
{
	nrf_delay_ms(15);
    bool transfer_succeeded = true;
	
    uint8_t config = htu21d_config_read();

    if (config != 0)
    {
            uint8_t data_buffer[2];
						command_resolution=command_resolution|config;
            data_buffer[0] = command_access_config;
            data_buffer[1] = command_resolution;

            transfer_succeeded &= twi_master_transfer(m_device_address, data_buffer, 2, TWI_ISSUE_STOP);
    }
    else
    {
        transfer_succeeded = false;
    }

    return transfer_succeeded;
}
bool htu21d_temp_read(int8_t *temperature, int8_t *temperature_fraction)
{
		bool transfer_succeeded = false;

		// Write: Begin read temperature command
    uint8_t data_buffer[2];

    // Read: 2 temperature bytes to data_buffer
    if (twi_master_transfer(m_device_address | TWI_READ_BIT, data_buffer, 2, TWI_ISSUE_STOP)) 
    {
				*temperature = (int8_t)data_buffer[0];
				*temperature_fraction = (int8_t)data_buffer[1];
				transfer_succeeded = true;
    }
		return transfer_succeeded;
}

bool htu21d_start_RH_conversion(void)
{
  return twi_master_transfer(m_device_address, (uint8_t*)&command_start_convert_RH, 1, TWI_ISSUE_STOP);
}

bool htu21d_is_RH_conversion_done(void)
{
	//czekamy na koniec konwersji...nie czekamy, w dokumentacji nic nie ma
	return true;
}

bool htu21d_RH_read(int8_t *RH, int8_t *RH_fraction)
{
		bool transfer_succeeded = false;

		// Write: Begin read temperature command
    uint8_t data_buffer[2];

    // Read: 2 temperature bytes to data_buffer
    if (twi_master_transfer(m_device_address | TWI_READ_BIT, data_buffer, 2, TWI_ISSUE_STOP)) 
    {
      *RH = (int8_t)data_buffer[0];
      *RH_fraction = (int8_t)data_buffer[1];
			//czekamy na koniec transmisji...nie czekamy, w dokumentacji nic nie ma    
      transfer_succeeded = true;
    }
		return transfer_succeeded;
}

int16_t zamiana_floata_na_inta (float pomiar_float, uint8_t dokladnosc)
{
				int i=0;
				if (dokladnosc>7) dokladnosc=7;
				uint32_t mnoznik=1;
				for (i=0;i<(dokladnosc+1);i++)
				{
					mnoznik=10*mnoznik;
				}
				int32_t pomiar_int = (pomiar_float*mnoznik);
				uint8_t ostatnia_cyfra = pomiar_int%10;
				if(ostatnia_cyfra<5) pomiar_int = pomiar_int/10;
				else pomiar_int = (pomiar_int/10)+1;
				return pomiar_int;
}

void i2c_temp_conv(void)
{	
	bool m_conversion_in_progress=false;
  	if (!m_conversion_in_progress) 
		{
				m_conversion_in_progress = htu21d_start_temp_conversion();
		}
		// Succeeded.
}

int16_t i2c_temp_read(void)
{
		int16_t biezace=0;
//		bool m_conversion_in_progress = false;
		int8_t temperature;
		int8_t temperature_fraction;

		if (htu21d_temp_read(&temperature, &temperature_fraction))
		{
				//St=temperature*256+(temperature_fraction>>5)*32;
			St=temperature*256+(temperature_fraction>>3)*8;

				odczyt_temperatury=-46.85+175.72*((float)St/65536);
				biezace=zamiana_floata_na_inta(odczyt_temperatury,2);
				
		}
		return biezace;
}

void i2c_RH_conv(void)
{
	bool m_conversion_in_progress=false;
		if (!m_conversion_in_progress) 
		{
				m_conversion_in_progress = htu21d_start_RH_conversion();
		}
}

int16_t i2c_RH_read(void)		
{
		int16_t biezace=0;
//		bool m_conversion_in_progress = false;
		int8_t RH;
		int8_t RH_fraction;

		if (htu21d_RH_read(&RH, &RH_fraction))
		{
				//SRH=RH*256+(RH_fraction>>5)*32;
				SRH=RH*256+(RH_fraction>>6)*64;
				odczyt_RH=-6+125*((float)SRH/65536);
				biezace=zamiana_floata_na_inta(odczyt_RH,2);

		}
		return biezace;
}
/*lint --flb "Leave library region" */ 



#ifndef htu21d_H
#define htu21d_H

/*lint ++flb "Enter library region" */

#include <stdbool.h>
#include <stdint.h>



bool htu21d_temp_read(int8_t *temperature_in_celcius, int8_t *temperature_fraction);

bool htu21d_RH_read(int8_t *RH, int8_t *RH_fraction);

//wywolaj
bool htu21d_start_temp_conversion(void);


bool htu21d_is_temp_conversion_done(void);
 
 //wywolaj
bool htu21d_start_RH_conversion(void);

bool htu21d_is_RH_conversion_done(void);

//wywolaj
uint8_t htu21d_init(void);

int16_t zamiana_floata_na_inta (float pomiar_float, uint8_t dokladnosc);

//wywolaj
void i2c_temp_conv(void);

//wywolaj
int16_t i2c_temp_read(void);

//wywolaj
void i2c_RH_conv(void);

//wywolaj
int16_t i2c_RH_read(void);	





/*lint --flb "Leave library region" */ 
#endif

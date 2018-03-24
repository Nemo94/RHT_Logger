package com.potemski.michal.rht_logger.gatt;

import java.util.UUID;

/**
 * Created by Michal on 2018-03-22.
 */

public abstract class RHTServiceData {


    public static final String RHT_SERVICE_STRING = "00001521-1212-efde-1523-785f20155065";
    public static final String HUMIDITY_CHAR_STRING = "00001522-1212-efde-1523-785f20155065";
    public static final String TEMPERATURE_CHAR_STRING = "00001523-1212-efde-1523-785f20155065";
    public static final String COMMAND_CHAR_STRING = "00001524-1212-efde-1523-785f20155065";
    public static final String CCCD_STRING = "00002902-0000-1000-8000-00805f9b34fb";
	
    public static final UUID RHT_SERVICE_UUID = UUID.fromString(RHT_SERVICE_STRING);
    public static final UUID HUMIDITY_CHAR_UUID = UUID.fromString(HUMIDITY_CHAR_STRING);
    public static final UUID TEMPERATURE_CHAR_UUID = UUID.fromString(TEMPERATURE_CHAR_STRING);
    public static final UUID COMMAND_CHAR_UUID = UUID.fromString(COMMAND_CHAR_STRING);
    public static final UUID CCCD_UUID = UUID.fromString(CCCD_STRING);
	
}

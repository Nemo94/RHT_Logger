package com.potemski.michal.rht_logger.gatt;

import java.util.UUID;

/**
 * Created by Michal on 2018-03-22.
 */

public abstract class RHTServiceData {


    public static final String RHT_SERVICE_STRING = "00001521-1212-efde-1523-785f20155065";
    public static final String STATUS_CHAR_STRING = "00001522-1212-efde-1523-785f20155065";
    public static final String MEASUREMENT_CHAR_STRING = "00001523-1212-efde-1523-785f20155065";
    public static final String COMMAND_CHAR_STRING = "00001524-1212-efde-1523-785f20155065";

    public static final UUID RHT_SERVICE_UUID = UUID.fromString(RHT_SERVICE_STRING);
    public static final UUID STATUS_CHAR_UUID = UUID.fromString(STATUS_CHAR_STRING);
    public static final UUID MEASUREMENT_CHAR_UUID = UUID.fromString(MEASUREMENT_CHAR_STRING);
    public static final UUID COMMAND_CHAR_UUID = UUID.fromString(COMMAND_CHAR_STRING);

}

package com.potemski.michal.rht_logger;


public abstract class Intents {

    public static final String BLUETOOTH_CONNECTED = "bluetooth_connected";
    public static final String BLUETOOTH_CONNECTING = "bluetooth_connecting";
    public static final String BLUETOOTH_DISCONNECTED = "bluetooth_disconnected";

	public static final String nRF_ERROR = "nrf_error";

    public static final String NEW_DATA_DOWNLOADING = "new_data_downloading";


    public static final String CURRENT_MEASUREMENTS_RECEIVED = "current_measurements_received";
    public static final String MEASUREMENTS_HISTORY_RECEIVED = "measurements_history_received";
    public static final String INTERVAL_CHANGED = "interval_changed";
    public static final String HISTORY_DELETED = "history_deleted";

    public static final String TEMPERATURE_HISTORY_KEY = "temperature_history_key";
    public static final String HUMIDITY_HISTORY_KEY = "humidity_history_key";
    public static final String TIME_HISTORY_KEY = "time_history_key";
    public static final String NUMBER_OF_MEASUREMENTS_KEY = "number_of_measurements_key";
    public static final String MEASUREMENT_PERIOD_KEY = "measurement_period_key";
    public static final String CURRENT_TEMPERATURE_KEY = "current_temperature_key";
    public static final String CURRENT_HUMIDITY_KEY = "current_humidity_key";

}

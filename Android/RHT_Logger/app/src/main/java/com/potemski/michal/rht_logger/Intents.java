package com.potemski.michal.rht_logger;


public abstract class Intents {

    public static final String BLUETOOTH_CONNECTED = "bluetooth_connected";
    public static final String BLUETOOTH_CONNECTING = "bluetooth_connecting";
    public static final String BLUETOOTH_DISCONNECTED = "bluetooth_disconnected";
	
	public static final String nRF_ERROR = "nrf_error";

	public static final String CURRENT_MEASUREMENTS_RECEIVED = "current_measurements_received";
    public static final String MEASUREMENTS_HISTORY_RECEIVED = "measurements_history_received";
    public static final String INTERVAL_CHANGED = "interval_changed";
    public static final String HISTORY_DELETED = "history_deleted";

}

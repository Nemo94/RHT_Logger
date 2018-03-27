/**
 * Created by Michal on 2018-03-26.
 */


package com.potemski.michal.rht_logger;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import com.potemski.michal.rht_logger.gatt.RHTBluetoothManager;
import com.potemski.michal.rht_logger.gatt.operations.GattInitializeBluetooth;
import com.potemski.michal.rht_logger.gatt.Enums;


public class ConnectionIntentService extends IntentService {

    //Current Bluetooth object we are connected/connecting with
    private RHTBluetoothManager conn;
	
	public static final String COMMAND_PARAM = "command_param";
    public static final String MEASUREMENT_PERIOD_PARAM = "measurement_period_param";
 
    public ConnectionIntentService() {
        super("ConnectionIntentService");
    }
 
    @Override
    protected void onHandleIntent(Intent intent) {
 
        int CommandValue = intent.getIntExtra(COMMAND_PARAM, Enums.CommandIndex.CONNECTED.getIndex());
		int MeasurementPeriodValue = intent.getIntExtra(MEASUREMENT_PERIOD_PARAM, 1);

        if(CommandValue == Enums.CommandIndex.CHANGE_INTERVAL.getIndex()) {
            conn = RHTBluetoothManager.getInstance(this, CommandValue, MeasurementPeriodValue);
        }
        else {

            conn = RHTBluetoothManager.getInstance(this, CommandValue);
        }

        conn.InitializeConnection();
    }
}
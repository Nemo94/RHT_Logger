/**
 * Created by Michal on 2018-03-10.
 */

package com.potemski.michal.rht_logger;


import android.Manifest;
import android.annotation.TargetApi;

import android.bluetooth.BluetoothAdapter;

import com.potemski.michal.rht_logger.gatt.RHTBluetoothManager;
import com.potemski.michal.rht_logger.gatt.operations.GattInitializeBluetooth;
import com.potemski.michal.rht_logger.gatt.Enums;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;

import android.os.Build;
import android.os.Bundle;

import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

import android.support.v7.app.AppCompatActivity;
import android.text.InputType;

import android.text.method.ScrollingMovementMethod;

import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import android.view.View;


//mac E7:27:F7:02:7D:C1

//API for BLE - Level 21 needed
@TargetApi(21)
public class MainActivity extends AppCompatActivity {

    //Current Bluetooth object we are connected/connecting with
    private RHTBluetoothManager conn;


    private static final String TAG = "MainActivity";

    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 2;

    //boolean indicating that one operation is already ongoing, preventing user from making problems with fast
    //tapping of different buttons
    private boolean OperationInProgress = false;

    //Object for storing the data
	private DataHolder mDataHolder;

    private int EditTextIntervalValue;

    private Button CurrentMeasurementsButton;
    private Button HistoryButton;
    private Button ChangeIntervalButton;
    private Button DeleteHistoryButton;

    private TextView MinutesTextView;
    private TextView MainTextView;
    private EditText MeasurementIntervalEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            showMessage(R.string.ble_not_supported);

            finish();
        }
		
		// On Android Marshmallow (6.0) and higher, we need ACCESS_COARSE_LOCATION or
        // ACCESS_FINE_LOCATION permission to get scan results, so check if we have. If not, ask the
        // user for permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        PERMISSION_REQUEST_COARSE_LOCATION
                );

                return;
            } // else: permission already granted
        } // else: running on older version of Android. In market grade app the older BLE API would be supported.



        // We set the content View of this Activity
        setContentView(R.layout.activity_main);

        //Get all the Buttons
        CurrentMeasurementsButton = (Button) findViewById(R.id.buttonCurrentMeasurements);
        HistoryButton = (Button) findViewById(R.id.buttonHistory);
        ChangeIntervalButton = (Button) findViewById(R.id.buttonChangeInterval);
        DeleteHistoryButton = (Button) findViewById(R.id.buttonDeleteHistory);

        // Get all the TextViews
        MainTextView = (TextView) findViewById(R.id.MainText);
        MinutesTextView = (TextView) findViewById(R.id.MinutestextView);
        MeasurementIntervalEditText = (EditText) findViewById(R.id.IntervalText);
        MeasurementIntervalEditText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL);


   

        //Create objects of MeasurementData class for Temperature and Humidity storage and of CommandData
        // class to store the commands, nRFStatus and Measurement Interval

		mDataHolder = new DataHolder();
		mDataHolder.Command = Enums.CommandIndex.CONNECTED.getIndex();
		mDataHolder.MeasurementPeriodInMinutes = 1;
        //Parse Edit Text
        MeasurementIntervalEditText.setText(String.valueOf(mDataHolder.MeasurementPeriodInMinutes));
        MeasurementIntervalEditText.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    //String TempString;
                    int TempInt;
                    //TempString = MeasurementIntervalEditText.getText().toString();
                    //TempInt = Integer.parseInt(TempString);
                    TempInt = Integer.valueOf(MeasurementIntervalEditText.getText().toString());
                    if (TempInt >= 1 && TempInt <= 240) {
                        EditTextIntervalValue = TempInt;
                        //MeasurementIntervalEditText.setText(String.valueOf(EditTextIntervalValue));
                        //MeasurementIntervalEditText.setText(TempString);
                    } else {
                        showMessage(R.string.measurement_interval_out_of_scope);
                    }
                }
                return false;
            }
        });
		
        ClearDisplayInfo();
    }


    @Override
    protected void onStop() {
        super.onStop();

        OperationInProgress = false;
        ClearDisplayInfo();
		
		disableBT();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();


        disableBT();
        if(broadcastReceiver!=null)
        {
            LocalBroadcastManager.getInstance(getApplicationContext())
                    .unregisterReceiver(broadcastReceiver);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    protected void onRestart() {
        super.onRestart();
		
		enableBT();
    }

    @Override
    public void onResume() {
        super.onResume();
		
		enableBT();

		
		IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intents.MEASUREMENTS_HISTORY_RECEIVED);
        intentFilter.addAction(Intents.CURRENT_MEASUREMENTS_RECEIVED);
		intentFilter.addAction(Intents.INTERVAL_CHANGED);
        intentFilter.addAction(Intents.HISTORY_DELETED);
        intentFilter.addAction(Intents.BLUETOOTH_CONNECTED);
        intentFilter.addAction(Intents.BLUETOOTH_CONNECTING);
        intentFilter.addAction(Intents.BLUETOOTH_DISCONNECTED);
		intentFilter.addAction(Intents.nRF_ERROR);


        // register our desire to receive broadcasts from RHT
        LocalBroadcastManager.getInstance(getApplicationContext())
                .registerReceiver(broadcastReceiver, intentFilter);
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {

                break;
            }

            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);

                break;
        }
    }
	
	public void enableBT(){
		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (!mBluetoothAdapter.isEnabled()){
			mBluetoothAdapter.enable();
		}
	}
	
	public void disableBT(){
		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter.isEnabled()){
			mBluetoothAdapter.disable();
		}
	}


	public void InitializeConnection()
	{
		// Init connection :)
        conn.queue(new GattInitializeBluetooth());
	}



    //Message with argument as String
    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    //Message with argument as int id from strings.xml
    private void showMessage(int sName) {
        Toast.makeText(this, getString(sName), Toast.LENGTH_LONG).show();
    }
	
	 // For receiving and displaying log messages from the Service thread
    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intents.CURRENT_MEASUREMENTS_RECEIVED)) {

			   Log.d(TAG, "Received CURRENT MEASUREMENTS");

			   mDataHolder.CurrentTemperature = intent.getFloatExtra(Intents.CURRENT_TEMPERATURE_KEY, 0);
			   mDataHolder.CurrentHumidity = intent.getFloatExtra(Intents.CURRENT_HUMIDITY_KEY, 0);
			   mDataHolder.MeasurementPeriodInMinutes = intent.getIntExtra(Intents.MEASUREMENT_PERIOD_KEY, 1);

			   OperationInProgress = false;
               ClearDisplayInfo();
               DisplayCurrentMeasurements();
               UpdateMinutesTextView();
  

            } 
			else if (intent.getAction().equals(Intents.MEASUREMENTS_HISTORY_RECEIVED)) {

			
				Log.d(TAG, "Received HISTORY OF MEASUREMENTS");

                mDataHolder.TemperatureArray = intent.getFloatArrayExtra(Intents.TEMPERATURE_HISTORY_KEY);
                mDataHolder.HumidityArray = intent.getFloatArrayExtra(Intents.HUMIDITY_HISTORY_KEY);
                mDataHolder.TimeArray = intent.getIntArrayExtra(Intents.TIME_HISTORY_KEY);
                mDataHolder.NumberOfMeasurementsReceived = intent.getIntExtra(Intents.NUMBER_OF_MEASUREMENTS_KEY, 0);
                mDataHolder.MeasurementPeriodInMinutes = intent.getIntExtra(Intents.MEASUREMENT_PERIOD_KEY, 1);

				OperationInProgress = false;
				ClearDisplayInfo();
                DisplayHistory();
                UpdateMinutesTextView();

            } 
			else if (intent.getAction().equals(Intents.INTERVAL_CHANGED)) {
			
				Log.d(TAG, "Changed Measurement Interval");

                mDataHolder.MeasurementPeriodInMinutes = intent.getIntExtra(Intents.MEASUREMENT_PERIOD_KEY, 1);

                OperationInProgress = false;
				ClearDisplayInfo();
                DisplayInfoMeasurementIntervalChanged();
                UpdateMinutesTextView();

				
            } 
			else if (intent.getAction().equals(Intents.HISTORY_DELETED)) {

				Log.d(TAG, "Deleted History");

                mDataHolder.MeasurementPeriodInMinutes = intent.getIntExtra(Intents.MEASUREMENT_PERIOD_KEY,1);

                OperationInProgress = false;
				ClearDisplayInfo();
                DisplayInfoHistoryDeleted();
                UpdateMinutesTextView();

            } 
			else if (intent.getAction().equals(Intents.BLUETOOTH_CONNECTED)) {

                Log.w(TAG, "Connected");
				DisplayInfoConnected();

            } 
			else if (intent.getAction().equals(Intents.BLUETOOTH_CONNECTING)) {

                Log.w(TAG, "Connecting");
				DisplayInfoConnecting();

            } 
			else if (intent.getAction().equals(Intents.BLUETOOTH_DISCONNECTED)) {

                Log.w(TAG, "Disconnected");
				OperationInProgress = false;
            }
			
			else if (intent.getAction().equals(Intents.nRF_ERROR)) {           

                Log.w(TAG, "nRF Error");
				OperationInProgress = false;

            } 
			
			

        }
    };

    //Method called for different buttons on click
    public void onClick(View v) {
        final int id = v.getId();
        switch (id) {
            //Current Measurements button
            case R.id.buttonCurrentMeasurements:
                //if operation isn't already ongoing, activate proper operation
                if (OperationInProgress == false) {
                    OperationInProgress = true;
                    conn = RHTBluetoothManager.getInstance(this, Enums.CommandIndex.CURRENT_MEASUREMENTS.getIndex() );

                    InitializeConnection();
                }
                //else - show Message and ignore the click
                else {
                    showMessage(R.string.operation_already_ongoing);
                }

                break;
            //Measurements History button
            case R.id.buttonHistory:
                if (OperationInProgress == false) {
                    OperationInProgress = true;
                    conn = RHTBluetoothManager.getInstance(this, Enums.CommandIndex.MEASUREMENTS_HISTORY.getIndex() );
                    InitializeConnection();
                }
                //else - show Message and ignore the click
                else {
                    showMessage(R.string.operation_already_ongoing);
                }
                break;
            //Change Measurement period button
            case R.id.buttonChangeInterval:
                if (OperationInProgress == false) {
                    OperationInProgress = true;
                    conn = RHTBluetoothManager.getInstance(this, Enums.CommandIndex.CHANGE_INTERVAL.getIndex(), EditTextIntervalValue );
                    InitializeConnection();
                }
                //else - show Message and ignore the click
                else {
                    showMessage(R.string.operation_already_ongoing);
                }
                break;
            //Delete History Button
            case R.id.buttonDeleteHistory:
                if (OperationInProgress == false) {
                    OperationInProgress = true;
                    conn = RHTBluetoothManager.getInstance(this, Enums.CommandIndex.DELETE_HISTORY.getIndex() );
                    InitializeConnection();
                }
                //else - show Message and ignore the click
                else {
                    showMessage(R.string.operation_already_ongoing);
                }
                break;

            default:
                break;


        }
    }
    //Methods for handling editing text in measurement interval EditView


    //Display text in MainTextView Methods
    private void DisplayHistory() {

        String s = "";
        s += R.string.history_time + "\t" + R.string.history_temperature + "\t" + R.string.history_humidity + "\n";

        for (int x = 0; x <= mDataHolder.NumberOfMeasurementsReceived; x++) {
            s += String.format("%d", mDataHolder.TimeArray[x]) + "\t" +
                    String.format("%2.2f", mDataHolder.TemperatureArray[x])  + "\t" +
                    String.format("%2.2f", mDataHolder.HumidityArray[x])  + "\n";
        }

        MainTextView.setMovementMethod(new ScrollingMovementMethod());
        MainTextView.setText(s);
    }

    private void DisplayCurrentMeasurements() {

        String s = "";
        s += R.string.current_temperature + String.format("%2.2f", mDataHolder.CurrentTemperature) + "\n";
        s += R.string.current_humidity + String.format("%2.2f", mDataHolder.CurrentHumidity) + "\n";
        MainTextView.setText(s);
    }

    private void DisplayInfoHistoryDeleted() {

        MainTextView.setMovementMethod(new ScrollingMovementMethod());
        MainTextView.setText(R.string.history_deleted);
    }

    private void DisplayInfoMeasurementIntervalChanged() {

        MainTextView.setMovementMethod(new ScrollingMovementMethod());
        MainTextView.setText(R.string.measurement_interval_changed);
    }

    private void DisplayInfoConnecting() {

        MainTextView.setText(R.string.connecting);
    }
	
	private void DisplayInfoConnected() {

        MainTextView.setText(R.string.connected);
    }

    private void ClearDisplayInfo() {

        MainTextView.setText(R.string.default_empty_string);
    }

    private void UpdateMinutesTextView() {

        String s = "";
        s += R.string.current_interval_string + String.valueOf(mDataHolder.MeasurementPeriodInMinutes) + R.string.minutes_string;
        MinutesTextView.setText(s);
    }
}

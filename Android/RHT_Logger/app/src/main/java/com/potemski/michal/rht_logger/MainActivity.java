/**
 * Created by Michal on 2018-03-10.
 */

package com.potemski.michal.rht_logger;


import android.Manifest;
import android.annotation.TargetApi;

import android.bluetooth.BluetoothAdapter;


import com.potemski.michal.rht_logger.gatt.Enums;
import com.potemski.michal.rht_logger.gatt.RHTBluetoothManager;

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




    private static final String TAG = "MainActivity";

    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 2;

    //boolean indicating that one operation is already ongoing, preventing user from making problems with fast
    //tapping of different buttons
    private boolean OperationInProgress = false;


    //Object for storing the data

    private int EditTextIntervalValue;

    private Button CurrentMeasurementsButton;
    private Button HistoryButton;
    private Button ChangeIntervalButton;
    private Button DeleteHistoryButton;

    private TextView MinutesTextView;
    private TextView MainTextView;
    private EditText MeasurementIntervalEditText;

    private RHTBluetoothManager conn;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        conn = RHTBluetoothManager.getInstance(getApplicationContext());

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

        EditTextIntervalValue = 1;

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



        //Parse Edit Text
        MeasurementIntervalEditText.setText(String.valueOf(EditTextIntervalValue));
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
                       // MeasurementIntervalEditText.setText(String.valueOf(EditTextIntervalValue));
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
        //ClearDisplayInfo();
		
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
			
			float[] TemperatureArray;
			float[] HumidityArray;
			int[] TimeArray;
			int NumberOfMeasurementsReceived;
			float CurrentTemperature;
			float CurrentHumidity;
			int MeasurementPeriodInMinutes;
	
	
            if (intent.getAction().equals(Intents.CURRENT_MEASUREMENTS_RECEIVED)) {

			   Log.i(TAG, "Received CURRENT MEASUREMENTS");


			    Bundle extras = intent.getExtras();
				
			    CurrentTemperature = intent.getFloatExtra(Intents.CURRENT_TEMPERATURE_KEY, 0);
			    CurrentHumidity = intent.getFloatExtra(Intents.CURRENT_HUMIDITY_KEY, 0);
			    MeasurementPeriodInMinutes = intent.getIntExtra(Intents.MEASUREMENT_PERIOD_KEY, 0);
                ClearDisplayInfo();
                DisplayCurrentMeasurements(CurrentTemperature, CurrentHumidity);
                UpdateMinutesTextView(MeasurementPeriodInMinutes);

//                if(intent.getExtras() != null) {
//
//                    CurrentTemperature = extras.getFloat(Intents.CURRENT_TEMPERATURE_KEY);
//                    CurrentHumidity = extras.getFloat(Intents.CURRENT_HUMIDITY_KEY);
//                    MeasurementPeriodInMinutes = extras.getInt(Intents.MEASUREMENT_PERIOD_KEY);
//
//                    ClearDisplayInfo();
//                    DisplayCurrentMeasurements(CurrentTemperature, CurrentHumidity);
//                    UpdateMinutesTextView(MeasurementPeriodInMinutes);
//                }
                OperationInProgress = false;



            } 
			else if (intent.getAction().equals(Intents.MEASUREMENTS_HISTORY_RECEIVED)) {

				TemperatureArray =  new float[30];
				HumidityArray =  new float[30];
				TimeArray =  new int[30];
           
				for(int i=0;i<30;i++) {
					TemperatureArray[i]= (float) 0.0;
					HumidityArray[i]= (float) 0.0;
					TimeArray[i]= 0;
				}
				Log.i(TAG, "Received HISTORY OF MEASUREMENTS");

				//Bundle extras = intent.getExtras();
				
                TemperatureArray = intent.getFloatArrayExtra(Intents.TEMPERATURE_HISTORY_KEY);
                HumidityArray = intent.getFloatArrayExtra(Intents.HUMIDITY_HISTORY_KEY);
                TimeArray = intent.getIntArrayExtra(Intents.TIME_HISTORY_KEY);
                NumberOfMeasurementsReceived = intent.getIntExtra(Intents.NUMBER_OF_MEASUREMENTS_KEY, 0);
                MeasurementPeriodInMinutes = intent.getIntExtra(Intents.MEASUREMENT_PERIOD_KEY, 0);
                ClearDisplayInfo();
                DisplayHistory(NumberOfMeasurementsReceived, TimeArray, TemperatureArray, HumidityArray);
                UpdateMinutesTextView(MeasurementPeriodInMinutes);

//                if(intent.getExtras() != null) {
//
//                    TemperatureArray = extras.getFloatArray(Intents.TEMPERATURE_HISTORY_KEY);
//                    HumidityArray = extras.getFloatArray(Intents.HUMIDITY_HISTORY_KEY);
//                    TimeArray = extras.getIntArray(Intents.TIME_HISTORY_KEY);
//                    NumberOfMeasurementsReceived = extras.getInt(Intents.NUMBER_OF_MEASUREMENTS_KEY);
//                    MeasurementPeriodInMinutes = extras.getInt(Intents.MEASUREMENT_PERIOD_KEY);
//                    ClearDisplayInfo();
//                    DisplayHistory(NumberOfMeasurementsReceived, TimeArray, TemperatureArray, HumidityArray);
//                    UpdateMinutesTextView(MeasurementPeriodInMinutes);
//                }
				OperationInProgress = false;


            } 
			else if (intent.getAction().equals(Intents.INTERVAL_CHANGED)) {
			
				Log.i(TAG, "Changed Measurement Interval");

                MeasurementPeriodInMinutes = intent.getIntExtra(Intents.MEASUREMENT_PERIOD_KEY, 0);

                OperationInProgress = false;
				ClearDisplayInfo();
                DisplayInfoMeasurementIntervalChanged();
                UpdateMinutesTextView(MeasurementPeriodInMinutes);

				
            } 
			else if (intent.getAction().equals(Intents.HISTORY_DELETED)) {

				Log.i(TAG, "Deleted History");

                MeasurementPeriodInMinutes = intent.getIntExtra(Intents.MEASUREMENT_PERIOD_KEY,0);

                OperationInProgress = false;
				ClearDisplayInfo();
                DisplayInfoHistoryDeleted();
                UpdateMinutesTextView(MeasurementPeriodInMinutes);

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
					
					
					//startConnectionService(Enums.CommandIndex.CURRENT_MEASUREMENTS.getIndex(), EditTextIntervalValue);

										
                    SetupConnection(Enums.CommandIndex.CURRENT_MEASUREMENTS.getIndex(), EditTextIntervalValue);
//					Intent startConnIntent = new Intent(this, ConnectionIntentService.class);
//					startConnIntent.putExtra(ConnectionIntentService.COMMAND_PARAM, Enums.CommandIndex.CHANGE_INTERVAL.getIndex());
//					startConnIntent.putExtra(ConnectionIntentService.MEASUREMENT_PERIOD_PARAM, EditTextIntervalValue);
//					startService(startConnIntent);
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
	
										
					//startConnectionService(Enums.CommandIndex.MEASUREMENTS_HISTORY.getIndex(), EditTextIntervalValue);
                    SetupConnection(Enums.CommandIndex.MEASUREMENTS_HISTORY.getIndex(), EditTextIntervalValue);
//					Intent startConnIntent = new Intent(this, ConnectionIntentService.class);
//					startConnIntent.putExtra(ConnectionIntentService.COMMAND_PARAM, Enums.CommandIndex.CHANGE_INTERVAL.getIndex());
//					startConnIntent.putExtra(ConnectionIntentService.MEASUREMENT_PERIOD_PARAM, EditTextIntervalValue);
//					startService(startConnIntent);
					

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
					
					//startConnectionService(Enums.CommandIndex.CHANGE_INTERVAL.getIndex(), EditTextIntervalValue);

                    SetupConnection(Enums.CommandIndex.CHANGE_INTERVAL.getIndex(), EditTextIntervalValue);

//					Intent startConnIntent = new Intent(this, ConnectionIntentService.class);
//					startConnIntent.putExtra(ConnectionIntentService.COMMAND_PARAM, Enums.CommandIndex.CHANGE_INTERVAL.getIndex());
//					startConnIntent.putExtra(ConnectionIntentService.MEASUREMENT_PERIOD_PARAM, EditTextIntervalValue);
//					startService(startConnIntent);
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

                    SetupConnection(Enums.CommandIndex.DELETE_HISTORY.getIndex(), EditTextIntervalValue);

//					Intent startConnIntent = new Intent(this, ConnectionIntentService.class);
//					startConnIntent.putExtra(ConnectionIntentService.COMMAND_PARAM, Enums.CommandIndex.CHANGE_INTERVAL.getIndex());
//					startConnIntent.putExtra(ConnectionIntentService.MEASUREMENT_PERIOD_PARAM, EditTextIntervalValue);
//					startService(startConnIntent);
					//startConnectionService(Enums.CommandIndex.DELETE_HISTORY.getIndex(), EditTextIntervalValue);
					
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
    private void DisplayHistory(int numberOfMeasurementsReceived, int[] timeArray, float[] temperatureArray, float[] humidityArray) {

        String s = "";
        //StringBuilder sb = new StringBuilder();

        //s += R.string.history_time + "\t" + R.string.history_temperature + "\t" + R.string.history_humidity + "\n";
        s += String.format("%s\t%s\t%s\n", this.getString(R.string.history_time), 
			this.getString(R.string.history_temperature), this.getString(R.string.history_humidity));

        for (int x = 0; x < numberOfMeasurementsReceived; x++) {
          //  s += String.format("%d", timeArray[x]) + "\t" +
               //     String.format("%2.2f", temperatureArray[x])  + "\t" +
                //    String.format("%2.2f", humidityArray[x])  + "\n";
            s += String.format("%d\t%2.2f\t%2.2f\n", timeArray[x], temperatureArray[x], humidityArray[x]);
            Log.i("main his", s);

        }

        MainTextView.setMovementMethod(new ScrollingMovementMethod());
        MainTextView.setText(s);
    }

    private void DisplayCurrentMeasurements(float currentTemperature, float currentHumidity) {

        //String s = "";
        //s += R.string.current_temperature + String.format("%2.2f", currentTemperature) + "\n";
        //s += R.string.current_humidity + String.format("%2.2f", currentHumidity) + "\n";
        String s = String.format("%s %2.2f\n%s %2.2f\n", this.getString(R.string.current_temperature),
                currentTemperature, this.getString(R.string.current_humidity), currentHumidity);

        Log.i("main cur", s);
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

    private void UpdateMinutesTextView(int measurementPeriodInMinutes) {

       // String s = "";
        //s += R.string.current_interval_string + String.valueOf(measurementPeriodInMinutes) + R.string.minutes_string;
        String s = String.format("%s%d%s", this.getString(R.string.current_interval_string),
					measurementPeriodInMinutes , this.getString(R.string.minutes_string));
        Log.i("main", s);
        MinutesTextView.setText(s);
    }

    public void SetupConnection(int CommandValue, int MeasurementPeriodValue)
    {


        if(CommandValue == Enums.CommandIndex.CHANGE_INTERVAL.getIndex()) {
            conn.setNewCommand(CommandValue);
            conn.setNewMeasurementPeriod(MeasurementPeriodValue);

        }
        else {
            conn.setNewCommand(CommandValue);
        }
        conn.InitializeConnection();
    }
}

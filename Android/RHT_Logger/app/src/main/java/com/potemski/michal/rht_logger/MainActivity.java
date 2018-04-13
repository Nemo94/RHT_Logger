/**
 * Created by Michal on 2018-03-10.
 */

package com.potemski.michal.rht_logger;


import android.Manifest;
import android.annotation.TargetApi;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;


import com.potemski.michal.rht_logger.gatt.Enums;
import com.potemski.michal.rht_logger.gatt.RHTBluetoothManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;

import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;

import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

import android.support.v7.app.AppCompatActivity;
import android.text.InputType;

import android.text.method.ScrollingMovementMethod;

import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import android.view.View;


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

    private int OpcodeCompleted;
    DialogInterface.OnClickListener dialogClickListener;

    private float[] TemperatureArray;
    private float[] HumidityArray;
    private int[] TimeArray;
    private int NumberOfMeasurementsReceived;
    private float CurrentTemperature;
    private float CurrentHumidity;
    private int MeasurementPeriodInMinutes;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TemperatureArray =  new float[50];
        HumidityArray =  new float[50];
        TimeArray =  new int[50];

        for(int i=0;i<50;i++) {
            TemperatureArray[i]= (float) 0.0;
            HumidityArray[i]= (float) 0.0;
            TimeArray[i]= 0;
        }
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
		MeasurementIntervalEditText.clearFocus();


        if(savedInstanceState != null){

            EditTextIntervalValue = savedInstanceState.getInt("MeasurementValue");
            MeasurementIntervalEditText.setText(String.valueOf(EditTextIntervalValue));
            MeasurementPeriodInMinutes = EditTextIntervalValue;
            UpdateMinutesTextView(MeasurementPeriodInMinutes);
        }
        else {
            EditTextIntervalValue = 1;
            MeasurementIntervalEditText.setText(String.valueOf(EditTextIntervalValue));
            UpdateMinutesTextView(MeasurementPeriodInMinutes);

        }
        //Parse Edit Text
        MeasurementIntervalEditText.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    String TempString = MeasurementIntervalEditText.getText().toString();
                    int TempInt;

                    try {
                        TempInt = Integer.valueOf(TempString);

                    }
                    catch(NumberFormatException e)
                    {
                        TempInt = 1;
                        EditTextIntervalValue = 1;
                        MeasurementIntervalEditText.setText(String.valueOf(EditTextIntervalValue));
                        showMessage(R.string.empty_data_entered);


                    }

                    if (TempInt >= 1 && TempInt <= 240) {
                        EditTextIntervalValue = TempInt;
                    } else {
                        showMessage(R.string.measurement_interval_out_of_scope);
                    }

                    MeasurementIntervalEditText.clearFocus();

                }
                return false;
            }
        });

        MeasurementIntervalEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    hideKeyboard(v);
                }
            }
        });
		MeasurementIntervalEditText.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				v.setFocusable(true);
				v.setFocusableInTouchMode(true);
			return false;
			}
		});

        dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case DialogInterface.BUTTON_POSITIVE:
                        OpcodeCompleted = 0;
                        OperationInProgress = true;

                        SetupBluetoothOperation(Enums.CommandIndex.DELETE_HISTORY.getIndex(), EditTextIntervalValue);
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        OperationInProgress = false;
                        break;
                }
            }
        };
		
        ClearDisplayInfo();
    }


    @Override
    protected void onStop() {
        super.onStop();

        OperationInProgress = false;
        DisconnectionOperation();

        if(broadcastReceiver!=null)
        {
            LocalBroadcastManager.getInstance(getApplicationContext())
                    .unregisterReceiver(broadcastReceiver);
        }
        if(mBroadcastReceiver1!=null) {
            unregisterReceiver(mBroadcastReceiver1);
        }

        DisableBluetoothAdapter();



    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        DisconnectionOperation();
        if(broadcastReceiver!=null)
        {
            LocalBroadcastManager.getInstance(getApplicationContext())
                    .unregisterReceiver(broadcastReceiver);
        }



        DisableBluetoothAdapter();


    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter1 = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBroadcastReceiver1, filter1);

    }

    @Override
    protected void onRestart() {
        super.onRestart();

        EnableBluetoothAdapter();
    }



    @Override
    public void onResume() {
        super.onResume();



        EnableBluetoothAdapter();
        OpcodeCompleted = 0;

            UpdateMinutesTextView(MeasurementPeriodInMinutes);


        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intents.MEASUREMENTS_HISTORY_RECEIVED);
        intentFilter.addAction(Intents.CURRENT_MEASUREMENTS_RECEIVED);
		intentFilter.addAction(Intents.INTERVAL_CHANGED);
        intentFilter.addAction(Intents.HISTORY_DELETED);
        intentFilter.addAction(Intents.BLUETOOTH_CONNECTED);
        intentFilter.addAction(Intents.BLUETOOTH_CONNECTING);
        intentFilter.addAction(Intents.BLUETOOTH_SCANNING);
        intentFilter.addAction(Intents.BLUETOOTH_SCANNING_TIMEOUT);
        intentFilter.addAction(Intents.BLUETOOTH_DISCONNECTED);
		intentFilter.addAction(Intents.nRF_ERROR);
        intentFilter.addAction(Intents.NEW_DATA_DOWNLOADING);
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);


        // register our desire to receive broadcasts from RHT
        LocalBroadcastManager.getInstance(getApplicationContext())
                .registerReceiver(broadcastReceiver, intentFilter);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState)
    {
        // Store UI state to the savedInstanceState.

        String s = String.format("%d", MeasurementPeriodInMinutes);
        Log.i("SavedMeasPeriod", s);
        if(MeasurementPeriodInMinutes >= 1 && MeasurementPeriodInMinutes <=240) {
            savedInstanceState.putInt("MeasurementValue", MeasurementPeriodInMinutes);
        }

        savedInstanceState.putInt("EditTextValue", EditTextIntervalValue);


        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {

        if(savedInstanceState != null){

            MeasurementPeriodInMinutes = savedInstanceState.getInt("MeasurementValue");
            EditTextIntervalValue = savedInstanceState.getInt("EditTextValue");
            MeasurementIntervalEditText.setText(String.valueOf(EditTextIntervalValue));
            UpdateMinutesTextView(MeasurementPeriodInMinutes);
        }
        else {
            EditTextIntervalValue = 1;
            MeasurementIntervalEditText.setText(String.valueOf(EditTextIntervalValue));

        }
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



	
	private void EnableBluetoothAdapter(){
		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (!mBluetoothAdapter.isEnabled()){
			mBluetoothAdapter.enable();
		}
	}
	
	private void DisableBluetoothAdapter(){
		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter.isEnabled()){
			mBluetoothAdapter.disable();
		}
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

			   Log.i(TAG, "Received CURRENT MEASUREMENTS");

                CurrentTemperature = intent.getFloatExtra(Intents.CURRENT_TEMPERATURE_KEY, 0);
			    CurrentHumidity = intent.getFloatExtra(Intents.CURRENT_HUMIDITY_KEY, 0);
			    MeasurementPeriodInMinutes = intent.getIntExtra(Intents.MEASUREMENT_PERIOD_KEY, 0);
                ClearDisplayInfo();
                //DisplayCurrentMeasurements(CurrentTemperature, CurrentHumidity);
                UpdateMinutesTextView(MeasurementPeriodInMinutes);
                OpcodeCompleted = 1;
                OperationInProgress = false;



            } 
			else if (intent.getAction().equals(Intents.MEASUREMENTS_HISTORY_RECEIVED)) {


				Log.i(TAG, "Received HISTORY OF MEASUREMENTS");

                TemperatureArray = intent.getFloatArrayExtra(Intents.TEMPERATURE_HISTORY_KEY);
                HumidityArray = intent.getFloatArrayExtra(Intents.HUMIDITY_HISTORY_KEY);
                TimeArray = intent.getIntArrayExtra(Intents.TIME_HISTORY_KEY);
                NumberOfMeasurementsReceived = intent.getIntExtra(Intents.NUMBER_OF_MEASUREMENTS_KEY, 0);
                MeasurementPeriodInMinutes = intent.getIntExtra(Intents.MEASUREMENT_PERIOD_KEY, 0);
                ClearDisplayInfo();
                //DisplayHistory(NumberOfMeasurementsReceived, TimeArray, TemperatureArray, HumidityArray);
                UpdateMinutesTextView(MeasurementPeriodInMinutes);
                OpcodeCompleted = 2;
				OperationInProgress = false;


            } 
			else if (intent.getAction().equals(Intents.INTERVAL_CHANGED)) {
			
				Log.i(TAG, "Changed Measurement Interval");

                MeasurementPeriodInMinutes = intent.getIntExtra(Intents.MEASUREMENT_PERIOD_KEY, 0);

                OperationInProgress = false;
				ClearDisplayInfo();
                DisplayInfoMeasurementIntervalChanged();
                UpdateMinutesTextView(MeasurementPeriodInMinutes);
                OpcodeCompleted = 3;


            } 
			else if (intent.getAction().equals(Intents.HISTORY_DELETED)) {

				Log.i(TAG, "Deleted History");

                MeasurementPeriodInMinutes = intent.getIntExtra(Intents.MEASUREMENT_PERIOD_KEY,0);

                OperationInProgress = false;
				ClearDisplayInfo();
                DisplayInfoHistoryDeleted();
                UpdateMinutesTextView(MeasurementPeriodInMinutes);
                OpcodeCompleted = 4;

            } 
			else if (intent.getAction().equals(Intents.BLUETOOTH_CONNECTED)) {

                Log.w(TAG, "Connected");
				DisplayInfoConnected();

            } 
			else if (intent.getAction().equals(Intents.BLUETOOTH_CONNECTING)) {

                Log.w(TAG, "Connecting");
				DisplayInfoConnecting();

            }
            else if (intent.getAction().equals(Intents.BLUETOOTH_SCANNING)) {

                Log.w(TAG, "Scanning");
                DisplayInfoScanning();

            }
            else if (intent.getAction().equals(Intents.BLUETOOTH_SCANNING_TIMEOUT)) {

                Log.w(TAG, "ScanningTimeout");
                DisplayInfoScanningTimeout();
                OperationInProgress = false;

            }
            else if (intent.getAction().equals(Intents.BLUETOOTH_DISCONNECTED)) {

                Log.w(TAG, "Disconnected");
				OperationInProgress = false;
                if(OpcodeCompleted > 0) {
                    LaunchDisplayHistoryActivity();
                }
                else
                {
                    DisplayInfoBluetoothTurnedOffDuringOperation();
                }

            }
			
			else if (intent.getAction().equals(Intents.nRF_ERROR)) {           

                Log.w(TAG, "nRF Error");
				OperationInProgress = false;

            }
            else if (intent.getAction().equals(Intents.NEW_DATA_DOWNLOADING)) {

                DisplayInfoDataDownloading();
            }

        }
    };


    private final BroadcastReceiver mBroadcastReceiver1 = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            final int bluetoothState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            if (bluetoothState == BluetoothAdapter.STATE_TURNING_OFF) {
                // The user bluetooth is turning off yet, but it is not disabled yet.
                OperationInProgress = false;
                Log.w("BLE off", "BLE off");
                conn = RHTBluetoothManager.getInstance(getApplicationContext());
                conn.ResetConnectionFlags();
                DisplayInfoBluetoothTurnedOff();

                return;
            }

            if (bluetoothState == BluetoothAdapter.STATE_OFF) {
                // The user bluetooth is already disabled.
                if(OperationInProgress == true) {
                    OperationInProgress = false;
                    Log.w("BLE off", "BLE off");
                    DisplayInfoBluetoothTurnedOff();
                    conn = RHTBluetoothManager.getInstance(getApplicationContext());
                    conn.ResetConnectionFlags();
                    DisplayInfoBluetoothTurnedOffDuringOperation();

                }
                else {
                    DisplayInfoBluetoothTurnedOff();

                }



                Log.i("BLE off", "BLE off");
                return;
            }

            if (bluetoothState == BluetoothAdapter.STATE_ON) {

                OperationInProgress = false;
                conn = RHTBluetoothManager.getInstance(getApplicationContext());
                conn.ResetConnectionFlags();
                Log.i("BLE on", "BLE on");
                DisplayInfoBluetoothTurnedOn();
                return;
            }

            if (bluetoothState == BluetoothAdapter.STATE_TURNING_ON) {

                OperationInProgress = false;
                conn = RHTBluetoothManager.getInstance(getApplicationContext());
                conn.ResetConnectionFlags();
                Log.i("BLE on", "BLE on");
                DisplayInfoBluetoothTurningOn();
                return;
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
                    OpcodeCompleted = 0;
                    OperationInProgress = true;

                    SetupBluetoothOperation(Enums.CommandIndex.CURRENT_MEASUREMENTS.getIndex(), EditTextIntervalValue);
        }
                //else - show Message and ignore the click
                else {
                    showMessage(R.string.operation_already_ongoing);
                }

                break;
            //Measurements History button
            case R.id.buttonHistory:
                if (OperationInProgress == false) {
                    OpcodeCompleted = 0;
                    OperationInProgress = true;

                    SetupBluetoothOperation(Enums.CommandIndex.MEASUREMENTS_HISTORY.getIndex(), EditTextIntervalValue);

                }
                //else - show Message and ignore the click
                else {
                    showMessage(R.string.operation_already_ongoing);
                }
                break;
            //Change Measurement period button
            case R.id.buttonChangeInterval:
                if (OperationInProgress == false) {
                    OpcodeCompleted = 0;
                    OperationInProgress = true;
                    SetupBluetoothOperation(Enums.CommandIndex.CHANGE_INTERVAL.getIndex(), EditTextIntervalValue);

                }
                //else - show Message and ignore the click
                else {
                    showMessage(R.string.operation_already_ongoing);
                }
                break;
            //Delete History Button
            case R.id.buttonDeleteHistory:
                if (OperationInProgress == false) {

                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setMessage(getString(R.string.delete_history_question)).setPositiveButton(getString(R.string.delete_history_yes), dialogClickListener)
                            .setNegativeButton(getString(R.string.delete_history_no), dialogClickListener).show();

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
        s += String.format("%12s %-10s %-10s\n", this.getString(R.string.history_time),
			this.getString(R.string.history_temperature), this.getString(R.string.history_humidity));

        for (int x = 0; x < numberOfMeasurementsReceived; x++) {
            s += String.format(" %5d min         % 4.2f°C       % 4.2f%%\n",
							timeArray[x], temperatureArray[x], humidityArray[x]);
            Log.i("main his", s);

        }

        MainTextView.setMovementMethod(new ScrollingMovementMethod());
        MainTextView.setText(s);
    }

    private void DisplayCurrentMeasurements(float currentTemperature, float currentHumidity) {

        String s = String.format("%-12s %2.2f°C\n%-12s    %2.2f%%\n", this.getString(R.string.current_temperature),
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

    private void DisplayInfoScanning() {

        MainTextView.setText(R.string.scanning);
    }

    private void DisplayInfoScanningTimeout() {

        MainTextView.setText(R.string.scanning_timeout);
    }
	private void DisplayInfoConnected() {

        MainTextView.setText(R.string.connected);
    }
    private void DisplayInfoDataDownloading() {

        MainTextView.setText(R.string.new_data);
    }

    private void ClearDisplayInfo() {

        MainTextView.setText(R.string.default_empty_string);
    }

    private void DisplayInfoBluetoothTurnedOff() {

        MainTextView.setText(R.string.bluetooth_turned_off);
    }

    private void DisplayInfoBluetoothTurnedOffDuringOperation() {

        MainTextView.setText(R.string.bluetooth_turned_off_during_operation);
    }

    private void DisplayInfoBluetoothTurnedOn() {

        MainTextView.setText(R.string.bluetooth_turned_on);
    }
    private void DisplayInfoBluetoothTurningOn() {

        MainTextView.setText(R.string.bluetooth_turning_on);
    }


    private void UpdateMinutesTextView(int measurementPeriodInMinutes) {
        if(MeasurementPeriodInMinutes >=1 && MeasurementPeriodInMinutes <= 240) {
            String s = String.format("%s %d %s", this.getString(R.string.current_interval_string),
                    measurementPeriodInMinutes, this.getString(R.string.minutes_string));
            Log.i("main", s);
            MinutesTextView.setText(s);
        }
        else
        {
            MinutesTextView.setText(getString(R.string.default_minutes_string));
        }
    }

    private void SetupBluetoothOperation(int CommandValue, int MeasurementPeriodValue)
    {
        EnableBluetoothAdapter();

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        conn = RHTBluetoothManager.getInstance(getApplicationContext());

        if(CommandValue == Enums.CommandIndex.CHANGE_INTERVAL.getIndex()) {

            conn.setNewCommand(CommandValue);
            conn.setNewMeasurementPeriod(MeasurementPeriodValue);

        }
        else {
            conn.setNewCommand(CommandValue);
        }

        DisplayInfoBluetoothTurningOn();

        while(!mBluetoothAdapter.isEnabled())
        { ;
        }
        DisplayInfoBluetoothTurnedOn();

        conn.InitializeNrfOperation();
    }

    private void DisconnectionOperation()
    {
        conn = RHTBluetoothManager.getInstance(getApplicationContext());

        conn.disconnect();
        conn.ResetConnectionFlags();

    }

    private void LaunchDisplayHistoryActivity()
    {
        Intent intent;
        switch(OpcodeCompleted)
        {
            case 1:

                intent = new Intent(this, DisplayHistoryActivity.class);
                intent.putExtra(Intents.CURRENT_TEMPERATURE_KEY, CurrentTemperature);
                intent.putExtra(Intents.CURRENT_HUMIDITY_KEY, CurrentHumidity);
                intent.putExtra(Intents.OPCODE_COMPLETED, OpcodeCompleted);
                startActivity(intent);

                break;

            case 2:

                intent = new Intent(this, DisplayHistoryActivity.class);
                intent.putExtra(Intents.TEMPERATURE_HISTORY_KEY, TemperatureArray);
                intent.putExtra(Intents.HUMIDITY_HISTORY_KEY, HumidityArray);
                intent.putExtra(Intents.TIME_HISTORY_KEY, TimeArray);
                intent.putExtra(Intents.NUMBER_OF_MEASUREMENTS_KEY, NumberOfMeasurementsReceived);
                intent.putExtra(Intents.OPCODE_COMPLETED, OpcodeCompleted);
                startActivity(intent);

                break;

            default:

                break;
        }
    }

    public void hideKeyboard(View view) {
        InputMethodManager inputMethodManager =(InputMethodManager)getSystemService(MainActivity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

}

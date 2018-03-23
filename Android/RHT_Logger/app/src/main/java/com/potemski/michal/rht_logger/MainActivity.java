/**
 * Created by Michal on 2018-03-10.
 */

package com.potemski.michal.rht_logger;


import android.Manifest;
import android.annotation.TargetApi;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;

import com.potemski.michal.rht_logger.gatt.CharacteristicChangeListener;
import com.potemski.michal.rht_logger.gatt.GattManager;
import com.potemski.michal.rht_logger.gatt.GattOperationBundle;
import com.potemski.michal.rht_logger.gatt.operations.GattCharacteristicWriteOperation;
import com.potemski.michal.rht_logger.gatt.operations.GattDisconnectOperation;
import com.potemski.michal.rht_logger.gatt.operations.GattOperation;
import com.potemski.michal.rht_logger.gatt.operations.GattSetNotificationOperation;


import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import android.os.Build;
import android.os.ParcelUuid;
import android.os.Bundle;

import android.support.annotation.NonNull;

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

import org.droidparts.annotation.bus.ReceiveEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.LinkedList;
import java.util.UUID;

//mac E7:27:F7:02:7D:C1

//API for BLE - Level 21 needed
@TargetApi(21)
public class MainActivity extends AppCompatActivity {
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter mBluetoothAdapter = null;

    //Current Bluetooth object we are connected/connecting with
    private GattManager mGattManager;
	private GattOperationBundle bundle;

    private static final String TAG = "MainActivity";
    //Queues to handle asynchronous write operations
    private Queue<BluetoothGattDescriptor> descriptorWriteQueue = new LinkedList<BluetoothGattDescriptor>();
    private Queue<BluetoothGattCharacteristic> writeCharacteristicQueue = new LinkedList<BluetoothGattCharacteristic>();

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 2;

    //boolean indicating that one operation is already ongoing, preventing user from making problems with fast
    //tapping of different buttons
    static boolean OperationInProgress = false;

    private static final UUID RHT_SERVICE_UUID = UUID.fromString("00001521-1212-efde-1523-785f20155065");
    private static final UUID HUMIDITY_CHAR_UUID = UUID.fromString("00001522-1212-efde-1523-785f20155065");
    private static final UUID TEMPERATURE_CHAR_UUID = UUID.fromString("00001523-1212-efde-1523-785f20155065");
    private static final UUID COMMAND_CHAR_UUID = UUID.fromString("00001524-1212-efde-1523-785f20155065");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    //Objects for CommandData, Temperature Measurement Data and Humidity Temperature Data
    private CommandData mCommandData;
    private MeasurementData mTemperatureData;
    private MeasurementData mHumidityData;

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

		     // Get the BluetoothManager so we can get the BluetoothAdapter
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

		
		checkBluetoothState();
		
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

        mCommandData = new CommandData(1,
                CommandData.nRF_Status.READY.getStatus(),
                0,
                0);
        mTemperatureData = new MeasurementData();
        mHumidityData = new MeasurementData();
        //Parse Edit Text
        MeasurementIntervalEditText.setText(String.valueOf(mCommandData.MeasurementPeriodInMinutes));
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

        // Stop scanning
        stopScanning();
        OperationInProgress = false;
        ClearDisplayInfo();
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
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Start the scan again if we were expecting this result
        switch (requestCode) {
            case REQUEST_ENABLE_BT: 
                if(OperationInProgress == true) {
					startScanning();
				}
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);

                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // Start the scan again if we were expecting this result
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if(OperationInProgress == true) {
					startScanning();
				}
                break;
            }

            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);

                break;
        }
    }
	
	public void checkBluetoothState()
	{
		 // Check if Bluetooth is enabled. If not, display a dialog requesting user permission to
        // enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            final Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);

            return;
        } // else: Bluetooth is enabled
	}

    //Start Scanning Method
    private void startScanning() {


		checkBluetoothState();
        // Start scanning
        ScanFilter scanFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(RHT_SERVICE_UUID))
                .build();
        ArrayList<ScanFilter> filters = new ArrayList<ScanFilter>();
        filters.add(scanFilter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        mBluetoothAdapter.getBluetoothLeScanner().startScan(filters, settings, scanCallback);

        Log.v(TAG, "ScanStart");
        //Refresh and update TextView
        ClearDisplayInfo();
        DisplayInfoConnecting();
    }

    //Stop Scanning Method
    private void stopScanning() {
        // Check if Bluetooth is enabled and stop scanning (will crash if disabled)
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);
        } // else: Bluetooth is disabled
    }


    // Callback when a BLE advertisement has been found.
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            super.onScanResult(callbackType, result);
            //showMessage("Scan complete!");
            final BluetoothDevice device = result.getDevice();
            stopScanning();
            mGattManager = new GattManager (MainActivity.this, device);
            mGattManager.connect(result);




        }
    };


    //Use EventBus to receive SERVICES_DISCOVERED event
    @ReceiveEvents(name = Events.SERVICES_DISCOVERED)
    public void Initiate(String _, final BluetoothDevice device)
    {

        GattOperationBundle bundle = new GattOperationBundle();
        //Enable notifications for the characteristics
        bundle.addOperation(new GattSetNotificationOperation(
                device,
                RHT_SERVICE_UUID,
                COMMAND_CHAR_UUID,
                CCCD));

        bundle.addOperation(new GattSetNotificationOperation(
                device,
                RHT_SERVICE_UUID,
                TEMPERATURE_CHAR_UUID,
                CCCD));

        bundle.addOperation(new GattSetNotificationOperation(
                device,
                RHT_SERVICE_UUID,
                HUMIDITY_CHAR_UUID,
                CCCD));
        //Write First Command to start receiving proper data
        bundle.addOperation(new GattCharacteristicWriteOperation(
                device,
                RHT_SERVICE_UUID,
                COMMAND_CHAR_UUID,
                mCommandData.EncodeCommandCharValue()));

        mGattManager.queue(bundle);

        mGattManager.addCharacteristicChangeListener(
                COMMAND_CHAR_UUID,
                new CharacteristicChangeListener() {
                    @Override
                    public void onCharacteristicChanged(String deviceAddress, BluetoothGattCharacteristic characteristic) {
                        //Deal with Command Char

                        byte[] array = new byte[4];
                        array = characteristic.getValue();
                        //final int charValue = characteristic.getIntValue(characteristic.FORMAT_UINT32, 0);
                        mCommandData.GetCommandData(array);


                        if (mCommandData.StatusValue == CommandData.nRF_Status.ERROR.getStatus()) {
                            mTemperatureData.ResetMeasurementArray();
                            mHumidityData.ResetMeasurementArray();
                            closeConnection(device);
                            showMessage(R.string.nRF_Error);
                            OperationInProgress = false;
                        } else if (mCommandData.StatusValue == CommandData.nRF_Status.BUSY.getStatus()) {
                            ;
                        }
                        //React when embedded system has indicated the completion of operation
                        else if (mCommandData.StatusValue == CommandData.nRF_Status.COMPLETE.getStatus()) {
                            if (mCommandData.CommandValue == CommandData.CommandIndex.CURRENT_MEASUREMENTS.getIndex()) {
                                //Reaction in the UI
                                ClearDisplayInfo();
                                DisplayCurrentMeasurements();
                                UpdateMinutesTextView();
                                //Update Command Char
                                mCommandData.CommandValue = CommandData.CommandIndex.CURRENT_MEASUREMENTS_RECEIVED.getIndex();
                                WriteToCommandChar(device);

                            } else if (mCommandData.CommandValue == CommandData.CommandIndex.MEASUREMENTS_HISTORY.getIndex()) {
                                //Reaction in the UI
                                ClearDisplayInfo();
                                DisplayHistory();
                                UpdateMinutesTextView();
                                //Update Command Char
                                mCommandData.CommandValue = CommandData.CommandIndex.HISTORY_MEASUREMENTS_RECEIVED.getIndex();
                                WriteToCommandChar(device);

                            } else if (mCommandData.CommandValue == CommandData.CommandIndex.CHANGE_INTERVAL.getIndex()) {
                                //Reaction in the UI
                                ClearDisplayInfo();
                                DisplayInfoMeasurementIntervalChanged();
                                UpdateMinutesTextView();
                                //Update Command Char
                                mCommandData.CommandValue = CommandData.CommandIndex.INTERVAL_CHANGED.getIndex();
                                WriteToCommandChar(device);

                            } else if (mCommandData.CommandValue == CommandData.CommandIndex.DELETE_HISTORY.getIndex()) {
                                //Reaction in the UI
                                ClearDisplayInfo();
                                DisplayInfoHistoryDeleted();
                                UpdateMinutesTextView();
                                //Update Command Char
                                mCommandData.CommandValue = CommandData.CommandIndex.HISTORY_DELETED.getIndex();
                                WriteToCommandChar(device);
                            }

                        }
                        //if NRF_State is READY and Command Value indicates completion of an operation, disconnect
                        else if (mCommandData.StatusValue == CommandData.nRF_Status.READY.getStatus()) {
                            if (mCommandData.CommandValue == CommandData.CommandIndex.CURRENT_MEASUREMENTS_RECEIVED.getIndex()
                                    || mCommandData.CommandValue == CommandData.CommandIndex.HISTORY_MEASUREMENTS_RECEIVED.getIndex()
                                    || mCommandData.CommandValue == CommandData.CommandIndex.HISTORY_DELETED.getIndex()
                                    || mCommandData.CommandValue == CommandData.CommandIndex.INTERVAL_CHANGED.getIndex()) {

                                mCommandData.CommandValue = CommandData.CommandIndex.CONNECTED.getIndex();
                                closeConnection(device);
                                OperationInProgress = false;
                            }
                        }
                    }
                });

        mGattManager.addCharacteristicChangeListener(
                TEMPERATURE_CHAR_UUID,
                new CharacteristicChangeListener() {
                    @Override
                    public void onCharacteristicChanged(String deviceAddress, BluetoothGattCharacteristic characteristic) {
                        //Deal with Temperature Char
                        byte[] array = new byte[4];
                        array = characteristic.getValue();
                        if (mCommandData.CommandValue == CommandData.CommandIndex.CURRENT_MEASUREMENTS.getIndex()) {
                            mTemperatureData.GetCurrentMeasurement(array);
                        } else if (mCommandData.CommandValue == CommandData.CommandIndex.MEASUREMENTS_HISTORY.getIndex()) {
                            mTemperatureData.AddMeasurement(array);
                        }
                    }
                });

        mGattManager.addCharacteristicChangeListener(
                HUMIDITY_CHAR_UUID,
                new CharacteristicChangeListener() {
                    @Override
                    public void onCharacteristicChanged(String deviceAddress, BluetoothGattCharacteristic characteristic) {
                        //Deal with Humidity Char
                        byte[] array = new byte[4];
                        array = characteristic.getValue();
                        if (mCommandData.CommandValue == CommandData.CommandIndex.CURRENT_MEASUREMENTS.getIndex()) {
                            mHumidityData.GetCurrentMeasurement(array);
                        } else if (mCommandData.CommandValue == CommandData.CommandIndex.MEASUREMENTS_HISTORY.getIndex()) {
                            mHumidityData.AddMeasurement(array);
                        }
                    }
                });
    }
	public static boolean getOperationInProgressStatus()
    {
        return OperationInProgress;
    }

    private void WriteToCommandChar(BluetoothDevice device)
    {
        GattOperationBundle bundle = new GattOperationBundle();
        //Write First Command to start receiving proper data
        bundle.addOperation(new GattCharacteristicWriteOperation(
                device,
                RHT_SERVICE_UUID,
                COMMAND_CHAR_UUID,
                mCommandData.EncodeCommandCharValue()));

        mGattManager.queue(bundle);
    }

    private void closeConnection(BluetoothDevice device)
    {
        GattOperationBundle bundle = new GattOperationBundle();
        bundle.addOperation(new GattDisconnectOperation(device));
        mGattManager.queue(bundle);
    }


    //Message with argument as String
    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    //Message with argument as int id from strings.xml
    private void showMessage(int sName) {
        Toast.makeText(this, getString(sName), Toast.LENGTH_LONG).show();
    }

    //Method called for different buttons on click
    public void onClick(View v) {
        final int id = v.getId();
        switch (id) {
            //Current Measurements button
            case R.id.buttonCurrentMeasurements:
                //if operation isn't already ongoing, activate proper operation
                if (OperationInProgress == false) {
                    OperationInProgress = true;
                    mCommandData.CommandValue = CommandData.CommandIndex.CURRENT_MEASUREMENTS.getIndex();
                    startScanning();
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
                    mCommandData.CommandValue = CommandData.CommandIndex.MEASUREMENTS_HISTORY.getIndex();
                    startScanning();
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
                    mCommandData.CommandValue = CommandData.CommandIndex.CHANGE_INTERVAL.getIndex();
                    mCommandData.SetMeasurementPeriodInMinutes(EditTextIntervalValue);
                    startScanning();
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
                    mCommandData.CommandValue = CommandData.CommandIndex.DELETE_HISTORY.getIndex();
                    startScanning();
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

        for (int x = 0; x <= mTemperatureData.NumberOfMeasurements; x++) {
            s += String.valueOf(mTemperatureData.TimeArray[x]) + "\t" +
                    String.valueOf(mTemperatureData.ValueArray[x]) + "\t" +
                    String.valueOf(mHumidityData.ValueArray[x]) + "\n";
        }

        MainTextView.setMovementMethod(new ScrollingMovementMethod());
        MainTextView.setText(s);
    }

    private void DisplayCurrentMeasurements() {

        String s = "";
        s += R.string.current_temperature + String.valueOf(mTemperatureData.CurrentMeasurementValue) + "\n";
        s += R.string.current_humidity + String.valueOf(mHumidityData.CurrentMeasurementValue) + "\n";
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

    private void ClearDisplayInfo() {

        MainTextView.setText(R.string.default_empty_string);
    }

    private void UpdateMinutesTextView() {

        String s = "";
        s += R.string.current_interval_string + String.valueOf(mCommandData.MeasurementPeriodInMinutes) + R.string.minutes_string;
        MinutesTextView.setText(s);
    }
}

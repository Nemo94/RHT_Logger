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
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import android.os.Build;
import android.os.ParcelUuid;
import android.os.Bundle;

import android.support.annotation.NonNull;


import android.text.Editable;
import android.text.TextWatcher;

import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import android.view.View;
import android.view.View.OnClickListener;

import java.util.List;
import java.util.UUID;


//API for BLE - Level 21 needed
@TargetApi(21)
public class MainActivity extends Activity {
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter mBluetoothAdapter = null;

    //Current Bluetooth object we are connected/connecting with
    private BluetoothGatt mBluetoothGatt;

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 2;

    private static final ParcelUuid BASE_UUID = ParcelUuid.fromString("65501520-5F78-2315-DEEF-121215000000");
    private static final UUID RHT_SERVICE_UUID = UUID.fromString("65501521-5F78-2315-DEEF-121215000000");
    private static final UUID HUMIDITY_CHAR_UUID = UUID.fromString("65501522-5F78-2315-DEEF-121215000000");
    private static final UUID TEMPERATURE_CHAR_UUID = UUID.fromString("65501523-5F78-2315-DEEF-121215000000");
    private static final UUID COMMAND_CHAR_UUID = UUID.fromString("65501524-5F78-2315-DEEF-121215000000");
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

        // We set the content View of this Activity
        setContentView(R.layout.activity_main);

        //Get all the Buttons
        CurrentMeasurementsButton = (Button) findViewById(R.id.buttonCurrentMeasurements);
        HistoryButton = (Button) findViewById(R.id.buttonHistory);
        ChangeIntervalButton = (Button) findViewById(R.id.buttonChangeInterval);
        DeleteHistoryButton = (Button) findViewById(R.id.buttonDeleteHistory);

        // Get all the TextViews
        MainTextView = (TextView) findViewById(R.id.MainText);
        MeasurementIntervalEditText = (EditText) findViewById(R.id.IntervalText);

        // Get the BluetoothManager so we can get the BluetoothAdapter
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        //Create objects of MeasurementData class for Temperature and Humidity storage and of CommandData
        // class to store the commands, nRFStatus and Measurement Interval

        mCommandData = new CommandData(1,
                CommandData.nRF_Status.READY.getStatus(),
                0,
               0);
        mTemperatureData = new MeasurementData();
        mHumidityData = new MeasurementData();

        MeasurementIntervalEditText.setText(String.valueOf(mCommandData.MeasurementPeriodInMinutes));

        MeasurementIntervalEditText.addTextChangedListener(textWatcher);

    }


    @Override
    protected void onStop() {
        super.onStop();

        // Stop scanning
        stopScanning();
        closeConnection();
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
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Start the scan again if we were expecting this result
        switch (requestCode) {
            case REQUEST_ENABLE_BT: {
                startScanning();

                break;
            }

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
                startScanning();
                break;
            }

            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);

                break;
        }
    }

    //Start Scanning Method
    private void startScanning() {
        // Check if Bluetooth is enabled. If not, display a dialog requesting user permission to
        // enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            final Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);

            return;
        } // else: Bluetooth is enabled

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

        // Start scanning
        mBluetoothAdapter.getBluetoothLeScanner().startScan(scanCallback);
    }

    //Stop Scanning Method
    private void stopScanning() {
        // Check if Bluetooth is enabled and stop scanning (will crash if disabled)
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);
        } // else: Bluetooth is enabled
    }


    // Callback when a BLE advertisement has been found.
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            super.onScanResult(callbackType, result);

            // Get the ScanRecord and check if it is defined (is nullable)
            final ScanRecord scanRecord = result.getScanRecord();
            if (scanRecord != null) {
                // Check if the Service UUIDs are defined (is nullable) and contain the discovery
                // UUID

                final List<ParcelUuid> serviceUuids = scanRecord.getServiceUuids();

                if (serviceUuids != null && serviceUuids.contains(BASE_UUID)) {
                    // We have found our device, so update the GUI, stop scanning and start
                    // connecting
                    final BluetoothDevice device = result.getDevice();

                    // We'll make sure the GUI is updated on the UI thread
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //TODO : text view .. laczenie
                        }
                    });

                    stopScanning();

                    mBluetoothGatt = device.connectGatt
                            (
                                    MainActivity.this,
                                    false,
                                    mBluetoothGattCallback
                            );
                }
            }
        }
    };

    private final BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        //Callback indicating when GATT client has connected/disconnected to/from a remote
        //GATT server.

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);


            // Start Service discovery if we're now connected
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices();

                } // else: not connected, continue
            } // else: not successful

        }
        //When Services are discovered Event Callback
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            // Check if Service discovery was successful and enable notifications
            if (status == BluetoothGatt.GATT_SUCCESS) {
                enableCommandNotification();
                enableHumidityNotification();
                enableTemperatureNotification();
                //FirstWrite
                mCommandData.CommandValue = CommandData.CommandIndex.CONNECTED.getIndex();
                WriteCommandChar(mCommandData.EncodeCommandCharValue());
            }

        }

        //Callback triggered as a result of a remote characteristic notification.

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            byte[] array = new byte[4];

            if (COMMAND_CHAR_UUID.equals(characteristic.getUuid())) {
                array = characteristic.getValue();
                mCommandData.GetCommandData(array);



                if(mCommandData.StatusValue == CommandData.nRF_Status.ERROR.getStatus())
                {
                    mTemperatureData.ResetMeasurementArray();
                    mHumidityData.ResetMeasurementArray();
                    closeConnection();
                    showMessage(R.string.nRF_Error);
                }
                else if(mCommandData.StatusValue == CommandData.nRF_Status.BUSY.getStatus())
                {
                    WriteCommandChar(mCommandData.EncodeCommandCharValue());
                }
                //React when embedded system has indicated the completion of operation
                else if(mCommandData.StatusValue == CommandData.nRF_Status.COMPLETE.getStatus())
                {
                    if (mCommandData.CommandValue == CommandData.CommandIndex.CURRENT_MEASUREMENTS.getIndex()) {
                        mCommandData.CommandValue = CommandData.CommandIndex.CURRENT_MEASUREMENTS_RECEIVED.getIndex();
                        WriteCommandChar(mCommandData.EncodeCommandCharValue());

                        //TODO: Reaction in the UI
                    } else if (mCommandData.CommandValue == CommandData.CommandIndex.MEASUREMENTS_HISTORY.getIndex())
                    {
                        mCommandData.CommandValue = CommandData.CommandIndex.HISTORY_MEASUREMENTS_RECEIVED.getIndex();
                        WriteCommandChar(mCommandData.EncodeCommandCharValue());
                        //TODO: Reaction in the UI
                    }else if (mCommandData.CommandValue == CommandData.CommandIndex.CHANGE_INTERVAL.getIndex())
                    {
                        mCommandData.CommandValue = CommandData.CommandIndex.INTERVAL_CHANGED.getIndex();
                        WriteCommandChar(mCommandData.EncodeCommandCharValue());
                        //TODO: Reaction in the UI
                    }else if (mCommandData.CommandValue == CommandData.CommandIndex.DELETE_HISTORY.getIndex())
                    {
                        mCommandData.CommandValue = CommandData.CommandIndex.HISTORY_DELETED.getIndex();
                        WriteCommandChar(mCommandData.EncodeCommandCharValue());
                        //TODO: Reaction in the UI
                    }

                }
                //if NRF_State is READY and Command Value indicates completion of an operation, disconnect
                else if(mCommandData.StatusValue == CommandData.nRF_Status.READY.getStatus()) {
                    if(mCommandData.CommandValue == CommandData.CommandIndex.CURRENT_MEASUREMENTS_RECEIVED.getIndex()
                        || mCommandData.CommandValue == CommandData.CommandIndex.HISTORY_MEASUREMENTS_RECEIVED.getIndex()
                        || mCommandData.CommandValue == CommandData.CommandIndex.HISTORY_DELETED.getIndex()
                        || mCommandData.CommandValue == CommandData.CommandIndex.INTERVAL_CHANGED.getIndex()) {

                        mCommandData.CommandValue = CommandData.CommandIndex.CONNECTED.getIndex();
                        WriteCommandChar(mCommandData.EncodeCommandCharValue());
                        closeConnection();
                    }

                }
                //else

            }
            if (HUMIDITY_CHAR_UUID.equals(characteristic.getUuid())) {
                array = characteristic.getValue();
                if (mCommandData.CommandValue == CommandData.CommandIndex.CURRENT_MEASUREMENTS.getIndex()) {
                    mHumidityData.GetCurrentMeasurement(array);
                } else if (mCommandData.CommandValue == CommandData.CommandIndex.MEASUREMENTS_HISTORY.getIndex())
                {
                    mHumidityData.AddMeasurement(array);
                }
            }
            if (TEMPERATURE_CHAR_UUID.equals(characteristic.getUuid())) {

                array = characteristic.getValue();
                if (mCommandData.CommandValue == CommandData.CommandIndex.CURRENT_MEASUREMENTS.getIndex()) {
                    mTemperatureData.GetCurrentMeasurement(array);
                } else if (mCommandData.CommandValue == CommandData.CommandIndex.MEASUREMENTS_HISTORY.getIndex())
                {
                    mTemperatureData.AddMeasurement(array);
                }

            } else {
                ;
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // actionTextView.setText(getString(R.string.action, action, getActionDescriptionForAction(action)));
                }
            });
        }

        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            byte[] array = new byte[4];

            // Check if writing was successful and check what it was that we have written so we can
            // determine the next step
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.getUuid().equals(COMMAND_CHAR_UUID)) {
                    array = characteristic.getValue();
                    mCommandData.GetCommandData(array);
                    if(mCommandData.CommandReceived == mCommandData.CommandValue)
                    {
                        //Do nothing, everything is OK
                        ;
                    }
                    else{
                        closeConnection();
                        showMessage(R.string.transmission_error);
                    }
                }
            } else {
                ;
            }
        }

    };
    //Write data to command char method
    public boolean WriteCommandChar(byte[] bytes)
    {
        boolean success = false;

        final BluetoothGattService RHTService = mBluetoothGatt.getService(RHT_SERVICE_UUID);
        if (RHTService != null) {
            // Check if the Command Characteristic is found, write the new value and store
            // the result
            final BluetoothGattCharacteristic commandCharacteristic
                    = RHTService.getCharacteristic(COMMAND_CHAR_UUID);
            if (commandCharacteristic != null) {
                commandCharacteristic.setValue(bytes);
                success = mBluetoothGatt.writeCharacteristic(commandCharacteristic);
            }
        }
        return success;
    }

    //Notifications enabling methods

    public void enableCommandNotification() {
        BluetoothGattService RHTService = mBluetoothGatt.getService(RHT_SERVICE_UUID);
        if (RHTService == null) {
            showMessage(R.string.RHT_service_not_found);
            return;
        }
        BluetoothGattCharacteristic CommandChar = RHTService.getCharacteristic(COMMAND_CHAR_UUID);
        if (CommandChar == null) {
            showMessage(R.string.command_char_not_found);
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(CommandChar, true);

        BluetoothGattDescriptor descriptor = CommandChar.getDescriptor(CCCD);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);
    }

    public void enableTemperatureNotification() {
        BluetoothGattService RHTService = mBluetoothGatt.getService(RHT_SERVICE_UUID);
        if (RHTService == null) {
            showMessage(R.string.RHT_service_not_found);
            return;
        }
        BluetoothGattCharacteristic TemperatureChar = RHTService.getCharacteristic(TEMPERATURE_CHAR_UUID);
        if (TemperatureChar == null) {
            showMessage(R.string.temperature_char_not_found);
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(TemperatureChar, true);

        BluetoothGattDescriptor descriptor = TemperatureChar.getDescriptor(CCCD);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);
    }

    public void enableHumidityNotification() {
        BluetoothGattService RHTService = mBluetoothGatt.getService(RHT_SERVICE_UUID);
        if (RHTService == null) {
            showMessage(R.string.RHT_service_not_found);
            return;
        }
        BluetoothGattCharacteristic HumidityChar = RHTService.getCharacteristic(HUMIDITY_CHAR_UUID);
        if (HumidityChar == null) {
            showMessage(R.string.humidity_char_not_found);
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(HumidityChar, true);

        BluetoothGattDescriptor descriptor = HumidityChar.getDescriptor(CCCD);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);
    }

    public void closeConnection()
    {
        if (mBluetoothGatt != null) {
            // Close the BluetoothGatt, closing the connection and cleaning up any resources
            mBluetoothGatt.close();
            mBluetoothGatt = null;
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

    public void onClick(View v) {
        final int id = v.getId();
        switch (id) {
            //case R.id.button1:
                // your code for button1 here
                //break;
           // case R.id.button2:
                // your code for button2 here
              //  break;
            // even more buttons here
        }
    }

    TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            String TempString;
            int TempInt;
            TempString = MeasurementIntervalEditText.getText().toString();
            TempInt = Integer.parseInt(TempString);

            if(TempInt<1 || TempInt>240) {
                EditTextIntervalValue = TempInt;
                //MeasurementIntervalEditText.setText(String.valueOf(EditTextIntervalValue));
                MeasurementIntervalEditText.setText(TempString);
            }
            else
            {
                showMessage(R.string.measurement_interval_out_of_scope);
            }
        }
    };
}

package com.potemski.michal.rht_logger.gatt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.ParcelUuid;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.potemski.michal.rht_logger.DataHolder;
import com.potemski.michal.rht_logger.gatt.operations.GattCharacteristicReadOperation;
import com.potemski.michal.rht_logger.gatt.operations.GattDescriptorReadOperation;
import com.potemski.michal.rht_logger.gatt.operations.GattCharacteristicWriteOperation;
import com.potemski.michal.rht_logger.gatt.operations.GattOperation;
import com.potemski.michal.rht_logger.gatt.operations.GattDiscoverServices;
import com.potemski.michal.rht_logger.gatt.operations.GattSetNotificationOperation;

import com.potemski.michal.rht_logger.Intents;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;


public class RHTBluetoothManager {
    private static final String LS = System.getProperty("line.separator");
    private static final String TAG = "RHTBluetoothManager";

    private static RHTBluetoothManager instance = null;
    private final Context context;

    private GattOperation mCurrentOperation = null;

    private AsyncTask<Void, Void, Void> mCurrentOperationTimeout;
    private BluetoothGatt mBluetoothGatt = null;
    private ConcurrentLinkedQueue<GattOperation> mQueue = new ConcurrentLinkedQueue<>();
    private HashMap<UUID, ArrayList<CharacteristicChangeListener>> mCharacteristicChangeListeners;

    public DataHolder mDataHolder;

    private int CurrentCommand;
    private int CurrentnRFStatus;
    private int CurrentMeasurementPeriod;

    final BluetoothManager bluetoothManager;
    final BluetoothAdapter bluetoothAdapter;


    protected RHTBluetoothManager(Context context, int CommandChosen) {
        this.context = context;
        this.bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager.getAdapter();
        this.mDataHolder = new DataHolder();
        this.CurrentCommand = CommandChosen;
    }

    protected RHTBluetoothManager(Context context, int CommandChosen, int MeasurementPeriodChosen) {
        this.context = context;
        this.bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager.getAdapter();
        this.mDataHolder = new DataHolder();
        this.CurrentCommand = CommandChosen;
        this.CurrentMeasurementPeriod = MeasurementPeriodChosen;

    }

    public static RHTBluetoothManager getInstance(Context context, int CommandChosen, int MeasurementPeriodChosen ) {
        if (instance == null) {
            synchronized (RHTBluetoothManager.class) {
                if (instance == null) {
                    instance = new RHTBluetoothManager(context, CommandChosen, MeasurementPeriodChosen);
                }

            }
        }
        return instance;
    }

    public static RHTBluetoothManager getInstance(Context context, int CommandChosen) {
            synchronized (RHTBluetoothManager.class) {
                if (instance == null) {
                    instance = new RHTBluetoothManager(context, CommandChosen);
                }

            }

        return instance;
    }

    public BluetoothDevice getDevice() {
        if (mBluetoothGatt != null) {
            return mBluetoothGatt.getDevice();
        }
        return null;
    }


    public void disconnect() {
        Log.w(TAG, "Closing GATT connection");

        // Close old conenction
        if (mBluetoothGatt != null) {
            // Not sure if to disconnect or to close first..
            mBluetoothGatt.disconnect();
            LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(Intents.BLUETOOTH_DISCONNECTED));
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }

        mQueue.clear();
        setCurrentOperation(null);

        //queue(new GattInitializeBluetooth());
    }


    public final synchronized void queue(GattOperation gattOperation) {
        mQueue.add(gattOperation);
        if (mCurrentOperation == null) {
            drive();
        } else {
            Log.v(TAG, "Queueing Gatt operation " + gattOperation.toString() + ", size: " + mQueue.size());
        }
    }

    public synchronized void setCurrentOperation(GattOperation currentOperation) {
        mCurrentOperation = currentOperation;
        if (currentOperation != null) {
            Log.v(TAG, "Current operation: " + mCurrentOperation.toString());
        } else {
            Log.v(TAG, "Current Operation has been finished");

            if (mCurrentOperationTimeout != null) {
                mCurrentOperationTimeout.cancel(true);
                mCurrentOperationTimeout = null;
            }

            drive();
        }
    }

    public synchronized void drive() {
        if (mCurrentOperation != null) {
            Log.v(TAG, "Still a query running (" + mCurrentOperation + "), waiting...");
            return;
        }

        if (mQueue.size() == 0) {
            Log.v(TAG, "Queue empty, drive loop stopped.");
            return;
        }

        setCurrentOperation(mQueue.poll());

        mCurrentOperationTimeout = new AsyncTask<Void, Void, Void>() {
            @Override
            protected synchronized Void doInBackground(Void... voids) {
                try {
                    Log.v(TAG, "Setting timeout for: " + mCurrentOperation.toString());
                    wait(22 * 1000);

                    if (isCancelled()) {
                        Log.v(TAG, "The timeout has already been cancelled.");
                    } else if (null == mCurrentOperation) {
                        Log.v(TAG, "The timeout was cancelled and the query was successful, so we do nothing.");
                    } else {
                        Log.v(TAG, "Timeout ran to completion, time to cancel the operation. Abort ships!");

                        setCurrentOperation(null);
                        return null;
                    }
                } catch (InterruptedException e) {
                    Log.v(TAG, "Timeout was stopped because of early success");
                }
                return null;
            }

            @Override
            protected synchronized void onCancelled() {
                super.onCancelled();
                notify();
            }
        }.execute();

        if (this.bluetoothAdapter != null && this.bluetoothAdapter.isEnabled()) {

            if (mBluetoothGatt != null) {
                execute(mBluetoothGatt, mCurrentOperation);
            } else {

                ScanFilter scanFilter = new ScanFilter.Builder()
                        .setServiceUuid(new ParcelUuid(RHTServiceData.RHT_SERVICE_UUID))
                        .build();
                ArrayList<ScanFilter> filters = new ArrayList<ScanFilter>();
                filters.add(scanFilter);

                ScanSettings settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();


                final BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();

                Log.w(TAG, "Starting scan.");
                // Start new connection
                scanner.startScan(filters, settings, new ScanCallback() {
                    @Override
                    public void onBatchScanResults(List<ScanResult> results) {
                        Log.w(TAG, "Batch results: " + results.size());
                    }

                    @Override
                    public void onScanFailed(int errorCode) {
                        Log.w(TAG, "Scan failed: " + errorCode);

                        disconnect();
                    }

                    @Override
                    public void onScanResult(final int callbackType, final ScanResult result) {
                        LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(Intents.BLUETOOTH_CONNECTING));

                        Log.w(TAG, "Found device: " + result.getDevice().getAddress());

                        if (callbackType == ScanSettings.CALLBACK_TYPE_FIRST_MATCH) {
                            Log.w(TAG, "Found a suitable device, stopping the scan.");
                            scanner.stopScan(this);

                            result.getDevice().connectGatt(context, false, new BluetoothGattCallback() {

                                @Override
                                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                                    super.onCharacteristicChanged(gatt, characteristic);
                                    Log.d(TAG,"Characteristic " + characteristic.getUuid() + "was changed");
                                    if (mCharacteristicChangeListeners.containsKey(characteristic.getUuid())) {
                                        for (CharacteristicChangeListener listener : mCharacteristicChangeListeners.get(characteristic.getUuid())) {
                                            listener.onCharacteristicChanged(getDevice().getAddress(), characteristic);
                                        }
                                    }
                                }

                                @Override
                                public void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, int status) {
                                    super.onCharacteristicRead(gatt, characteristic, status);


                                    Log.w(TAG, "onCharacteristicRead");

                                    ((GattCharacteristicReadOperation) mCurrentOperation).onRead(characteristic);

                                    setCurrentOperation(null);
                                    drive();
                                }

                                @Override
                                public void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, int status) {
                                    super.onCharacteristicWrite(gatt, characteristic, status);

                                    Log.w(TAG, "onCharacteristicWrite");

                                    setCurrentOperation(null);
                                    drive();
                                }


                                @Override
                                public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
                                    super.onConnectionStateChange(gatt, status, newState);

                                    // https://github.com/NordicSemiconductor/puck-central-android/blob/master/PuckCentral/app/src/main/java/no/nordicsemi/puckcentral/bluetooth/gatt/GattManager.java#L117
                                    if (status == 133) {
                                        Log.e(TAG, "Got the status 133 bug, closing gatt");
                                        disconnect();
                                        return;
                                    }

                                    final String stateMessage;
                                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                                        stateMessage = "CONNECTED";
                                    } else if (newState == BluetoothProfile.STATE_CONNECTING) {
                                        stateMessage = "CONNECTING";
                                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                        stateMessage = "DISCONNECTED";
                                    } else if (newState == BluetoothProfile.STATE_DISCONNECTING) {
                                        stateMessage = "DISCONNECTING";
                                    } else {
                                        stateMessage = "UNKNOWN (" + newState + ")";
                                    }

                                    Log.w(TAG, "onConnectionStateChange " + getGattStatusMessage(status) + " " + stateMessage);

                                    if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                                        mBluetoothGatt = gatt;
                                        queue(new GattDiscoverServices());

                                        LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(Intents.BLUETOOTH_CONNECTED));
                                    } else {
                                        LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(Intents.BLUETOOTH_DISCONNECTED));
                                        disconnect();

                                        Log.w(TAG, "Cannot establish Bluetooth connection.");
                                    }

                                    setCurrentOperation(null);
                                }


                                @Override
                                public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                                    super.onDescriptorWrite(gatt, descriptor, status);

                                    Log.w(TAG, "onDescriptorWrite" + getGattStatusMessage(status) + " status " + descriptor);

                                    setCurrentOperation(null);
                                }

                                @Override
                                public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                                    super.onDescriptorRead(gatt, descriptor, status);

                                    ((GattDescriptorReadOperation) mCurrentOperation).onRead(descriptor);

                                    Log.w(TAG, "onDescriptorRead " + getGattStatusMessage(status) + " status " + descriptor);

                                    setCurrentOperation(null);
                                }


                                @Override
                                public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
                                    super.onReliableWriteCompleted(gatt, status);

                                    Log.w(TAG, "onReliableWriteCompleted status " + status);
                                }

                                @Override
                                public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
                                    super.onServicesDiscovered(gatt, status);

                                    if (status == BluetoothGatt.GATT_SUCCESS) {
                                        BluetoothGattService service = gatt.getService(RHTServiceData.RHT_SERVICE_UUID);

                                        final List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();

                                        addCharacteristicChangeListener
                                        (
                                                RHTServiceData.COMMAND_CHAR_UUID,
                                                new CharacteristicChangeListener() {
                                                    @Override
                                                    public void onCharacteristicChanged(String deviceAddress, BluetoothGattCharacteristic characteristic) {
                                                        //Deal with Command Char

                                                        byte[] array = new byte[4];
                                                        array = characteristic.getValue();
                                                        //final int charValue = characteristic.getIntValue(characteristic.FORMAT_UINT32, 0);
                                                        mEnums.GetEnums(array);


                                                        if (mDataHolder.nRFStatus == Enums.nRF_Status.ERROR.getStatus()) {
                                                            mDataHolder.NumberOfMeasurementsReceived = 0;
                                                            disconnect();
                                                            Intent intent = new Intent(Intents.nRF_ERROR);
                                                            intent.putExtra(Intents.Error_KEY, value);
                                                            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                                                        } else if (mDataHolder.nRFStatus == Enums.nRF_Status.BUSY.getStatus()) {
                                                            ;
                                                        }
                                                        //React when embedded system has indicated the completion of operation
                                                        else if (mDataHolder.nRFStatus == Enums.nRF_Status.COMPLETE.getStatus()) {
                                                            if (mDataHolder.Command == Enums.CommandIndex.CURRENT_MEASUREMENTS.getIndex()) {

                                                                //Update Command Char
                                                                mDataHolder.Command = Enums.CommandIndex.CURRENT_MEASUREMENTS_RECEIVED.getIndex();

                                                                queue(new GattCharacteristicWriteOperation(
                                                                        RHTServiceData.RHT_SERVICE_UUID,
                                                                        RHTServiceData.COMMAND_CHAR_UUID,
                                                                        mEnums.EncodeCommandValue()
                                                                ));

                                                                LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(Intents.CURRENT_MEASUREMENTS_RECEIVED));

                                                            } else if (mDataHolder.Command == Enums.CommandIndex.MEASUREMENTS_HISTORY.getIndex()) {
                                                                //Update Command Char
                                                                mDataHolder.Command = Enums.CommandIndex.HISTORY_MEASUREMENTS_RECEIVED.getIndex();

                                                                queue(new GattCharacteristicWriteOperation(
                                                                        RHTServiceData.RHT_SERVICE_UUID,
                                                                        RHTServiceData.COMMAND_CHAR_UUID,
                                                                        mEnums.EncodeCommandValue()
                                                                ));

                                                                LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(Intents.MEASUREMENTS_HISTORY_RECEIVED));

                                                            } else if (mDataHolder.Command == Enums.CommandIndex.CHANGE_INTERVAL.getIndex()) {
                                                                //Update Command Char
                                                                mEnums.CommandValue = Enums.CommandIndex.INTERVAL_CHANGED.getIndex();

                                                                queue(new GattCharacteristicWriteOperation(
                                                                        RHTServiceData.RHT_SERVICE_UUID,
                                                                        RHTServiceData.COMMAND_CHAR_UUID,
                                                                        mEnums.EncodeCommandValue()
                                                                ));

                                                                LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(Intents.INTERVAL_CHANGED));

                                                            } else if (mDataHolder.Command == Enums.CommandIndex.DELETE_HISTORY.getIndex()) {

                                                                //Update Command Char
                                                                mDataHolder.Command= Enums.CommandIndex.HISTORY_DELETED.getIndex();

                                                                queue(new GattCharacteristicWriteOperation(
                                                                        RHTServiceData.RHT_SERVICE_UUID,
                                                                        RHTServiceData.COMMAND_CHAR_UUID,
                                                                        mEnums.EncodeCommandValue()
                                                                ));

                                                                LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(Intents.HISTORY_DELETED));
                                                            }

                                                        }
                                                        //if NRF_State is READY and Command Value indicates completion of an operation, disconnect
                                                        else if (mEnums.StatusValue == Enums.nRF_Status.READY.getStatus()) {
                                                            if (mEnums.CommandValue == Enums.CommandIndex.CURRENT_MEASUREMENTS_RECEIVED.getIndex()
                                                                    || mEnums.CommandValue == Enums.CommandIndex.HISTORY_MEASUREMENTS_RECEIVED.getIndex()
                                                                    || mEnums.CommandValue == Enums.CommandIndex.HISTORY_DELETED.getIndex()
                                                                    || mEnums.CommandValue == Enums.CommandIndex.INTERVAL_CHANGED.getIndex()) {

                                                                mEnums.CommandValue = Enums.CommandIndex.CONNECTED.getIndex();
                                                                disconnect();
                                                            }
                                                        }
                                                    }
                                                });

                                                addCharacteristicChangeListener(
                                                RHTServiceData.TEMPERATURE_CHAR_UUID,
                                                new CharacteristicChangeListener() {
                                                    @Override
                                                    public void onCharacteristicChanged(String deviceAddress, BluetoothGattCharacteristic characteristic) {
                                                        //Deal with Temperature Char
                                                        byte[] array = new byte[4];
                                                        array = characteristic.getValue();
                                                        if (mDataHolder.Command == Enums.CommandIndex.CURRENT_MEASUREMENTS.getIndex()) {
                                                            mTemperatureData.GetCurrentMeasurement(array);
                                                        } else if (mEnums.CommandValue == Enums.CommandIndex.MEASUREMENTS_HISTORY.getIndex()) {
                                                            mTemperatureData.AddMeasurement(array);
                                                        }
                                                    }
                                                });

                                                addCharacteristicChangeListener(
                                                RHTServiceData.HUMIDITY_CHAR_UUID,
                                                new CharacteristicChangeListener() {
                                                    @Override
                                                    public void onCharacteristicChanged(String deviceAddress, BluetoothGattCharacteristic characteristic) {
                                                        //Deal with Humidity Char
                                                        byte[] array = new byte[4];
                                                        array = characteristic.getValue();
                                                        if (mDataHolder.Command == Enums.CommandIndex.CURRENT_MEASUREMENTS.getIndex()) {
                                                            mHumidityData.GetCurrentMeasurement(array);
                                                        } else if (mDataHolder.Command == Enums.CommandIndex.MEASUREMENTS_HISTORY.getIndex()) {
                                                            mHumidityData.AddMeasurement(array);
                                                        }
                                                    }
                                                });


                                        Log.w(TAG, "Found service");

                                        for (BluetoothGattCharacteristic character : characteristics) {
                                            if (character.getUuid().equals(RHTServiceData.HUMIDITY_CHAR_UUID)) {

                                                for (BluetoothGattDescriptor descriptor : character.getDescriptors()) {
                                                    queue(new GattSetNotificationOperation(
                                                            RHTServiceData.RHT_SERVICE_UUID,
                                                            RHTServiceData.HUMIDITY_CHAR_UUID,
                                                            descriptor.getUuid()
                                                    ));
                                                }
                                            }

                                            if (character.getUuid().equals(RHTServiceData.TEMPERATURE_CHAR_UUID)) {

                                                for (BluetoothGattDescriptor descriptor : character.getDescriptors()) {
                                                    queue(new GattSetNotificationOperation(
                                                            RHTServiceData.RHT_SERVICE_UUID,
                                                            RHTServiceData.TEMPERATURE_CHAR_UUID,
                                                            descriptor.getUuid()
                                                    ));
                                                }
                                            }

                                            if (character.getUuid().equals(RHTServiceData.COMMAND_CHAR_UUID)) {

                                                for (BluetoothGattDescriptor descriptor : character.getDescriptors()) {
                                                    queue(new GattSetNotificationOperation(
                                                            RHTServiceData.RHT_SERVICE_UUID,
                                                            RHTServiceData.HUMIDITY_CHAR_UUID,
                                                            descriptor.getUuid()
                                                    ));
                                                }
                                            }

                                        }
                                        Log.w(TAG, "Written to CCCDs");

                                        queue(new GattCharacteristicWriteOperation(
                                                RHTServiceData.RHT_SERVICE_UUID,
                                                RHTServiceData.COMMAND_CHAR_UUID,
                                                mCommmandData.EncodeCommandValue()
                                                        ));
                                    }


                                    Log.w(TAG, "onServicesDiscovered " + getGattStatusMessage(status));

                                    setCurrentOperation(null);
                                }
                            });

                        }
                    }
                });
            }
        } else {


        }
    }

    public void addCharacteristicChangeListener(UUID characteristicUuid, CharacteristicChangeListener characteristicChangeListener) {
        if (!mCharacteristicChangeListeners.containsKey(characteristicUuid)) {
            mCharacteristicChangeListeners.put(characteristicUuid, new ArrayList<CharacteristicChangeListener>());
        }
        mCharacteristicChangeListeners.get(characteristicUuid).add(characteristicChangeListener);
    }

    private synchronized void execute(BluetoothGatt gatt, GattOperation operation) {
        if (operation != mCurrentOperation) {
            Log.e(TAG, "Already other service running!!");
            return;
        }

        operation.execute(gatt);

        if (!operation.hasAvailableCompletionCallback()) {
            setCurrentOperation(null);
        }
    }

    private String getGattStatusMessage(final int status) {
        final String statusMessage;
        if (status == BluetoothGatt.GATT_SUCCESS) {
            statusMessage = "SUCCESS";
        } else if (status == BluetoothGatt.GATT_FAILURE) {
            statusMessage = "FAILED";
        } else if (status == BluetoothGatt.GATT_WRITE_NOT_PERMITTED) {
            statusMessage = "NOT PERMITTED";
        } else if (status == 133) {
            statusMessage = "Found the strange 133 bug";
        } else {
            statusMessage = "UNKNOWN (" + status + ")";
        }

        return statusMessage;
    }
}

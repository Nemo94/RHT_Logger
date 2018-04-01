package com.potemski.michal.rht_logger.gatt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
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

import com.potemski.michal.rht_logger.gatt.operations.GattCharacteristicReadOperation;
import com.potemski.michal.rht_logger.gatt.operations.GattCharacteristicWriteOperation;
import com.potemski.michal.rht_logger.gatt.operations.GattInitializeBluetooth;
import com.potemski.michal.rht_logger.gatt.operations.GattOperation;
import com.potemski.michal.rht_logger.gatt.operations.GattDiscoverServices;

import com.potemski.michal.rht_logger.Intents;
import com.potemski.michal.rht_logger.ExtractMeasurementData;
import com.potemski.michal.rht_logger.EncodeCommands;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;



public class RHTBluetoothManager{
    private static final String LS = System.getProperty("line.separator");
    private static final String TAG = "RHTBluetoothManager";

    private static RHTBluetoothManager instance = null;
    private final Context context;

    private GattOperation mCurrentOperation = null;

    private AsyncTask<Void, Void, Void> mCurrentOperationTimeout;
    private BluetoothGatt mBluetoothGatt = null;
    private ConcurrentLinkedQueue<GattOperation> mQueue = new ConcurrentLinkedQueue<>();

    private int MeasurementPeriodInMinutes;
    private int nRFStatus;
    private int Command;
    private int MeasurementId;
	private float[] TemperatureArray;
	private float[] HumidityArray;
    private int[] TimeArray;
    private int NumberOfMeasurementsReceived;
    private float CurrentTemperature;
	private float CurrentHumidity;
	
	
    private int CharRead;

    private int NewCommand;
    private int NewMeasurementPeriod;

    private boolean ConnectionEstablished;

    private boolean OperationComplete;
    private boolean DownloadComplete;

    final BluetoothManager bluetoothManager;
    final BluetoothAdapter bluetoothAdapter;


    protected RHTBluetoothManager(Context context) {
        this.context = context;
        this.bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager.getAdapter();
        this.nRFStatus = Enums.nRF_Status.PENDING.getStatus();
        this.CharRead = 0;
        this.ConnectionEstablished = false;
        this.OperationComplete = false;
        this.DownloadComplete = false;


        this.TemperatureArray =  new float[30];
		this.HumidityArray =  new float[30];
		this.TimeArray =  new int[30];
           
		   for(int i=0;i<30;i++) {
                this.TemperatureArray[i]= (float) 0.0;
				this.HumidityArray[i]= (float) 0.0;
				this.TimeArray[i]= 0;
            }
		


    }

    public static RHTBluetoothManager getInstance(Context context) {
        if (instance == null) {
            synchronized (RHTBluetoothManager.class) {
                if (instance == null) {
                    instance = new RHTBluetoothManager(context);
                }

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
            mBluetoothGatt.disconnect();
            //mBluetoothGatt.close();
            //mBluetoothGatt = null;
        }

        mQueue.clear();
        setCurrentOperation(null);

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

                        if (callbackType == ScanSettings.CALLBACK_TYPE_ALL_MATCHES) {
                            Log.w(TAG, "Found a suitable device, stopping the scan.");
                            scanner.stopScan(this);

                            result.getDevice().connectGatt(context, false, new BluetoothGattCallback() {

                                @Override
                                public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
                                    super.onServicesDiscovered(gatt, status);

                                    if (status == BluetoothGatt.GATT_SUCCESS) {

                                        ConnectionEstablished = true;
                                        OperationComplete = false;
                                        DownloadComplete = false;
                                        Command = NewCommand;
                                        MeasurementPeriodInMinutes = NewMeasurementPeriod;
                                        String s = "cm =" + String.valueOf(Command) +
                                                "msp =" + String.valueOf(NewMeasurementPeriod) ;
                                        Log.i("FirstWrite",  s);

                                        LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(Intents.NEW_DATA_DOWNLOADING));

                                        queue(new GattCharacteristicWriteOperation(
                                                RHTServiceData.RHT_SERVICE_UUID,
                                                RHTServiceData.COMMAND_CHAR_UUID,
                                                EncodeCommands.EncodeCommandCharValue(Command ,
                                                        NewMeasurementPeriod)
                                        ));
                                        Log.w(TAG, "onServicesDiscovered " + getGattStatusMessage(status));
                                    }


                                }

                                @Override
                                public void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, int status) {
                                    super.onCharacteristicRead(gatt, characteristic, status);
                                    //int array;
                                    byte[] array_st = new byte[4];
                                    byte[] array_meas = new byte[4];

                                    if (characteristic.getUuid().equals(RHTServiceData.STATUS_CHAR_UUID )) {

									    array_st = characteristic.getValue();

										processStatusData(array_st);
										CharRead = CharRead + 1;

									}
									else if (characteristic.getUuid().equals(RHTServiceData.MEASUREMENT_CHAR_UUID )) {

                                       array_meas = characteristic.getValue();

										processMeasurementData(array_meas);
										CharRead = CharRead + 1;

									}

                                    if(CharRead >= 2) {
									    CharRead = 0;
                                        String s = "cm =" + String.valueOf(Command) +
                                                " msp =" + String.valueOf(NewMeasurementPeriod) ;
                                        Log.i("Write",  s);
                                        queue(new GattCharacteristicWriteOperation(
                                                RHTServiceData.RHT_SERVICE_UUID,
                                                RHTServiceData.COMMAND_CHAR_UUID,
                                                EncodeCommands.EncodeCommandCharValue(Command,
                                                        NewMeasurementPeriod)
                                        ));
                                    }

                                    setCurrentOperation(null);
                                    drive();
                                }

                                @Override
                                public void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, int status) {
                                    super.onCharacteristicWrite(gatt, characteristic, status);

                                    if(Command == Enums.CommandIndex.CONNECTED.getIndex() &&
                                            nRFStatus == Enums.nRF_Status.READY.getStatus()) {
                                        OperationComplete = true;
                                    }

                                    if(OperationComplete == false) {

                                        new Thread(new Runnable() {
                                            @Override
                                            public void run() {

                                                //gatt.readCharacteristic(characteristic);
                                                try {
                                                    Thread.sleep(40);
                                                } catch (InterruptedException ex) {
                                                }

                                                queue(new GattCharacteristicReadOperation(
                                                        RHTServiceData.RHT_SERVICE_UUID,
                                                        RHTServiceData.STATUS_CHAR_UUID,
                                                        null
                                                ));

                                                try {
                                                    Thread.sleep(40);
                                                } catch (InterruptedException ex) {
                                                }


                                                queue(new GattCharacteristicReadOperation(
                                                        RHTServiceData.RHT_SERVICE_UUID,
                                                        RHTServiceData.MEASUREMENT_CHAR_UUID,
                                                        null
                                                ));

                                                setCurrentOperation(null);
                                                drive();
                                            }
                                        }).start();
                                    }
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
										//Workaround for Google Issue 183108: NullPointerException in BluetoothGatt.java when disconnecting and closing
										try {
											gatt.close();
                                            ConnectionEstablished = false;
                                            LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(Intents.BLUETOOTH_DISCONNECTED));
										} catch (Exception e) {
											Log.d(TAG, "close ignoring: " + e);
										}
                                    } else if (newState == BluetoothProfile.STATE_DISCONNECTING) {
                                        stateMessage = "DISCONNECTING";
                                        ConnectionEstablished = false;
                                    } else {
                                        stateMessage = "UNKNOWN (" + newState + ")";
                                    }

                                    Log.w(TAG, "onConnectionStateChange " + getGattStatusMessage(status) + " " + stateMessage);

                                    if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                                        mBluetoothGatt = gatt;
                                        queue(new GattDiscoverServices());

                                        LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(Intents.BLUETOOTH_CONNECTED));
                                        NumberOfMeasurementsReceived = 0;

                                    } else {
                                        ConnectionEstablished = false;

                                    }

                                    setCurrentOperation(null);
                                }


                        });

							
                        }
                    }
                });
            }
        }
    }
	
	public void InitializeNrfOperation() {

        String s = "ConnEstabl =" + String.valueOf(ConnectionEstablished) +
                "comm =" + String.valueOf(NewCommand) +
                "op_cpl =" + String.valueOf(OperationComplete);
        Log.i("FirstWrite",  s);


        if(ConnectionEstablished == true) {

            OperationComplete = false;
            DownloadComplete = false;
            Command = NewCommand;
            MeasurementPeriodInMinutes = NewMeasurementPeriod;
            s = "cm =" + String.valueOf(Command) +
                    "msp =" + String.valueOf(NewMeasurementPeriod) ;
            Log.i("FirstWrite",  s);

            LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(Intents.NEW_DATA_DOWNLOADING));

            queue(new GattCharacteristicWriteOperation(
                    RHTServiceData.RHT_SERVICE_UUID,
                    RHTServiceData.COMMAND_CHAR_UUID,
                    EncodeCommands.EncodeCommandCharValue(Command ,
                            NewMeasurementPeriod)
            ));        }
        else {
            mBluetoothGatt = null;
            queue(new GattInitializeBluetooth());
            setCurrentOperation(null);
        }
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

    public void setNewCommand(int newCommand) {
        NewCommand = newCommand;
		CharRead = 0;
    }

    public void setNewMeasurementPeriod(int newMeasurementPeriod) {
        NewMeasurementPeriod = newMeasurementPeriod;
		CharRead = 0;
    }

    public void ResetConnectionFlags()
    {
        ConnectionEstablished = false;
        OperationComplete = false;
        DownloadComplete = false;
    }
	
	private void processStatusData(byte[] array) {
		

        Log.i(TAG, "onCharacteristicRead - STATUS");

        MeasurementPeriodInMinutes = ExtractMeasurementData.GetMeasurementPeriod(array);
        nRFStatus = ExtractMeasurementData.GetNRFStatus(array);

        if(nRFStatus == Enums.nRF_Status.BUSY.getStatus()) {
            NumberOfMeasurementsReceived = ExtractMeasurementData.GetMeasurementIndex(array);
            MeasurementId = ExtractMeasurementData.GetMeasurementId(array);

        }
        else if(nRFStatus == Enums.nRF_Status.COMPLETE.getStatus()) {


			if (Command == Enums.CommandIndex.CURRENT_MEASUREMENTS.getIndex()) {

				Command = Enums.CommandIndex.CURRENT_MEASUREMENTS_RECEIVED.getIndex();
				String d =  "period= " + String.valueOf(MeasurementPeriodInMinutes) +
							" tmp= " + String.valueOf(CurrentTemperature) +
							" rh= " + String.valueOf(CurrentHumidity);
				Log.i("meas_read", d);
				
				Intent intent = new Intent(Intents.CURRENT_MEASUREMENTS_RECEIVED);
				intent.putExtra(Intents.CURRENT_TEMPERATURE_KEY, CurrentTemperature);
				intent.putExtra(Intents.CURRENT_HUMIDITY_KEY, CurrentHumidity);
				intent.putExtra(Intents.MEASUREMENT_PERIOD_KEY, MeasurementPeriodInMinutes);
				LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
				DownloadComplete = true;

			} else if (Command == Enums.CommandIndex.MEASUREMENTS_HISTORY.getIndex()) {

				Command = Enums.CommandIndex.HISTORY_MEASUREMENTS_RECEIVED.getIndex();

				Intent intent = new Intent(Intents.MEASUREMENTS_HISTORY_RECEIVED);
				intent.putExtra(Intents.TEMPERATURE_HISTORY_KEY, TemperatureArray);
				intent.putExtra(Intents.HUMIDITY_HISTORY_KEY, HumidityArray);
				intent.putExtra(Intents.TIME_HISTORY_KEY, TimeArray);
				intent.putExtra(Intents.NUMBER_OF_MEASUREMENTS_KEY, NumberOfMeasurementsReceived);
				intent.putExtra(Intents.MEASUREMENT_PERIOD_KEY, MeasurementPeriodInMinutes);
				LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                DownloadComplete = true;

			} else if (Command == Enums.CommandIndex.DELETE_HISTORY.getIndex()) {

				Command = Enums.CommandIndex.HISTORY_DELETED.getIndex();

				Intent intent = new Intent(Intents.HISTORY_DELETED);
				intent.putExtra(Intents.MEASUREMENT_PERIOD_KEY, MeasurementPeriodInMinutes);
				LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                DownloadComplete = true;


			} else if (Command == Enums.CommandIndex.CHANGE_INTERVAL.getIndex()) {

				Command = Enums.CommandIndex.INTERVAL_CHANGED.getIndex();

				Intent intent = new Intent(Intents.INTERVAL_CHANGED);
				intent.putExtra(Intents.MEASUREMENT_PERIOD_KEY, MeasurementPeriodInMinutes);
				LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                DownloadComplete = true;

			}
        }
        else if(nRFStatus == Enums.nRF_Status.MEASURING.getStatus()) {

            //do nothing
        }
        else if(nRFStatus == Enums.nRF_Status.READY.getStatus() && DownloadComplete == true) {

            if (Command == Enums.CommandIndex.CURRENT_MEASUREMENTS_RECEIVED.getIndex()) {
                Command = Enums.CommandIndex.CONNECTED.getIndex();
            }
            else if (Command == Enums.CommandIndex.HISTORY_MEASUREMENTS_RECEIVED.getIndex()) {
                Command = Enums.CommandIndex.CONNECTED.getIndex();
            }
            else if (Command == Enums.CommandIndex.HISTORY_DELETED.getIndex()) {
                Command = Enums.CommandIndex.CONNECTED.getIndex();
            }
            else if (Command == Enums.CommandIndex.INTERVAL_CHANGED.getIndex()) {
                Command = Enums.CommandIndex.CONNECTED.getIndex();
            }

        }

        else if(nRFStatus == Enums.nRF_Status.ERROR.getStatus()) {

            disconnect();
            LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(Intents.nRF_ERROR));

        }

        String s = "state= " + String.valueOf(nRFStatus) + " period= " + String.valueOf(MeasurementPeriodInMinutes)
                + " id= " + String.valueOf(MeasurementId) + " index= " + String.valueOf(NumberOfMeasurementsReceived);
        Log.i("ST", s);
    }

	private void processMeasurementData(byte [] array) {

        Log.i(TAG, "onCharacteristicRead - MEASUREMENT");

        int temp1 = ExtractMeasurementData.GetTime(array);
        float temp2 = ExtractMeasurementData.GetTemperatureValue(array);
        String s = "t= " + String.valueOf(temp1) + " m= " + String.valueOf(temp2);
        Log.i("MEAS", s);

        if (Command == Enums.CommandIndex.CURRENT_MEASUREMENTS.getIndex()) {
			
            if(nRFStatus == Enums.nRF_Status.BUSY.getStatus()) {
                    if (MeasurementId == Enums.MeasurementIdIndex.TEMPERATURE.getIndex()) {
						
						CurrentTemperature = ExtractMeasurementData.GetTemperatureValue(array);

                    } else if (MeasurementId == Enums.MeasurementIdIndex.HUMIDITY.getIndex()) {

                        CurrentHumidity = ExtractMeasurementData.GetHumidityValue(array);
                    }
            }
        }
        else if (Command == Enums.CommandIndex.MEASUREMENTS_HISTORY.getIndex()) {
                                            
			if(nRFStatus == Enums.nRF_Status.BUSY.getStatus()) {
                if (NumberOfMeasurementsReceived < 30) {
                    if(MeasurementId == Enums.MeasurementIdIndex.TEMPERATURE.getIndex()) {
						
                        TemperatureArray[NumberOfMeasurementsReceived] =
                        ExtractMeasurementData.GetTemperatureValue(array);
                        TimeArray[NumberOfMeasurementsReceived] = ExtractMeasurementData.GetTime(array);
						
                    }
                    else if(MeasurementId == Enums.MeasurementIdIndex.HUMIDITY.getIndex()) {
						
                        HumidityArray[NumberOfMeasurementsReceived] = ExtractMeasurementData.GetHumidityValue(array);
                    }
                }
				else{
					nRFStatus = Enums.nRF_Status.ERROR.getStatus();
                }
            }
        }

    }
		
}

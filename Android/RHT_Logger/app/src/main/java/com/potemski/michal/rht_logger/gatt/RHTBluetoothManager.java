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
import android.os.Bundle;
import android.os.ParcelUuid;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

//import com.potemski.michal.rht_logger.DataHolder;
import com.potemski.michal.rht_logger.gatt.operations.GattCharacteristicReadOperation;
import com.potemski.michal.rht_logger.gatt.operations.GattDescriptorReadOperation;
import com.potemski.michal.rht_logger.gatt.operations.GattCharacteristicWriteOperation;
import com.potemski.michal.rht_logger.gatt.operations.GattInitializeBluetooth;
import com.potemski.michal.rht_logger.gatt.operations.GattOperation;
import com.potemski.michal.rht_logger.gatt.operations.GattDiscoverServices;
import com.potemski.michal.rht_logger.gatt.operations.GattSetNotificationOperation;

import com.potemski.michal.rht_logger.Intents;
import com.potemski.michal.rht_logger.ExtractMeasurementData;
import com.potemski.michal.rht_logger.EncodeCommands;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
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
    //private HashMap<UUID, ArrayList<CharacteristicChangeListener>> mCharacteristicChangeListeners;

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
	private int DescriptorsWritten;

    private int NewCommand;
    private int NewMeasurementPeriod;

    final BluetoothManager bluetoothManager;
    final BluetoothAdapter bluetoothAdapter;


    protected RHTBluetoothManager(Context context) {
        this.context = context;
        this.bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager.getAdapter();
        this.nRFStatus = Enums.nRF_Status.PENDING.getStatus();
//        this.mCharacteristicChangeListeners = new HashMap<UUID, ArrayList<CharacteristicChangeListener>>();
        this.DescriptorsWritten = 0;
        this.CharRead = 0;
		
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

        DescriptorsWritten = 0;
        // Close old conenction
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            //mBluetoothGatt.close();
            //mBluetoothGatt = null;
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

                        if (callbackType == ScanSettings.CALLBACK_TYPE_ALL_MATCHES) {
                            Log.w(TAG, "Found a suitable device, stopping the scan.");
                            scanner.stopScan(this);

                            result.getDevice().connectGatt(context, false, new BluetoothGattCallback() {

                                @Override
                                public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
                                    super.onServicesDiscovered(gatt, status);

                                    if (status == BluetoothGatt.GATT_SUCCESS) {
                                        BluetoothGattService service = gatt.getService(RHTServiceData.RHT_SERVICE_UUID);

                                        final List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
										
//										queue(new GattSetNotificationOperation(
//                                  		RHTServiceData.RHT_SERVICE_UUID,
//                                          RHTServiceData.STATUS_CHAR_UUID,
//                                          RHTServiceData.CCCD_UUID
//                                          ));

//										queue(new GattSetNotificationOperation(
//                                  		RHTServiceData.RHT_SERVICE_UUID,
//                                          RHTServiceData.MEASUREMENT_CHAR_UUID,
//                                          RHTServiceData.CCCD_UUID
//                                          ));


                                        Command = NewCommand;
                                        MeasurementPeriodInMinutes = NewMeasurementPeriod;
                                        String s = "cm =" + String.valueOf(Command) +
                                                "msp =" + String.valueOf(MeasurementPeriodInMinutes) ;
                                        Log.i("FirstWrite",  s);

                                        queue(new GattCharacteristicWriteOperation(
                                                RHTServiceData.RHT_SERVICE_UUID,
                                                RHTServiceData.COMMAND_CHAR_UUID,
                                                EncodeCommands.EncodeCommandCharValue(NewCommand ,
                                                        NewMeasurementPeriod)
                                        ));

                                    }


                                    Log.w(TAG, "onServicesDiscovered " + getGattStatusMessage(status));

                                    setCurrentOperation(null);
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
                                                " msp =" + String.valueOf(MeasurementPeriodInMinutes) ;
                                        Log.i("Write",  s);
                                        queue(new GattCharacteristicWriteOperation(
                                                RHTServiceData.RHT_SERVICE_UUID,
                                                RHTServiceData.COMMAND_CHAR_UUID,
                                                EncodeCommands.EncodeCommandCharValue(Command,
                                                        MeasurementPeriodInMinutes)
                                        ));
                                    }

                                   // ((GattCharacteristicReadOperation) mCurrentOperation).onRead(characteristic);

                                    setCurrentOperation(null);
                                    drive();
                                }

                                @Override
                                public void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, int status) {
                                    super.onCharacteristicWrite(gatt, characteristic, status);

                                   // Log.w(TAG, "onCharacteristicWrite :" + characteristic.toString());

									 //try{
									//	Thread.sleep(10);
									 //}catch(InterruptedException ex){}
									new Thread(new Runnable() {
										@Override
										public void run() {
												//gatt.readCharacteristic(characteristic);
											try{
												Thread.sleep(50);
											}catch(InterruptedException ex){}
											
											queue(new GattCharacteristicReadOperation(
												RHTServiceData.RHT_SERVICE_UUID,
												RHTServiceData.STATUS_CHAR_UUID,
												null
											));

											queue(new GattCharacteristicReadOperation(
												RHTServiceData.RHT_SERVICE_UUID,
												RHTServiceData.MEASUREMENT_CHAR_UUID,
												null
											));

											setCurrentOperation(null);
											drive();
										}
									}).start();
																		 
/*                                     queue(new GattCharacteristicReadOperation(
                                            RHTServiceData.RHT_SERVICE_UUID,
                                            RHTServiceData.STATUS_CHAR_UUID,
                                            null
                                    ));

                                    queue(new GattCharacteristicReadOperation(
                                            RHTServiceData.RHT_SERVICE_UUID,
                                            RHTServiceData.MEASUREMENT_CHAR_UUID,
                                            null
                                    ));

                                    setCurrentOperation(null);
                                    drive(); */
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
											LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(Intents.BLUETOOTH_DISCONNECTED));
										} catch (Exception e) {
											Log.d(TAG, "close ignoring: " + e);
										}
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
                                        NumberOfMeasurementsReceived = 0;

                                    } else {
                                        //LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(Intents.BLUETOOTH_DISCONNECTED));
                                       // disconnect();
                                        NumberOfMeasurementsReceived = 0;

                                        Log.w(TAG, "Cannot establish Bluetooth connection.");
                                    }

                                    setCurrentOperation(null);
                                }


                                @Override
                                public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                                    super.onDescriptorWrite(gatt, descriptor, status);


                                    Log.w(TAG, "onDescriptorWrite" + getGattStatusMessage(status) + " status " + descriptor);
                                    DescriptorsWritten ++;
                                    if(DescriptorsWritten > 2 ) {


                                        queue(new GattCharacteristicWriteOperation(
                                                RHTServiceData.RHT_SERVICE_UUID,
                                                RHTServiceData.COMMAND_CHAR_UUID,
                                                EncodeCommands.EncodeCommandCharValue(NewCommand, NewMeasurementPeriod))
                                        );

                                    }
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
                                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                                    super.onCharacteristicChanged(gatt, characteristic);
                                    Log.i(TAG,"Characteristic " + characteristic.getUuid() + "was changed");

                                    //if (mCharacteristicChangeListeners.containsKey(characteristic.getUuid())) {
                                       // for (CharacteristicChangeListener listener : mCharacteristicChangeListeners.get(characteristic.getUuid())) {
                                      //      listener.onCharacteristicChanged(getDevice().getAddress(), characteristic);
                                      //  }
                                   // }


                                }
                            });

							
                        }
                    }
                });
            }
        } else {


        }
    }
	
	public void InitializeConnection()
	{
		// Init connection :)
        queue(new GattInitializeBluetooth());
	}


    //public void addCharacteristicChangeListener(UUID characteristicUuid, CharacteristicChangeListener characteristicChangeListener) {
    //    if (!mCharacteristicChangeListeners.containsKey(characteristicUuid)) {
    //        mCharacteristicChangeListeners.put(characteristicUuid, new ArrayList<CharacteristicChangeListener>());
    //    }
    //    mCharacteristicChangeListeners.get(characteristicUuid).add(characteristicChangeListener);
    //}

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
		DescriptorsWritten = 0;
    }

    public void setNewMeasurementPeriod(int newMeasurementPeriod) {
        NewMeasurementPeriod = newMeasurementPeriod;
		CharRead = 0;
		DescriptorsWritten = 0;
    }
	
	private void processStatusData(byte[] array) {
		
										

		CharRead = CharRead + 1;

        Log.i(TAG, "onCharacteristicRead - STATUS");
        MeasurementPeriodInMinutes = ExtractMeasurementData.GetMeasurementPeriod(array);
        MeasurementId = ExtractMeasurementData.GetMeasurementId(array);
        NumberOfMeasurementsReceived = ExtractMeasurementData.GetMeasurementIndex(array);
        nRFStatus = ExtractMeasurementData.GetNRFStatus(array);

        String s = "state= " + String.valueOf(nRFStatus) + " period= " + String.valueOf(MeasurementPeriodInMinutes)
                 + " id= " + String.valueOf(MeasurementId) + " index= " + String.valueOf(NumberOfMeasurementsReceived);
        Log.i("ST", s);

        if(nRFStatus == Enums.nRF_Status.BUSY.getStatus()) {


		/*    if (Command == Enums.CommandIndex.CURRENT_MEASUREMENTS.getIndex()) {

				Command = Enums.CommandIndex.CURRENT_MEASUREMENTS.getIndex();

			} else if (Command == Enums.CommandIndex.MEASUREMENTS_HISTORY.getIndex()) {

				Command = Enums.CommandIndex.MEASUREMENTS_HISTORY.getIndex();

			} else if (Command == Enums.CommandIndex.DELETE_HISTORY.getIndex()) {

				Command = Enums.CommandIndex.DELETE_HISTORY.getIndex();

			} else if (Command == Enums.CommandIndex.CHANGE_INTERVAL.getIndex()) {

				Command = Enums.CommandIndex.CHANGE_INTERVAL.getIndex();
				MeasurementPeriodInMinutes = NewMeasurementPeriod;
			} */
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
													
	//			Bundle extras = new Bundle();
	//
	//			extras.putFloat(Intents.CURRENT_TEMPERATURE_KEY, CurrentTemperature);
	//          extras.putFloat(Intents.CURRENT_HUMIDITY_KEY, CurrentHumidity);
	//          extras.putInt(Intents.MEASUREMENT_PERIOD_KEY, MeasurementPeriodInMinutes);
	//			intent.putExtras(extras);

				LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

			} else if (Command == Enums.CommandIndex.MEASUREMENTS_HISTORY.getIndex()) {

				Command = Enums.CommandIndex.HISTORY_MEASUREMENTS_RECEIVED.getIndex();

				Intent intent = new Intent(Intents.MEASUREMENTS_HISTORY_RECEIVED);

				intent.putExtra(Intents.TEMPERATURE_HISTORY_KEY, TemperatureArray);
				intent.putExtra(Intents.HUMIDITY_HISTORY_KEY, HumidityArray);
				intent.putExtra(Intents.TIME_HISTORY_KEY, TimeArray);
				intent.putExtra(Intents.NUMBER_OF_MEASUREMENTS_KEY, NumberOfMeasurementsReceived);
				intent.putExtra(Intents.MEASUREMENT_PERIOD_KEY, MeasurementPeriodInMinutes);

	//			Bundle extras = new Bundle();

	//			  extras.putFloatArray(Intents.TEMPERATURE_HISTORY_KEY, TemperatureArray);
	//            extras.putFloatArray(Intents.HUMIDITY_HISTORY_KEY, HumidityArray);
	//            extras.putIntArray(Intents.TIME_HISTORY_KEY, TimeArray);
	//            extras.putInt(Intents.NUMBER_OF_MEASUREMENTS_KEY, NumberOfMeasurementsReceived);
	//            extras.putInt(Intents.MEASUREMENT_PERIOD_KEY, MeasurementPeriodInMinutes);

				LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

			} else if (Command == Enums.CommandIndex.DELETE_HISTORY.getIndex()) {

				Command = Enums.CommandIndex.HISTORY_DELETED.getIndex();

				Intent intent = new Intent(Intents.HISTORY_DELETED);
				intent.putExtra(Intents.MEASUREMENT_PERIOD_KEY, MeasurementPeriodInMinutes);

				LocalBroadcastManager.getInstance(context).sendBroadcast(intent);


			} else if (Command == Enums.CommandIndex.CHANGE_INTERVAL.getIndex()) {

				Command = Enums.CommandIndex.INTERVAL_CHANGED.getIndex();

				Intent intent = new Intent(Intents.INTERVAL_CHANGED);

				intent.putExtra(Intents.MEASUREMENT_PERIOD_KEY, MeasurementPeriodInMinutes);

				LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

			}
        }
        else if(nRFStatus == Enums.nRF_Status.READY.getStatus()) {

            disconnect();

        }
        else if(nRFStatus == Enums.nRF_Status.ERROR.getStatus()) {

            disconnect();
            LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(Intents.nRF_ERROR));

        }
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

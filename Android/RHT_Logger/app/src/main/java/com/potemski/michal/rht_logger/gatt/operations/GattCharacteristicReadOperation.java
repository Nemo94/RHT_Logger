package com.potemski.michal.rht_logger.gatt.operations;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import com.potemski.michal.rht_logger.gatt.GattCharacteristicReadCallback;

import java.util.UUID;

public class GattCharacteristicReadOperation extends GattOperation {

    private final UUID mService;
    private final UUID mCharacteristic;
    private final GattCharacteristicReadCallback mCallback;

    public GattCharacteristicReadOperation(final UUID service, final UUID characteristic, final GattCharacteristicReadCallback callback) {
        super();
        mService = service;
        mCharacteristic = characteristic;
        mCallback = callback;
    }

    @Override
    public void execute(final BluetoothGatt gatt) {

        BluetoothGattCharacteristic characteristic = gatt.getService(mService).getCharacteristic(mCharacteristic);
        gatt.readCharacteristic(characteristic);
    }

    @Override
    public boolean hasAvailableCompletionCallback() {
        return true;
    }

    //public void onRead(final BluetoothGattCharacteristic characteristic) {
      //  if (mCallback != null) {
        //    mCallback.call(characteristic.getValue());
        //}
    //}

    @Override
    public String toString() {
        return "GattCharacteristicReadOperation";
    }
}

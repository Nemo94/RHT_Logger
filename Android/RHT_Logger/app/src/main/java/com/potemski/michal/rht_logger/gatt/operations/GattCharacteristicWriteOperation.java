package com.potemski.michal.rht_logger.gatt.operations;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;


import java.util.UUID;

public class GattCharacteristicWriteOperation extends GattOperation {
    private static final String TAG = "GattCharacteristicWriteOperation";

    private final UUID mService;
    private final UUID mCharacteristic;
    private final byte[] mValue;

    public GattCharacteristicWriteOperation(final UUID service, final UUID characteristic, byte[] value) {
        super();
        mService = service;
        mCharacteristic = characteristic;
        mValue = value;
    }

    @Override
    public void execute(final BluetoothGatt gatt) {
        BluetoothGattCharacteristic characteristic = gatt.getService(mService).getCharacteristic(mCharacteristic);
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        characteristic.setValue(new byte[2]);
        //characteristic.setValue(mValue, BluetoothGattCharacteristic.FORMAT_UINT32, 0);
        characteristic.setValue(mValue);
        //Log.v("Connection Write OP", String.format("Write val = %d\n", mValue));
        gatt.writeCharacteristic(characteristic);
    }

    @Override
    public boolean hasAvailableCompletionCallback() {
        return true;
    }

    @Override
    public String toString() {
        return "GattCharacteristicWriteOperation";
    }
}

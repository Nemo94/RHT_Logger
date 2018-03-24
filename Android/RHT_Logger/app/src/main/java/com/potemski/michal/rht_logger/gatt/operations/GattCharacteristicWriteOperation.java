package com.potemski.michal.rht_logger.gatt.operations;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import com.potemski.michal.rht_logger.gatt.RHTBluetoothManager;

import java.util.UUID;

public class GattCharacteristicWriteOperation extends GattOperation {
    private static final String TAG = "GattCharacteristicWriteOperation";

    private final UUID mService;
    private final UUID mCharacteristic;
    private final byte[] mValue;

    public GattCharacteristicWriteOperation(final UUID service, final UUID characteristic, byte[] value, final boolean addCRC, final boolean transform) {
        super();
        mService = service;
        mCharacteristic = characteristic;
        mValue = value;
    }

    @Override
    public void execute(final BluetoothGatt gatt) {
        BluetoothGattCharacteristic characteristic = gatt.getService(mService).getCharacteristic(mCharacteristic);
        characteristic.setValue(mValue);
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

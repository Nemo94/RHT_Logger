package com.potemski.michal.rht_logger.gatt.operations;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattDescriptor;

import com.potemski.michal.rht_logger.gatt.GattDescriptorReadCallback;

import java.util.UUID;

public class GattDescriptorReadOperation extends GattOperation {

    private final UUID mService;
    private final UUID mCharacteristic;
    private final UUID mDescriptor;
    private final GattDescriptorReadCallback mCallback;

    public GattDescriptorReadOperation(final UUID service,
                                       final UUID characteristic,
                                       final UUID descriptor,
                                       final GattDescriptorReadCallback callback) {
        super();
        mService = service;
        mCharacteristic = characteristic;
        mDescriptor = descriptor;
        mCallback = callback;
    }

    @Override
    public void execute(final BluetoothGatt gatt) {
        BluetoothGattDescriptor descriptor = gatt.getService(mService).getCharacteristic(mCharacteristic).getDescriptor(mDescriptor);
        gatt.readDescriptor(descriptor);
    }

    @Override
    public boolean hasAvailableCompletionCallback() {
        return true;
    }

    public void onRead(BluetoothGattDescriptor descriptor) {
        mCallback.call(descriptor.getValue());
    }

    @Override
    public String toString() {
        return "GattDescriptorReadOperation";
    }
}

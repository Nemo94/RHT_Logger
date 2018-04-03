package com.potemski.michal.rht_logger.gatt.operations;

import android.bluetooth.BluetoothGatt;

public class GattDisconnectOperation extends GattOperation {

    public GattDisconnectOperation() {
        super();
    }

    @Override
    public void execute(final BluetoothGatt gatt) {
        gatt.disconnect();
    }

    @Override
    public boolean hasAvailableCompletionCallback() {
        return true;
    }

    @Override
    public String toString() {
        return "GattDisconnectOperation";
    }
}

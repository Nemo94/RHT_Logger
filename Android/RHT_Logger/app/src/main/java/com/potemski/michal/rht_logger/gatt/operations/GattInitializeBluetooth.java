package com.potemski.michal.rht_logger.gatt.operations;

import android.bluetooth.BluetoothGatt;


public class GattInitializeBluetooth extends GattOperation {
    @Override
    public void execute(BluetoothGatt bluetoothGatt) {
        // Do nothing
    }

    @Override
    public boolean hasAvailableCompletionCallback() {
        return false;
    }

    @Override
    public String toString() {
        return "GattInitializeBluetooth";
    }
}

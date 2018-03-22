package com.potemski.michal.rht_logger.gatt;

import android.bluetooth.BluetoothGattCharacteristic;

public interface CharacteristicChangeListener {
    public void onCharacteristicChanged(String deviceAddress, BluetoothGattCharacteristic characteristic);
}

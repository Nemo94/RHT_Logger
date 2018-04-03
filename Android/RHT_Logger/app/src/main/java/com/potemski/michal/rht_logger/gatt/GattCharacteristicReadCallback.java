package com.potemski.michal.rht_logger.gatt;

public interface GattCharacteristicReadCallback {
    void call(byte[] characteristic);
}

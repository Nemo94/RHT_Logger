package com.potemski.michal.rht_logger.gatt;

public interface GattDescriptorReadCallback {
    void call(byte[] value);
}

package com.potemski.michal.rht_logger.gatt;

import java.util.ArrayList;

import com.potemski.michal.rht_logger.gatt.operations.GattOperation;

public class GattOperationBundle {
    final ArrayList<GattOperation> operations;

    public GattOperationBundle() {
        operations = new ArrayList<>();
    }

    public void addOperation(GattOperation operation) {
        operations.add(operation);
        operation.setBundle(this);
    }

    public ArrayList<GattOperation> getOperations() {
        return operations;
    }
}

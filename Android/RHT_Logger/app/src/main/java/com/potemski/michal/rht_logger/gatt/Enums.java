package com.potemski.michal.rht_logger.gatt;

/**
 * Created by Michal on 2018-03-23.
 */

public abstract class Enums{

    public enum CommandIndex {
        CURRENT_MEASUREMENTS(1),
        CURRENT_MEASUREMENTS_RECEIVED(2),
        MEASUREMENTS_HISTORY(3),
        HISTORY_MEASUREMENTS_RECEIVED(4),
        DELETE_HISTORY(5),
        HISTORY_DELETED(6),
        CHANGE_INTERVAL(7),
        INTERVAL_CHANGED(8),
        CONNECTED(9);

        private final int index;

        CommandIndex(final int val) {
            index = val;
        }

        public int getIndex() {
            return index;
        }
    }

    public enum nRF_Status {
        PENDING(0),
        READY(1),
        BUSY(2),
        COMPLETE(3),
        MEASURING(4),
        ERROR(5);

        private final int index;

        nRF_Status(final int val) {
            index = val;
        }

        public int getStatus() {
            return index;
        }
    }
	
	    public enum MeasurementIdIndex {
        NONE(0),
        TEMPERATURE(1),
        HUMIDITY(2),
        COMPLETE(3);

        private final int index;

        MeasurementIdIndex(final int val) {
            index = val;
        }

        public int getIndex() {
            return index;
        }
    }
}

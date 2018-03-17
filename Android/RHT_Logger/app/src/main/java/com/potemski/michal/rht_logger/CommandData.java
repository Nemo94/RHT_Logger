package com.potemski.michal.rht_logger;

/**
 * Created by Michal on 2018-03-17.
 */

public class CommandData {

    public int MeasurementPeriodInMinutes;
    public int StatusValue;
    public int CommandValue;

    public void GetCommandData(byte[] bytes)
        {
        MeasurementPeriodInMinutes = GetMeasurementPeriodValueFromByteArray(bytes);
        StatusValue = GetStatusValueFromByteArray(bytes);
    }
    private int GetStatusValueFromByteArray(byte[] bytes)
    {
        return bytes[0] | ((bytes[1] & 0xFF)<<8);
    }

    private int GetMeasurementPeriodValueFromByteArray(byte[] bytes)
    {
        return ((bytes[2] & 0xFF ) | ((bytes[3] & 0xFF )<<8));
    }

    public void SetMeasurementPeriodInMinutes(int value)
    {
        if(value>=1 && value<=240)
        {
            MeasurementPeriodInMinutes = value;
        }
    }

    public byte[] EncodeCommandCharValue()
    {
        byte[] data = new byte [4];

        if(MeasurementPeriodInMinutes<1 || MeasurementPeriodInMinutes>240)
        {
            MeasurementPeriodInMinutes = 1;
        }
        if(CommandValue < 1 || CommandValue > 7 )
        {
            CommandValue = CommandIndex.CONNECTED.getIndex();
        }

        data[0] = (byte) ( CommandValue & 0xFF);
        data[1] = (byte) (( CommandValue >> 8) & 0xFF);
        data[2] = (byte) ( MeasurementPeriodInMinutes & 0xFF);
        data[3] = (byte) (( MeasurementPeriodInMinutes >> 8) & 0xFF);

        return data;
    }

    public enum CommandIndex {
        CURRENT_MEASUREMENTS(1),
        CURRENT_MEASUREMENTS_RECEIVED(2),
        MEASUREMENTS_HISTORY(3),
        HISTORY_MEASUREMENTS_RECEIVED(4),
        DELETE_HISTORY(5),
        CHANGE_INTERVAL(6),
        CONNECTED(7);

        private int index;

        CommandIndex(int val) {
            this.index = val;
        }

        public int getIndex() {
            return index;
        }
    }

    public enum nRF_Status {
        ERROR(0),
        READY(1),
        BUSY(2),
        COMPLETE(3);

        private int index;

        nRF_Status(int val) {
            this.index = val;
        }

        public int getStatus() {
            return index;
        }
    }
}

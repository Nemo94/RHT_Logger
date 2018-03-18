package com.potemski.michal.rht_logger;

/**
 * Created by Michal on 2018-03-17.
 */

public class CommandData {

    public int MeasurementPeriodInMinutes;
    public int StatusValue;
    public int CommandValue;
    public int CommandReceived;

    //Constructor providing start non-default values for objects
    CommandData(int MeasPeriod, int StatVal, int CommVal, int CommReceived)
    {
        MeasurementPeriodInMinutes = MeasPeriod;
        StatusValue = StatVal;
        CommandValue = CommVal;
        CommandReceived = CommReceived;
    }
    //Get Command Char Values from its uint32 packet
    public void GetCommandData(byte[] bytes)
    {
        MeasurementPeriodInMinutes = GetMeasurementPeriodValueFromByteArray(bytes);
        StatusValue = GetStatusValueFromByteArray(bytes);
        CommandReceived = GetCommandValueFromByteArray(bytes);
    }
    private int GetStatusValueFromByteArray(byte[] bytes)
    {
        int temp = (int)((bytes[1] & 0xFF)<<8);
        if(temp < nRF_Status.ERROR.getStatus() || temp > nRF_Status.COMPLETE.getStatus())
        {
            return temp;
        }
        else
        {
            return nRF_Status.ERROR.getStatus();
        }
    }

    private int GetCommandValueFromByteArray(byte[] bytes)
    {
        int temp = (int)bytes[0];

        if(CommandValue < CommandIndex.CURRENT_MEASUREMENTS.getIndex() ||
                CommandValue > CommandIndex.CONNECTED.getIndex() )
        {
            temp = CommandIndex.CONNECTED.getIndex();
        }

        return temp;
    }

    private int GetMeasurementPeriodValueFromByteArray(byte[] bytes)
    {
        int temp = (int) ((bytes[2] & 0xFF ) | ((bytes[3] & 0xFF )<<8));

        if(temp < 1 || temp > 240)
        {
            temp = 1;
        }
        return temp;
    }

    public void SetMeasurementPeriodInMinutes(int value)
    {
        if(value>=1 && value<=240)
        {
            MeasurementPeriodInMinutes = value;
        }
    }
    //Encode Packet for Command Char in uint32
    public byte[] EncodeCommandCharValue()
    {
        byte[] data = new byte [4];

        if(MeasurementPeriodInMinutes < 1 || MeasurementPeriodInMinutes > 240)
        {
            MeasurementPeriodInMinutes = 1;
        }
        if(CommandValue < CommandIndex.CURRENT_MEASUREMENTS.getIndex() ||
                CommandValue > CommandIndex.CONNECTED.getIndex() )
        {
            CommandValue = CommandIndex.CONNECTED.getIndex();
        }

        data[0] = (byte) ( CommandValue & 0xFF);
        data[1] = (byte) (( StatusValue >> 8) & 0xFF);
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
        ERROR(0),
        READY(1),
        BUSY(2),
        COMPLETE(3);

        private final int index;

        nRF_Status(final int val) {
            index = val;
        }

        public int getStatus() {
            return index;
        }
    }
}

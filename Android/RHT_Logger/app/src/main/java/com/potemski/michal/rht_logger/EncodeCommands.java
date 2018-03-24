/**
 * Created by Michal on 2018-03-23.
 */


package com.potemski.michal.rht_logger;

public abstract class EncodeCommands
{

    public static byte[] EncodeCommandCharValue(int CommandValue, int MeasurementPeriodInMinutes)
    {
        byte[] data = new byte [4];

        data[0] = (byte) ( CommandValue & 0xFF);
        data[1] = (byte) (0);
        data[2] = (byte) ( MeasurementPeriodInMinutes & 0xFF);
        data[3] = (byte) (( MeasurementPeriodInMinutes >> 8) & 0xFF);

        return data;
    }

}
/**
 * Created by Michal on 2018-03-23.
 */


package com.potemski.michal.rht_logger;

public abstract class EncodeCommands
{
    private static int byteToInt(byte[] bi) {
        return bi[3] & 0xFF | (bi[2] & 0xFF) << 8 |
                (bi[1] & 0xFF) << 16 | (bi[0] & 0xFF) << 24;
    }

    public static int EncodeCommandCharValue(int CommandValue, int MeasurementPeriodInMinutes)
    {
        byte[] data = new byte [4];

        data[0] = (byte) ( CommandValue & 0xFF);
        data[1] = (byte) (0);
        data[2] = (byte) ( MeasurementPeriodInMinutes & 0xFF);
        data[3] = (byte) (( MeasurementPeriodInMinutes >> 8) & 0xFF);
        //data[0] = 10;
        //data[1] = 2;
        //data[2] = 3;
        //data[3] = 40;
        int temp;
        temp = byteToInt(data);
        return temp;
    }

}
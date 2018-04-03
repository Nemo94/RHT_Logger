/**
 * Created by Michal on 2018-03-23.
 */


package com.potemski.michal.rht_logger;

public abstract class EncodeCommands
{
    //private static int byteToInt(byte[] bi) {
    //    return bi[3] & 0xFF | (bi[2] & 0xFF) << 8 |
   //             (bi[1] & 0xFF) << 16 | (bi[0] & 0xFF) << 24;
   // }

    public static byte[] EncodeCommandCharValue(int CommandValue, int MeasurementPeriodInMinutes)
    {
        byte[] data = new byte [4];

        data[0] = (byte) (CommandValue & 0xFF);
        data[1] = (byte) (MeasurementPeriodInMinutes & 0xFF);

        return data;
        //int temp;
        //temp = byteToInt(data);
        //return temp;
    }

}
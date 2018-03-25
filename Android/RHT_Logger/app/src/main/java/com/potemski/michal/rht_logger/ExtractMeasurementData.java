/**
 * Created by Michal on 2018-03-23.
 */


package com.potemski.michal.rht_logger;

public abstract class ExtractMeasurementData
{

    public static byte[] intToByte(int a)
    {
        byte[] ret = new byte[4];
        ret[3] = (byte) (a & 0xFF);
        ret[2] = (byte) ((a >> 8) & 0xFF);
        ret[1] = (byte) ((a >> 16) & 0xFF);
        ret[0] = (byte) ((a >> 24) & 0xFF);
        return ret;
    }


    public static int GetTime(int encoded)
    {
        byte[] bytes = new byte[4];
        int retValue;
        bytes = intToByte(encoded);
        retValue = ((bytes[2] & 0xFF) | (bytes[1] & 0xFF)<<8);

        return retValue;
    }

    public static float GetTemperatureValue(int encoded)
    {
        byte[] bytes = new byte[4];
        bytes = intToByte(encoded);
        int temp = bytes[0] | ((bytes[1] & 0xFF)<<8);
        float Value = (float)temp/100;
        return Value;
    }
	
	public static float GetHumidityValue(int encoded)
    {
        byte[] bytes = new byte[4];
        bytes = intToByte(encoded);
        int temp = bytes[0] | ((bytes[1] & 0xFF)<<8);
        float Value = (float)temp/100;
        return Value;
    }
	
	public static int GetNRFStatus(int encoded)
    {
        byte[] bytes = new byte[4];
        bytes = intToByte(encoded);

        int temp = (int)((bytes[1] & 0xFF)<<8);

            return temp;

    }

    public static int GetCommand(int encoded)
    {
        byte[] bytes = new byte[4];
        bytes = intToByte(encoded);

        int temp = (int)bytes[0];

        return temp;
    }

    public static int GetMeasurementPeriod(int encoded)
    {
        byte[] bytes = new byte[4];
        bytes = intToByte(encoded);

        int temp = (int) ((bytes[2] & 0xFF ) | ((bytes[3] & 0xFF )<<8));

        return temp;
    }

}
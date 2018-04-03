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


    public static int GetTime(byte[] bytes)
    {
        //byte[] bytes = new byte[4];
        int retValue;
        //bytes = intToByte(encoded);
        retValue = ((bytes[2] & 0xFF) | (bytes[3] & 0xFF)<<8);

        return retValue;
    }

    public static float GetTemperatureValue(byte[] bytes)
    {
        //byte[] bytes = new byte[4];
        //bytes = intToByte(encoded);
        int temp = (bytes[0] & 0xFF) | ((bytes[1] & 0xFF)<<8);
        float Value = (float)temp/100;
        return Value;
    }
	
	public static float GetHumidityValue(byte[] bytes)
    {
        //byte[] bytes = new byte[4];
       // bytes = intToByte(encoded);
        int temp = (bytes[0] & 0xFF) | ((bytes[1] & 0xFF)<<8);
        float Value = (float)temp/100;
        return Value;
    }
	
	public static int GetNRFStatus(byte[] bytes)
    {
        //byte[] bytes = new byte[4];
        //bytes = intToByte(encoded);

        int temp = (int)((bytes[0] & 0xFF));

            return temp;

    }

    public static int GetMeasurementId(byte[] bytes)
    {
        //byte[] bytes = new byte[4];
        //bytes = intToByte(encoded);

        int temp = (int)(bytes[2] & 0xFF);

        return temp;
    }
	
	public static int GetMeasurementIndex(byte[] bytes)
    {
        //byte[] bytes = new byte[4];
        //bytes = intToByte(encoded);

        int temp = (int)(bytes[3] & 0xFF);

        return temp;
    }

    public static int GetMeasurementPeriod(byte[] bytes)
    {
        //byte[] bytes = new byte[4];
        //bytes = intToByte(encoded);
        //it is enough to get one byte (LSB)
        int temp = (int) (bytes[1] & 0xFF );

        return temp;
    }

}
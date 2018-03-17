/**
 * Created by Michal on 2018-03-10.
 */


package com.potemski.michal.rht_logger;

public class GlobalData
{
    public static float[] TemperatureArray =  new float[200];
    public static float[] HumidityArray =  new float[200];
    public static int[] TimeArray =  new int[200];
    public static int NumberOfMeasurements;
    public static float CurrentTemperature;
    public static float CurrentHumidity;




    public int GetCommandValueFromByteArray(byte[] bytes)
    {
        return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
    }

    public int GetMeasurementPeriodValueFromByteArray(byte[] bytes)
    {
        return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
    }

    public int GetTimeValueFromByteArray(byte[] bytes)
    {
        return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
    }

    public float GetTemperatureValueFromByteArray(byte[] bytes)
    {
        int temp = bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
        float Temperature = (float)temp/100;
        return Temperature;
    }

    public float GetHumidityValueFromByteArray(byte[] bytes)
    {
        int temp = bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
        float Temperature = (float)temp/100;
        return Temperature;
    }
}
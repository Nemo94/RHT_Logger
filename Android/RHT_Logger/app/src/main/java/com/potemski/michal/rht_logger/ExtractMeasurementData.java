/**
 * Created by Michal on 2018-03-23.
 */


package com.potemski.michal.rht_logger;

public abstract class ExtractMeasurementData
{

    public int GetTime(byte[] bytes)
    {
        return ((bytes[2] & 0xFF) | (bytes[1] & 0xFF)<<8);
    }

    public float GetTemperatureValue(byte[] bytes)
    {
        int temp = bytes[0] | ((bytes[1] & 0xFF)<<8);
        float Value = (float)temp/100;
        return Value;
    }
	
	public float GetHumidityValue(byte[] bytes)
    {
        int temp = bytes[0] | ((bytes[1] & 0xFF)<<8);
        float Value = (float)temp/100;
        return Value;
    }
	
	public int GetNRFStatus(byte[] bytes)
    {
        int temp = (int)((bytes[1] & 0xFF)<<8);

            return temp;

    }

    public int GetCommand(byte[] bytes)
    {
        int temp = (int)bytes[0];

        return temp;
    }

    public int GetMeasurementPeriod(byte[] bytes)
    {
        int temp = (int) ((bytes[2] & 0xFF ) | ((bytes[3] & 0xFF )<<8));

        return temp;
    }

}
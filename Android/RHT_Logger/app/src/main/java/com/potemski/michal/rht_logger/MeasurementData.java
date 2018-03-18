/**
 * Created by Michal on 2018-03-10.
 */


package com.potemski.michal.rht_logger;

public class MeasurementData
{
    public float[] ValueArray =  new float[200];
    public int[] TimeArray =  new int[200];
    public int NumberOfMeasurements;
    public float CurrentMeasurementValue;


    public void AddMeasurement(byte[] bytes)
    {
        if(NumberOfMeasurements < 200)
        {
            ValueArray[NumberOfMeasurements] = GetMeasurementValueFromByteArray(bytes);
            TimeArray[NumberOfMeasurements] = GetTimeValueFromByteArray(bytes);
            NumberOfMeasurements = NumberOfMeasurements + 1;
        }
        //else - sth went very wrong - if app is expanded, it will handle error here
    }

    public void GetCurrentMeasurement(byte[] bytes)
    {
        CurrentMeasurementValue = GetMeasurementValueFromByteArray(bytes);
    }

    public void ResetMeasurementArray()
    {
        NumberOfMeasurements = 0;
    }
    private int GetTimeValueFromByteArray(byte[] bytes)
    {
        return ((bytes[2] & 0xFF) | (bytes[1] & 0xFF)<<8);
    }

    private float GetMeasurementValueFromByteArray(byte[] bytes)
    {
        int temp = bytes[0] | ((bytes[1] & 0xFF)<<8);
        float Value = (float)temp/100;
        return Value;
    }

}
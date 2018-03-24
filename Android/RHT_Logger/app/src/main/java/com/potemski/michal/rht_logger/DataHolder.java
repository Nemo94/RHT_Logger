package com.potemski.michal.rht_logger;

/**
 * Created by Michal on 2018-03-23.
 */

public class DataHolder{

    public int MeasurementPeriodInMinutes;
    public int nRFStatus;
    public int Command;
	public float[] TemperatureArray =  new float[30];
	public float[] HumidityArray =  new float[30];
    public int[] TimeArray =  new int[30];
    public int NumberOfMeasurementsReceived;
    public float CurrentTemperature;
	public float CurrentHumidity;
}
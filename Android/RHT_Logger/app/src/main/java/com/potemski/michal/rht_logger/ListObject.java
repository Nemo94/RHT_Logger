package com.potemski.michal.rht_logger;

/**
 * Created by Michal on 06/04/2018.
 */

public class ListObject {

    private String Temperature;
	private String Humidity;
	private String Time;

	
	public ListObject(String time, String temperature, String humidity) {
        this.Temperature = temperature;
        this.Humidity = humidity;
		this.Time = time;
    }
	
	public String getTemperature() {
        return Temperature;
    }
 
    public String getHumidity() {
        return Humidity;
    }
	
	public String getTime() {
        return Time;
    }
	
}

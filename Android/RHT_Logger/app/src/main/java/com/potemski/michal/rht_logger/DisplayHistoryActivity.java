package com.potemski.michal.rht_logger;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ListView;

public class DisplayHistoryActivity extends AppCompatActivity {

    ListView listView;

    private CustomAdapter mAdapter;

    private int Opcode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.displayhistory_activity);

        listView = (ListView) findViewById(R.id.listView);

        mAdapter = new CustomAdapter(this);

        // Get the Intent that started this activity and extract the string
        Intent intent = getIntent();
        Opcode = intent.getIntExtra(Intents.OPCODE_COMPLETED, 0);
        String StringTemperature;
        String StringHumidity;
        if(Opcode == 1) {


            float CurrentTemperature;
            float CurrentHumidity;

            CurrentTemperature = intent.getFloatExtra(Intents.CURRENT_TEMPERATURE_KEY, 0);
            CurrentHumidity = intent.getFloatExtra(Intents.CURRENT_HUMIDITY_KEY, 0);

            mAdapter.addSectionHeaderItem(new ListObject(getString(R.string.history_time),
                    getString(R.string.history_temperature), getString(R.string.history_humidity)));

            StringTemperature = convertTemperatureToString(CurrentTemperature);
            StringHumidity = convertHumidityToString(CurrentHumidity);

            mAdapter.addItem(new ListObject(getString(R.string.time_now),
                    StringTemperature, StringHumidity));

        }
        else if(Opcode == 2) {

            float[] TemperatureArray;
            float[] HumidityArray;
            int[] TimeArray;
            int NumberOfMeasurementsReceived;

            String StringTime;

            TemperatureArray =  new float[50];
            HumidityArray =  new float[50];
            TimeArray =  new int[50];

            for(int i=0;i<50;i++) {
                TemperatureArray[i]= (float) 0.0;
                HumidityArray[i]= (float) 0.0;
                TimeArray[i]= 0;
            }

            NumberOfMeasurementsReceived = intent.getIntExtra(Intents.NUMBER_OF_MEASUREMENTS_KEY, 0);
            TemperatureArray = intent.getFloatArrayExtra(Intents.TEMPERATURE_HISTORY_KEY);
            HumidityArray = intent.getFloatArrayExtra(Intents.HUMIDITY_HISTORY_KEY);
            TimeArray = intent.getIntArrayExtra(Intents.TIME_HISTORY_KEY);

            mAdapter.addSectionHeaderItem(new ListObject(getString(R.string.history_time),
                    getString(R.string.history_temperature), getString(R.string.history_humidity)));

            for(int i=0; i < NumberOfMeasurementsReceived; i++) {
                StringTime = convertTimeToString(TimeArray[i]);
                StringTemperature = convertTemperatureToString(TemperatureArray[i]);
                StringHumidity = convertHumidityToString(HumidityArray[i]);
                mAdapter.addItem(new ListObject(StringTime,
                        StringTemperature, StringHumidity));
            }

        }
        else {
            finish();
        }

                listView.setAdapter(mAdapter);

    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case android.R.id.home:
                onBackPressed();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private String convertTemperatureToString(float temperature) {

        String s = String.format("%-2.2f", temperature);
        //String s = Float.toString(temperature);
                s += "°C";
        return s;
    }

    private String convertHumidityToString(float humidity) {

        //String s = Float.toString(humidity);
        String s = String.format("%-2.2f", humidity);
        s += "%";
        return s;
    }

    private String convertTimeToString(int time) {

        //String s = Integer.toString(time);
        String s = String.format("%-3d", time);
        s += " min";
        return s;
    }
}

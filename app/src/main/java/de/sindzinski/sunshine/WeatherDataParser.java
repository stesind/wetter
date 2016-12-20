package de.sindzinski.sunshine;

/**
 * Created by steffen on 19.01.16.
 */

import android.os.Message;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

public class WeatherDataParser {

    private final String LOG_TAG = "WeatherDataParser";
    // JSON Node names
    private static final String TAG_CITY = "city";
    private static final String TAG_CITY_NAME = "name";
    private static final String TAG_LIST = "list";
    private static final String TAG_DATE = "dt";
    private static final String TAG_TEMP = "temp";
    private static final String TAG_TEMP_DAY = "day";
    private static final String TAG_TEMP_MIN = "min";
    private static final String TAG_TEMP_MAX = "max";
    private static final String TAG_TEMP_NIGHT = "night";

    // contacts JSONArray
    JSONArray contacts = null;

    /**
     * Given a string of the form returned by the api call:
     * http://api.openweathermap.org/data/2.5/forecast/daily?q=94043&mode=json&units=metric&cnt=7
     * retrieve the maximum temperature for the day indicated by dayIndex
     * (Note: 0-indexed, so 0 would refer to the first day).
     */
    public double getMaxTemperatureForDay(String weatherJsonStr, int dayIndex)
            throws JSONException {

        parseWeatherJSON(weatherJsonStr, 0);
        parseWeatherJSON(weatherJsonStr, 0);
        return -1;
    }

    public double parseWeatherJSON(String jsonString, int day) {
        JSONObject json = null;
        double tempDay =0;
        double tempMin =0 ;
        double tempMax =0;
        double tempNight =0;

        // getting JSON string from URL
        //JSONObject json = .getJSONFromUrl(url);
        try {
            json = new JSONObject(jsonString);
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.toString());
        }

        try {
            //getting the city
            //JSONObject city = json.getJSONObject(TAG_CITY);
            JSONArray list = json.getJSONArray(TAG_LIST);

            // looping through All days
            //for (int i = 0; i < list.length(); i++) {
            //    JSONObject d = list.getJSONObject(i);
            try {
                JSONObject d = list.getJSONObject(day);
                // Storing each json item in variable
                String id = d.getString(TAG_DATE);

                // Temp is again JSON Object
                JSONObject temp = d.getJSONObject(TAG_TEMP);
                tempDay = temp.getInt(TAG_TEMP_DAY);
                tempMin = temp.getInt(TAG_TEMP_MIN);
                tempMax = temp.getInt(TAG_TEMP_MAX);
                tempNight = temp.getInt(TAG_TEMP_NIGHT);

            } catch (JSONException e) {
                Log.e(LOG_TAG, e.toString());
            }
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.toString());
        }
        return tempMin;
    }
}


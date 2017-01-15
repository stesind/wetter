package de.sindzinski.wetter.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncRequest;
import android.content.SyncResult;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.IntDef;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.view.ViewCompat;
import android.util.Log;
import android.view.animation.OvershootInterpolator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.Vector;

import de.sindzinski.wetter.BuildConfig;
import de.sindzinski.wetter.MainActivity;
import de.sindzinski.wetter.R;
import de.sindzinski.wetter.Utility;
import de.sindzinski.wetter.data.WeatherContract;

import static de.sindzinski.wetter.data.WeatherContract.TYPE_CURRENT;
import static de.sindzinski.wetter.data.WeatherContract.TYPE_DAILY;
import static de.sindzinski.wetter.data.WeatherContract.TYPE_HOURLY;

public class WetterSyncAdapter extends AbstractThreadedSyncAdapter {
    public final String LOG_TAG = WetterSyncAdapter.class.getSimpleName();
    // Interval at which to sync with the weather, in seconds.
    // 60 seconds (1 minute) * 180 = 3 hours
    public static final int SYNC_INTERVAL = 60 * 180;
    public static final int SYNC_FLEXTIME = SYNC_INTERVAL / 3;
    private static final long DAY_IN_MILLIS = 1000 * 60 * 60 * 24;
    private static final long HOUR_IN_MILLIS = 1000 * 60 * 60;
    private static final long MINUTE_IN_MILLIS = 1000 * 60 ;
    private static final int WEATHER_NOTIFICATION_ID = 3004;


    private static final String[] NOTIFY_WEATHER_PROJECTION = new String[]{
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC
    };

    // these indices must match the projection
    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_MAX_TEMP = 1;
    private static final int INDEX_MIN_TEMP = 2;
    private static final int INDEX_SHORT_DESC = 3;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({LOCATION_STATUS_OK, LOCATION_STATUS_SERVER_DOWN, LOCATION_STATUS_SERVER_INVALID, LOCATION_STATUS_UNKNOWN, LOCATION_STATUS_INVALID})
    public @interface LocationStatus {
    }

    public static final int LOCATION_STATUS_OK = 0;
    public static final int LOCATION_STATUS_SERVER_DOWN = 1;
    public static final int LOCATION_STATUS_SERVER_INVALID = 2;
    public static final int LOCATION_STATUS_UNKNOWN = 3;
    public static final int LOCATION_STATUS_INVALID = 4;

    public WetterSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        syncAllSources(account, extras, authority, provider, syncResult, TYPE_DAILY);

        if (Utility.getHourlyForecast(getContext())) {
            syncAllSources(account, extras, authority, provider, syncResult, TYPE_HOURLY);
        }

        syncAllSources(account, extras, authority, provider, syncResult, TYPE_CURRENT);

        notifyWeather();
    }

    private void syncAllSources(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult, Integer type) {
        Log.d(LOG_TAG, "Starting sync");
        String locationQuery = Utility.getPreferredLocation(getContext());

        // These two need to be declared outside the try/catch
        // so that they can be closed in the finally block.
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        // Will contain the raw JSON response as a string.
        String forecastJsonStr = null;

        String format = "json";
        String units = "metric";
        int numDays = 14;

        try {
            // Construct the URL for the OpenWeatherMap query
            // Possible parameters are avaiable at OWM's forecast API page, at
            // http://openweathermap.org/API#forecast

            final String QUERY_PARAM = "q";
            final String FORMAT_PARAM = "mode";
            final String UNITS_PARAM = "units";
            final String DAYS_PARAM = "cnt";
            final String APPID_PARAM = "APPID";
            Uri builtUri = null;

            if (type == TYPE_HOURLY) {
                final String FORECAST_BASE_URL =
                        "http://api.openweathermap.org/data/2.5/forecast?";
                builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(QUERY_PARAM, locationQuery)
                        .appendQueryParameter(FORMAT_PARAM, format)
                        .appendQueryParameter(UNITS_PARAM, units)
                        .appendQueryParameter(APPID_PARAM, BuildConfig.OPEN_WEATHER_MAP_API_KEY)
                        .build();
            } else if (type == TYPE_DAILY) {
                final String FORECAST_BASE_URL =
                        "http://api.openweathermap.org/data/2.5/forecast/daily?";
                builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(QUERY_PARAM, locationQuery)
                        .appendQueryParameter(FORMAT_PARAM, format)
                        .appendQueryParameter(UNITS_PARAM, units)
                        .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
                        .appendQueryParameter(APPID_PARAM, BuildConfig.OPEN_WEATHER_MAP_API_KEY)
                        .build();
            } else if (type == TYPE_CURRENT) { //current
                final String FORECAST_BASE_URL =
                        "http://api.openweathermap.org/data/2.5/weather?";
                builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(QUERY_PARAM, locationQuery)
                        .appendQueryParameter(FORMAT_PARAM, format)
                        .appendQueryParameter(UNITS_PARAM, units)
                        .appendQueryParameter(APPID_PARAM, BuildConfig.OPEN_WEATHER_MAP_API_KEY)
                        .build();
            }
            URL url = new URL(builtUri.toString());

            // Create the request to OpenWeatherMap, and open the connection
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();

            // Read the input stream into a String
            InputStream inputStream = urlConnection.getInputStream();
            StringBuffer buffer = new StringBuffer();
            if (inputStream == null) {
                // Nothing to do.
                return;
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null) {
                // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                // But it does make debugging a *lot* easier if you print out the completed
                // buffer for debugging.
                buffer.append(line + "\n");
            }

            if (buffer.length() == 0) {
                // Stream was empty.  No point in parsing.
                setLocationStatus(getContext(), LOCATION_STATUS_SERVER_DOWN);
                return;
            }
            forecastJsonStr = buffer.toString();
            if (type == TYPE_HOURLY) {
                getWeatherDataFromJsonHourly(forecastJsonStr, locationQuery);
            } else if (type == TYPE_DAILY) {
                getWeatherDataFromJsonDaily(forecastJsonStr, locationQuery);
            } else if (type== TYPE_CURRENT) {
                getWeatherDataFromJsonCurrent(forecastJsonStr, locationQuery);
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error ", e);
            // If the code didn't successfully get the weather data, there's no point in attempting
            // to parse it.
            setLocationStatus(getContext(), LOCATION_STATUS_SERVER_DOWN);
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
            setLocationStatus(getContext(), LOCATION_STATUS_SERVER_INVALID);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException e) {
                    Log.e(LOG_TAG, "Error closing stream", e);
                }
            }
        }
        return;
    }

    /**
     * Take the String representing the complete forecast in JSON Format and
     * pull out the data we need to construct the Strings needed for the wireframes.
     * <p>
     * Fortunately parsing is easy:  constructor takes the JSON string and converts it
     * into an Object hierarchy for us.
     */

    private void getWeatherDataFromJsonCurrent(String forecastJsonStr,
                                               String locationSetting)
            throws JSONException {

        // Now we have a String representing the complete forecast in JSON Format.
        // Fortunately parsing is easy:  constructor takes the JSON string and converts it
        // into an Object hierarchy for us.

        // These are the names of the JSON objects that need to be extracted.
        // Location information
        String OWM_CITY = "id";
        String OWM_CITY_NAME = "name";
        String OWM_COORD = "coord";

        // Location coordinate
        String OWM_LATITUDE = "lat";
        String OWM_LONGITUDE = "lon";

        String OWM_TIME = "dt";
        // Weather information.  Each day's forecast info is an element of the "list" array.

        String OWM_WIND = "wind";
        String OWM_WIND_SPEED = "speed";
        String OWM_WIND_DIRECTION = "deg";

        String OWM_MAIN = "main";
        String OWM_TEMP = "temp";
        String OWM_MAX = "temp_max";
        String OWM_MIN = "temp_min";
        String OWM_PRESSURE = "pressure";
        String OWM_HUMIDITY = "humidity";
        String OWM_MESSAGE_CODE = "cod";

        String OWM_WEATHER = "weather";
        String OWM_WEATHER_ID = "id";
        String OWM_DESCRIPTION = "main";
        String OWM_WEATHER_ICON = "icon";

        String OWM_CLOUDS = "clouds";
        String OWM_CLOUDS_ALL = "all";

        String OWM_RAIN = "rain";
        String OWM_RAIN_3h = "3h";
        String OWM_SNOW = "snow";
        String OWM_SNOW_3h = "3h";

        String OWM_SYS = "sys";
        String OWM_SUN_RISE = "sunrise";
        String OWM_SUN_SET = "sunset";

        try {
            JSONObject forecastJson = new JSONObject(forecastJsonStr);

            // do we have an error?
            if (forecastJson.has(OWM_MESSAGE_CODE)) {
                int errorCode = forecastJson.getInt(OWM_MESSAGE_CODE);

                switch (errorCode) {
                    case HttpURLConnection.HTTP_OK:
                        break;
                    case HttpURLConnection.HTTP_NOT_FOUND:
                        setLocationStatus(getContext(), LOCATION_STATUS_INVALID);
                        return;
                    default:
                        setLocationStatus(getContext(), LOCATION_STATUS_SERVER_DOWN);
                        return;
                }
            }


            int cityID = forecastJson.getInt(OWM_CITY);
            String cityName = forecastJson.getString(OWM_CITY_NAME);

            double cityLatitude = 0;
            double cityLongitude = 0;
            if (forecastJson.has(OWM_COORD)) {
                JSONObject coord = forecastJson.getJSONObject(OWM_COORD);
                cityLatitude = coord.getDouble(OWM_LATITUDE);
                cityLongitude = coord.getDouble(OWM_LONGITUDE);
            }

            long locationId = addLocation(locationSetting, cityName, cityLatitude, cityLongitude);

            long timeInMillis = 0;

            double pressure;
            int humidity;
            int clouds;
            double windSpeed;
            double windDirection;

            double min;
            double max;
            double temp;

            double rain = 0;
            double snow = 0;

            String icon;

            String description;
            int weatherId;

            long sunRise = 0;
            long sunSet = 0;

            // Cheating to convert this to UTC time, which is what we want anyhow
            timeInMillis = forecastJson.getLong(OWM_TIME) * 1000;

            JSONObject weatherObject = forecastJson.getJSONArray(OWM_WEATHER).getJSONObject(0);
            description = weatherObject.getString(OWM_DESCRIPTION);
            weatherId = weatherObject.getInt(OWM_WEATHER_ID);
            icon = weatherObject.getString(OWM_WEATHER_ICON);

            JSONObject mainObject = forecastJson.getJSONObject(OWM_MAIN);
            temp = mainObject.getDouble(OWM_TEMP);
            max = mainObject.getDouble(OWM_MAX);
            min = mainObject.getDouble(OWM_MIN);
            pressure = mainObject.getDouble(OWM_PRESSURE);
            humidity = mainObject.getInt(OWM_HUMIDITY);

            JSONObject windObject = forecastJson.getJSONObject(OWM_WIND);
            windSpeed = windObject.getDouble(OWM_WIND_SPEED);
            windDirection = windObject.getDouble(OWM_WIND_DIRECTION);

            JSONObject cloudsObject = forecastJson.getJSONObject(OWM_CLOUDS);
            clouds = cloudsObject.getInt(OWM_CLOUDS_ALL);

            if (forecastJson.has(OWM_RAIN)) {
                JSONObject rainObject = forecastJson.getJSONObject(OWM_RAIN);
                rain = rainObject.has(OWM_RAIN_3h) ? rainObject.getDouble(OWM_RAIN_3h) : 0;
            }

            if (forecastJson.has(OWM_SNOW)) {
                JSONObject snowObject = forecastJson.getJSONObject(OWM_SNOW);
                snow = snowObject.has(OWM_SNOW_3h) ? snowObject.getDouble(OWM_SNOW_3h) : 0;
            }

            if (forecastJson.has(OWM_SYS)) {
                JSONObject sysObject = forecastJson.getJSONObject(OWM_SYS);
                sunRise = sysObject.has(OWM_SUN_RISE) ? sysObject.getLong(OWM_SUN_RISE) : 0;
                sunSet = sysObject.has(OWM_SUN_SET) ? sysObject.getLong(OWM_SUN_SET) : 0;
            }


            ContentValues weatherValues = new ContentValues();

            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_LOC_KEY, locationId);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_DATE, timeInMillis);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_HUMIDITY, humidity);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_PRESSURE, pressure);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_CLOUDS, clouds);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_WIND_SPEED, windSpeed);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_DEGREES, windDirection);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP, max);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP, min);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_TEMP, temp);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_SHORT_DESC, description);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_WEATHER_ID, weatherId);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_RAIN, rain);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_SNOW, snow);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_ICON, icon);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_TYPE, TYPE_CURRENT);

            // Insert the new weather information into the database
            Vector<ContentValues> cVVector = new Vector<ContentValues>(1);
            cVVector.add(weatherValues);

            // add to database
            if (cVVector.size() > 0) {

                //delete all old data of given type
                String selection = WeatherContract.WeatherEntry.COLUMN_TYPE + " = ? ";
                String[] selectionArgs = new String[]{Integer.toString(TYPE_CURRENT)};
                getContext().getContentResolver().delete(WeatherContract.WeatherEntry.CONTENT_URI,
                        selection,
                        selectionArgs);

                ContentValues[] cvArray = new ContentValues[cVVector.size()];
                cVVector.toArray(cvArray);
                getContext().getContentResolver().bulkInsert(WeatherContract.WeatherEntry.CONTENT_URI, cvArray);

                // delete old data so we don't build up an endless history
                // get the time beginning of today for daily
                Calendar cal = Calendar.getInstance(TimeZone.getDefault());
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                timeInMillis = cal.getTimeInMillis();

//                String selection = WeatherContract.WeatherEntry.COLUMN_DATE + " < ? AND " +
//                        WeatherContract.WeatherEntry.COLUMN_TYPE + " = ? ";
//                String[] selectionArgs = new String[]{Long.toString(timeInMillis), Integer.toString(TYPE_CURRENT)};
//                getContext().getContentResolver().delete(WeatherContract.WeatherEntry.CONTENT_URI,
//                        selection,
//                        selectionArgs);
            }
            Log.d(LOG_TAG, "Sync Complete. " + cVVector.size() + " Inserted");
            setLocationStatus(getContext(), LOCATION_STATUS_OK);

        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
            setLocationStatus(getContext(), LOCATION_STATUS_SERVER_INVALID);
        }
    }

    private void getWeatherDataFromJsonDaily(String forecastJsonStr,
                                             String locationSetting)
            throws JSONException {

        // Now we have a String representing the complete forecast in JSON Format.
        // Fortunately parsing is easy:  constructor takes the JSON string and converts it
        // into an Object hierarchy for us.

        // These are the names of the JSON objects that need to be extracted.
        // Location information
        String OWM_CITY = "city";
        String OWM_CITY_NAME = "name";
        String OWM_COORD = "coord";

        // Location coordinate
        String OWM_LATITUDE = "lat";
        String OWM_LONGITUDE = "lon";

        String OWM_TIME = "dt";
        // Weather information.  Each day's forecast info is an element of the "list" array.
        String OWM_LIST = "list";

        String OWM_PRESSURE = "pressure";
        String OWM_HUMIDITY = "humidity";
        String OWM_CLOUDS = "clouds";
        String OWM_WIND = "wind";
        String OWM_WINDSPEED = "speed";
        String OWM_WIND_DIRECTION = "deg";

        // All temperatures are children of the "temp" object.
        String OWM_TEMPERATURE = "temp";
        String OWM_MAX = "max";
        String OWM_MIN = "min";

        String OWM_WEATHER = "weather";
        String OWM_DESCRIPTION = "main";
        String OWM_WEATHER_ID = "id";

        String OWM_MESSAGE_CODE = "cod";

        String OWM_MORNING = "morn";
        String OWM_DAY = "day";
        String OWM_EVENING = "eve";
        String OWM_NIGHT = "night";

        String OWM_SNOW = "snow";
        String OWM_RAIN = "rain";

        String OWM_ICON = "icon";

        try {
            JSONObject forecastJson = new JSONObject(forecastJsonStr);

            // do we have an error?
            if (forecastJson.has(OWM_MESSAGE_CODE)) {
                int errorCode = forecastJson.getInt(OWM_MESSAGE_CODE);

                switch (errorCode) {
                    case HttpURLConnection.HTTP_OK:
                        break;
                    case HttpURLConnection.HTTP_NOT_FOUND:
                        setLocationStatus(getContext(), LOCATION_STATUS_INVALID);
                        return;
                    default:
                        setLocationStatus(getContext(), LOCATION_STATUS_SERVER_DOWN);
                        return;
                }
            }

            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            JSONObject cityJson = forecastJson.getJSONObject(OWM_CITY);
            String cityName = cityJson.getString(OWM_CITY_NAME);

            double cityLatitude = 0;
            double cityLongitude = 0;
            if (cityJson.has(OWM_COORD)) {
                JSONObject cityCoord = cityJson.getJSONObject(OWM_COORD);
                cityLatitude = cityCoord.getDouble(OWM_LATITUDE);
                cityLongitude = cityCoord.getDouble(OWM_LONGITUDE);
            }

            long locationId = addLocation(locationSetting, cityName, cityLatitude, cityLongitude);

            // Insert the new weather information into the database
            Vector<ContentValues> cVVector = new Vector<ContentValues>(weatherArray.length());

            // OWM returns daily forecasts based upon the local time of the city that is being
            // asked for, which means that we need to know the GMT offset to translate this data
            // properly.

            // Since this data is also sent in-order and the first day is always the
            // current day, we're going to take advantage of that to get a nice
            // normalized UTC date for all of our weather.
            long timeInMillis = 0;

            for (int i = 0; i < weatherArray.length(); i++) {
                // These are the values that will be collected.

                double pressure;
                int humidity;
                int clouds;
                double windSpeed;
                double windDirection;

                double high;
                double low;
                double morning;
                double day;
                double evening;
                double night;

                double rain;
                double snow;

                String icon;

                String description;
                int weatherId;

                // Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                // Cheating to convert this to UTC time, which is what we want anyhow
                timeInMillis = dayForecast.getLong(OWM_TIME) * 1000;

                windSpeed = dayForecast.getDouble(OWM_WINDSPEED);
                windDirection = dayForecast.getDouble(OWM_WIND_DIRECTION);

                pressure = dayForecast.getDouble(OWM_PRESSURE);
                humidity = dayForecast.getInt(OWM_HUMIDITY);
                clouds = dayForecast.getInt(OWM_CLOUDS);

                // Temperatures are in a child object called "temp".  Try not to name variables
                // "temp" when working with temperature.  It confuses everybody.
                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                high = temperatureObject.getDouble(OWM_MAX);
                low = temperatureObject.getDouble(OWM_MIN);
                morning = temperatureObject.getDouble(OWM_MORNING);
                day = temperatureObject.getDouble(OWM_DAY);
                evening = temperatureObject.getDouble(OWM_EVENING);
                night = temperatureObject.getDouble(OWM_NIGHT);

                // Description is in a child array called "weather", which is 1 element long.
                // That element also contains a weather code.
                JSONObject weatherObject =
                        dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);
                weatherId = weatherObject.getInt(OWM_WEATHER_ID);
                icon = weatherObject.getString(OWM_ICON);

                ContentValues weatherValues = new ContentValues();

                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_LOC_KEY, locationId);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_DATE, timeInMillis);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_HUMIDITY, humidity);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_PRESSURE, pressure);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_CLOUDS, clouds);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_WIND_SPEED, windSpeed);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_DEGREES, windDirection);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP, high);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP, low);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_MORNING_TEMP, morning);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_DAY_TEMP, day);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_EVENING_TEMP, evening);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_NIGHT_TEMP, night);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_SHORT_DESC, description);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_WEATHER_ID, weatherId);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_ICON, icon);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_TYPE, TYPE_DAILY);

                cVVector.add(weatherValues);
            }

            int inserted = 0;
            // add to database
            if (cVVector.size() > 0) {

                //delete all old data of given type
//                String selection = WeatherContract.WeatherEntry.COLUMN_TYPE + " = ? ";
//                String[] selectionArgs = new String[]{Integer.toString(type)};
//                getContext().getContentResolver().delete(WeatherContract.WeatherEntry.CONTENT_URI,
//                        selection,
//                        selectionArgs);

                ContentValues[] cvArray = new ContentValues[cVVector.size()];
                cVVector.toArray(cvArray);
                getContext().getContentResolver().bulkInsert(WeatherContract.WeatherEntry.CONTENT_URI, cvArray);

                // delete old data so we don't build up an endless history
                // get the time beginning of today for daily
                Calendar cal = Calendar.getInstance(TimeZone.getDefault());
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                timeInMillis = cal.getTimeInMillis();


                String selection = WeatherContract.WeatherEntry.COLUMN_DATE + " < ? AND " +
                        WeatherContract.WeatherEntry.COLUMN_TYPE + " = ? ";
                String[] selectionArgs = new String[]{Long.toString(timeInMillis), Integer.toString(TYPE_DAILY)};
                getContext().getContentResolver().delete(WeatherContract.WeatherEntry.CONTENT_URI,
                        selection,
                        selectionArgs);
            }
            Log.d(LOG_TAG, "Sync Complete. " + cVVector.size() + " Inserted");
            setLocationStatus(getContext(), LOCATION_STATUS_OK);

        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
            setLocationStatus(getContext(), LOCATION_STATUS_SERVER_INVALID);
        }
    }

    /**
     * Take the String representing the complete forecast in JSON Format and
     * pull out the data we need to construct the Strings needed for the wireframes.
     * <p>
     * Fortunately parsing is easy:  constructor takes the JSON string and converts it
     * into an Object hierarchy for us.
     */
    private void getWeatherDataFromJsonHourly(String forecastJsonStr,
                                              String locationSetting)
            throws JSONException {

        // Now we have a String representing the complete forecast in JSON Format.
        // Fortunately parsing is easy:  constructor takes the JSON string and converts it
        // into an Object hierarchy for us.

        // These are the names of the JSON objects that need to be extracted.
        // Location information

        // Location information
        String OWM_CITY = "city";
        String OWM_CITY_NAME = "name";
        String OWM_COORD = "coord";

        // Location coordinate
        String OWM_LATITUDE = "lat";
        String OWM_LONGITUDE = "lon";

        String OWM_TIME = "dt";
        // Weather information.  Each day's forecast info is an element of the "list" array.
        String OWM_LIST = "list";

        String OWM_PRESSURE = "pressure";
        String OWM_HUMIDITY = "humidity";
        String OWM_WIND = "wind";
        String OWM_WIND_SPEED = "speed";
        String OWM_WIND_DIRECTION = "deg";

        // All temperatures are children of the "temp" object.
        String OWM_MAIN = "main";
        String OWM_TEMP = "temp";
        String OWM_MAX = "temp_max";
        String OWM_MIN = "temp_min";

        String OWM_WEATHER = "weather";
        String OWM_WEATHER_ID = "id";
        String OWM_DESCRIPTION = "main";
        String OWM_MESSAGE_CODE = "cod";
        String OWM_WEATHER_ICON = "icon";

        String OWM_CLOUDS = "clouds";
        String OWM_CLOUDS_ALL = "all";

        String OWM_RAIN = "rain";
        String OWM_RAIN_3h = "3h";
        String OWM_SNOW = "snow";
        String OWM_SNOW_3h = "3h";


        String OWM_ICON = "icon";

        try {
            JSONObject forecastJson = new JSONObject(forecastJsonStr);

            // do we have an error?
            if (forecastJson.has(OWM_MESSAGE_CODE)) {
                int errorCode = forecastJson.getInt(OWM_MESSAGE_CODE);

                switch (errorCode) {
                    case HttpURLConnection.HTTP_OK:
                        break;
                    case HttpURLConnection.HTTP_NOT_FOUND:
                        setLocationStatus(getContext(), LOCATION_STATUS_INVALID);
                        return;
                    default:
                        setLocationStatus(getContext(), LOCATION_STATUS_SERVER_DOWN);
                        return;
                }
            }

            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            JSONObject cityJson = forecastJson.getJSONObject(OWM_CITY);
            String cityName = cityJson.getString(OWM_CITY_NAME);

            double cityLatitude = 0;
            double cityLongitude = 0;
            if (cityJson.has(OWM_COORD)) {
                JSONObject cityCoord = cityJson.getJSONObject(OWM_COORD);
                cityLatitude = cityCoord.getDouble(OWM_LATITUDE);
                cityLongitude = cityCoord.getDouble(OWM_LONGITUDE);
            }

            long locationId = addLocation(locationSetting, cityName, cityLatitude, cityLongitude);

            // Insert the new weather information into the database
            Vector<ContentValues> cVVector = new Vector<ContentValues>(weatherArray.length());

            // OWM returns daily forecasts based upon the local time of the city that is being
            // asked for, which means that we need to know the GMT offset to translate this data
            // properly.

            // Since this data is also sent in-order and the first day is always the
            // current day, we're going to take advantage of that to get a nice
            // normalized UTC date for all of our weather.
            long timeInMillis = 0;

            for (int i = 0; i < weatherArray.length(); i++) {
                // These are the values that will be collected.

                double pressure = 0;
                int humidity = 0;
                double windSpeed;
                double windDirection;

                double temp;
                double max;
                double min;
                double morning;
                double day;
                double evening;
                double night;

                double rain = 0;
                double snow = 0;
                int clouds;

                String icon;

                String description;
                int weatherId;

                // Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                // Cheating to convert this to UTC time, which is what we want anyhow
                timeInMillis = dayForecast.getLong(OWM_TIME) * 1000;

                // Description is in a child array called "weather", which is 1 element long.
                // That element also contains a weather code.
                JSONObject weatherObject =
                        dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);
                weatherId = weatherObject.getInt(OWM_WEATHER_ID);
                icon = weatherObject.getString(OWM_ICON);

                // Temperatures are in a child object called "temp".  Try not to name variables
                // "temp" when working with temperature.  It confuses everybody.
                JSONObject mainObject = dayForecast.getJSONObject(OWM_MAIN);
                temp = mainObject.getDouble(OWM_TEMP);
                max = mainObject.getDouble(OWM_MAX);
                min = mainObject.getDouble(OWM_MIN);
                pressure = mainObject.getDouble(OWM_PRESSURE);
                humidity = mainObject.getInt(OWM_HUMIDITY);

                JSONObject windObject = dayForecast.getJSONObject(OWM_WIND);
                windSpeed = windObject.getDouble(OWM_WIND_SPEED);
                windDirection = windObject.getDouble(OWM_WIND_DIRECTION);

                JSONObject cloudsObject = dayForecast.getJSONObject(OWM_CLOUDS);
                clouds = cloudsObject.getInt(OWM_CLOUDS_ALL);

                if (dayForecast.has(OWM_RAIN)) {
                    JSONObject rainObject = dayForecast.getJSONObject(OWM_RAIN);
                    rain = rainObject.has(OWM_RAIN_3h) ? rainObject.getDouble(OWM_RAIN_3h) : 0;
                }

                if (dayForecast.has(OWM_SNOW)) {
                    JSONObject snowObject = dayForecast.getJSONObject(OWM_SNOW);
                    snow = snowObject.has(OWM_SNOW_3h) ? snowObject.getDouble(OWM_SNOW_3h) : 0;
                }

                ContentValues weatherValues = new ContentValues();

                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_LOC_KEY, locationId);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_DATE, timeInMillis);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_HUMIDITY, humidity);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_PRESSURE, pressure);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_WIND_SPEED, windSpeed);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_DEGREES, windDirection);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_TEMP, temp);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP, max);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP, min);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_SHORT_DESC, description);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_WEATHER_ID, weatherId);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_ICON, icon);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_RAIN, rain);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_SNOW, snow);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_TYPE, TYPE_HOURLY);

                cVVector.add(weatherValues);
            }

            int inserted = 0;
            // add to database
            if (cVVector.size() > 0) {

                //delete all old data of given type
                String selection = WeatherContract.WeatherEntry.COLUMN_TYPE + " = ? ";
                String[] selectionArgs = new String[]{Integer.toString(TYPE_HOURLY)};
                getContext().getContentResolver().delete(WeatherContract.WeatherEntry.CONTENT_URI,
                        selection,
                        selectionArgs);

                ContentValues[] cvArray = new ContentValues[cVVector.size()];
                cVVector.toArray(cvArray);
                getContext().getContentResolver().bulkInsert(WeatherContract.WeatherEntry.CONTENT_URI, cvArray);

                // delete old data so we don't build up an endless history

                // get the time beginning of today for daily
                Calendar cal = Calendar.getInstance(TimeZone.getDefault());
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                timeInMillis = cal.getTimeInMillis();

//                String selection = WeatherContract.WeatherEntry.COLUMN_DATE + " < ? AND " +
//                        WeatherContract.WeatherEntry.COLUMN_TYPE + " = ? ";
//                String[] selectionArgs = new String[]{Long.toString(timeInMillis), Integer.toString(TYPE_DAILY)};
//                getContext().getContentResolver().delete(WeatherContract.WeatherEntry.CONTENT_URI,
//                        selection,
//                        selectionArgs);
            }
            Log.d(LOG_TAG, "Sync Complete. " + cVVector.size() + " Inserted");
            setLocationStatus(getContext(), LOCATION_STATUS_OK);

        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
            setLocationStatus(getContext(), LOCATION_STATUS_SERVER_INVALID);
        }
    }

    private void notifyWeather() {
        Context context = getContext();
        //checking the last update and notify if it' the first of the day
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String displayNotificationsKey = context.getString(R.string.pref_enable_notifications_key);
        boolean displayNotifications = prefs.getBoolean(displayNotificationsKey,
                Boolean.parseBoolean(context.getString(R.string.pref_enable_notifications_default)));

        if (displayNotifications) {

            String lastNotificationKey = context.getString(R.string.pref_last_notification);
            long lastSync = prefs.getLong(lastNotificationKey, 0);

            if (System.currentTimeMillis() - lastSync >= MINUTE_IN_MILLIS) {
                // Last sync was more than 1 day ago, let's send a notification with the weather.
                String locationQuery = Utility.getPreferredLocation(context);

                Uri weatherUri = WeatherContract.WeatherEntry.buildWeatherLocationCurrent(locationQuery);

                // we'll query our contentProvider, as always
                Cursor cursor = context.getContentResolver().query(weatherUri, NOTIFY_WEATHER_PROJECTION, null, null, null);

                if (cursor.moveToFirst()) {
                    int weatherId = cursor.getInt(INDEX_WEATHER_ID);
                    double high = cursor.getDouble(INDEX_MAX_TEMP);
                    double low = cursor.getDouble(INDEX_MIN_TEMP);
                    String desc = cursor.getString(INDEX_SHORT_DESC);

                    int iconId = Utility.getIconResourceForWeatherCondition(weatherId);
                    Resources resources = context.getResources();
                    Bitmap largeIcon = BitmapFactory.decodeResource(resources,
                            Utility.getArtResourceForWeatherCondition(weatherId));
                    String title = context.getString(R.string.app_name);

                    // Define the text of the forecast.
                    String contentText = String.format(context.getString(R.string.format_notification),
                            desc,
                            Utility.formatTemperature(context, high, Utility.isMetric(context)),
                            Utility.formatTemperature(context, low, Utility.isMetric(context)));

                    // NotificationCompatBuilder is a very convenient way to build backward-compatible
                    // notifications.  Just throw in some data.
                    NotificationCompat.Builder mBuilder =
                            new NotificationCompat.Builder(getContext())
                                    .setColor(resources.getColor(R.color.primary_light))
                                    .setSmallIcon(iconId)
                                    .setLargeIcon(largeIcon)
                                    .setContentTitle(title)
                                    .setContentText(contentText);

                    // Make something interesting happen when the user clicks on the notification.
                    // In this case, opening the app is sufficient.
                    Intent resultIntent = new Intent(context, MainActivity.class);

                    // The stack builder object will contain an artificial back stack for the
                    // started Activity.
                    // This ensures that navigating backward from the Activity leads out of
                    // your application to the Home screen.
                    TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
                    stackBuilder.addNextIntent(resultIntent);
                    PendingIntent resultPendingIntent =
                            stackBuilder.getPendingIntent(
                                    0,
                                    PendingIntent.FLAG_UPDATE_CURRENT
                            );
                    mBuilder.setContentIntent(resultPendingIntent);

                    NotificationManager mNotificationManager =
                            (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
                    // WEATHER_NOTIFICATION_ID allows you to update the notification later on.
                    mNotificationManager.notify(WEATHER_NOTIFICATION_ID, mBuilder.build());

                    //refreshing last sync
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putLong(lastNotificationKey, System.currentTimeMillis());
                    editor.commit();
                }
                cursor.close();
            }
        }
    }

    /**
     * Helper method to handle insertion of a new location in the weather database.
     *
     * @param locationSetting The location string used to request updates from the server.
     * @param cityName        A human-readable city name, e.g "Mountain View"
     * @param lat             the latitude of the city
     * @param lon             the longitude of the city
     * @return the row ID of the added location.
     */
    long addLocation(String locationSetting, String cityName, double lat, double lon) {
        long locationId;

        // First, check if the location with this city name exists in the db
        Cursor locationCursor = getContext().getContentResolver().query(
                WeatherContract.LocationEntry.CONTENT_URI,
                new String[]{WeatherContract.LocationEntry._ID},
                WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING + " = ?",
                new String[]{locationSetting},
                null);

        if (locationCursor.moveToFirst()) {
            int locationIdIndex = locationCursor.getColumnIndex(WeatherContract.LocationEntry._ID);
            locationId = locationCursor.getLong(locationIdIndex);
        } else {
            // Now that the content provider is set up, inserting rows of data is pretty simple.
            // First create a ContentValues object to hold the data you want to insert.
            ContentValues locationValues = new ContentValues();

            // Then add the data, along with the corresponding name of the data type,
            // so the content provider knows what kind of value is being inserted.
            locationValues.put(WeatherContract.LocationEntry.COLUMN_CITY_NAME, cityName);
            locationValues.put(WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING, locationSetting);
            locationValues.put(WeatherContract.LocationEntry.COLUMN_COORD_LAT, lat);
            locationValues.put(WeatherContract.LocationEntry.COLUMN_COORD_LONG, lon);

            // Finally, insert location data into the database.
            Uri insertedUri = getContext().getContentResolver().insert(
                    WeatherContract.LocationEntry.CONTENT_URI,
                    locationValues
            );

            // The resulting URI contains the ID for the row.  Extract the locationId from the Uri.
            locationId = ContentUris.parseId(insertedUri);
        }

        locationCursor.close();
        // Wait, that worked?  Yes!
        return locationId;
    }

    /**
     * Helper method to schedule the sync adapter periodic execution
     */
    public static void configurePeriodicSync(Context context, int syncInterval, int flexTime) {
        Account account = getSyncAccount(context);
        String authority = context.getString(R.string.content_authority);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // we can enable inexact timers in our periodic sync
            SyncRequest request = new SyncRequest.Builder().
                    syncPeriodic(syncInterval, flexTime).
                    setSyncAdapter(account, authority).
                    setExtras(new Bundle()).build();
            ContentResolver.requestSync(request);
        } else {
            ContentResolver.addPeriodicSync(account,
                    authority, new Bundle(), syncInterval);
        }
    }

    /**
     * Helper method to have the sync adapter sync immediately
     *
     * @param context The context used to access the account service
     */
    public static void syncImmediately(Context context) {
        Bundle bundle = new Bundle();
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        ContentResolver.requestSync(getSyncAccount(context),
                context.getString(R.string.content_authority), bundle);
    }

    /**
     * Helper method to get the fake account to be used with SyncAdapter, or make a new one
     * if the fake account doesn't exist yet.  If we make a new account, we call the
     * onAccountCreated method so we can initialize things.
     *
     * @param context The context used to access the account service
     * @return a fake account.
     */
    public static Account getSyncAccount(Context context) {
        // Get an instance of the Android account manager
        AccountManager accountManager =
                (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);

        // Create the account type and default account
        Account newAccount = new Account(
                context.getString(R.string.app_name), context.getString(R.string.sync_account_type));

        // If the password doesn't exist, the account doesn't exist
        if (null == accountManager.getPassword(newAccount)) {

        /*
         * Add the account and account type, no password or user data
         * If successful, return the Account object, otherwise report an error.
         */
            if (!accountManager.addAccountExplicitly(newAccount, "", null)) {
                return null;
            }
            /*
             * If you don't set android:syncable="true" in
             * in your <provider> element in the manifest,
             * then call ContentResolver.setIsSyncable(account, AUTHORITY, 1)
             * here.
             */

            onAccountCreated(newAccount, context);
        }
        return newAccount;
    }

    private static void onAccountCreated(Account newAccount, Context context) {
        /*
         * Since we've created an account
         */
        WetterSyncAdapter.configurePeriodicSync(context, SYNC_INTERVAL, SYNC_FLEXTIME);

        /*
         * Without calling setSyncAutomatically, our periodic sync will not be enabled.
         */
        ContentResolver.setSyncAutomatically(newAccount, context.getString(R.string.content_authority), true);

        /*
         * Finally, let's do a sync to get things started
         */
        syncImmediately(context);
    }

    public static void initializeSyncAdapter(Context context) {
        getSyncAccount(context);
    }

    /**
     * Sets the location status into shared preference.  This function should not be called from
     * the UI thread because it uses commit to write to the shared preferences.
     *
     * @param c              Context to get the PreferenceManager from.
     * @param locationStatus The IntDef value to set
     */
    static private void setLocationStatus(Context c, @LocationStatus int locationStatus) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(c);
        SharedPreferences.Editor spe = sp.edit();
        spe.putInt(c.getString(R.string.pref_location_status_key), locationStatus);
        spe.commit();
    }

}
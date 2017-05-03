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
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.IntDef;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

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
import de.sindzinski.wetter.data.WeatherContract;
import de.sindzinski.wetter.util.Utility;

import static de.sindzinski.wetter.data.WeatherContract.PROVIDER_OWM;
import static de.sindzinski.wetter.data.WeatherContract.PROVIDER_WUG;
import static de.sindzinski.wetter.data.WeatherContract.TYPE_CURRENT;
import static de.sindzinski.wetter.data.WeatherContract.TYPE_DAILY;
import static de.sindzinski.wetter.data.WeatherContract.TYPE_HOURLY;
import static de.sindzinski.wetter.util.Utility.getLastSync;
import static de.sindzinski.wetter.util.Utility.getLocationId;
import static de.sindzinski.wetter.util.Utility.getLocationSetting;
import static de.sindzinski.wetter.util.Utility.getPreferredLocation;
import static de.sindzinski.wetter.util.Utility.setLastSync;
import static de.sindzinski.wetter.util.Utility.wordFirstCap;

public class WetterSyncAdapter extends AbstractThreadedSyncAdapter {
    public final String LOG_TAG = WetterSyncAdapter.class.getSimpleName();

    // Interval at which to sync with the weather, in seconds.
    // 60 seconds (1 minute) * 180 = 3 hours
    public static final long SYNC_INTERVAL = 60L * 60L;
    //    public static final int SYNC_INTERVAL = 60 * 180;
    public static final long SYNC_FLEXTIME = SYNC_INTERVAL / 3;
    private static final long DAY_IN_MILLIS = 1000L * 60L * 60L * 24L;
    private static final long HOUR_IN_MILLIS = 1000L * 60L * 60L;
    private static final long MINUTE_IN_MILLIS = 1000L * 60L;
    private static final int WEATHER_NOTIFICATION_ID = 3004;

    public static final String ACTION_DATA_UPDATED =
            "de.sindzinski.wetter.ACTION_DATA_UPDATED";

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
        if (Utility.getProvider(getContext()).equals(getContext().getString(R.string.pref_provider_owm))) {
//        if (Utility.getProvider(getContext()) == getContext().getString(R.string.pref_provider_owm)) {

            syncAllSources(account, extras, authority, provider, syncResult, TYPE_DAILY);

            if (Utility.getHourlyForecast(getContext())) {
                syncAllSources(account, extras, authority, provider, syncResult, TYPE_HOURLY);
            }

            syncAllSources(account, extras, authority, provider, syncResult, TYPE_CURRENT);
        } else {
            syncAllSourcesWUG(account, extras, authority, provider, syncResult, TYPE_HOURLY);
            syncAllSourcesWUG(account, extras, authority, provider, syncResult, TYPE_CURRENT);
            syncAllSourcesWUG(account, extras, authority, provider, syncResult, TYPE_DAILY);
        }

        updateWidgets();
        notifyWeather();
    }

    private void syncAllSourcesWUG(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult, Integer type) {
        Log.d(LOG_TAG, "Starting sync");
        String locationSetting = getPreferredLocation(getContext());
        long locationId = getPreferredLocationCityId(getContext(), locationSetting);

        // These two need to be declared outside the try/catch
        // so that they can be closed in the finally block.
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        // Will contain the raw JSON response as a string.
        String forecastJsonStr = null;

        String format = "json";
        String units = "metric";
        int numDays = 14;
        String locationQuery;

        try {
            // Construct the URL for the OpenWeatherMap query
            // Possible parameters are avaiable at OWM's forecast API page, at
            // http://openweathermap.org/API#forecast

            Uri builtUri = null;

            if (type == TYPE_HOURLY) {
                final String TYPE_PATH = "hourly";
                final String FORECAST_BASE_URL =
                        "http://api.wunderground.com/api/";
                builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendPath(Utility.getApiKey(getContext()))
//                        .appendPath("geolookup")
                        .appendPath("conditions")
                        .appendPath(TYPE_PATH)
                        .appendPath("q")
                        .appendEncodedPath(getLocationSetting(getContext()))
                        .build();
            } else if (type == TYPE_DAILY) {
                final String TYPE_PATH = "forecast10day";
                final String FORECAST_BASE_URL =
                        "http://api.wunderground.com/api/";
                builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendPath(Utility.getApiKey(getContext()))
//                        .appendPath("geolookup")
//                        .appendPath("conditions")
                        .appendPath(TYPE_PATH)
                        .appendPath("q")
                        .appendEncodedPath(getLocationSetting(getContext()))
                        .build();
            } else if (type == TYPE_CURRENT) {
                final String TYPE_PATH = "conditions";
                final String FORECAST_BASE_URL =
                        "http://api.wunderground.com/api/";
                builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendPath(Utility.getApiKey(getContext()))
//                        .appendPath("geolookup")
//                        .appendPath("conditions")
                        .appendPath(TYPE_PATH)
                        .appendPath("q")
                        .appendEncodedPath(getLocationSetting(getContext()))
                        .build();
            }

            URL url = new URL(builtUri.toString() + ".json");

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
                getWeatherDataFromJsonHourlyWUG(forecastJsonStr, locationSetting);
            } else if (type == TYPE_DAILY) {
                getWeatherDataFromJsonDailyWUG(forecastJsonStr, locationSetting);
            } else if (type == TYPE_CURRENT) {
                getWeatherDataFromJsonCurrentWUG(forecastJsonStr, locationSetting);
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

    private void syncAllSources(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult, Integer type) {
        Log.d(LOG_TAG, "Starting sync");
        String locationSetting = getPreferredLocation(getContext());
        long locationId = getPreferredLocationCityId(getContext(), locationSetting);

        // These two need to be declared outside the try/catch
        // so that they can be closed in the finally block.
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        // Will contain the raw JSON response as a string.
        String forecastJsonStr = null;

        String format = "json";
        String units = "metric";
        int numDays = 14;
        String locationQuery;

        try {
            // Construct the URL for the OpenWeatherMap query
            // Possible parameters are avaiable at OWM's forecast API page, at
            // http://openweathermap.org/API#forecast

            String QUERY_PARAM = "q";
            final String FORMAT_PARAM = "mode";
            final String UNITS_PARAM = "units";
            final String DAYS_PARAM = "cnt";
            final String APPID_PARAM = "APPID";
            Uri builtUri = null;

            // use the correct parameter if location is city name or id
            if (locationId == 0) {
                QUERY_PARAM = "q";
                locationQuery = getLocationSetting(getContext());
            } else {
                QUERY_PARAM = "id";
                locationQuery = Long.toString(locationId);
            }
            if (type == TYPE_HOURLY) {
                final String FORECAST_BASE_URL =
                        "http://api.openweathermap.org/data/2.5/forecast?";
                builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(QUERY_PARAM, locationQuery)
                        .appendQueryParameter(FORMAT_PARAM, format)
                        .appendQueryParameter(UNITS_PARAM, units)
                        .appendQueryParameter(APPID_PARAM, Utility.getApiKey(getContext()))
                        .build();
            } else if (type == TYPE_DAILY) {
                final String FORECAST_BASE_URL =
                        "http://api.openweathermap.org/data/2.5/forecast/daily?";
                builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(QUERY_PARAM, locationQuery)
                        .appendQueryParameter(FORMAT_PARAM, format)
                        .appendQueryParameter(UNITS_PARAM, units)
                        .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
                        .appendQueryParameter(APPID_PARAM, Utility.getApiKey(getContext()))
                        .build();
            } else if (type == TYPE_CURRENT) { //current
                final String FORECAST_BASE_URL =
                        "http://api.openweathermap.org/data/2.5/weather?";
                builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(QUERY_PARAM, locationQuery)
                        .appendQueryParameter(FORMAT_PARAM, format)
                        .appendQueryParameter(UNITS_PARAM, units)
                        .appendQueryParameter(APPID_PARAM, Utility.getApiKey(getContext()))
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
                getWeatherDataFromJsonHourly(forecastJsonStr, locationSetting);
            } else if (type == TYPE_DAILY) {
                getWeatherDataFromJsonDaily(forecastJsonStr, locationSetting);
            } else if (type == TYPE_CURRENT) {
                getWeatherDataFromJsonCurrent(forecastJsonStr, locationSetting);
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
        String OWM_CITY_ID = "id";
        String OWM_CITY_NAME = "name";

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


            long cityId = forecastJson.getLong(OWM_CITY_ID);

            long locationId = getLocationId(getContext(), locationSetting);
            if (locationId<0) {
                Log.d(LOG_TAG, "no location id found");
                geoLookUp(getContext(), locationSetting, 0, 0);
                locationId = getLocationId(getContext(), locationSetting);
                if (locationId<0) {
                    return;
                }
            }

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
            windSpeed = windObject.has(OWM_WIND_DIRECTION) ? windObject.getDouble(OWM_WIND_SPEED) : 0;
            windDirection = windObject.has(OWM_WIND_DIRECTION) ? windObject.getDouble(OWM_WIND_DIRECTION) : 0;

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
                sunRise = sysObject.has(OWM_SUN_RISE) ? (sysObject.getLong(OWM_SUN_RISE) * 1000) : 0;
                sunSet = sysObject.has(OWM_SUN_SET) ? (sysObject.getLong(OWM_SUN_SET) * 1000) : 0;
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
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_SUN_RISE, sunRise);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_SUN_SET, sunSet);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_TYPE, TYPE_CURRENT);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_PROVIDER, PROVIDER_OWM);

            // Insert the new weather information into the database
            Vector<ContentValues> cVVector = new Vector<ContentValues>(1);
            cVVector.add(weatherValues);

            // add to database
            if (cVVector.size() > 0) {

                //delete all old data of given type
                deleteOldWeatherData(getContext(), locationId, TYPE_CURRENT, 0);

                ContentValues[] cvArray = new ContentValues[cVVector.size()];
                cVVector.toArray(cvArray);
                getContext().getContentResolver().bulkInsert(WeatherContract.WeatherEntry.CONTENT_URI, cvArray);

                Log.d(LOG_TAG, "Sync Complete. " + cVVector.size() + " Inserted");
                setLocationStatus(getContext(), LOCATION_STATUS_OK);
            }
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
            setLocationStatus(getContext(), LOCATION_STATUS_SERVER_INVALID);
            setLastSync(getContext(), System.currentTimeMillis());
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
        String OWM_CITY_ID = "id";


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

            long locationId = getLocationId(getContext(), locationSetting);
            if (locationId<0) {
                Log.d(LOG_TAG, "no location id found");
                geoLookUp(getContext(), locationSetting,0,0);
                locationId = getLocationId(getContext(), locationSetting);
                if (locationId<0) {
                    return;
                }
            }

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
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_PROVIDER, PROVIDER_OWM);

                cVVector.add(weatherValues);
            }

            int inserted = 0;
            // add to database
            if (cVVector.size() > 0) {

                deleteOldWeatherData(getContext(), locationId, TYPE_DAILY, 0);

                ContentValues[] cvArray = new ContentValues[cVVector.size()];
                cVVector.toArray(cvArray);
                getContext().getContentResolver().bulkInsert(WeatherContract.WeatherEntry.CONTENT_URI, cvArray);

            }
            Log.d(LOG_TAG, "Sync Complete. " + cVVector.size() + " Inserted");
            setLocationStatus(getContext(), LOCATION_STATUS_OK);

        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
            setLocationStatus(getContext(), LOCATION_STATUS_SERVER_INVALID);
            setLastSync(getContext(), System.currentTimeMillis());
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
        String OWM_CITY_ID = "id";
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

            long locationId = getLocationId(getContext(), Utility.getLocationSetting(getContext()));
            if (locationId<0) {
                Log.d(LOG_TAG, "no location id found");
                geoLookUp(getContext(), locationSetting, 0, 0);
                locationId = getLocationId(getContext(), locationSetting);
                if (locationId<0) {
                    return;
                }
            }

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
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_PROVIDER, PROVIDER_OWM);

                cVVector.add(weatherValues);
            }

            int inserted = 0;
            // add to database
            if (cVVector.size() > 0) {

                //delete all old data of given type
                deleteOldWeatherData(getContext(), locationId, TYPE_HOURLY, 0);

                ContentValues[] cvArray = new ContentValues[cVVector.size()];
                cVVector.toArray(cvArray);
                getContext().getContentResolver().bulkInsert(WeatherContract.WeatherEntry.CONTENT_URI, cvArray);


            }
            Log.d(LOG_TAG, "Sync Complete. " + cVVector.size() + " Inserted");
            setLocationStatus(getContext(), LOCATION_STATUS_OK);

        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
            setLocationStatus(getContext(), LOCATION_STATUS_SERVER_INVALID);
            setLastSync(getContext(), System.currentTimeMillis());
        }
    }

    private void getWeatherDataFromJsonCurrentWUG(String forecastJsonStr,
                                                  String locationSetting)
            throws JSONException {

        try {
            JSONObject forecastJson = new JSONObject(forecastJsonStr);

            // do we have an error?
            if (forecastJson.has("Error")) {
                String errorType = forecastJson.getString("type");
                String errorDescription = forecastJson.getString("description");

                switch (errorType) {
                    case "querynotfound":
                        setLocationStatus(getContext(), LOCATION_STATUS_INVALID);
                        return;
                    default:
                        setLocationStatus(getContext(), LOCATION_STATUS_SERVER_DOWN);
                        return;
                }
            }

            long locationId = getLocationId(getContext(), locationSetting);
            if (locationId<0) {
                Log.d(LOG_TAG, "no location id found");
                geoLookUp(getContext(), locationSetting, 0,0);
                locationId = getLocationId(getContext(), locationSetting);
                if (locationId<0) {
                    return;
                }
            }

            // Insert the new weather information into the database
            JSONObject currentWeather = forecastJson.getJSONObject("current_observation");

            long timeInMillis = currentWeather.getLong("local_epoch") * 1000;

            String description = currentWeather.getString("weather");

            String icon = currentWeather.getString("icon");

            double temp = currentWeather.getDouble("temp_c");
            double feelsLike = currentWeather.getDouble("feelslike_c");

            int pressure = currentWeather.getInt("pressure_mb");
            String humString = currentWeather.getString("relative_humidity");
            int humidity = Integer.parseInt(humString.replaceAll("%", ""));

            double windSpeed = currentWeather.getDouble("wind_kph");
            double windDirection = currentWeather.getDouble("wind_degrees");

            int clouds = currentWeather.has("sky") ? currentWeather.getInt("sky") : 0;
            int uvi = currentWeather.has("UV") ? currentWeather.getInt("UV") : 0;

            double rain = currentWeather.has("precip_1hr_metric") ? currentWeather.getDouble("precip_1hr_metric") : 0;
            double snow = currentWeather.has("snow_1hr_metric") ? currentWeather.getDouble("snow_1hr_metric") : 0;

            ContentValues weatherValues = new ContentValues();

            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_LOC_KEY, locationId);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_DATE, timeInMillis);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_HUMIDITY, humidity);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_PRESSURE, pressure);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_CLOUDS, clouds);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_UVI, uvi);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_WIND_SPEED, windSpeed);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_DEGREES, windDirection);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_TEMP, temp);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_FEELSLIKE, feelsLike);
//                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP, null);
//                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP, null);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_SHORT_DESC, description);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_WEATHER_ID, 0);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_ICON, icon);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_RAIN, rain);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_SNOW, snow);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_TYPE, TYPE_CURRENT);
            weatherValues.put(WeatherContract.WeatherEntry.COLUMN_PROVIDER, PROVIDER_WUG);


            //delete all old data of given type
            deleteOldWeatherData(getContext(), locationId, TYPE_CURRENT, 0);

            getContext().getContentResolver().insert(WeatherContract.WeatherEntry.CONTENT_URI, weatherValues);

            // delete old data so we don't build up an endless history


            Log.d(LOG_TAG, "Sync Complete. Current Weather Inserted");
            setLocationStatus(getContext(), LOCATION_STATUS_OK);

        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
            setLocationStatus(getContext(), LOCATION_STATUS_SERVER_INVALID);
            setLastSync(getContext(), System.currentTimeMillis());
        }
    }

    private void getWeatherDataFromJsonHourlyWUG(String forecastJsonStr,
                                                 String locationSetting)
            throws JSONException {

        // Now we have a String representing the complete forecast in JSON Format.
        // Fortunately parsing is easy:  constructor takes the JSON string and converts it
        // into an Object hierarchy for us.

        // These are the names of the JSON objects that need to be extracted.
        // Location information

        // Location coordinate
        String OWM_LATITUDE = "lat";
        String OWM_LONGITUDE = "lon";

        try {
            JSONObject forecastJson = new JSONObject(forecastJsonStr);

            // do we have an error?
            if (forecastJson.has("Error")) {
                String errorType = forecastJson.getString("type");
                String errorDescription = forecastJson.getString("description");

                switch (errorType) {
                    case "querynotfound":
                        setLocationStatus(getContext(), LOCATION_STATUS_INVALID);
                        return;
                    default:
                        setLocationStatus(getContext(), LOCATION_STATUS_SERVER_DOWN);
                        return;
                }
            }

            long locationId = getLocationId(getContext(), locationSetting);
            if (locationId<0) {
                Log.d(LOG_TAG, "no location id found");
                geoLookUp(getContext(), locationSetting,0,0);
                locationId = getLocationId(getContext(), locationSetting);
                if (locationId<0) {
                    return;
                }
            }

            // Insert the new weather information into the database
            JSONArray weatherArray = forecastJson.getJSONArray("hourly_forecast");
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

                // Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                // Cheating to convert this to UTC time, which is what we want anyhow
                JSONObject timeObject = dayForecast.getJSONObject("FCTTIME");
                timeInMillis = timeObject.getLong("epoch") * 1000;

                // Description is in a child array called "weather", which is 1 element long.
                // That element also contains a weather code.

                String description = dayForecast.getString("condition");
                int weatherId = dayForecast.getInt("fctcode");
                String icon = dayForecast.getString("icon");

                double temp = dayForecast.getJSONObject("temp").getDouble("metric");
                double feelsLike = dayForecast.getJSONObject("feelslike").getDouble("metric");

                int pressure = dayForecast.getJSONObject("mslp").getInt("metric");
                int humidity = dayForecast.getInt("humidity");

                JSONObject windObject = dayForecast.getJSONObject("wspd");
                double windSpeed = windObject.getDouble("metric");
                double windDirection = dayForecast.getJSONObject("wdir").getDouble("degrees");

                int clouds = dayForecast.getInt("sky");
                int uvi = dayForecast.getInt("uvi");

                double rain = dayForecast.has("qpf") ? dayForecast.getJSONObject("qpf").getDouble("metric") : 0;
                double snow = dayForecast.has("snow") ? dayForecast.getJSONObject("snow").getDouble("metric") : 0;

                ContentValues weatherValues = new ContentValues();

                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_LOC_KEY, locationId);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_DATE, timeInMillis);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_HUMIDITY, humidity);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_PRESSURE, pressure);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_CLOUDS, clouds);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_UVI, uvi);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_WIND_SPEED, windSpeed);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_DEGREES, windDirection);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_TEMP, temp);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_FEELSLIKE, feelsLike);
//                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP, null);
//                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP, null);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_SHORT_DESC, description);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_WEATHER_ID, weatherId);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_ICON, icon);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_RAIN, rain);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_SNOW, snow);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_TYPE, TYPE_HOURLY);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_PROVIDER, PROVIDER_WUG);

                cVVector.add(weatherValues);
            }

            int inserted = 0;
            // add to database
            if (cVVector.size() > 0) {

                //delete all old data of given type
                deleteOldWeatherData(getContext(), locationId, TYPE_HOURLY, 0);

                ContentValues[] cvArray = new ContentValues[cVVector.size()];
                cVVector.toArray(cvArray);
                getContext().getContentResolver().bulkInsert(WeatherContract.WeatherEntry.CONTENT_URI, cvArray);

                // delete old data so we don't build up an endless history

            }
            Log.d(LOG_TAG, "Sync Complete. " + cVVector.size() + " Inserted");
            setLocationStatus(getContext(), LOCATION_STATUS_OK);

        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
            setLocationStatus(getContext(), LOCATION_STATUS_SERVER_INVALID);
            setLastSync(getContext(), System.currentTimeMillis());
        }
    }

    private void getWeatherDataFromJsonDailyWUG(String forecastJsonStr,
                                                String locationSetting)
            throws JSONException {

        // Now we have a String representing the complete forecast in JSON Format.
        // Fortunately parsing is easy:  constructor takes the JSON string and converts it
        // into an Object hierarchy for us.

        // These are the names of the JSON objects that need to be extracted.
        // Location information

        // Location coordinate
        String OWM_LATITUDE = "lat";
        String OWM_LONGITUDE = "lon";

        try {
            JSONObject forecastJson = new JSONObject(forecastJsonStr);

            // do we have an error?
            if (forecastJson.has("Error")) {
                String errorType = forecastJson.getString("type");
                String errorDescription = forecastJson.getString("description");

                switch (errorType) {
                    case "querynotfound":
                        setLocationStatus(getContext(), LOCATION_STATUS_INVALID);
                        return;
                    default:
                        setLocationStatus(getContext(), LOCATION_STATUS_SERVER_DOWN);
                        return;
                }
            }

            long locationId = getLocationId(getContext(), locationSetting);
            if (locationId<0) {
                Log.d(LOG_TAG, "no location id found");
                geoLookUp(getContext(), locationSetting,0,0);
                locationId = getLocationId(getContext(), locationSetting);
                if (locationId<0) {
                    return;
                }
            }

            // Insert the new weather information into the database
            JSONObject forecast = forecastJson.getJSONObject("forecast");
            JSONObject simpleForecast = forecast.getJSONObject("simpleforecast");
            JSONArray weatherArray = simpleForecast.getJSONArray("forecastday");
            Vector<ContentValues> cVVector = new Vector<ContentValues>(weatherArray.length());

            // OWM returns daily forecasts based upon the local time of the city that is being
            // asked for, which means that we need to know the GMT offset to translate this data
            // properly.

            // Since this data is also sent in-order and the first day is always the
            // current day, we're going to take advantage of that to get a nice
            // normalized UTC date for all of our weather.

            for (int i = 0; i < weatherArray.length(); i++) {
                // These are the values that will be collected.

                // Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                // Cheating to convert this to UTC time, which is what we want anyhow
                JSONObject date = dayForecast.getJSONObject("date");
                long timeInMillis = date.getLong("epoch") * 1000;

                // Description is in a child array called "weather", which is 1 element long.
                // That element also contains a weather code.

                String description = dayForecast.getString("conditions");
                int weatherId = dayForecast.getInt("pop");
                String icon = dayForecast.getString("icon");

//                double temp = dayForecast.getJSONObject("temp").getDouble("metric");
//                double feelsLike = dayForecast.getJSONObject("feelslike").getDouble("metric");
//                String sHigh =  dayForecast.getJSONObject("high").getString("celsius");
//                double high = Double.valueOf(sHigh);
                double high = dayForecast.getJSONObject("high").getDouble("celsius");
                double low = dayForecast.getJSONObject("low").getDouble("celsius");

//                int pressure = dayForecast.getJSONObject("mslp").getInt("metric");
                int humidity = dayForecast.getInt("avehumidity");

                JSONObject windObject = dayForecast.getJSONObject("avewind");
                double windSpeed = windObject.getDouble("kph");
                double windDirection = windObject.getDouble("degrees");

                int clouds = dayForecast.getInt("pop");
//                int uvi = dayForecast.getInt("uvi");

                double rain = dayForecast.has("qpf_allday") ? dayForecast.getJSONObject("qpf_allday").getDouble("mm") : 0;
                double snow = dayForecast.has("snow_allday") ? dayForecast.getJSONObject("snow_allday").getDouble("cm") : 0;

                ContentValues weatherValues = new ContentValues();

                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_LOC_KEY, locationId);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_DATE, timeInMillis);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_HUMIDITY, humidity);
//                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_PRESSURE, pressure);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_CLOUDS, clouds);
//                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_UVI, uvi);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_WIND_SPEED, windSpeed);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_DEGREES, windDirection);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_DAY_TEMP, high);
//                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_FEELSLIKE, feelsLike);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP, high);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP, low);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_SHORT_DESC, description);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_WEATHER_ID, 0);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_ICON, icon);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_RAIN, rain);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_SNOW, snow);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_TYPE, TYPE_DAILY);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_PROVIDER, PROVIDER_WUG);

                cVVector.add(weatherValues);
            }

            int inserted = 0;
            // add to database
            if (cVVector.size() > 0) {

                deleteOldWeatherData(getContext(), locationId, TYPE_DAILY, 0);

                ContentValues[] cvArray = new ContentValues[cVVector.size()];
                cVVector.toArray(cvArray);
                getContext().getContentResolver().bulkInsert(WeatherContract.WeatherEntry.CONTENT_URI, cvArray);

                // delete old data so we don't build up an endless history

            }
            Log.d(LOG_TAG, "Sync Complete. " + cVVector.size() + " Inserted");
            setLocationStatus(getContext(), LOCATION_STATUS_OK);

        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
            setLocationStatus(getContext(), LOCATION_STATUS_SERVER_INVALID);
            setLastSync(getContext(), System.currentTimeMillis());
        }
    }

    private void updateWidgets() {
        Context context = getContext();
        // Setting the package ensures that only components in our app will receive the broadcast
        Intent dataUpdatedIntent = new Intent(ACTION_DATA_UPDATED)
                .setPackage(context.getPackageName());
        context.sendBroadcast(dataUpdatedIntent);
    }

    private void notifyWeather() {
        // these indices must match the projection
        final int INDEX_WEATHER_ID = 0;
        final int INDEX_TEMP = 1;
        final int INDEX_MAX_TEMP = 2;
        final int INDEX_MIN_TEMP = 3;
        final int INDEX_SHORT_DESC = 4;
        final int INDEX_ICON = 5;

        final String TAG = "glide";

        final String[] NOTIFY_WEATHER_PROJECTION = new String[]{
                WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
                WeatherContract.WeatherEntry.COLUMN_TEMP,
                WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
                WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
                WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
                WeatherContract.WeatherEntry.COLUMN_ICON
        };

        Context context = getContext();
        //checking the last update and notify if it' the first of the day
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String displayNotificationsKey = context.getString(R.string.pref_enable_notifications_key);
        boolean displayNotifications = prefs.getBoolean(displayNotificationsKey,
                Boolean.parseBoolean(context.getString(R.string.pref_enable_notifications_default)));

        if (displayNotifications) {
            if (System.currentTimeMillis() - getLastSync(context) >= MINUTE_IN_MILLIS) {
                // Last sync was more than 1 day ago, let's send a notification with the weather.
                String locationQuery = getPreferredLocation(context);

                Uri weatherUri = WeatherContract.WeatherEntry.buildWeatherLocationType(locationQuery, TYPE_CURRENT);

                // we'll query our contentProvider, as always
                Cursor cursor = context.getContentResolver().query(weatherUri, NOTIFY_WEATHER_PROJECTION, null, null, null);

                if (cursor.moveToFirst()) {
                    int weatherId = cursor.getInt(INDEX_WEATHER_ID);
                    double temp = cursor.getDouble(INDEX_TEMP);
                    double high = cursor.getDouble(INDEX_MAX_TEMP);
                    double low = cursor.getDouble(INDEX_MIN_TEMP);
                    String desc = cursor.getString(INDEX_SHORT_DESC);
                    int iconId = R.drawable.ic_clear;
                    Bitmap largeIcon = null;

                    if (Utility.getProvider(context).equals(context.getString(R.string.pref_provider_wug))) {
                        String iconString = cursor.getString(INDEX_ICON);
                    } else {
                        iconId = Utility.getIconResourceForWeatherCondition(weatherId);
                        largeIcon = BitmapFactory.decodeResource(context.getResources(),
                                Utility.getArtResourceForWeatherCondition(weatherId));
                    }


                    String title = context.getString(R.string.app_name);

                    // Define the text of the forecast.
                    String contentText = String.format(context.getString(R.string.format_notification),
                            desc,
                            Utility.formatTemperature(context, temp, Utility.isMetric(context))
                    );

                    // NotificationCompatBuilder is a very convenient way to build backward-compatible
                    // notifications.  Just throw in some data.
                    NotificationCompat.Builder mBuilder =
                            new NotificationCompat.Builder(getContext())
                                    .setColor(context.getResources().getColor(R.color.primary))
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

                }
                cursor.close();
            }
        }
        //refreshing last sync
        setLastSync(context, System.currentTimeMillis());

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
    public static long addLocation(Context context, String locationSetting, String cityName, double lat, double lon, long city_id, String timeZoneId) {
        long locationId;
        final String LOG_TAG = "Wetter: adding location";
        // locationid is the internal db primary key
        // city_id is the owm id for the city
        // First, check if the location with this city name exists in the db
        Cursor locationCursor = context.getContentResolver().query(
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
            locationValues.put(WeatherContract.LocationEntry.COLUMN_TIME_ZONE, timeZoneId);
            locationValues.put(WeatherContract.LocationEntry.COLUMN_CITY_ID, city_id);

            // Finally, insert location data into the database.
            Uri insertedUri = context.getContentResolver().insert(
                    WeatherContract.LocationEntry.CONTENT_URI,
                    locationValues
            );

            // The resulting URI contains the ID for the row.  Extract the locationId from the Uri.
            locationId = ContentUris.parseId(insertedUri);
            Log.d(LOG_TAG, "Added location");
        }

        locationCursor.close();
        // Wait, that worked?  Yes!
        return locationId;
    }

    public static Long getPreferredLocationCityId(Context context, String locationSetting) {
        long locationId = 0;

        // locationid is the internal db primary key
        // city_id is the owm id for the city
        // First, check if the location with this city name exists in the db
        Cursor locationCursor = context.getContentResolver().query(
                WeatherContract.LocationEntry.CONTENT_URI,
                new String[]{WeatherContract.LocationEntry.COLUMN_CITY_ID},
                WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING + " = ?",
                new String[]{locationSetting},
                null);

        if (locationCursor.moveToFirst()) {
            int locationIdIndex = locationCursor.getColumnIndex(WeatherContract.LocationEntry.COLUMN_CITY_ID);
            locationId = locationCursor.getLong(locationIdIndex);
        }

        locationCursor.close();

        return locationId;
    }


    /**
     * Helper method to schedule the sync adapter periodic execution, called from on account created
     */
    public static void configurePeriodicSync(Context context, long syncInterval, long flexTime) {
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

    public static void setSyncInterval(Context context, long syncInterval) {
        Account account = getSyncAccount(context);
        String authority = context.getString(R.string.content_authority);

//        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
//        long syncInterval = Long.parseLong(sp.getString(context.getString(R.string.pref_sync_key), context.getString(R.string.pref_sync_default)));

        //convert sync interval from minutes to seconds
        syncInterval = syncInterval * 60;
        // Global variables
        // A content resolver for accessing the provider
        ContentResolver mResolver;
        // Get the content resolver for your app
        mResolver = context.getContentResolver();
        /*
         * Turn on periodic syncing
         */


        ContentResolver.addPeriodicSync(
                account,
                authority,
                Bundle.EMPTY,
                syncInterval);
    }


    /**
     * Helper method to have the sync adapter sync immediately
     *
     * @param context The context used to access the account service
     */
    public static boolean syncImmediately(Context context) {

        if (System.currentTimeMillis() - Utility.getLastSync(context) >= MINUTE_IN_MILLIS * 1) {
            Bundle bundle = new Bundle();
            bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
            bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
            ContentResolver.requestSync(getSyncAccount(context),
                    context.getString(R.string.content_authority), bundle);
            return true;
        } else {
            return false;
        }
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
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        long syncInterval = Long.parseLong(sp.getString(context.getString(R.string.pref_sync_key), context.getString(R.string.pref_sync_default)));
        long flexTime = syncInterval / 3;
        WetterSyncAdapter.configurePeriodicSync(context, syncInterval, flexTime);

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

    static public void deleteOldWeatherData(Context mContext, Long locationId, Integer mType, Integer mOffset) {
        // delete old data so we don't build up an endless history
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        cal.set(Calendar.HOUR_OF_DAY, cal.get(Calendar.HOUR_OF_DAY) + mOffset);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long timeInMillis = cal.getTimeInMillis();

        String selection = WeatherContract.WeatherEntry.COLUMN_DATE + " < ? AND " +
                WeatherContract.WeatherEntry.COLUMN_LOC_KEY + " = ? AND " +
                WeatherContract.WeatherEntry.COLUMN_TYPE + " = ? ";
        String[] selectionArgs = new String[]{Long.toString(timeInMillis), Long.toString(locationId), Integer.toString(mType)};
        mContext.getContentResolver().delete(WeatherContract.WeatherEntry.CONTENT_URI,
                selection,
                selectionArgs);
    }

    static public void deleteOldWeatherData(Context mContext, Integer mType, Integer mOffset) {
        // delete old data so we don't build up an endless history
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        cal.set(Calendar.HOUR_OF_DAY, cal.get(Calendar.HOUR_OF_DAY) + mOffset);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long timeInMillis = cal.getTimeInMillis();

        String selection =
                WeatherContract.WeatherEntry.COLUMN_DATE + " < ? AND " +
                        WeatherContract.WeatherEntry.COLUMN_TYPE + " = ? ";
        String[] selectionArgs = new String[]{
                Long.toString(timeInMillis),
                Integer.toString(mType)
        };
        mContext.getContentResolver().delete(WeatherContract.WeatherEntry.CONTENT_URI,
                selection,
                selectionArgs);
    }

    static private void deleteWeatherData(Context mContext, Long locationId, Integer mType) {
        // delete old data so we don't build up an endless history

        String selection =
                WeatherContract.WeatherEntry.COLUMN_LOC_KEY + " = ? AND " +
                        WeatherContract.WeatherEntry.COLUMN_TYPE + " = ? ";
        String[] selectionArgs = new String[]{Long.toString(locationId), Integer.toString(mType)};
        mContext.getContentResolver().delete(WeatherContract.WeatherEntry.CONTENT_URI,
                selection,
                selectionArgs);
    }

    public String gettimeZoneId(double lat, double lon) {
        final String LOG_TAG = "timeZoneId";
        String timeZoneId = "";
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        try {
            final String BASE_URL = "https://maps.googleapis.com/maps/api/timezone";

            final String API_KEY = BuildConfig.GOOGLE_MAPS_TIMEZONE_API_KEY;
            String urlPart = "json?" + "location=" + Double.toString(lat) + "," + Double.toString(lon) + "&timestamp=" + System.currentTimeMillis() / 1000 + "&key=" + API_KEY;
            Uri builtUri = Uri.parse(BASE_URL).buildUpon()
                    .appendEncodedPath(urlPart)
                    .build();
            Log.d(LOG_TAG, builtUri.toString());
//            Uri builtUri = Uri.parse(BASE_URL).buildUpon()
////                    .appendPath(API_KEY)
//                    .appendQueryParameter("location", Double.toString(lat) + "," + Double.toHexString(lon))
//                    .appendQueryParameter("timestamp", Long.toString(System.currentTimeMillis() / 1000))
//                    .appendQueryParameter("key", API_KEY)
//                    .build();
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
                return timeZoneId;
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
                return timeZoneId;
            }


            JSONObject jsonResponse = new JSONObject(buffer.toString());
            String responseString = jsonResponse.getString("timeZoneId");


//            for (int i = 0; i < responseString.length(); i++) {
//                if (Character.isUpperCase(responseString.charAt(i))) {
//                    char c = responseString.charAt(i);
//                    timeZoneId = timeZoneId + c;
//                }
//            }

            timeZoneId = responseString;
            Log.d(LOG_TAG, timeZoneId);
            return timeZoneId;

        } catch (IOException e) {
            Log.e(LOG_TAG, "Error ", e);
            // If the code didn't successfully get the weather data, there's no point in attempting
            // to parse it.
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();

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
            return timeZoneId;
        }
    }

    public static long geoLookUp(Context context, String locationSetting, double lat, double lon) {

        final String LOG_TAG = "Wetter geolookup";
        // These two need to be declared outside the try/catch
        // so that they can be closed in the finally block.
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        // Will contain the raw JSON response as a string.
        String forecastJsonStr = null;

        String format = "json";
        String units = "metric";
        int numDays = 14;
        String locationQuery;

        if (locationSetting.equals("")) {
            locationSetting = Double.toString(lat) + "," + Double.toString(lon);
        }

        Long locationId = -1L;
        try {
            // Construct the URL for the OpenWeatherMap query
            // Possible parameters are avaiable at OWM's forecast API page, at
            // http://openweathermap.org/API#forecast

            Uri builtUri = null;

            final String FORECAST_BASE_URL =
                    "http://api.wunderground.com/api/";
            builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                    .appendPath(Utility.getApiKey(context))
                    .appendPath("geolookup")
                    .appendPath("q")
                    .appendEncodedPath(locationSetting)
                    .build();

            URL url = new URL(builtUri.toString() + ".json");

            // Create the request to OpenWeatherMap, and open the connection
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();

            // Read the input stream into a String
            InputStream inputStream = urlConnection.getInputStream();
            StringBuffer buffer = new StringBuffer();
            if (inputStream == null) {
                // Nothing to do.
                Log.d(LOG_TAG, "empty inputStream");
                return locationId;
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
                setLocationStatus(context, LOCATION_STATUS_SERVER_DOWN);
                Log.d(LOG_TAG, "empty buffer");
                return locationId;
            }
            forecastJsonStr = buffer.toString();

            //process json
            final String LAT = "lat";
            final String LON = "lon";
            final String TZ_LONG = "tz_long";
            final String TZ_SHORT = "tz_short";
            final String COUNTRY = "country_name";
            final String CITY = "city";
            final String STATE = "state";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);

            // do we have an error?
            if (forecastJson.has("Error")) {
                String errorType = forecastJson.getString("type");
                String errorDescription = forecastJson.getString("description");

                switch (errorType) {
                    case "querynotfound":
                        setLocationStatus(context, LOCATION_STATUS_INVALID);
                        return locationId;
                    default:
                        setLocationStatus(context, LOCATION_STATUS_SERVER_DOWN);
                        return locationId;
                }
            }

            if (forecastJson.has("location")) {

                JSONObject json = forecastJson.getJSONObject("location");
                long cityId = json.getLong("wmo");
                String city = json.getString(CITY);
                String country = json.getString(COUNTRY);
                String state = json.getString(STATE);
                if (country.equals("USA")) {
                    country = state;
                }
                lat = json.getDouble(LAT);
                lon = json.getDouble(LON);
                String timeZoneId = json.getString(TZ_LONG);
//                    String timeZoneId = gettimeZoneId(cityLatitude, cityLongitude);
                Log.d(LOG_TAG, timeZoneId);
                StringBuilder builder = new StringBuilder();
                builder
                        .append(country)
                        .append("/")
                        .append(city);

                locationSetting = builder.toString(); //This is the complete locationsetting as wug.
                locationSetting = wordFirstCap(locationSetting, "/");

                locationId = addLocation(context, locationSetting, city, lon, lat, cityId, timeZoneId);
            }

        } catch (IOException e) {
            Log.e(LOG_TAG, "Error ", e);
            // If the code didn't successfully get the weather data, there's no point in attempting
            // to parse it.
            setLocationStatus(context, LOCATION_STATUS_SERVER_DOWN);
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
            setLocationStatus(context, LOCATION_STATUS_SERVER_INVALID);
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
        return locationId;

    }

}
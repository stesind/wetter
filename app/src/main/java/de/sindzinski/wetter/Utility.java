/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.sindzinski.wetter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.TimeZone;

import de.sindzinski.wetter.data.WeatherContract;
import de.sindzinski.wetter.sync.WetterSyncAdapter;

public class Utility {
    public static String getPreferredLocation(Context context) {
        if (getProvider(context) == context.getString(R.string.pref_provider_owm)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String location = prefs.getString(context.getString(R.string.pref_location_key),
                    context.getString(R.string.pref_location_default));
            return location;
        } else {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String location = prefs.getString(context.getString(R.string.pref_location_wug_key),
                    context.getString(R.string.pref_location_wug_default));
            return location;
        }
    }

    public static void setPreferredLocation(Context context, String locationSetting) {
        if (getProvider(context) == context.getString(R.string.pref_provider_owm)) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(context.getResources().getString(R.string.pref_location_key), locationSetting);
            editor.apply();
        } else {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(context.getResources().getString(R.string.pref_location_wug_key), locationSetting);
            editor.apply();
        }
    }

    public static String getProvider(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String provider = prefs.getString(context.getString(R.string.pref_provider_key),
                context.getString(R.string.pref_provider_owm));
        return provider;
    }

    public static boolean getHourlyForecast(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(context.getString(R.string.pref_enable_hourly_forecast_key),
                Boolean.valueOf(context.getString(R.string.pref_enable_hourly_forecast_default)));
    }


    public static Integer getPreferredLocation_Status(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getInt(context.getString(R.string.pref_location_status_key),
                WetterSyncAdapter.LOCATION_STATUS_UNKNOWN);
    }


    public static boolean isMetric(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(context.getString(R.string.pref_units_key),
                context.getString(R.string.pref_units_metric))
                .equals(context.getString(R.string.pref_units_metric));
    }

    public static String formatTemperature(Context context, double temperature, boolean isMetric) {
        double temp;
        if (!isMetric) {
            temp = 9 * temperature / 5 + 32;
        } else {
            temp = temperature;
        }
        return context.getString(R.string.format_temperature, temp);
    }

    static String formatDate(long dateInMilliseconds) {
        Date date = new Date(dateInMilliseconds);
        return DateFormat.getDateInstance().format(date);
    }

    // Format used for storing dates in the database.  ALso used for converting those strings
    // back into date objects for comparison/processing.
    public static final String DATE_FORMAT = "yyyyMMdd";

    /**
     * Helper method to convert the database representation of the date into something to display
     * to users.  As classy and polished a user experience as "20140102" is, we can do better.
     *
     * @param context      Context to use for resource localization
     * @param timeInMillis The date in milliseconds
     * @return a user-friendly representation of the date.
     */

    public static String getHourString(Context context, long timeInMillis) {

        TimeZone timezone = TimeZone.getDefault();
        SimpleDateFormat shortDateFormat = new SimpleDateFormat("HH:MM");
        shortDateFormat.setTimeZone(timezone);
        return shortDateFormat.format(timeInMillis);
//        GregorianCalendar gregorianCalendar = new GregorianCalendar(timezone);
//        gregorianCalendar.setTimeInMillis(timeInMillis);
//        int hour = gregorianCalendar.get(Calendar.HOUR_OF_DAY);
//        int minute = gregorianCalendar.get(Calendar.MINUTE);
//        return hour + ":" + minute;
    }

    @SuppressLint("StringFormatMatches")
    public static String getHourlyDayString(Context context, long timeInMillis) {
        // The day string for forecast uses the following logic:
        // For today: "Today, June 8"
        // For tomorrow:  "Tomorrow"
        // For the next 5 days: "Wednesday
        // " (just the day name)
        // For all days after that: "Mon Jun 8"
        TimeZone timezone = TimeZone.getDefault();
        GregorianCalendar gregorianCalendar = new GregorianCalendar(timezone);
        gregorianCalendar.setTimeInMillis(timeInMillis);
        int day = gregorianCalendar.get(Calendar.DAY_OF_MONTH);
        //code for formatting the date
        Calendar calendar = Calendar.getInstance();
        int today = calendar.get(Calendar.DAY_OF_MONTH);

        Date time = gregorianCalendar.getTime();
        SimpleDateFormat shortDateFormat = new SimpleDateFormat("EEE MMM dd HH:MM");
        shortDateFormat.setTimeZone(timezone);
        // If the date we're building the String for is today's date, the format
        // is "Today, June 24"

//            if (today == day) {
//                int formatId = R.string.format_full_friendly_date;
//                SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("dd.MMM");
//                return String.format(context.getString(
//                        formatId,
//                        gregorianCalendar.getDisplayName(gregorianCalendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()),
//                        shortenedDateFormat.format(timeInMillis)));
//            } else
        if (day < today + 1) {
            // If the input date is less than a week in the future, just return the day name.
            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("HH:00");
            shortDateFormat.setTimeZone(timezone);
            return shortenedDateFormat.format(timeInMillis);
        } else {
            // Otherwise, use the form "Mon Jun 3"
            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE HH:00");
            shortDateFormat.setTimeZone(timezone);
            return shortenedDateFormat.format(timeInMillis);
        }
    }
    /**
     * Helper method to convert the database representation of the date into something to display
     * to users.  As classy and polished a user experience as "20140102" is, we can do better.
     *
     * @param context      Context to use for resource localization
     * @param timeInMillis The date in milliseconds
     * @return a user-friendly representation of the date.
     */
    @SuppressLint("StringFormatMatches")
    public static String getDailyDayString(Context context, long timeInMillis) {
        // The day string for forecast uses the following logic:
        // For today: "Today, June 8"
        // For tomorrow:  "Tomorrow"
        // For the next 5 days: "Wednesday
        // " (just the day name)
        // For all days after that: "Mon Jun 8"
        TimeZone timezone = TimeZone.getDefault();
        GregorianCalendar gregorianCalendar = new GregorianCalendar(timezone);
        gregorianCalendar.setTimeInMillis(timeInMillis);
//        int day = gregorianCalendar.get(GregorianCalendar.DAY_OF_MONTH);

        //code for formatting the date
        Calendar calendar = Calendar.getInstance();
        int today = calendar.get(Calendar.DAY_OF_MONTH);
        calendar.setTimeInMillis(timeInMillis);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        // If the date we're building the String for is today's date, the format
        // is "Today, June 24"

        if (today == day) {
            int formatId = R.string.format_full_friendly_date;
            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("dd.MMM");
            shortenedDateFormat.setTimeZone(timezone);

            return String.format(context.getString(
                    formatId,
                    "Today ",
                    gregorianCalendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()),
                    shortenedDateFormat.format(timeInMillis)));
        } else if (day < today + 7) {
            // If the input date is less than a week in the future, just return the day name.
            String dayName = gregorianCalendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault());//Locale.US);
            return dayName;
        } else {
            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
            shortenedDateFormat.setTimeZone(timezone);
            return shortenedDateFormat.format(timeInMillis);
        }

    }

     /**
     * Converts db date format to the format "Month day", e.g "June 24".
     *
     * @param context      Context to use for resource localization
     * @param dateInMillis The db formatted date string, expected to be of the form specified
     *                     in Utility.DATE_FORMAT
     * @return The day in the form of a string formatted "December 6"
     */
    public static String getFormattedMonthDay(Context context, long dateInMillis) {
        SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("MMMM dd");
        TimeZone timezone = TimeZone.getDefault();
        shortenedDateFormat.setTimeZone(timezone);
        String monthDayString = shortenedDateFormat.format(dateInMillis);
        return monthDayString;
    }

    public static String wordFirstCap(String str, String delimiter)
    {
        String[] words = str.trim().split(delimiter);
        StringBuilder ret = new StringBuilder();
        for(int i = 0; i < words.length; i++)
        {
            if(words[i].trim().length() > 0)
            {
                Log.e("words[i].trim",""+words[i].trim().charAt(0));
                ret.append(Character.toUpperCase(words[i].trim().charAt(0)));
                ret.append(words[i].trim().substring(1));
                if(i < words.length - 1) {
                    ret.append(delimiter);
                }
            }
        }

        return ret.toString();
    }

    public static String getSmallFormattedWind(Context context, float windSpeed, float degrees) {
        int windFormat;
        if (Utility.isMetric(context)) {
            windFormat = R.string.format_small_wind_kmh;
        } else {
            windFormat = R.string.format_small_wind_mph;
            windSpeed = .621371192237334f * windSpeed;
        }

        // From wind direction in degrees, determine compass direction as a string (e.g NW)
        // You know what's fun, writing really long if/else statements with tons of possible
        // conditions.  Seriously, try it!
        String direction = "Unknown";
        if (degrees >= 337.5 || degrees < 22.5) {
            direction = "N";
        } else if (degrees >= 22.5 && degrees < 67.5) {
            direction = "NE";
        } else if (degrees >= 67.5 && degrees < 112.5) {
            direction = "E";
        } else if (degrees >= 112.5 && degrees < 157.5) {
            direction = "SE";
        } else if (degrees >= 157.5 && degrees < 202.5) {
            direction = "S";
        } else if (degrees >= 202.5 && degrees < 247.5) {
            direction = "SW";
        } else if (degrees >= 247.5 && degrees < 292.5) {
            direction = "W";
        } else if (degrees >= 292.5 && degrees < 337.5) {
            direction = "NW";
        }
        return String.format(context.getString(windFormat), windSpeed, direction);
    }

    public static String getFormattedWind(Context context, float windSpeed, float degrees) {
        int windFormat;
        if (Utility.isMetric(context)) {
            windFormat = R.string.format_wind_kmh;
        } else {
            windFormat = R.string.format_wind_mph;
            windSpeed = .621371192237334f * windSpeed;
        }

        // From wind direction in degrees, determine compass direction as a string (e.g NW)
        // You know what's fun, writing really long if/else statements with tons of possible
        // conditions.  Seriously, try it!
        String direction = "Unknown";
        if (degrees >= 337.5 || degrees < 22.5) {
            direction = "N";
        } else if (degrees >= 22.5 && degrees < 67.5) {
            direction = "NE";
        } else if (degrees >= 67.5 && degrees < 112.5) {
            direction = "E";
        } else if (degrees >= 112.5 && degrees < 157.5) {
            direction = "SE";
        } else if (degrees >= 157.5 && degrees < 202.5) {
            direction = "S";
        } else if (degrees >= 202.5 && degrees < 247.5) {
            direction = "SW";
        } else if (degrees >= 247.5 && degrees < 292.5) {
            direction = "W";
        } else if (degrees >= 292.5 && degrees < 337.5) {
            direction = "NW";
        }
        return String.format(context.getString(windFormat), windSpeed, direction);
    }

    /**
     * Helper method to provide the icon resource id according to the weather condition id returned
     * by the OpenWeatherMap call.
     *
     * @param weatherId from OpenWeatherMap API response
     * @return resource id for the corresponding icon. -1 if no relation is found.
     */
    public static int getIconResourceForWeatherCondition(int weatherId) {
        // Based on weather code data found at:
        // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
        if (weatherId >= 200 && weatherId <= 232) {
            return R.drawable.ic_storm;
        } else if (weatherId >= 300 && weatherId <= 321) {
            return R.drawable.ic_light_rain;
        } else if (weatherId >= 500 && weatherId <= 504) {
            return R.drawable.ic_rain;
        } else if (weatherId == 511) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 520 && weatherId <= 531) {
            return R.drawable.ic_rain;
        } else if (weatherId >= 600 && weatherId <= 622) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 701 && weatherId <= 761) {
            return R.drawable.ic_fog;
        } else if (weatherId == 761 || weatherId == 781) {
            return R.drawable.ic_storm;
        } else if (weatherId == 800) {
            return R.drawable.ic_clear;
        } else if (weatherId == 801) {
            return R.drawable.ic_light_clouds;
        } else if (weatherId >= 802 && weatherId <= 804) {
            return R.drawable.ic_cloudy;
        }
        return -1;
    }

    /**
     * Helper method to provide the art resource id according to the weather condition id returned
     * by the OpenWeatherMap call.
     *
     * @param weatherId from OpenWeatherMap API response
     * @return resource id for the corresponding icon. -1 if no relation is found.
     */
    public static int getArtResourceForWeatherCondition(int weatherId) {
        // Based on weather code data found at:
        // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
        if (weatherId >= 200 && weatherId <= 232) {
            return R.drawable.art_storm;
        } else if (weatherId >= 300 && weatherId <= 321) {
            return R.drawable.art_light_rain;
        } else if (weatherId >= 500 && weatherId <= 504) {
            return R.drawable.art_rain;
        } else if (weatherId == 511) {
            return R.drawable.art_snow;
        } else if (weatherId >= 520 && weatherId <= 531) {
            return R.drawable.art_rain;
        } else if (weatherId >= 600 && weatherId <= 622) {
            return R.drawable.art_snow;
        } else if (weatherId >= 701 && weatherId <= 761) {
            return R.drawable.art_fog;
        } else if (weatherId == 761 || weatherId == 781) {
            return R.drawable.art_storm;
        } else if (weatherId == 800) {
            return R.drawable.art_clear;
        } else if (weatherId == 801) {
            return R.drawable.art_light_clouds;
        } else if (weatherId >= 802 && weatherId <= 804) {
            return R.drawable.art_clouds;
        }
        return -1;
    }

    /**
     * Helper method to provide the art urls according to the weather condition id returned
     * by the OpenWeatherMap call.
     *
     * @param context Context to use for retrieving the URL format
     * @param weatherId from OpenWeatherMap API response
     * @return url for the corresponding weather artwork. null if no relation is found.
     */
    public static String getArtUrlForWeatherCondition(Context context, int weatherId) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String formatArtUrl = prefs.getString(context.getString(R.string.pref_art_pack_key),
                context.getString(R.string.pref_art_pack_sunshine));

        // Based on weather code data found at:
        // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
        if (weatherId >= 200 && weatherId <= 232) {
            return String.format(Locale.US, formatArtUrl, "storm");
        } else if (weatherId >= 300 && weatherId <= 321) {
            return String.format(Locale.US, formatArtUrl, "light_rain");
        } else if (weatherId >= 500 && weatherId <= 504) {
            return String.format(Locale.US, formatArtUrl, "rain");
        } else if (weatherId == 511) {
            return String.format(Locale.US, formatArtUrl, "snow");
        } else if (weatherId >= 520 && weatherId <= 531) {
            return String.format(Locale.US, formatArtUrl, "rain");
        } else if (weatherId >= 600 && weatherId <= 622) {
            return String.format(Locale.US, formatArtUrl, "snow");
        } else if (weatherId >= 701 && weatherId <= 761) {
            return String.format(Locale.US, formatArtUrl, "fog");
        } else if (weatherId == 761 || weatherId == 781) {
            return String.format(Locale.US, formatArtUrl, "storm");
        } else if (weatherId == 800) {
            return String.format(Locale.US, formatArtUrl, "clear");
        } else if (weatherId == 801) {
            return String.format(Locale.US, formatArtUrl, "light_clouds");
        } else if (weatherId >= 802 && weatherId <= 804) {
            return String.format(Locale.US, formatArtUrl, "clouds");
        }
        return null;
    }


    /**
     * Helper method to provide the string according to the weather
     * condition id returned by the OpenWeatherMap call.
     * @param context Android context
     * @param weatherId from OpenWeatherMap API response
     * @return string for the weather condition. null if no relation is found.
     */
    public static String getStringForWeatherCondition(Context context, int weatherId) {
        // Based on weather code data found at:
        // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
        int stringId;
        if (weatherId >= 200 && weatherId <= 232) {
            stringId = R.string.condition_2xx;
        } else if (weatherId >= 300 && weatherId <= 321) {
            stringId = R.string.condition_3xx;
        } else switch(weatherId) {
            case 500:
                stringId = R.string.condition_500;
                break;
            case 501:
                stringId = R.string.condition_501;
                break;
            case 502:
                stringId = R.string.condition_502;
                break;
            case 503:
                stringId = R.string.condition_503;
                break;
            case 504:
                stringId = R.string.condition_504;
                break;
            case 511:
                stringId = R.string.condition_511;
                break;
            case 520:
                stringId = R.string.condition_520;
                break;
            case 531:
                stringId = R.string.condition_531;
                break;
            case 600:
                stringId = R.string.condition_600;
                break;
            case 601:
                stringId = R.string.condition_601;
                break;
            case 602:
                stringId = R.string.condition_602;
                break;
            case 611:
                stringId = R.string.condition_611;
                break;
            case 612:
                stringId = R.string.condition_612;
                break;
            case 615:
                stringId = R.string.condition_615;
                break;
            case 616:
                stringId = R.string.condition_616;
                break;
            case 620:
                stringId = R.string.condition_620;
                break;
            case 621:
                stringId = R.string.condition_621;
                break;
            case 622:
                stringId = R.string.condition_622;
                break;
            case 701:
                stringId = R.string.condition_701;
                break;
            case 711:
                stringId = R.string.condition_711;
                break;
            case 721:
                stringId = R.string.condition_721;
                break;
            case 731:
                stringId = R.string.condition_731;
                break;
            case 741:
                stringId = R.string.condition_741;
                break;
            case 751:
                stringId = R.string.condition_751;
                break;
            case 761:
                stringId = R.string.condition_761;
                break;
            case 762:
                stringId = R.string.condition_762;
                break;
            case 771:
                stringId = R.string.condition_771;
                break;
            case 781:
                stringId = R.string.condition_781;
                break;
            case 800:
                stringId = R.string.condition_800;
                break;
            case 801:
                stringId = R.string.condition_801;
                break;
            case 802:
                stringId = R.string.condition_802;
                break;
            case 803:
                stringId = R.string.condition_803;
                break;
            case 804:
                stringId = R.string.condition_804;
                break;
            case 900:
                stringId = R.string.condition_900;
                break;
            case 901:
                stringId = R.string.condition_901;
                break;
            case 902:
                stringId = R.string.condition_902;
                break;
            case 903:
                stringId = R.string.condition_903;
                break;
            case 904:
                stringId = R.string.condition_904;
                break;
            case 905:
                stringId = R.string.condition_905;
                break;
            case 906:
                stringId = R.string.condition_906;
                break;
            case 951:
                stringId = R.string.condition_951;
                break;
            case 952:
                stringId = R.string.condition_952;
                break;
            case 953:
                stringId = R.string.condition_953;
                break;
            case 954:
                stringId = R.string.condition_954;
                break;
            case 955:
                stringId = R.string.condition_955;
                break;
            case 956:
                stringId = R.string.condition_956;
                break;
            case 957:
                stringId = R.string.condition_957;
                break;
            case 958:
                stringId = R.string.condition_958;
                break;
            case 959:
                stringId = R.string.condition_959;
                break;
            case 960:
                stringId = R.string.condition_960;
                break;
            case 961:
                stringId = R.string.condition_961;
                break;
            case 962:
                stringId = R.string.condition_962;
                break;
            default:
                return context.getString(R.string.condition_unknown);
        }
        return context.getString(stringId);
    }

    /**
     * Helper method to return whether or not Sunshine is using local graphics.
     *
     * @param context Context to use for retrieving the preference
     * @return true if Sunshine is using local graphics, false otherwise.
     */
    public static boolean usingLocalGraphics(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String sunshineArtPack = context.getString(R.string.pref_art_pack_sunshine);
        return prefs.getString(context.getString(R.string.pref_art_pack_key),
                sunshineArtPack).equals(sunshineArtPack);
    }

    /**
     * Returns true if the network is available or about to become available.
     *
     * @param c Context used to get the ConnectivityManager
     * @return
     */
    static public boolean isNetworkAvailable(Context c) {
        ConnectivityManager cm =
                (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
    }

    /**
     * @param c Context used to get the SharedPreferences
     * @return the location status integer type
     */
    @SuppressWarnings("ResourceType")
    static public
    @WetterSyncAdapter.LocationStatus
    int getLocationStatus(Context c) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(c);
        return sp.getInt(c.getString(R.string.pref_location_status_key), WetterSyncAdapter.LOCATION_STATUS_UNKNOWN);
    }

    public static void resetLocationStatus(Context c) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(c);
        SharedPreferences.Editor spe = sp.edit();
        spe.putInt(c.getString(R.string.pref_location_status_key), WetterSyncAdapter.LOCATION_STATUS_UNKNOWN);
        spe.apply();
    }

    public static long getLocationId(Context context, String locationSetting) {

        long locationId=0;

        Cursor locationCursor = context.getContentResolver().query(
                WeatherContract.LocationEntry.CONTENT_URI,
                new String[]{WeatherContract.LocationEntry._ID},
                WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING + " = ?",
                new String[]{locationSetting},
                null);

        if (locationCursor.moveToFirst()) {
            int locationIdIndex = locationCursor.getColumnIndex(WeatherContract.LocationEntry._ID);
            locationId = locationCursor.getLong(locationIdIndex);
        }

        return locationId;
    }

    public static void deleteLocationWeatherData(Context context, long locationId) {
        //delete all old data of given type
        String selection = WeatherContract.WeatherEntry.COLUMN_LOC_KEY + " = ? ";
        String[] selectionArgs = new String[]{Long.toString(locationId)};
        context.getContentResolver().delete(WeatherContract.WeatherEntry.CONTENT_URI,
                selection,
                selectionArgs);
    }

    public static void deleteLocationId(Context context, long locationId) {
        String selection = WeatherContract.LocationEntry._ID + " = ? ";
        String[] selectionArgs = new String[]{Long.toString(locationId)};
        context.getContentResolver().delete(WeatherContract.LocationEntry.CONTENT_URI,
                selection,
                selectionArgs);
    }

    public static void deleteCurrentLocation(Context context) {
        String currentLocationSetting = Utility.getPreferredLocation(context);
        long locationId = Utility.getLocationId(context, currentLocationSetting);
        Utility.deleteLocationWeatherData(context, locationId);
        Utility.deleteLocationId(context, locationId);
        String validLocationSetting = Utility.getValidLocationSetting(context);
        Utility.setPreferredLocation(context,validLocationSetting);
        Utility.resetLocationStatus(context);
        WetterSyncAdapter.syncImmediately(context);
    }

    public static String getValidLocationSetting(Context context) {
        String validLocationSetting = "";

        Cursor locationCursor = context.getContentResolver().query(
                WeatherContract.LocationEntry.CONTENT_URI,
                new String[]{WeatherContract.LocationEntry._ID,
                        WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING},
                null,
                null,
                null);

        if (locationCursor.moveToFirst()) {
            int locationIdIndex = locationCursor.getColumnIndex(WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING);
            validLocationSetting = locationCursor.getString(locationIdIndex);
        }

        return validLocationSetting;
    }

    public static long getLastSync(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String lastSyncKey = context.getString(R.string.pref_last_sync);
        return prefs.getLong(lastSyncKey, 0);
    }

    public static void setLastSync(Context context, long timeInMillis) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        String lastSyncKey = context.getString(R.string.pref_last_sync);
        editor.putLong(lastSyncKey, timeInMillis);
        editor.commit();
    }
    static private void setLocationStatus(Context c, int locationStatus) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(c);
        SharedPreferences.Editor spe = sp.edit();
        spe.putInt(c.getString(R.string.pref_location_status_key), locationStatus);
        spe.commit();
    }

    public static void showSnackbar(Context context, View view, int stringRessource) {
        Snackbar snackbar;
        snackbar = Snackbar.make(view, stringRessource, Snackbar.LENGTH_SHORT);
        snackbar.setDuration(Snackbar.LENGTH_LONG);
        View sbView = snackbar.getView();
        sbView.setBackgroundColor(ContextCompat.getColor(context,R.color.primary));
        snackbar.show();
    }

    public static HashSet<TextView> getTextViews(ViewGroup root){
        HashSet<TextView> views=new HashSet<>();
        for(int i=0;i<root.getChildCount();i++){
            View v=root.getChildAt(i);
            if(v instanceof TextView){
                views.add((TextView)v);
            }else if(v instanceof ViewGroup){
                views.addAll(getTextViews((ViewGroup)v));
            }
        }
        return views;
    }

    public static String getApiKey(Context context) {
        if (getProvider(context) == context.getString(R.string.pref_provider_owm)) {
            if (BuildConfig.OPEN_WEATHER_MAP_API_KEY.equals("")) {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                String apiKey = sp.getString(context.getString(R.string.pref_location_status_key), "");
                return apiKey;
            } else {
                return BuildConfig.OPEN_WEATHER_MAP_API_KEY;
            }
        } else {
            return BuildConfig.WUNDERGROUND_API_KEY;
        }
    }
}
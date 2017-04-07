package de.sindzinski.wetter.widget;

/**
 * Created by steffen on 27.02.17.
 */

import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.widget.RemoteViews;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.AppWidgetTarget;

import java.util.Locale;

import de.sindzinski.wetter.MainActivity;
import de.sindzinski.wetter.R;
import de.sindzinski.wetter.data.WeatherContract;
import de.sindzinski.wetter.util.Utility;

import static de.sindzinski.wetter.data.WeatherContract.TYPE_DAILY;

/**
 * IntentService which handles updating all Today widgets with the latest data
 */
public class TodayWidgetIntentService extends IntentService {
    private static final String[] FORECAST_COLUMNS = {
            WeatherContract.WeatherEntry.COLUMN_DATE,
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.WeatherEntry.COLUMN_ICON,
            WeatherContract.LocationEntry.COLUMN_TIME_ZONE
    };
    // these indices must match the projection
    private static final int INDEX_DATE = 0;
    private static final int INDEX_WEATHER_ID = 1;
    private static final int INDEX_SHORT_DESC = 2;
    private static final int INDEX_MAX_TEMP = 3;
    private static final int INDEX_MIN_TEMP = 4;
    private static final int INDEX_ICON = 5;
    private static final int INDEX_TIME_ZONE = 6;

    public TodayWidgetIntentService() {
        super("TodayWidgetIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // Retrieve all of the Today widget ids: these are the widgets we need to update
        Context mContext = this.getApplicationContext();
//        Context mContext = this;
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);

        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(this,
                TodayWidgetProvider.class));

        // Get today's data from the ContentProvider
        String location = Utility.getPreferredLocation(this);
        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDateType(
                location, System.currentTimeMillis(), TYPE_DAILY);
        Cursor data = getContentResolver().query(weatherForLocationUri, FORECAST_COLUMNS, null,
                null, WeatherContract.WeatherEntry.COLUMN_DATE + " ASC");
        if (data == null) {
            return;
        }
        if (!data.moveToFirst()) {
            data.close();
            return;
        }

        // Extract the weather data from the Cursor
        int weatherId = data.getInt(INDEX_WEATHER_ID);
        int weatherArtResourceId = Utility.getArtResourceForWeatherCondition(weatherId);
        String description = data.getString(INDEX_SHORT_DESC);
        long timeInMillis = data.getLong(INDEX_DATE);
        double maxTemp = data.getDouble(INDEX_MAX_TEMP);
        double minTemp = data.getDouble(INDEX_MIN_TEMP);
        String formattedMaxTemperature = Utility.formatTemperature(this, maxTemp, true);
        String formattedMinTemperature = Utility.formatTemperature(this, minTemp, true);
        String icon = data.getString(INDEX_ICON);
        String timeZoneName = data.getString(INDEX_TIME_ZONE);
        data.close();

        // Perform this loop procedure for each Today widget
        for (int appWidgetId : appWidgetIds) {
            // get the type of the widget
            Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);
            int category = options.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1);
            boolean isLockScreen = category == AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD;

            // Find the correct layout based on the widget's width
            int widgetWidth = getWidgetWidth(appWidgetManager, appWidgetId);
            int defaultWidth = getResources().getDimensionPixelSize(R.dimen.widget_today_default_width);
            int largeWidth = getResources().getDimensionPixelSize(R.dimen.widget_today_large_width);
            int layoutId;
            if (widgetWidth >= largeWidth) {
                layoutId = R.layout.widget_today_large;
            } else if (widgetWidth >= defaultWidth) {
                layoutId = R.layout.widget_today;
            } else {
                layoutId = R.layout.widget_today_small;
            }

            RemoteViews remoteViews = new RemoteViews(getPackageName(), layoutId);
            final AppWidgetTarget appWidgetTarget = new AppWidgetTarget(this, remoteViews, R.id.widget_icon, appWidgetIds);

//             Add the data to the RemoteViews
//             Get weather icon
            int defaultImage = Utility.getIconResourceForWeatherCondition(weatherId);

            String prefProvider = Utility.getProvider(mContext);
            String provider = mContext.getString(R.string.pref_provider_wug);

            String artPack = Utility.getWeatherArtPack(mContext);

            final String url;

            if (artPack.isEmpty()) {
                if (defaultImage > 0) {
                    // local images
                    remoteViews.setImageViewResource(R.id.widget_icon, defaultImage);
                }
            } else {
                if (artPack.equals(mContext.getString(R.string.pref_art_pack_owm))) {
                    url = String.format(Locale.US, artPack, icon);

                } else if (artPack.equals(mContext.getString(R.string.pref_art_pack_cute_dogs))) {
                    url = Utility.getArtUrlForWeatherCondition(mContext, weatherId);

                } else {
                    url = String.format(Locale.US, artPack, icon);
                }

                Handler mainHandler = new Handler(Looper.getMainLooper());
                Runnable myRunnable = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Glide
                                    .with(getApplicationContext()) // safer!
                                    .load(url)
                                    .asBitmap()
                                    .into(appWidgetTarget);
                        } catch (IllegalArgumentException e) {
                            e.printStackTrace();
                        }
                    }
                };
                mainHandler.post(myRunnable);
            }

            // Content Descriptions for RemoteViews were only added in ICS MR1
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                setRemoteContentDescription(remoteViews, description);
            }

//          remoteViews.setImageViewResource(R.id.widget_icon, weatherArtResourceId);
            remoteViews.setTextViewText(R.id.widget_description, description);
            remoteViews.setTextViewText(R.id.widget_day, Utility.getShortWeekDay(mContext, timeInMillis, timeZoneName ));
            remoteViews.setTextViewText(R.id.widget_temperature, formattedMaxTemperature + " / " + formattedMinTemperature);

//            // Create an Intent to launch MainActivity
//            Intent launchIntent = new Intent(this, MainActivity.class);
//            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, launchIntent, 0);
//            remoteViews.setOnClickPendingIntent(R.id.widget, pendingIntent);

                        // Register an onClickListener
            Intent clickIntent = new Intent(this.getApplicationContext(),
                    WidgetProvider.class);

            clickIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            clickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,
                    appWidgetIds);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    getApplicationContext(), 0, clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            remoteViews.setOnClickPendingIntent(R.id.widget, pendingIntent);


            // Tell the AppWidgetManager to perform an update on the current app widget
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews);
        }

    }

    private int getWidgetWidth(AppWidgetManager appWidgetManager, int appWidgetId) {
        // Prior to Jelly Bean, widgets were always their default size
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return getResources().getDimensionPixelSize(R.dimen.widget_today_default_width);
        }
        // For Jelly Bean and higher devices, widgets can be resized - the current size can be
        // retrieved from the newly added App Widget Options
        return getWidgetWidthFromOptions(appWidgetManager, appWidgetId);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private int getWidgetWidthFromOptions(AppWidgetManager appWidgetManager, int appWidgetId) {
        Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);
        if (options.containsKey(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)) {
            int minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
            // The width returned is in dp, but we'll convert it to pixels to match the other widths
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, minWidthDp,
                    displayMetrics);
        }
        return getResources().getDimensionPixelSize(R.dimen.widget_today_default_width);
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private void setRemoteContentDescription(RemoteViews views, String description) {
        views.setContentDescription(R.id.widget_icon, description);
    }
}

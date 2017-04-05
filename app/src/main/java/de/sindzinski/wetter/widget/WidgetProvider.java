package de.sindzinski.wetter.widget;

/**
 * Created by steffen on 28.02.17.
 */

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.widget.RemoteViews;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.AppWidgetTarget;

import java.util.Locale;
import java.util.Random;

import de.sindzinski.wetter.MainActivity;
import de.sindzinski.wetter.R;
import de.sindzinski.wetter.data.WeatherContract;
import de.sindzinski.wetter.util.Utility;

import static de.sindzinski.wetter.data.WeatherContract.TYPE_DAILY;

public class WidgetProvider extends AppWidgetProvider {
    private static final String[] FORECAST_COLUMNS = {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.WeatherEntry.COLUMN_ICON
    };
    // these indices must match the projection
    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_SHORT_DESC = 1;
    private static final int INDEX_MAX_TEMP = 2;
    private static final int INDEX_MIN_TEMP = 3;
    private static final int INDEX_ICON = 4;

//    private static final String ACTION_CLICK = "ACTION_CLICK";
    private static final String LOG_TAG = "wetter.widget";

//    @Override
//    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
//                         int[] appWidgetIds) {
//
//        Log.w(LOG_TAG, "onUpdate method called");
//        // Get all ids
//        ComponentName thisWidget = new ComponentName(context,
//                WidgetProvider.class);
//        int[] allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
//
//        // Build the intent to call the service
//        Intent intent = new Intent(context.getApplicationContext(),
//                UpdateWidgetService.class);
//        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, allWidgetIds);
//
//        // Update the widgets via the service
//        context.startService(intent);
//    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
                         int[] appWidgetIds) {

//        // Get all ids
//        ComponentName thisWidget = new ComponentName(context,
//                WidgetProvider.class);
//        int[] allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
//        for (int widgetId : allWidgetIds) {
//            // get the type of the widget
//            Bundle options = appWidgetManager.getAppWidgetOptions(widgetId);
//
//            int category = options.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1);
//            boolean isLockScreen = category == AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD;
//
//            // create some random data
//            int number = (new Random().nextInt(100));
//
//            RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
//                    R.layout.widget_layout);
//            Log.w("WidgetExample", String.valueOf(number));
//            // Set the text
//            remoteViews.setTextViewText(R.id.update, String.valueOf(number));


            Context mContext = context;
//        Context mContext = this;

            AppWidgetTarget appWidgetTarget;

            // Get today's data from the ContentProvider
            String location = Utility.getPreferredLocation(mContext);
            Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDateType(
                    location, System.currentTimeMillis(), TYPE_DAILY);
            Cursor data = mContext.getContentResolver().query(weatherForLocationUri, FORECAST_COLUMNS, null,
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
            double maxTemp = data.getDouble(INDEX_MAX_TEMP);
            double minTemp = data.getDouble(INDEX_MIN_TEMP);
            String formattedMaxTemperature = Utility.formatTemperature(mContext, maxTemp, true);
            String formattedMinTemperature = Utility.formatTemperature(mContext, minTemp, true);
            String icon = data.getString(INDEX_ICON);
            data.close();

            // Perform this loop procedure for each Today widget
            for (int appWidgetId : appWidgetIds) {
                // get the type of the widget
                Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);
                int category = options.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1);
                boolean isLockScreen = category == AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD;

                // Find the correct layout based on the widget's width
                int layoutId;

                    layoutId = R.layout.widget_today;

                RemoteViews remoteViews = new RemoteViews(context.getPackageName(), layoutId);
                appWidgetTarget = new AppWidgetTarget(context, remoteViews, R.id.widget_icon, appWidgetIds);

//             Add the data to the RemoteViews
//             Get weather icon
                int defaultImage = Utility.getIconResourceForWeatherCondition(weatherId);

                String prefProvider = Utility.getProvider(mContext);
                String provider = mContext.getString(R.string.pref_provider_wug);

                String artPack = Utility.getWeatherArtPack(mContext);
                try {
                    if (artPack.equals(mContext.getString(R.string.pref_art_pack_owm))) {
                        Glide
                                .with(mContext) // safer!
                                .load(String.format(Locale.US, artPack, icon))
                                .asBitmap()
                                .into(appWidgetTarget);
                    } else if (artPack.equals(mContext.getString(R.string.pref_art_pack_cute_dogs))) {
                        Glide
                                .with(mContext) // safer!
                                .load(Utility.getArtUrlForWeatherCondition(mContext, weatherId))
                                .asBitmap()
                                .into(appWidgetTarget);
                    } else if (artPack.isEmpty()) {
                        if (defaultImage > 0) {
                            // local images
                            remoteViews.setImageViewResource(R.id.widget_icon, defaultImage);
                        }
                    } else {
                        Glide
                                .with(mContext.getApplicationContext()) // safer!
                                .load(String.format(Locale.US, artPack, icon))
                                .asBitmap()
                                .into(appWidgetTarget);
                    }
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }

//          remoteViews.setImageViewResource(R.id.widget_icon, weatherArtResourceId);
                remoteViews.setTextViewText(R.id.widget_description, description);
                remoteViews.setTextViewText(R.id.widget_high_temperature, formattedMaxTemperature);
                remoteViews.setTextViewText(R.id.widget_low_temperature, formattedMinTemperature);


//            // Register an onClickListener for callin main activity
//            // Create an Intent to launch MainActivity
//            Intent launchIntent = new Intent(context, MainActivity.class);
//            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, launchIntent, 0);
//            remoteViews.setOnClickPendingIntent(R.id.widget, pendingIntent);


                // Register an onClickListener for updating data
                Intent intent = new Intent(context, WidgetProvider.class);

                intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);

                PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
                        0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                remoteViews.setOnClickPendingIntent(R.id.widget, pendingIntent);


//                            // Register an onClickListener
//            Intent clickIntent = new Intent(context, WidgetProvider.class);
//
//            clickIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
//            clickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
//
//            PendingIntent pendingIntent = PendingIntent.getBroadcast( context, 0, clickIntent,
//                    PendingIntent.FLAG_UPDATE_CURRENT);
//            remoteViews.setOnClickPendingIntent(R.id.widget_high_temperature, pendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, remoteViews);
        }
    }

}
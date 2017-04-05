package de.sindzinski.wetter.widget;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.Target;

import java.util.Locale;
import java.util.concurrent.ExecutionException;

import de.sindzinski.wetter.R;
import de.sindzinski.wetter.data.WeatherContract;
import de.sindzinski.wetter.util.Utility;

import static de.sindzinski.wetter.data.WeatherContract.TYPE_DAILY;

/**
 * RemoteViewsService controlling the data being shown in the scrollable weather detail widget
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class DetailWidgetRemoteViewsService extends RemoteViewsService {
    public final String LOG_TAG = DetailWidgetRemoteViewsService.class.getSimpleName();
    private static final String[] FORECAST_COLUMNS = {
            WeatherContract.WeatherEntry.TABLE_NAME + "." + WeatherContract.WeatherEntry._ID,
            WeatherContract.WeatherEntry.COLUMN_DATE,
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.WeatherEntry.COLUMN_ICON
    };
    // these indices must match the projection
    static final int INDEX_WEATHER_ID = 0;
    static final int INDEX_WEATHER_DATE = 1;
    static final int INDEX_WEATHER_CONDITION_ID = 2;
    static final int INDEX_WEATHER_DESC = 3;
    static final int INDEX_WEATHER_MAX_TEMP = 4;
    static final int INDEX_WEATHER_MIN_TEMP = 5;
    static final int INDEX_ICON = 6;

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new RemoteViewsFactory() {
            private Cursor data = null;

            @Override
            public void onCreate() {
                // Nothing to do
            }

            @Override
            public void onDataSetChanged() {
                if (data != null) {
                    data.close();
                }
                // This method is called by the app hosting the widget (e.g., the launcher)
                // However, our ContentProvider is not exported so it doesn't have access to the
                // data. Therefore we need to clear (and finally restore) the calling identity so
                // that calls use our process and permission
                final long identityToken = Binder.clearCallingIdentity();
                String location = Utility.getPreferredLocation(DetailWidgetRemoteViewsService.this);
//                Uri weatherForLocationUri = WeatherContract.WeatherEntry
//                        .buildWeatherLocationWithStartDate(location, System.currentTimeMillis());
                Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDateType(
                        location, System.currentTimeMillis(), TYPE_DAILY);
                data = getContentResolver().query(weatherForLocationUri,
                        FORECAST_COLUMNS,
                        null,
                        null,
                        WeatherContract.WeatherEntry.COLUMN_DATE + " ASC");
                Binder.restoreCallingIdentity(identityToken);
            }

            @Override
            public void onDestroy() {
                if (data != null) {
                    data.close();
                    data = null;
                }
            }

            @Override
            public int getCount() {
                return data == null ? 0 : data.getCount();
            }

            @Override
            public RemoteViews getViewAt(int position) {
                if (position == AdapterView.INVALID_POSITION ||
                        data == null || !data.moveToPosition(position)) {
                    return null;
                }
                RemoteViews views = new RemoteViews(getPackageName(),
                        R.layout.widget_detail_list_item);
                int weatherId = data.getInt(INDEX_WEATHER_CONDITION_ID);
                int weatherArtResourceId = Utility.getIconResourceForWeatherCondition(weatherId);
                Bitmap weatherArtImage = null;
//                if ( !Utility.usingLocalGraphics(DetailWidgetRemoteViewsService.this) ) {
//                    String weatherArtResourceUrl = Utility.getArtUrlForWeatherCondition(
//                            DetailWidgetRemoteViewsService.this, weatherId);
//                    try {
//                        weatherArtImage =
//                          Glide.with(DetailWidgetRemoteViewsService.this)
//                                .load(weatherArtResourceUrl)
//                                .asBitmap()
//                                .error(weatherArtResourceId)
//                                .into(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL).get();
//                    } catch (InterruptedException | ExecutionException e) {
//                        Log.e(LOG_TAG, "Error retrieving large icon from " + weatherArtResourceUrl, e);
//                    }
//                }

                Context mContext = getApplicationContext();
                final String icon = data.getString(INDEX_ICON);
//                RemoteViews remoteViews = new RemoteViews(getPackageName(), layoutId);

                int defaultImage = Utility.getIconResourceForWeatherCondition(weatherId);

                String prefProvider = Utility.getProvider(mContext);
                String provider = mContext.getString(R.string.pref_provider_wug);

                String artPack = Utility.getWeatherArtPack(mContext);

                final String url;

                if (artPack.equals(mContext.getString(R.string.pref_art_pack_owm))) {
                    url = String.format(Locale.US, artPack, icon);

                } else if (artPack.equals(mContext.getString(R.string.pref_art_pack_cute_dogs))) {
                    url = Utility.getArtUrlForWeatherCondition(mContext, weatherId);

                } else {
                    url = String.format(Locale.US, artPack, icon);
                }

                try {
                    weatherArtImage =
                            Glide.with(DetailWidgetRemoteViewsService.this)
                                    .load(url)
                                    .asBitmap()
                                    .error(weatherArtResourceId)
                                    .into(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL).get();
                } catch (InterruptedException | ExecutionException e) {
                    Log.e(LOG_TAG, "Error retrieving large icon from " + url, e);
                }

                String description = data.getString(INDEX_WEATHER_DESC);
                long dateInMillis = data.getLong(INDEX_WEATHER_DATE);
                String formattedDate = Utility.getShortWeekDay(
                        DetailWidgetRemoteViewsService.this, dateInMillis);
                double maxTemp = data.getDouble(INDEX_WEATHER_MAX_TEMP);
                double minTemp = data.getDouble(INDEX_WEATHER_MIN_TEMP);
                String formattedMaxTemperature =
                        Utility.formatTemperature(DetailWidgetRemoteViewsService.this, maxTemp, true);
                String formattedMinTemperature =
                        Utility.formatTemperature(DetailWidgetRemoteViewsService.this, minTemp, true);
                if (weatherArtImage != null) {
                    views.setImageViewBitmap(R.id.widget_icon, weatherArtImage);
                } else {
                    views.setImageViewResource(R.id.widget_icon, weatherArtResourceId);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                    setRemoteContentDescription(views, description);
                }
                views.setTextViewText(R.id.widget_date, formattedDate);
                views.setTextViewText(R.id.widget_description, description);
                views.setTextViewText(R.id.widget_high_temperature, formattedMaxTemperature);
                views.setTextViewText(R.id.widget_low_temperature, formattedMinTemperature);


//                final Intent fillInIntent = new Intent();
//                String locationSetting =
//                        Utility.getPreferredLocation(DetailWidgetRemoteViewsService.this);
//                Uri weatherUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(
//                        locationSetting,
//                        dateInMillis);
//                fillInIntent.setData(weatherUri);
//                views.setOnClickFillInIntent(R.id.widget_list_item, fillInIntent);

                // Register an onClickListener
                Intent clickIntent = new Intent(mContext,
                        WidgetProvider.class);

                clickIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
//                clickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,
//                        appWidgetIds);

                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        getApplicationContext(), 0, clickIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);
                views.setOnClickPendingIntent(R.id.widget, pendingIntent);


                return views;
            }

            @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
            private void setRemoteContentDescription(RemoteViews views, String description) {
                views.setContentDescription(R.id.widget_icon, description);
            }

            @Override
            public RemoteViews getLoadingView() {
                return new RemoteViews(getPackageName(), R.layout.widget_detail_list_item);
            }

            @Override
            public int getViewTypeCount() {
                return 1;
            }

            @Override
            public long getItemId(int position) {
                if (data.moveToPosition(position))
                    return data.getLong(INDEX_WEATHER_ID);
                return position;
            }

            @Override
            public boolean hasStableIds() {
                return true;
            }
        };
    }
}

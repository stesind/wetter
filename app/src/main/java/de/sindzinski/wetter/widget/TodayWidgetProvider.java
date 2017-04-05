package de.sindzinski.wetter.widget;

/**
 * Created by steffen on 27.02.17.
 */

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import de.sindzinski.wetter.sync.WetterSyncAdapter;

/**
 * Provider for a horizontally expandable widget showing today's weather.
 *
 * Delegates widget updating to {@link TodayWidgetIntentService} to ensure that
 * data retrieval is done on a background thread
 */
public class TodayWidgetProvider extends AppWidgetProvider {
    public final String LOG_TAG = this.getClass().getSimpleName();

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(LOG_TAG, "onUpdate called");
        context.startService(new Intent(context, TodayWidgetIntentService.class));
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
                                          int appWidgetId, Bundle newOptions) {
        Log.d(LOG_TAG, "onChanged called");
        context.startService(new Intent(context, TodayWidgetIntentService.class));
    }

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        super.onReceive(context, intent);
        Log.d(LOG_TAG, "onReceive called");
        if (WetterSyncAdapter.ACTION_DATA_UPDATED.equals(intent.getAction())) {
            context.startService(new Intent(context, TodayWidgetIntentService.class));
        }
    }
}
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

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncStatusObserver;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.TimeZone;

import de.sindzinski.wetter.data.WeatherContract;
import de.sindzinski.wetter.sync.WetterSyncAdapter;
import de.sindzinski.wetter.util.Utility;

import static de.sindzinski.wetter.data.WeatherContract.TYPE_CURRENT;
import static de.sindzinski.wetter.data.WeatherContract.TYPE_CURRENT_HOURLY;
import static de.sindzinski.wetter.data.WeatherContract.TYPE_HOURLY;

/**
 * Encapsulates fetching the forecast and displaying it as a {@link ListView} layout.
 */
public class ForecastHourlyFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor>,
        SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String LOG_TAG = ForecastHourlyFragment.class.getSimpleName();
    private ForecastAdapterHourly mForecastAdapter;

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private Object syncObserverHandle;

    private ListView mListView;
    private int mPosition = ListView.INVALID_POSITION;
    private boolean mUseTodayLayout = true;

    private static final String SELECTED_KEY = "selected_position";

    private static final int FORECAST_LOADER_HOURLY = 0;
    // For the forecast view we're showing only a small subset of the stored data.
    // Specify the columns we need.
    private static final String[] FORECAST_COLUMNS = {
            // In this case the id needs to be fully qualified with a table name, since
            // the content provider joins the location & weather tables in the background
            // (both have an _id column)
            // On the one hand, that's annoying.  On the other, you can search the weather table
            // using the location set by the user, which is only in the Location table.
            // So the convenience is worth it.
            WeatherContract.WeatherEntry.TABLE_NAME + "." + WeatherContract.WeatherEntry._ID,
            WeatherContract.WeatherEntry.COLUMN_DATE,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING,
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.LocationEntry.COLUMN_COORD_LAT,
            WeatherContract.LocationEntry.COLUMN_COORD_LONG,
            WeatherContract.LocationEntry.COLUMN_CITY_NAME,
            WeatherContract.LocationEntry.COLUMN_TIME_ZONE,
            WeatherContract.WeatherEntry.COLUMN_WIND_SPEED,
            WeatherContract.WeatherEntry.COLUMN_DEGREES,
            WeatherContract.WeatherEntry.COLUMN_CLOUDS,
            WeatherContract.WeatherEntry.COLUMN_RAIN,
            WeatherContract.WeatherEntry.COLUMN_SNOW,
            WeatherContract.WeatherEntry.COLUMN_ICON,
            WeatherContract.WeatherEntry.COLUMN_SUN_RISE,
            WeatherContract.WeatherEntry.COLUMN_SUN_SET,
            WeatherContract.WeatherEntry.COLUMN_FEELSLIKE,
            WeatherContract.WeatherEntry.COLUMN_UVI,
            WeatherContract.WeatherEntry.COLUMN_TYPE,

    };

    // These indices are tied to FORECAST_COLUMNS.  If FORECAST_COLUMNS changes, these
    // must change.
    static final int COL_WEATHER_ID = 0;
    static final int COL_WEATHER_DATE = 1;
    static final int COL_WEATHER_DESC = 2;
    static final int COL_WEATHER_TEMP = 3;
    static final int COL_WEATHER_MAX_TEMP = 4;
    static final int COL_WEATHER_MIN_TEMP = 5;
    static final int COL_LOCATION_SETTING = 6;
    static final int COL_WEATHER_CONDITION_ID = 7;
    static final int COL_COORD_LAT = 8;
    static final int COL_COORD_LONG = 9;
    static final int COL_CITY_NAME = 10;
    static final int COL_TIME_ZONE = 11;
    static final int COL_WEATHER_WIND_SPEED = 12;
    static final int COL_WEATHER_DEGREES = 13;
    static final int COL_WEATHER_CLOUDS = 14;
    static final int COL_WEATHER_RAIN = 15;
    static final int COL_WEATHER_SNOW = 16;
    static final int COL_WEATHER_ICON = 17;
    static final int COL_WEATHER_SUN_RISE = 18;
    static final int COL_WEATHER_SUN_SET = 19;
    static final int COL_WEATHER_FEELSLIKE = 20;
    static final int COL_WEATHER_UVI = 21;
    static final int COL_TYPE = 22;

    /**
     * A callback interface that all activities containing this fragment must
     * implement. This mechanism allows activities to be notified of item
     * selections.
     */
    public interface CallbackHourly {
        /**
         * DetailFragmentCallback for when an item has been selected.
         */
        void onItemSelectedHourly(Uri dateUri);
    }

    public ForecastHourlyFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Add this line in order for this fragment to handle menu events.
        setHasOptionsMenu(true);
    }

//    @Override
//    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
//        inflater.inflate(R.menu.forecastfragment, menu);
//    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            updateWeather();
            return true;
        }
        if (id == R.id.action_map) {
            openPreferredLocationInMap();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // The ForecastAdapter will take data from a source and
        // use it to populate the ListView it's attached to.
        mForecastAdapter = new ForecastAdapterHourly(getActivity(), null, 0);

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        // Get a reference to the ListView, and attach this adapter to it.
        mListView = (ListView) rootView.findViewById(R.id.listview_forecast);
        mListView.setAdapter(mForecastAdapter);
        mListView.setNestedScrollingEnabled(true);
        View emptyView = rootView.findViewById(R.id.listview_forecast_empty);
        mListView.setEmptyView(emptyView);
        // We'll call our MainActivity
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                // CursorAdapter returns a cursor at the correct position for getItem(), or null
                // if it cannot seek to that position.
                Cursor cursor = (Cursor) adapterView.getItemAtPosition(position);
                if (cursor != null) {
                    String locationSetting = Utility.getPreferredLocation(getActivity());
                    Integer type;
                    if (Utility.getProvider(getActivity()).equals(getActivity().getString(R.string.pref_provider_wug))) {
                        type = WeatherContract.TYPE_HOURLY;
                    } else {
                        type = WeatherContract.TYPE_CURRENT_HOURLY;
                    }
                    ((CallbackHourly) getActivity())
                            .onItemSelectedHourly(WeatherContract.WeatherEntry.buildWeatherLocationWithDateType(
                                    locationSetting, cursor.getLong(COL_WEATHER_DATE), type));
                }
                mPosition = position;
            }
        });

                        /*
         * Sets up a SwipeRefreshLayout.OnRefreshListener that is invoked when the user
         * performs a swipe-to-refresh gesture.
         */
        mSwipeRefreshLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.swiperefresh);
        mSwipeRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        Log.i(LOG_TAG, "onRefresh called from SwipeRefreshLayout");

                        // This method performs the actual data-refresh operation.
                        // The method calls setRefreshing(false) when it's finished.
                        // get the new data from you data source
                        if (!WetterSyncAdapter.syncImmediately(getActivity())) {
                            if (mSwipeRefreshLayout.isRefreshing()) {
                                mSwipeRefreshLayout.setRefreshing(false);
                            }
                        };

                        /* our swipeRefreshLayout needs to be notified when the data is
                        returned in order for it to stop the animation */
//                        mHandler.post(refreshing);
                    }
                }
        );
        // sets the colors used in the refresh animation
        mSwipeRefreshLayout.setColorSchemeResources(
                R.color.primary,
                R.color.primary_dark,
                R.color.primary_light
        );

        // If there's instance state, mine it for useful information.
        // The end-goal here is that the user never knows that turning their device sideways
        // does crazy lifecycle related things.  It should feel like some stuff stretched out,
        // or magically appeared to take advantage of room, but data or place in the app was never
        // actually *lost*.
        if (savedInstanceState != null && savedInstanceState.containsKey(SELECTED_KEY)) {
            // The listview probably hasn't even been populated yet.  Actually perform the
            // swapout in onLoadFinished.
            mPosition = savedInstanceState.getInt(SELECTED_KEY);
        }

        mForecastAdapter.setUseTodayLayout(mUseTodayLayout);

        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        getLoaderManager().initLoader(FORECAST_LOADER_HOURLY, null, this);
        super.onActivityCreated(savedInstanceState);
    }

    // since we read the location when we create the loader, all we need to do is restart things
    void onLocationChanged() {
        updateWeather();
        getLoaderManager().restartLoader(FORECAST_LOADER_HOURLY, null, this);
    }

    private void updateWeather() {
        WetterSyncAdapter.syncImmediately(getActivity());
    }

    private void openPreferredLocationInMap() {
        // Using the URI scheme for showing a location found on a map.  This super-handy
        // intent can is detailed in the "Common Intents" page of Android's developer site:
        // http://developer.android.com/guide/components/intents-common.html#Maps
        if (null != mForecastAdapter) {
            Cursor c = mForecastAdapter.getCursor();
            if (null != c) {
                c.moveToPosition(0);
                String posLat = c.getString(COL_COORD_LAT);
                String posLong = c.getString(COL_COORD_LONG);
                Uri geoLocation = Uri.parse("geo:" + posLat + "," + posLong);

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(geoLocation);

                if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    Log.d(LOG_TAG, "Couldn't call " + geoLocation.toString() + ", no receiving apps installed!");
                }
            }

        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // When tablets rotate, the currently selected list item needs to be saved.
        // When no item is selected, mPosition will be set to Listview.INVALID_POSITION,
        // so check for that before storing.
        if (mPosition != ListView.INVALID_POSITION) {
            outState.putInt(SELECTED_KEY, mPosition);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
        // This is called when a new Loader needs to be created.  This
        // fragment only uses one loader, so we don't care about checking the id.

        // To only show current and future dates, filter the query to return weather only for
        // dates after or including today.
        if (id != FORECAST_LOADER_HOURLY) {
            return null;
        }
        // get the time beginning of today
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        cal.set(Calendar.HOUR_OF_DAY, cal.get(Calendar.HOUR_OF_DAY) - 1);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long timeInMillis = cal.getTimeInMillis();
        //long timeInMillis = System.currentTimeMillis():
        // Sort order:  Ascending, by date.
        String sortOrder = WeatherContract.WeatherEntry.COLUMN_TYPE + " DESC," + WeatherContract.WeatherEntry.COLUMN_DATE + " ASC";

        String locationSetting = Utility.getPreferredLocation(getActivity());

        Integer type = TYPE_CURRENT_HOURLY;

        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDateType(
                locationSetting, timeInMillis, type);

        return new CursorLoader(getActivity(),
                weatherForLocationUri,
                FORECAST_COLUMNS,
                null,
                null,
                sortOrder);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {

        if (loader.getId() == FORECAST_LOADER_HOURLY) {
            mForecastAdapter.swapCursor(data);
            if (mPosition != ListView.INVALID_POSITION) {
                // If we don't need to restart the loader, and there's a desired position to restore
                // to, do so now.
                mListView.smoothScrollToPosition(mPosition);
            }
            updateEmptyView();

//            Log.d(LOG_TAG, DatabaseUtils.dumpCursorToString(data));
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mForecastAdapter.swapCursor(null);
    }

    public void setUseTodayLayout(boolean useTodayLayout) {
        mUseTodayLayout = useTodayLayout;
        if (mForecastAdapter != null) {
            mForecastAdapter.setUseTodayLayout(mUseTodayLayout);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        sp.registerOnSharedPreferenceChangeListener(this);

        syncObserverHandle = ContentResolver.addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_PENDING
                        | ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE,
                new ForecastHourlyFragment.MySyncStatusObserver());

        //only display furure hourly items
        String locationSetting = Utility.getPreferredLocation(getActivity());
        WetterSyncAdapter.deleteOldWeatherData(getContext(), Utility.getLocationId(getContext(), locationSetting), TYPE_HOURLY, 0);
        WetterSyncAdapter.deleteOldWeatherData(getContext(), Utility.getLocationId(getContext(), locationSetting), TYPE_CURRENT, -1);

    }

    @Override
    public void onPause() {
        super.onPause();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        sp.unregisterOnSharedPreferenceChangeListener(this);

        if (syncObserverHandle != null) {
            ContentResolver.removeStatusChangeListener(syncObserverHandle);
            syncObserverHandle = null;
        }
    }

    /*
        Updates the empty list view with contextually relevant information that the user can
        use to determine why they aren't seeing weather.
     */
    private void updateEmptyView() {
        if (mForecastAdapter.getCount() == 0) {
            TextView tv = (TextView) getView().findViewById(R.id.listview_forecast_empty);
            if (null != tv) {
                // if cursor is empty, why? do we have an invalid location
                int message = R.string.empty_forecast_list;
                @WetterSyncAdapter.LocationStatus int location = Utility.getLocationStatus(getActivity());
                switch (location) {
                    case WetterSyncAdapter.LOCATION_STATUS_UNKNOWN:
                        message = R.string.pref_location_unknown_description;
                        break;
                    case WetterSyncAdapter.LOCATION_STATUS_SERVER_INVALID:
                        message = R.string.empty_forecast_list_server_error;
                        break;
                    case WetterSyncAdapter.LOCATION_STATUS_INVALID:
                        message = R.string.pref_location_error_description;
                        break;
                    default:
                        if (!Utility.isNetworkAvailable(getActivity())) {
                            message = R.string.empty_forecast_list_no_network;
                        }
                }
                tv.setText(message);
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.pref_location_status_key))) {
            updateEmptyView();
        }
        if (key.equals(getString(R.string.pref_location_key))) {
            onLocationChanged();
        }
        if (key.equals(getString(R.string.pref_provider_key))) {
            onLocationChanged();
        }
    }


    private class MySyncStatusObserver implements SyncStatusObserver {
        @Override
        public void onStatusChanged(int which) {
            Account mAccount = WetterSyncAdapter.getSyncAccount(getContext());
            String mAuthority =  getString(R.string.content_authority);
            if (which == ContentResolver.SYNC_OBSERVER_TYPE_PENDING) {
                // 'Pending' state changed.
                if (ContentResolver.isSyncPending(mAccount, mAuthority)) {
                    // There is now a pending sync.
                    Log.d(LOG_TAG, "Sync is pending" );
                } else {
                    // There is no longer a pending sync.
                    Log.d(LOG_TAG, "Sync is not longer pending" );
                }
            } else if (which == ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE) {
                // 'Active' state changed.
                if (ContentResolver.isSyncActive(mAccount, mAuthority)) {
                    // There is now an active sync.
                    Log.d(LOG_TAG, "Sync is active" );
                } else {
                    Log.d(LOG_TAG, "Sync is not longer active" );
                    updateRefresh(false);
//                    if (mSwipeRefreshLayout.isRefreshing()) {
//                        mSwipeRefreshLayout.setRefreshing(false);
//                    }
                    // There is no longer an active sync.
                }
            }
        }
    }
    private void updateRefresh(final boolean isSyncing) {
        getActivity().runOnUiThread(new Runnable() {

            @Override
            public void run() {
                if (!isSyncing) {
                    if (mSwipeRefreshLayout.isRefreshing()) {
                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                } else {
//                    mRefreshMenu.setActionView(null);
                }
            }
        });
    }
}
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

import android.*;
import android.Manifest;
import android.accounts.Account;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.NavigationView;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.facebook.stetho.Stetho;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.sindzinski.wetter.data.WeatherContract;
import de.sindzinski.wetter.sync.WetterSyncAdapter;

import static de.sindzinski.wetter.Utility.wordFirstCap;


public class MainActivity extends AppCompatActivity implements
        ForecastDailyFragment.CallbackDaily,
        ForecastHourlyFragment.CallbackHourly,
        NavigationView.OnNavigationItemSelectedListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final String DETAILFRAGMENT_TAG = "DFTAG";

    private String mLocation;

    public static final String AUTHORITY = "de.sindzinski.wetter";
    // Account
    public static final Account ACCOUNT = new Account("steffen", AUTHORITY);
    // Sync interval constants
    public static final long SECONDS_PER_MINUTE = 60L;
    public static final long SYNC_INTERVAL_IN_MINUTES = 10L;
    public static final long SYNC_INTERVAL = SYNC_INTERVAL_IN_MINUTES;
    ContentResolver mResolver;
    protected ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;
    private NavigationView mNavigationView;
    private NavigationView navigationView;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private ViewPager mViewPager;
    private final int PERMISSIONS_REQUEST_GET_LOCATION = 101;
    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setUpStetho();

        mLocation = Utility.getPreferredLocation(this);

        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mViewPager = (ViewPager) findViewById(R.id.viewpager);
        ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager());
        adapter.addFragment(new ForecastHourlyFragment(), "Hourly Forecast");
        adapter.addFragment(new ForecastDailyFragment(), "Daily Forecast");

        mViewPager.setAdapter(adapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);

        WetterSyncAdapter.initializeSyncAdapter(this);

        setUpTheme();

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        navigationView = (NavigationView) findViewById(R.id.navigation_view);
        navigationView.setNavigationItemSelectedListener(this);

        addLocationToNavigation();

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.registerOnSharedPreferenceChangeListener(this);
    }

    private void addLocationToNavigation() {
        Cursor locationCursor = this.getContentResolver().query(
                WeatherContract.LocationEntry.CONTENT_URI,
                new String[]{WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING},
                "",
                new String[]{},
                null);

        final Menu menu = navigationView.getMenu();
        int i = 0;
        while (locationCursor.moveToNext()) {
            i++;
            String locationSetting = locationCursor.getString(
                    locationCursor.getColumnIndex(WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING));
            menu.add(R.id.group1, i, Menu.NONE, locationSetting).setIcon(ContextCompat.getDrawable(this, R.drawable.ic_home_black_24dp));
        }
        locationCursor.close();
    }

    public void reInitializeNavigation() {
        navigationView.getMenu().clear();
        navigationView.inflateMenu(R.menu.drawer_view);
        addLocationToNavigation();
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_about) {
        } else if (id == R.id.nav_add_location) {
            addLocationSetting();
            View view = findViewById(R.id.viewpager);
            Utility.showSnackbar(this, findViewById(R.id.viewpager), R.string.location_added);
        } else if (id == R.id.nav_remove_current_location) {
            Utility.deleteCurrentLocation(this);
            reInitializeNavigation();
            View view = findViewById(R.id.viewpager);
            Utility.showSnackbar(this, findViewById(R.id.viewpager), R.string.location_removed);
        } else if (id == R.id.nav_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
//            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
//            sp.registerOnSharedPreferenceChangeListener(this);
        } else {
            String locationSetting = item.getTitle().toString();
//            long locationId = WetterSyncAdapter.getPreferredLocationCityId(this, locationSetting);
//            if (locationId != 0) {
                //change the location, sync and update adapters
                Utility.setPreferredLocation(this, locationSetting);
                Utility.resetLocationStatus(this);
                Utility.setLastSync(this, System.currentTimeMillis() - 1000 * 60 * 10);
                WetterSyncAdapter.syncImmediately(this);
                Utility.showSnackbar(this, findViewById(R.id.viewpager), R.string.location_changed);
//            }
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    public void addLocationSettingDialog() {
        String locationSetting = "";

        final EditText input = new EditText(MainActivity.this);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_title_add_location_setting);
        builder.setView(input);
        // Set the action buttons
        builder
                .setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        // User clicked OK, so save the mSelectedItems results somewhere
                        // or return them to the component that opened the dialog
                        String locationSetting = input
                                .getText()
                                .toString()
                                .toLowerCase()
                                .replaceAll("\\s+", "");
                        if (locationSetting.compareTo("") != 0) {
                            Utility.setPreferredLocation(getApplicationContext(), locationSetting);
                            Utility.resetLocationStatus(getApplicationContext());
                            WetterSyncAdapter.syncImmediately(getApplicationContext());
                            reInitializeNavigation();
                        }

                    }
                })
                .setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                })
                .setNeutralButton(R.string.dialog_auto, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        // User clicked OK, so save the mSelectedItems results somewhere
                        // or return them to the component that opened the dialog
                        Location location = getLocation();
                        String locationSetting = getLocationSetting(location);
                        if (locationSetting.compareTo("") != 0) {
                            Utility.setPreferredLocation(getApplicationContext(), locationSetting);
                            Utility.resetLocationStatus(getApplicationContext());
                            WetterSyncAdapter.syncImmediately(getApplicationContext());
                            reInitializeNavigation();
                        }
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void addLocationSetting() {
        addLocationSettingDialog();
    }


    public void askForPermissions() {
        // Here, thisActivity is the current activity
        if ((ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) &&
                (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED))
                {

            // Should we show an explanation?
            if ((ActivityCompat.shouldShowRequestPermissionRationale(this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION)) &&
                    (ActivityCompat.shouldShowRequestPermissionRationale(this,
                            android.Manifest.permission.ACCESS_FINE_LOCATION)))
                    {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                        new String[]{
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_FINE_LOCATION},
                        PERMISSIONS_REQUEST_GET_LOCATION);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_GET_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    addLocationSetting();

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    /*
    one time ask, but should be asked beforehand
     */
    public void checkAndAskForPermission() {
        final Context context = this;
        if ((ContextCompat.checkSelfPermission((Activity) context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) &&
                ContextCompat.checkSelfPermission((Activity) context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSIONS_REQUEST_GET_LOCATION);
        }
    }
    public boolean checkForPermission() {

        if ((ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) &&
                ContextCompat.checkSelfPermission( this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            return false;
        }
    }

    public Location getLocation() {
        Location location = null;
        String provider = "";
        String locationSetting = "";

        if (!checkForPermission()) {
            askForPermissions();
            return null;
        }

        try {
            // Get the location manager
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            // Define the criteria how to select the locatioin provider -> use
            // default
            Criteria criteria = new Criteria();
            if (locationManager != null) {
                provider = locationManager.getBestProvider(criteria, false);
                location = locationManager.getLastKnownLocation(provider);
            }

        } catch (Exception ex) {
            Log.e(LOG_TAG, "Error creating location service: " + ex.getMessage());
        }

        return location;
    }

    public String getLocationSetting(Location location) {
        String locationSetting = "";
        //locale must be set to us for getting english countey names
        Geocoder geoCoder = new Geocoder(this, Locale.US);
        StringBuilder builder = new StringBuilder();
        try {
            double lat = location.getLatitude();
            double lng = location.getLongitude();

            List<Address> address = geoCoder.getFromLocation(lat, lng, 1);
//            String countryCode = address.get(0).getCountryCode();
            String country = address.get(0).getCountryName();
//            String country = CountryCodes.getCountry(countryCode);
            String city = address.get(0).getLocality();
            builder
                    .append(country)
                    .append("/")
                    .append(city);

            locationSetting = builder.toString(); //This is the complete locationsetting as wug.
            locationSetting = wordFirstCap(locationSetting, "/");
        } catch (IOException e) {
        } catch (NullPointerException e) {
        }

        return locationSetting;
    }

//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.main, menu);
//        return true;
//    }

//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        // Handle action bar item clicks here. The action bar will
//        // automatically handle clicks on the Home/Up button, so long
//        // as you specify a parent activity in AndroidManifest.xml.
//        int id = item.getItemId();
//        switch (id) {
//            case android.R.id.home:
//                mDrawerLayout.openDrawer(GravityCompat.START);
//                return true;
//            case R.id.action_settings:
//                startActivity(new Intent(this, SettingsActivity.class));
//                return true;
//        }
//        return super.onOptionsItemSelected(item);
//    }

    @Override
    public void onPause() {
        super.onPause();
//        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
//        sp.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String location = Utility.getPreferredLocation(this);
        // update the location in our second pane using the fragment manager
        if (location != null && !location.equals(mLocation)) {
            ForecastHourlyFragment ff = (ForecastHourlyFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_forecast);
            if (null != ff) {
                ff.onLocationChanged();
            }
            DetailFragment df = (DetailFragment) getSupportFragmentManager().findFragmentByTag(DETAILFRAGMENT_TAG);
            if (null != df) {
                df.onLocationChanged(location);
            }
            mLocation = location;
        }

//        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
//        sp.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onItemSelectedDaily(Uri contentUri) {
        Intent intent = new Intent(this, DetailActivity.class)
                .setData(contentUri);
        startActivity(intent);
    }

    @Override
    public void onItemSelectedHourly(Uri contentUri) {
        Intent intent = new Intent(this, DetailActivity.class)
                .setData(contentUri);
        startActivity(intent);
    }

//    public void rotateFabForward() {
//        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.button_check);
//        ViewCompat.animate(fab)
//                .rotation(135.0F)
//                .withLayer()
//                .setDuration(3000L)
//                .setInterpolator(new OvershootInterpolator(10.0F))
//                .start();
//    }
//
//    public void rotateFabBackward() {
//        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.button_check);
//        ViewCompat.animate(fab)
//                .rotation(0.0F)
//                .withLayer()
//                .setDuration(300L)
//                .setInterpolator(new OvershootInterpolator(10.0F))
//                .start();
//    }

    private void setUpStetho() {
        // Create an InitializerBuilder
        Stetho.InitializerBuilder initializerBuilder =
                Stetho.newInitializerBuilder(this);
        // Enable Chrome DevTools
        initializerBuilder.enableWebKitInspector(
                Stetho.defaultInspectorModulesProvider(this)
        );
        // Enable command line interface
        initializerBuilder.enableDumpapp(
                Stetho.defaultDumperPluginsProvider(this)
        );
        // Use the InitializerBuilder to generate an Initializer
        Stetho.Initializer initializer = initializerBuilder.build();
        // Initialize Stetho with the Initializer
        Stetho.initialize(initializer);
    }

    private void setUpTheme() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String theme = prefs.getString(this.getString(R.string.pref_theme_key),
                this.getString(R.string.pref_theme_night));
        if (theme.equals(getString(R.string.pref_theme_night))) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if (theme.equals(getString(R.string.pref_theme_day))) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(this.getString(R.string.pref_location_key))) {
            reInitializeNavigation();
        }

        if (key.equals(this.getString(R.string.pref_theme_key))) {
            setUpTheme();
        }
        if (key.equals(this.getString(R.string.pref_provider_key))) {
            Utility.deleteAllWeather(this);
            Utility.resetLocationStatus(this);
            Utility.setLastSync(this, System.currentTimeMillis() - 1000 * 60 * 20);
            WetterSyncAdapter.syncImmediately(this);
            Utility.showSnackbar(this, findViewById(R.id.viewpager), R.string.location_changed);

        }
        if (key.equals(this.getString(R.string.pref_delete_data_key))) {
            Utility.deleteAllWeather(this);
            Utility.resetLocationStatus(this);
            Utility.setLastSync(this, System.currentTimeMillis() - 1000 * 60 * 10);
            WetterSyncAdapter.syncImmediately(this);
            Utility.showSnackbar(this, this.findViewById(R.id.viewpager), R.string.deleted_weather_data);
        }
    }


    class ViewPagerAdapter extends FragmentPagerAdapter {
        private final List<Fragment> mFragmentList = new ArrayList<>();
        private final List<String> mFragmentTitleList = new ArrayList<>();

        public ViewPagerAdapter(FragmentManager manager) {
            super(manager);
        }

        @Override
        public Fragment getItem(int position) {
            return mFragmentList.get(position);
        }

        @Override
        public int getCount() {
            return mFragmentList.size();
        }

        @Override
        public int getItemPosition(Object object) {
            if (mFragmentList.contains(object)) return mFragmentList.indexOf(object);
            else return POSITION_NONE;
        }

        public void addFragment(Fragment fragment, String title) {
            mFragmentList.add(fragment);
            mFragmentTitleList.add(title);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mFragmentTitleList.get(position);
        }

        public Fragment getFragment(int position) {
            return mFragmentList.get(position);
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            super.destroyItem(container, position, object);

            if (position <= getCount()) {
                FragmentManager manager = ((Fragment) object).getFragmentManager();
                FragmentTransaction trans = manager.beginTransaction();
                trans.remove((Fragment) object);
                trans.commit();
            }
        }
    }

}

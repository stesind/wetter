/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.widget.Toast;

import de.sindzinski.wetter.data.WeatherContract;
import de.sindzinski.wetter.sync.WetterSyncAdapter;

/**
 * A {@link PreferenceActivity} that presents a set of application settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        // Display the fragment as the main content.
        getFragmentManager().beginTransaction().replace(android.R.id.content,
                new PrefsFragment()).commit();
    }

    public static class PrefsFragment extends PreferenceFragment
            implements
            Preference.OnPreferenceChangeListener,
            SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // Add 'general' preferences, defined in the XML file
            addPreferencesFromResource(R.xml.pref_general);

            // For all preferences, attach an OnPreferenceChangeListener so the UI summary can be
            // updated when the preference changes.
            bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_location_key)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_location_wug_key)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_api_key_key)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_units_key)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_art_pack_key)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_theme_key)));
            bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_provider_key)));

            // set texts correctly
            onSharedPreferenceChanged(null, "");
        }

        // Registers a shared preference change listener that gets notified when preferences change
        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        // Unregisters a shared preference change listener
        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        /**
         * Attaches a listener so the summary is always updated with the preference value.
         * Also fires the listener once, to initialize the summary (so it shows up before the value
         * is changed.)
         */
        private void bindPreferenceSummaryToValue(Preference preference) {
            // Set the listener to watch for value changes.
            preference.setOnPreferenceChangeListener(this);

            // Set the preference summaries
            setPreferenceSummary(preference,
                    PreferenceManager
                            .getDefaultSharedPreferences(preference.getContext())
                            .getString(preference.getKey(), ""));
        }

        private void setPreferenceSummary(Preference preference, Object value) {
            String stringValue = value.toString();
            String key = preference.getKey();

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list (since they have separate labels/values).
                ListPreference listPreference = (ListPreference) preference;
                int prefIndex = listPreference.findIndexOfValue(stringValue);
                if (prefIndex >= 0) {
                    preference.setSummary(listPreference.getEntries()[prefIndex]);
                }
            } else if (key.equals(getString(R.string.pref_location_key))) {
                @WetterSyncAdapter.LocationStatus int status = Utility.getLocationStatus(getActivity());
                switch (status) {
                    case WetterSyncAdapter.LOCATION_STATUS_OK:
                        preference.setSummary(stringValue);
                        break;
                    case WetterSyncAdapter.LOCATION_STATUS_UNKNOWN:
                        preference.setSummary(getString(R.string.pref_location_unknown_description, value.toString()));
                        break;
                    case WetterSyncAdapter.LOCATION_STATUS_INVALID:
                        preference.setSummary(getString(R.string.pref_location_error_description, value.toString()));
                        break;
                    default:
                        // Note --- if the server is down we still assume the value
                        // is valid
                        preference.setSummary(stringValue);
                }
                status = Utility.getLocationStatus(getActivity());
                switch (status) {
                    case WetterSyncAdapter.LOCATION_STATUS_OK:
                        preference.setSummary(stringValue);
                        break;
                    case WetterSyncAdapter.LOCATION_STATUS_UNKNOWN:
                        preference.setSummary(getString(R.string.pref_location_unknown_description, value.toString()));
                        break;
                    case WetterSyncAdapter.LOCATION_STATUS_INVALID:
                        preference.setSummary(getString(R.string.pref_location_error_description, value.toString()));
                        break;
                    default:
                        // Note --- if the server is down we still assume the value
                        // is valid
                        preference.setSummary(stringValue);
                }
            } else {
                // For other preferences, set the summary to the value's simple string representation.
                preference.setSummary(stringValue);
            }
        }

        // This gets called before the preference is changed
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();
            String key = preference.getKey();
            if (key.equals(getString(R.string.pref_location_wug_key))) {
                String newValue = Utility.wordFirstCap(stringValue, "/");
                SharedPreferences.Editor editor = preference.getEditor();
                editor.putString(getResources().getString(R.string.pref_location_wug_key), newValue);
                editor.commit();
            }
            setPreferenceSummary(preference, value);
            return true;
        }

        // This gets called after the preference is changed, which is important because we
        // start our synchronization here
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(getString(R.string.pref_location_key))) {
                // we've changed the location
                // first clear locationStatus
                Utility.resetLocationStatus(getActivity());
                WetterSyncAdapter.syncImmediately(getActivity());
            } else if (key.equals(getString(R.string.pref_location_wug_key))) {
                    Utility.resetLocationStatus(getActivity());
                    WetterSyncAdapter.syncImmediately(getActivity());
            } else if (key.equals(getString(R.string.pref_units_key))) {
                // units have changed. update lists of weather entries accordingly
                getActivity().getContentResolver().notifyChange(WeatherContract.WeatherEntry.CONTENT_URI, null);
            } else if (key.equals(getString(R.string.pref_location_status_key))) {
                // our location status has changed.  Update the summary accordingly
                Preference locationPreference = findPreference(getString(R.string.pref_location_key));
                bindPreferenceSummaryToValue(locationPreference);
            } else if (key.equals(getString(R.string.pref_art_pack_key))) {
                // art pack have changed. update lists of weather entries accordingly
                getActivity().getContentResolver().notifyChange(WeatherContract.WeatherEntry.CONTENT_URI, null);
            } else if (key.equals(getString((R.string.pref_theme_key)))) {
                // toast restart app required
                //
                // Toast.makeText(getActivity(), getString(R.string.notification_restart), Toast.LENGTH_SHORT).show();

            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public Intent getParentActivityIntent() {
        return super.getParentActivityIntent().addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }
}

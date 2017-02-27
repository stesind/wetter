package de.sindzinski.wetter;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.test.espresso.matcher.BoundedMatcher;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;
import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.onData;
;
import static android.support.test.espresso.action.ViewActions.clearText;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.closeSoftKeyboard;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.assertion.ViewAssertions.matches;

import static android.support.test.espresso.core.deps.guava.base.Preconditions.checkNotNull;
import static android.support.test.espresso.matcher.PreferenceMatchers.withKey;
import static android.support.test.espresso.matcher.PreferenceMatchers.withSummaryText;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withChild;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static de.sindzinski.wetter.R.string.pref_location_key;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.AllOf.allOf;

/**
 * Created by steffen on 23.02.17.
 */

@RunWith(AndroidJUnit4.class)
@LargeTest
public class SettingsActivityTest {

    @Rule
    public ActivityTestRule<SettingsActivity> mActivityRule =
            new ActivityTestRule<>(SettingsActivity.class);

    @Test
    public void locationPreferenceTest() {
        Resources resources = mActivityRule.getActivity().getResources();
        String mStringToBetyped = "Berlin";
        onData(allOf(
                is(instanceOf(Preference.class)),
                withKey(resources.getString(R.string.pref_location_key)))).
//                withSummary(R.string.summary_working_in_background_preference),
//                withTitle(R.string.title_working_in_background_preference)))
//                .onChildView(withText(title))
                check(matches(isDisplayed()));
        onData(allOf(is(instanceOf(Preference.class)), withKey(resources.getString(R.string.pref_location_key)))).perform(click());
        //dialog opens for entering the text, needs to be handles differently
        //see list preference example

//        onData(allOf(is(instanceOf(Preference.class)), withKey(resources.getString(R.string.pref_location_key)))).perform(clearText());
//        onData(allOf(is(instanceOf(Preference.class)), withKey(resources.getString(R.string.pref_location_key)))).perform(typeText(mStringToBetyped), closeSoftKeyboard());
//        onData(allOf(is(instanceOf(Preference.class)), withKey(resources.getString(R.string.pref_location_key)))).check(matches(withText(mStringToBetyped)));

    }

//    1. Create a matcher:

    public static Matcher<Object> withDialogListContent(String expectedText) {
        checkNotNull(expectedText);

        return withDialogListContent(equalTo(expectedText));
    }

    @SuppressWarnings("rawtypes")
    public static Matcher<Object> withDialogListContent(final Matcher<String> itemTextMatcher) {
        checkNotNull(itemTextMatcher);
        return new BoundedMatcher<Object, String>(String.class) {
            @Override    public boolean matchesSafely(String value){
                return itemTextMatcher.matches(value);
            }

            @Override    public void describeTo(Description description) {
                description.appendText("with Dialog List Content: ");
                itemTextMatcher.describeTo(description);
            }
        };
    }

    @Test
    public void testListPreference() {
        Resources resources = mActivityRule.getActivity().getResources();
        onData(allOf(
                is(instanceOf(Preference.class)),
                withKey(resources.getString(R.string.pref_units_key)))).
                perform(click());
        // find new zealand..
        onData(withDialogListContent(
                resources.getString(R.string.pref_units_label_metric))).
                inAdapterView(withChild(withId(android.R.id.text1))).
                check(matches(isDisplayed()));
        onData(withDialogListContent(
                resources.getString(R.string.pref_units_label_metric))).
                inAdapterView(withChild(withId(android.R.id.text1))).
                perform(click());

        // we are licensed.
         onData(allOf(
                 is(instanceOf(Preference.class)),
                 withKey(resources.getString(R.string.pref_units_key)),
                 withSummaryText(resources.getString(R.string.pref_units_label_metric)))).
                 check(matches(isDisplayed()));

        // shared preferences are updated later and cannot be tested here
        SharedPreferences sharedPreferences =
                getDefaultSharedPreferences(getInstrumentation().getTargetContext());
        assertThat(
                sharedPreferences.getString(
                    resources.getString(R.string.pref_units_key),
                        resources.getString(R.string.pref_units_label_metric)),
                is(equalTo(
                        resources.getString(R.string.pref_units_label_metric))));
    }

    @Before
    @After
    public void cleanSharedPrefs(){
        SharedPreferences sharedPreferences =
//                getInstrumentation().getTargetContext().getSharedPreferences(MyBusStopsActivity.FAV_LIST, Context.MODE_PRIVATE);
                getDefaultSharedPreferences(getInstrumentation().getTargetContext());
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.commit();
    }

}

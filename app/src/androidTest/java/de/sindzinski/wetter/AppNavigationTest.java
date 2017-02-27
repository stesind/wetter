package de.sindzinski.wetter;

/**
 * Created by steffen on 20.02.17.
 */


import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.v4.widget.DrawerLayout;
import android.view.Gravity;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.contrib.DrawerActions.open;
import static android.support.test.espresso.contrib.DrawerMatchers.isClosed;
import static android.support.test.espresso.contrib.DrawerMatchers.isOpen;
import static android.support.test.espresso.contrib.NavigationViewActions.navigateTo;
import static android.support.test.espresso.matcher.RootMatchers.isDialog;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withContentDescription;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.fail;


/**
 * Tests for the {@link DrawerLayout} layout component in {@link MainActivity} which manages
 * navigation within the app.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class AppNavigationTest {

    /**
     * {@link ActivityTestRule} is a JUnit {@link Rule @Rule} to launch your activity under test.
     *
     * <p>
     * Rules are interceptors which are executed for each test method and are important building
     * blocks of Junit tests.
     */
    @Rule
    public ActivityTestRule<MainActivity> mActivityTestRule =
            new ActivityTestRule<>(MainActivity.class);

    @Test
    public void clickOnSettingsNavigationItem_ShowsSettingsScreen() {
        // Open Drawer to click on navigation.
        onView(withId(R.id.drawer_layout))
                .check(matches(isClosed(Gravity.LEFT))) // Left Drawer should be closed.
                .perform(open()); // Open Drawer

        // Start settings screen.
        onView(withId(R.id.navigation_view))
                .perform(navigateTo(R.id.nav_settings));

//        // Start statistics screen.
//        onView(withId(R.id.nav_view))
//                .perform(navigateTo(R.id.statistics_navigation_menu_item));
//
//        // Check that statistics Activity was opened.
//        String expectedNoStatisticsText = InstrumentationRegistry.getTargetContext()
//                .getString(R.string.pref_location_label);
//        onView(withId(R.id.pref_location_label)).check(matches(withText(expectedNoStatisticsText)));
    }

    @Test
    public void clickOnAddLocationItem_ShowsAddLocationDialog() {
        // Open Drawer to click on navigation.
        onView(withId(R.id.drawer_layout))
                .check(matches(isClosed(Gravity.LEFT))) // Left Drawer should be closed.
                .perform(open()); // Open Drawer

        // Start add location dialog.
        onView(withId(R.id.navigation_view))
                .perform(navigateTo(R.id.nav_add_location));

        // Check that add location dialog was opened.
        onView(withText(R.string.dialog_title_add_location_setting))
                .inRoot(isDialog()) // <---
                .check(matches(isDisplayed()));

        onView(withText(R.string.dialog_ok))
                .inRoot(isDialog())
                .check(matches(isDisplayed()));

        onView(withText(R.string.dialog_cancel))
                .inRoot(isDialog())
                .check(matches(isDisplayed()));

        onView(withText(R.string.dialog_auto))
                .inRoot(isDialog())
                .check(matches(isDisplayed()));
    }

    @Test
    public void clickOnAndroidHomeIcon_OpensNavigation() {
        // Check that left drawer is closed at startup
        onView(withId(R.id.drawer_layout))
                .check(matches(isClosed(Gravity.LEFT))); // Left Drawer should be closed.

        // Open Drawer
        String navigateUpDesc = mActivityTestRule.getActivity()
                .getString(android.support.v7.appcompat.R.string.abc_action_bar_up_description);
//        onView(withContentDescription(navigateUpDesc)).perform(click());
        onView(withId(R.id.drawer_layout)).perform(open());
        // Check if drawer is open
        onView(withId(R.id.drawer_layout))
                .check(matches(isOpen(Gravity.LEFT))); // Left drawer is open open.
    }

}

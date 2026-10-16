package com.tplayer.mediaplayer
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tplayer.mediaplayer.ui.MainActivity
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @Test fun settingsAndHelpAreReachableWithoutMediaPermission() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(org.hamcrest.Matchers.allOf(withText("Settings"), isDisplayed())).perform(click())
            onView(withText("Make it yours")).check(matches(isDisplayed()))
            onView(withText("Help & supported features")).perform(scrollTo(), click())
            onView(withText("TN-Player help")).check(matches(isDisplayed()))
        }
    }
}

package com.example.connectme
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

class MainActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun testEditProfile() {
        Thread.sleep(3000)
        onView(withId(R.id.loginButton)).perform(click())
        onView(withId(R.id.EditProfile)).perform(click())
        onView(withId(R.id.DoneEditing)).perform(click())

    }

    @Test
    fun testCheckFollowersAndFollowing() {
        Thread.sleep(3000)

        onView(withId(R.id.loginButton)).perform(click())
        onView(withId(R.id.FollowersPage)).perform(click())
        onView(withId(R.id.FollowingTab)).perform(click())
        onView(withId(R.id.FollowersTab)).perform(click())
        onView(withId(R.id.BackButton)).perform(click())

    }


}
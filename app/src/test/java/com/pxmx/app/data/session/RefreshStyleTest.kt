package com.pxmx.app.data.session

import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.ui.extras.RefreshEggSample
import com.pxmx.app.ui.extras.cogSpinDegreesPerSecond
import com.pxmx.app.ui.extras.nextRefreshEggStreak
import com.pxmx.app.ui.extras.stepRefreshEgg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RefreshStyleTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var store: SessionStore

    @Before
    fun setup() {
        prefs = FakeSharedPreferences()
        store = SessionStore(injectedPrefs = prefs)
    }

    @Test
    fun preferenceDefaultsFalse_andSetRefreshCogSticks() {
        assertFalse("refreshCog must default to false", store.refreshCog.value)

        store.setRefreshCog(true)
        assertTrue("refreshCog must be true after setRefreshCog(true)", store.refreshCog.value)

        val reloadedStore = SessionStore(injectedPrefs = prefs)
        assertTrue("refreshCog must persist in new SessionStore", reloadedStore.refreshCog.value)

        store.setRefreshCog(false)
        assertFalse("refreshCog must be false after toggle off", store.refreshCog.value)
        val freshStore = SessionStore(injectedPrefs = prefs)
        assertFalse("refreshCog must persist false", freshStore.refreshCog.value)
    }

    @Test
    fun purgeAll_resetsRefreshCog() {
        store.setRefreshCog(true)
        assertTrue(store.refreshCog.value)

        store.purgeAll()
        assertFalse("purgeAll with no context must reset refreshCog to false", store.refreshCog.value)

        val reloaded = SessionStore(injectedPrefs = prefs)
        assertFalse("reloaded store after purgeAll must be false", reloaded.refreshCog.value)
    }

    @Test
    fun pureFunction_cogSpinDegreesPerSecond() {
        assertEquals(0f, cogSpinDegreesPerSecond(pullFraction = 0f, refreshing = false), 0.0001f)
        assertEquals(720f, cogSpinDegreesPerSecond(pullFraction = 1f, refreshing = false), 0.0001f)
        assertEquals(180f, cogSpinDegreesPerSecond(pullFraction = 0.5f, refreshing = false), 0.0001f)
        assertEquals(0f, cogSpinDegreesPerSecond(pullFraction = -0.5f, refreshing = false), 0.0001f)
        assertEquals(0f, cogSpinDegreesPerSecond(pullFraction = -1f, refreshing = false), 0.0001f)
        assertEquals(900f, cogSpinDegreesPerSecond(pullFraction = 0f, refreshing = true), 0.0001f)
        assertEquals(900f, cogSpinDegreesPerSecond(pullFraction = 0.5f, refreshing = true), 0.0001f)
        assertEquals(900f, cogSpinDegreesPerSecond(pullFraction = 1f, refreshing = true), 0.0001f)
    }

    @Test
    fun nullGapReturnsStreak1AndAccept() {
        val res0 = nextRefreshEggStreak(previous = 0, idleGapMillis = null)
        assertEquals(1, res0.streak)
        assertTrue(res0.accepted)

        val res2 = nextRefreshEggStreak(previous = 2, idleGapMillis = null)
        assertEquals(1, res2.streak)
        assertTrue(res2.accepted)
    }

    @Test
    fun gap419KeepsPreviousStreakAndDoesNotAccept() {
        val res1 = nextRefreshEggStreak(previous = 1, idleGapMillis = 419L)
        assertEquals(1, res1.streak)
        assertFalse(res1.accepted)

        val res2 = nextRefreshEggStreak(previous = 2, idleGapMillis = 419L)
        assertEquals(2, res2.streak)
        assertFalse(res2.accepted)
    }

    @Test
    fun gap420Increments() {
        val res1 = nextRefreshEggStreak(previous = 1, idleGapMillis = 420L)
        assertEquals(2, res1.streak)
        assertTrue(res1.accepted)

        val res0 = nextRefreshEggStreak(previous = 0, idleGapMillis = 420L)
        assertEquals(1, res0.streak)
        assertTrue(res0.accepted)
    }

    @Test
    fun gap750Increments() {
        val res1 = nextRefreshEggStreak(previous = 1, idleGapMillis = 750L)
        assertEquals(2, res1.streak)
        assertTrue(res1.accepted)

        val res2 = nextRefreshEggStreak(previous = 2, idleGapMillis = 750L)
        assertEquals(3, res2.streak)
        assertTrue(res2.accepted)
    }

    @Test
    fun gap751ReturnsStreak1() {
        val res2 = nextRefreshEggStreak(previous = 2, idleGapMillis = 751L)
        assertEquals(1, res2.streak)
        assertTrue(res2.accepted)

        val res1 = nextRefreshEggStreak(previous = 1, idleGapMillis = 751L)
        assertEquals(1, res1.streak)
        assertTrue(res1.accepted)
    }

    @Test
    fun previous2PlusValidGapReturnsStreak3AndUnlock() {
        val initialRocket = RefreshEggSample(
            streak = 2,
            lastIdleMillis = 1000L,
            lastRefreshing = false,
            useCog = false,
        )
        val steppedRocket = stepRefreshEgg(
            sample = initialRocket,
            nowMillis = 1500L,
            isRefreshing = true,
            isArmed = true,
            useCog = false,
        )
        assertEquals(3, steppedRocket.streak)
        assertTrue("Rocket must unlock on streak 3", steppedRocket.rocketUnlocked)
        assertFalse("Cog must remain locked", steppedRocket.cogUnlocked)
        assertTrue(steppedRocket.inFlight)

        val initialCog = RefreshEggSample(
            streak = 2,
            lastIdleMillis = 1000L,
            lastRefreshing = false,
            useCog = true,
        )
        val steppedCog = stepRefreshEgg(
            sample = initialCog,
            nowMillis = 1500L,
            isRefreshing = true,
            isArmed = true,
            useCog = true,
        )
        assertEquals(3, steppedCog.streak)
        assertTrue("Cog must unlock on streak 3", steppedCog.cogUnlocked)
        assertFalse("Rocket must remain locked", steppedCog.rocketUnlocked)
        assertTrue(steppedCog.inFlight)
    }

    @Test
    fun unarmedRisingEdgeDoesNotIncrement() {
        val initial = RefreshEggSample(
            streak = 1,
            lastIdleMillis = 1000L,
            lastRefreshing = false,
            useCog = false,
        )
        val stepped = stepRefreshEgg(
            sample = initial,
            nowMillis = 1500L,
            isRefreshing = true,
            isArmed = false,
            useCog = false,
        )
        assertEquals("Streak must not increment on unarmed rise", 1, stepped.streak)
        assertFalse("Unarmed rise must not be in-flight accepted", stepped.inFlight)
        assertFalse("Rocket must not unlock", stepped.rocketUnlocked)
    }

    @Test
    fun styleSwitchClearsStreakAndKeepsBothUnlockFlags() {
        val initial = RefreshEggSample(
            streak = 2,
            lastIdleMillis = 1000L,
            inFlight = true,
            lastRefreshing = true,
            lastAccepted = true,
            useCog = false,
            rocketUnlocked = true,
            cogUnlocked = true,
        )
        val switched = stepRefreshEgg(
            sample = initial,
            nowMillis = 1500L,
            isRefreshing = true,
            isArmed = false,
            useCog = true,
        )
        assertEquals("Streak must be 0 after style switch", 0, switched.streak)
        assertEquals("Idle baseline must be null after style switch", null, switched.lastIdleMillis)
        assertFalse("In-flight flag must be cleared", switched.inFlight)
        assertTrue("Rocket unlock must be retained", switched.rocketUnlocked)
        assertTrue("Cog unlock must be retained", switched.cogUnlocked)
        assertTrue("useCog must be true", switched.useCog)

        // Verify active refresh after style switch is not treated as a new rise
        val nextTick = stepRefreshEgg(
            sample = switched,
            nowMillis = 1600L,
            isRefreshing = true,
            isArmed = true,
            useCog = true,
        )
        assertEquals("Active refresh after style switch must not be treated as a new rise", 0, nextTick.streak)
        assertFalse(nextTick.inFlight)
    }

    @Test
    fun fallingEdgeAfterIgnoredRiseDoesNotMoveIdleBaseline() {
        val initial = RefreshEggSample(
            streak = 1,
            lastIdleMillis = 1000L,
            lastRefreshing = false,
            useCog = false,
        )
        // 1. Ignored rise due to gap < 420ms (e.g. gap = 300ms)
        val riseIgnoredGap = stepRefreshEgg(
            sample = initial,
            nowMillis = 1300L,
            isRefreshing = true,
            isArmed = true,
            useCog = false,
        )
        assertEquals(1000L, riseIgnoredGap.lastIdleMillis)
        assertEquals(1, riseIgnoredGap.streak)

        val fallAfterIgnoredGap = stepRefreshEgg(
            sample = riseIgnoredGap,
            nowMillis = 1800L,
            isRefreshing = false,
            isArmed = false,
            useCog = false,
        )
        assertEquals("Falling edge after ignored rise must not move idle baseline", 1000L, fallAfterIgnoredGap.lastIdleMillis)

        // 2. Ignored rise due to unarmed pull
        val riseUnarmed = stepRefreshEgg(
            sample = initial,
            nowMillis = 1500L,
            isRefreshing = true,
            isArmed = false,
            useCog = false,
        )
        val fallAfterUnarmed = stepRefreshEgg(
            sample = riseUnarmed,
            nowMillis = 1900L,
            isRefreshing = false,
            isArmed = false,
            useCog = false,
        )
        assertEquals("Falling edge after unarmed rise must not move idle baseline", 1000L, fallAfterUnarmed.lastIdleMillis)
    }
}

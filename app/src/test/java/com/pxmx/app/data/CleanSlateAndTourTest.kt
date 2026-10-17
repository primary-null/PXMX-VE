package com.pxmx.app.data

import com.pxmx.app.data.model.AuthMode
import com.pxmx.app.data.model.FirewallRule
import com.pxmx.app.data.model.SavedProfile
import com.pxmx.app.data.model.SdnStatusInfo
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.ServerLampThresholds
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.model.ThemeMode
import com.pxmx.app.data.session.SessionStore
import com.pxmx.app.ui.tour.TourController
import com.pxmx.app.ui.tour.TourPanelPlacement
import com.pxmx.app.ui.tour.TourStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CleanSlateAndTourTest {

    @Before
    fun setUp() {
        TourController.reset()
    }

    @Test
    fun sessionStore_purgeAll_resetsTourFlagAndState() {
        val fakePrefs = FakeSharedPreferences()
        val sessionStore = SessionStore(injectedPrefs = fakePrefs)

        // Populate initial state
        val config = ServerConfig(host = "192.0.2.50", port = 8006, authMode = AuthMode.PASSWORD)
        val profile = sessionStore.saveProfileFromLogin(config, saveCredentials = true)
        sessionStore.setSession(SessionState(config = config, ticket = "test-ticket", csrf = "csrf"))
        sessionStore.setThemeMode(ThemeMode.LIGHT)
        sessionStore.setServerLampThresholds(ServerLampThresholds(10, 60))
        sessionStore.markTourCompleted()

        assertTrue(sessionStore.tourCompleted.value)
        assertEquals(1, sessionStore.profiles.value.size)
        assertEquals(ThemeMode.LIGHT, sessionStore.themeMode.value)
        assertEquals(10, sessionStore.serverLampThresholds.value.activityPercent)
        assertEquals(60, sessionStore.serverLampThresholds.value.warnPercent)

        // Perform Clean Slate purge
        sessionStore.purgeAll()

        assertFalse(sessionStore.tourCompleted.value)
        assertTrue(sessionStore.profiles.value.isEmpty())
        assertNull(sessionStore.session.value)
        assertEquals(ThemeMode.OLED_DARK, sessionStore.themeMode.value)
        assertEquals(5, sessionStore.serverLampThresholds.value.activityPercent)
        assertEquals(85, sessionStore.serverLampThresholds.value.warnPercent)

        // Verify next launch sees pristine state
        val nextLaunchStore = SessionStore(injectedPrefs = fakePrefs)
        assertFalse(nextLaunchStore.tourCompleted.value)
        assertTrue(nextLaunchStore.profiles.value.isEmpty())
        assertEquals(ThemeMode.OLED_DARK, nextLaunchStore.themeMode.value)
        assertEquals(5, nextLaunchStore.serverLampThresholds.value.activityPercent)
        assertEquals(85, nextLaunchStore.serverLampThresholds.value.warnPercent)
    }

    @Test
    fun sessionStore_serverLampThresholds_persistenceAndClamping() {
        val fakePrefs = FakeSharedPreferences()
        val sessionStore = SessionStore(injectedPrefs = fakePrefs)

        // Defaults when keys missing
        assertEquals(5, sessionStore.serverLampThresholds.value.activityPercent)
        assertEquals(85, sessionStore.serverLampThresholds.value.warnPercent)

        // Set custom thresholds
        sessionStore.setServerLampThresholds(ServerLampThresholds(10, 70))
        assertEquals(10, sessionStore.serverLampThresholds.value.activityPercent)
        assertEquals(70, sessionStore.serverLampThresholds.value.warnPercent)

        // Slide activity up against warn bumps warn to activity+1 (cap 100)
        sessionStore.setServerLampActivity(75)
        assertEquals(75, sessionStore.serverLampThresholds.value.activityPercent)
        assertEquals(76, sessionStore.serverLampThresholds.value.warnPercent)

        // Slide warn down against activity drops activity to warn-1 (floor 0)
        sessionStore.setServerLampWarn(50)
        assertEquals(49, sessionStore.serverLampThresholds.value.activityPercent)
        assertEquals(50, sessionStore.serverLampThresholds.value.warnPercent)

        // Reset restores defaults
        sessionStore.resetServerLampThresholds()
        assertEquals(5, sessionStore.serverLampThresholds.value.activityPercent)
        assertEquals(85, sessionStore.serverLampThresholds.value.warnPercent)
    }

    @Test
    fun tourController_fullAdvanceProgression() {
        val fakePrefs = FakeSharedPreferences()
        val sessionStore = SessionStore(injectedPrefs = fakePrefs)
        TourController.reset()

        // Not started yet
        assertNull(TourController.currentStep.value)

        // Trigger tour eligibility
        TourController.startTourIfEligible(sessionStore)
        assertEquals(TourStep.GUEST_CARD, TourController.currentStep.value)

        // Advancing wrong step should not change state
        TourController.advance(TourStep.AUTO_BUTTON)
        assertEquals(TourStep.GUEST_CARD, TourController.currentStep.value)

        // Step 1: Guest card -> Step 2: PWR button
        TourController.advance(TourStep.GUEST_CARD)
        assertEquals(TourStep.PWR_BUTTON, TourController.currentStep.value)

        // Step 2: PWR button -> Step 3: AUTO button
        TourController.advance(TourStep.PWR_BUTTON)
        assertEquals(TourStep.AUTO_BUTTON, TourController.currentStep.value)

        // Step 3: AUTO button -> Step 4: LOG strip
        TourController.advance(TourStep.AUTO_BUTTON)
        assertEquals(TourStep.LOG_STRIP, TourController.currentStep.value)

        // Step 4: LOG strip -> Step 5: SERVER_LAMPS
        TourController.advance(TourStep.LOG_STRIP)
        assertEquals(TourStep.SERVER_LAMPS, TourController.currentStep.value)

        // Step 5: SERVER_LAMPS -> Step 6: CONSOLE
        TourController.advance(TourStep.SERVER_LAMPS)
        assertEquals(TourStep.CONSOLE, TourController.currentStep.value)

        // Step 6: CONSOLE -> Step 7: HISTORY
        TourController.advance(TourStep.CONSOLE)
        assertEquals(TourStep.HISTORY, TourController.currentStep.value)

        // Step 7: HISTORY -> Step 8: NUCLEUS
        TourController.advance(TourStep.HISTORY)
        assertEquals(TourStep.NUCLEUS, TourController.currentStep.value)

        // Step 8: NUCLEUS -> Step 9: PULL_ROCKET
        TourController.advance(TourStep.NUCLEUS)
        assertEquals(TourStep.PULL_ROCKET, TourController.currentStep.value)

        // Step 9: PULL_ROCKET -> Step 10: BEZEL_HOLD
        TourController.advance(TourStep.PULL_ROCKET)
        assertEquals(TourStep.BEZEL_HOLD, TourController.currentStep.value)

        // Step 10: BEZEL_HOLD -> Step 11: SUMMARY
        TourController.advance(TourStep.BEZEL_HOLD)
        assertEquals(TourStep.SUMMARY, TourController.currentStep.value)

        // Step 11: Summary done -> complete tour
        TourController.complete(sessionStore)
        assertNull(TourController.currentStep.value)
        assertTrue(sessionStore.tourCompleted.value)

        // Re-starting when completed should do nothing
        TourController.startTourIfEligible(sessionStore)
        assertNull(TourController.currentStep.value)
    }

    @Test
    fun tourController_pwrAdvance_transitionsToAutoButton() {
        val fakePrefs = FakeSharedPreferences()
        val sessionStore = SessionStore(injectedPrefs = fakePrefs)
        TourController.reset()

        TourController.startTourIfEligible(sessionStore)
        assertEquals(TourStep.GUEST_CARD, TourController.currentStep.value)

        // Advance past guest card to reach PWR step
        TourController.advance(TourStep.GUEST_CARD)
        assertEquals(TourStep.PWR_BUTTON, TourController.currentStep.value)

        // Advancing unrelated step does not skip or corrupt PWR step
        TourController.advance(TourStep.SERVER_LAMPS)
        assertEquals(TourStep.PWR_BUTTON, TourController.currentStep.value)

        // Advancing PWR button transitions to AUTO_BUTTON step
        TourController.advance(TourStep.PWR_BUTTON)
        assertEquals(TourStep.AUTO_BUTTON, TourController.currentStep.value)
    }

    @Test
    fun tourController_skip_marksCompleted() {
        val fakePrefs = FakeSharedPreferences()
        val sessionStore = SessionStore(injectedPrefs = fakePrefs)
        TourController.reset()

        TourController.startTourIfEligible(sessionStore)
        assertEquals(TourStep.GUEST_CARD, TourController.currentStep.value)

        TourController.skip(sessionStore)
        assertNull(TourController.currentStep.value)
        assertTrue(sessionStore.tourCompleted.value)
    }

    @Test
    fun sdnStatusInfo_and_firewallRule_parsing() {
        val sdnOk = SdnStatusInfo.fromMap(
            mapOf("zone" to "vxlan-prod", "type" to "zone", "status" to "ok", "controller" to "evpn-ctrl")
        )
        assertEquals("vxlan-prod", sdnOk.name)
        assertEquals("zone", sdnOk.type)
        assertEquals("evpn-ctrl", sdnOk.controller)
        assertTrue(sdnOk.isOk)

        val sdnErr = SdnStatusInfo.fromMap(
            mapOf("name" to "broken-zone", "status" to "error")
        )
        assertEquals("broken-zone", sdnErr.name)
        assertFalse(sdnErr.isOk)

        val ruleWithLog = FirewallRule.fromMap(
            mapOf("pos" to 1, "type" to "in", "action" to "ACCEPT", "enable" to 1, "log" to "info")
        )
        assertTrue(ruleWithLog.hasLog)
        assertEquals("info", ruleWithLog.log)

        val ruleNoLog = FirewallRule.fromMap(
            mapOf("pos" to 2, "type" to "in", "action" to "DROP", "enable" to 1, "log" to "nolog")
        )
        assertFalse(ruleNoLog.hasLog)
    }

    @Test
    fun tourController_tapPathLambdas_guestDetailPowerPathsAdvance() {
        val fakePrefs = FakeSharedPreferences()
        val sessionStore = SessionStore(injectedPrefs = fakePrefs)
        TourController.reset()
        TourController.startTourIfEligible(sessionStore)
        assertEquals(TourStep.GUEST_CARD, TourController.currentStep.value)

        // Step 1: Guest Card tap
        val onGuestCardTap: () -> Unit = {
            TourController.advance(TourStep.GUEST_CARD)
        }
        onGuestCardTap()
        assertEquals(TourStep.PWR_BUTTON, TourController.currentStep.value)

        // Step 2 path A: onOpenPowerMenu lambda (e.g. from PowerCard header or ⋮ button)
        var powerMenuOpened = false
        val onOpenPowerMenu: () -> Unit = {
            powerMenuOpened = true
            TourController.advance(TourStep.PWR_BUTTON)
        }
        onOpenPowerMenu()
        assertTrue(powerMenuOpened)
        assertEquals(TourStep.AUTO_BUTTON, TourController.currentStep.value)

        // Reset to test Step 2 path B: onDismissRequest lambda
        TourController.reset()
        TourController.startTourIfEligible(sessionStore)
        TourController.advance(TourStep.GUEST_CARD)
        assertEquals(TourStep.PWR_BUTTON, TourController.currentStep.value)

        var powerMenuDismissed = false
        val onPowerMenuDismiss: () -> Unit = {
            powerMenuDismissed = true
            TourController.advance(TourStep.PWR_BUTTON)
        }
        onPowerMenuDismiss()
        assertTrue(powerMenuDismissed)
        assertEquals(TourStep.AUTO_BUTTON, TourController.currentStep.value)

        // Reset to test Step 2 path C: onPower direct action lambda
        TourController.reset()
        TourController.startTourIfEligible(sessionStore)
        TourController.advance(TourStep.GUEST_CARD)
        assertEquals(TourStep.PWR_BUTTON, TourController.currentStep.value)

        var powerActionTriggered = false
        val onPower: (String) -> Unit = { _ ->
            TourController.advance(TourStep.PWR_BUTTON)
            powerActionTriggered = true
        }
        onPower("shutdown")
        assertTrue(powerActionTriggered)
        assertEquals(TourStep.AUTO_BUTTON, TourController.currentStep.value)

        // Step 3: AUTO tap on Home
        val onAutoTap: () -> Unit = {
            TourController.advance(TourStep.AUTO_BUTTON)
        }
        onAutoTap()
        assertEquals(TourStep.LOG_STRIP, TourController.currentStep.value)

        // Step 4: Log strip tap on GuestDetail or Home
        var logsOpened = false
        val onOpenLogs: () -> Unit = {
            TourController.advance(TourStep.LOG_STRIP)
            logsOpened = true
        }
        onOpenLogs()
        assertTrue(logsOpened)
        assertEquals(TourStep.SERVER_LAMPS, TourController.currentStep.value)

        // Step 5: Observational SERVER_LAMPS advance via NEXT button
        TourController.advance(TourStep.SERVER_LAMPS)
        assertEquals(TourStep.CONSOLE, TourController.currentStep.value)

        // Step 6: Observational CONSOLE advance via NEXT button
        TourController.advance(TourStep.CONSOLE)
        assertEquals(TourStep.HISTORY, TourController.currentStep.value)

        // Step 7: Observational HISTORY advance via NEXT button
        TourController.advance(TourStep.HISTORY)
        assertEquals(TourStep.NUCLEUS, TourController.currentStep.value)

        // Step 8: Observational NUCLEUS advance via NEXT button
        TourController.advance(TourStep.NUCLEUS)
        assertEquals(TourStep.PULL_ROCKET, TourController.currentStep.value)

        // Step 9: Observational PULL_ROCKET advance via NEXT button
        TourController.advance(TourStep.PULL_ROCKET)
        assertEquals(TourStep.BEZEL_HOLD, TourController.currentStep.value)

        // Step 10: Observational BEZEL_HOLD advance via NEXT button
        TourController.advance(TourStep.BEZEL_HOLD)
        assertEquals(TourStep.SUMMARY, TourController.currentStep.value)
    }

    @Test
    fun backNavigation_modalOrderingLogic() {
        // Test modal resolution helper: open modal must be handled before screen pop
        var powerMenuOpen = true
        var popBackStackCalled = false

        fun handleBackPress() {
            if (powerMenuOpen) {
                powerMenuOpen = false
            } else {
                popBackStackCalled = true
            }
        }

        // First back press closes power menu
        handleBackPress()
        assertFalse(powerMenuOpen)
        assertFalse(popBackStackCalled)

        // Second back press pops screen
        handleBackPress()
        assertTrue(popBackStackCalled)
    }

    @Test
    fun tourStep_panelPlacement_neverCoversTarget() {
        // Step 1: GUEST_CARD target is guest card in top/middle list -> panel at BOTTOM
        assertEquals(TourPanelPlacement.BOTTOM, TourStep.GUEST_CARD.placement)

        // Step 2: PWR_BUTTON target is app-bar ⋮ menu at top of GuestDetail -> panel at BOTTOM
        assertEquals(TourPanelPlacement.BOTTOM, TourStep.PWR_BUTTON.placement)

        // Step 3: AUTO_BUTTON target is AUTO button in top/middle list -> panel at BOTTOM
        assertEquals(TourPanelPlacement.BOTTOM, TourStep.AUTO_BUTTON.placement)

        // Step 4: LOG_STRIP target is SystemLogStrip at bottom bar -> panel at TOP
        assertEquals(TourPanelPlacement.TOP, TourStep.LOG_STRIP.placement)

        // Step 5: SERVER_LAMPS target is cluster bezel at top -> panel at TOP
        assertEquals(TourPanelPlacement.TOP, TourStep.SERVER_LAMPS.placement)

        // Step 6: CONSOLE target is console button in guest detail -> panel at BOTTOM
        assertEquals(TourPanelPlacement.BOTTOM, TourStep.CONSOLE.placement)

        // Step 7: HISTORY target is history charts -> panel at BOTTOM
        assertEquals(TourPanelPlacement.BOTTOM, TourStep.HISTORY.placement)

        // Step 8: NUCLEUS -> panel at BOTTOM
        assertEquals(TourPanelPlacement.BOTTOM, TourStep.NUCLEUS.placement)

        // Step 9: PULL_ROCKET -> panel at BOTTOM
        assertEquals(TourPanelPlacement.BOTTOM, TourStep.PULL_ROCKET.placement)

        // Step 10: BEZEL_HOLD -> panel at BOTTOM
        assertEquals(TourPanelPlacement.BOTTOM, TourStep.BEZEL_HOLD.placement)

        // Step 11 / SUMMARY panel has internal DONE action -> panel at BOTTOM
        assertEquals(TourPanelPlacement.BOTTOM, TourStep.SUMMARY.placement)
    }

    @Test
    fun tourStep_usesNext_onlyObservationalSteps() {
        assertFalse(TourStep.GUEST_CARD.usesNext)
        assertFalse(TourStep.PWR_BUTTON.usesNext)
        assertFalse(TourStep.AUTO_BUTTON.usesNext)
        assertFalse(TourStep.LOG_STRIP.usesNext)
        assertTrue(TourStep.SERVER_LAMPS.usesNext)
        assertTrue(TourStep.CONSOLE.usesNext)
        assertTrue(TourStep.HISTORY.usesNext)
        assertTrue(TourStep.NUCLEUS.usesNext)
        assertTrue(TourStep.PULL_ROCKET.usesNext)
        assertTrue(TourStep.BEZEL_HOLD.usesNext)
        assertFalse(TourStep.SUMMARY.usesNext)
    }

    @Test
    fun tourController_reset_clearsStateFlow() {
        val fakePrefs = FakeSharedPreferences()
        val sessionStore = SessionStore(injectedPrefs = fakePrefs)

        TourController.startTourIfEligible(sessionStore)
        assertEquals(TourStep.GUEST_CARD, TourController.currentStep.value)

        TourController.advance(TourStep.GUEST_CARD)
        assertEquals(TourStep.PWR_BUTTON, TourController.currentStep.value)

        // Reset must clear the active step back to null
        TourController.reset()
        assertNull(TourController.currentStep.value)
    }

    @Test
    fun opsBoardStore_cleanSlate_purgeHandlesNullContextAndInMemStore() {
        val store = com.pxmx.app.data.ops.OpsBoardStore.create(null)
        assertTrue(store is com.pxmx.app.data.ops.InMemoryOpsBoardStore)
        // purge with null context does not throw
        com.pxmx.app.data.ops.OpsBoardStore.purge(null)

        // Purge via sessionStore with null context does not throw
        val fakePrefs = FakeSharedPreferences()
        val sessionStore = SessionStore(injectedPrefs = fakePrefs)
        sessionStore.purgeAll(context = null)
    }

    @Test
    fun opsBoardStore_purgeFile_deletesFileInDir() {
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "pxmx_test_" + System.currentTimeMillis())
        tempDir.mkdirs()
        try {
            val file = java.io.File(tempDir, com.pxmx.app.data.ops.OpsBoardStore.FILE_NAME)
            file.writeText("{}")
            assertTrue(file.exists())

            val stubContext = object : android.content.ContextWrapper(null) {
                override fun getFilesDir(): java.io.File = tempDir
            }
            com.pxmx.app.data.ops.OpsBoardStore.purge(stubContext)
            assertFalse("Ops board file should be deleted on purge", file.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}

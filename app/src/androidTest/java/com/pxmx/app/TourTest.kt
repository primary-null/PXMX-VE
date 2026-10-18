package com.pxmx.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.pxmx.app.ui.tour.TourController
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TourTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<ProxmoxApp>()
        app.sessionStore.clearSession()
        app.sessionStore.listProfiles().forEach { app.sessionStore.deleteProfile(it.id) }
        app.sessionStore.setAutoConnect(false)
        app.sessionStore.clearPreviousSession()
        app.sessionStore.resetTour()
        TourController.reset()
    }

    private fun waitForLoginScreen() {
        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodesWithText("Connect your hypervisor").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun performFakeLogin() {
        waitForLoginScreen()
        composeTestRule.onNode(hasSetTextAction() and hasText("Host / IP", substring = true))
            .performScrollTo()
            .performTextReplacement("fake.local")
        composeTestRule.onNode(hasSetTextAction() and hasText("Username", substring = true))
            .performScrollTo()
            .performTextReplacement("root@pam")
        composeTestRule.onNode(hasSetTextAction() and (hasText("Password", substring = true) or hasText("••••••••")))
            .performScrollTo()
            .performTextReplacement("password")
        composeTestRule.onNodeWithText("Connect").performScrollTo().performClick()

        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodesWithText("GUESTS").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun dismissTour() {
        if (composeTestRule.onAllNodesWithText("SKIP").fetchSemanticsNodes().isEmpty()) {
            try {
                composeTestRule.waitUntil(1000) {
                    composeTestRule.onAllNodesWithText("SKIP").fetchSemanticsNodes().isNotEmpty()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Throwable) {
                return
            }
        }
        composeTestRule.onNodeWithText("SKIP").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("QUICK TOUR", substring = true).fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.waitForIdle()
    }

    private fun openOverflowMenu() {
        composeTestRule.onNodeWithTag("tech_action_menu").performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun waitForText(text: String, timeoutMillis: Long = 5_000) {
        composeTestRule.waitUntil(timeoutMillis) {
            composeTestRule.onAllNodesWithText(text, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun splashShowsThenLoginForm() {
        waitForLoginScreen()
        composeTestRule.onNodeWithText("PXMX").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connect your hypervisor").assertIsDisplayed()
        composeTestRule.onNode(hasSetTextAction() and hasText("Host / IP", substring = true))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNode(hasSetTextAction() and hasText("Username", substring = true))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNode(hasSetTextAction() and (hasText("Password", substring = true) or hasText("••••••••")))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Connect").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun loginValidation_emptyShowsError() {
        waitForLoginScreen()
        composeTestRule.onNode(hasSetTextAction() and hasText("Host / IP", substring = true))
            .performScrollTo()
            .performTextReplacement("")
        composeTestRule.onNodeWithText("Connect").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Host is required").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun fiveTapsDemoMode_entersDashboardWithDemoBadge() {
        waitForLoginScreen()
        // Hidden 5-tap gesture on PXMX title triggers demo mode
        repeat(5) {
            composeTestRule.onNodeWithText("PXMX").performClick()
        }

        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodesWithText("GUESTS").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("DEMO").assertIsDisplayed()
        composeTestRule.onNodeWithText("ALL").assertIsDisplayed()
        composeTestRule.onNodeWithText("GUESTS").assertIsDisplayed()
    }

    @Test
    fun loginReachesDashboard() {
        performFakeLogin()
        dismissTour()

        // Check header wordmark and subtitle connection context
        composeTestRule.onNodeWithText("PXMX").assertIsDisplayed()
        // Compact mastheads intentionally omit the host while retaining version context.
        composeTestRule.onNodeWithText("PVE 8.3.0", substring = true).assertIsDisplayed()

        // Check tabs
        composeTestRule.onNodeWithText("ALL").assertIsDisplayed()
        composeTestRule.onNodeWithText("GUESTS").assertIsDisplayed()
        composeTestRule.onNodeWithText("NODES").assertIsDisplayed()
        composeTestRule.onNodeWithText("STORAGE").assertIsDisplayed()

        // Check guests in list (ResourceCard uses original case for display name)
        composeTestRule.onNodeWithText("nova").assertIsDisplayed()
        composeTestRule.onNodeWithText("nebula").assertIsDisplayed()
    }

    @Test
    fun dashboardTabs_renderWithoutCrash() {
        performFakeLogin()

        // 1. GUESTS Tab (default)
        composeTestRule.onNodeWithText("GUESTS").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("nova").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("nova").assertIsDisplayed()
        composeTestRule.onNodeWithText("nebula").assertIsDisplayed()

        // 2. NODES Tab (shows 3 nodes)
        composeTestRule.onNodeWithText("NODES").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("ALPHA").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("ALPHA").assertIsDisplayed()
        composeTestRule.onNodeWithText("BETA").assertIsDisplayed()
        composeTestRule.onNodeWithText("GAMMA").assertIsDisplayed()

        // 3. STORAGE Tab (shows storage pools)
        composeTestRule.onNodeWithText("STORAGE").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("LOCAL-LVM").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithText("LOCAL").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("LOCAL-LVM").onFirst().assertIsDisplayed()

        // 4. ALL Tab
        composeTestRule.onNodeWithText("ALL").performClick()
        composeTestRule.onNodeWithText("ALL").assertIsDisplayed()
    }

    @Test
    fun searchFiltersList() {
        performFakeLogin()

        composeTestRule.onNodeWithText("Search name · node · vmid · tag").performTextInput("200")

        composeTestRule.onNodeWithText("nebula").assertIsDisplayed()
        composeTestRule.onNodeWithText("nova").assertDoesNotExist()
    }

    @Test
    fun guestCard_showsPowerRow_andLongPressOpensMenu() {
        performFakeLogin()
        dismissTour()

        // Quick action row elements
        composeTestRule.onAllNodesWithText("PWR").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("RST").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("AUTO").onFirst().assertIsDisplayed()

        // Long press PWR to open power menu
        composeTestRule.onAllNodesWithContentDescription("Power").onFirst().performTouchInput {
            longClick()
        }

        // TechMenuItem renders labels in uppercase.
        waitForText("Shutdown")
        composeTestRule.onNodeWithText("Shutdown", ignoreCase = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Reboot", ignoreCase = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Stop", ignoreCase = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Reset", ignoreCase = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Suspend", ignoreCase = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Auto-start", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun runningGuestDetail_showsSectionsAndActions() {
        performFakeLogin()
        dismissTour()

        composeTestRule.onNode(hasText("nova") and !hasSetTextAction()).performClick()

        // Detail screen header & state
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("RUNNING").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithText("nova").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("RUNNING").assertIsDisplayed()

        // SubcomposeLayout also measures an unplaced copy of the bezel.
        // Require a visible control without confusing it with that measurement copy.
        listOf("hw", "net", "opt", "snap", "bkp").forEach { slug ->
            val scrollable = composeTestRule.onAllNodes(
                hasTestTag("guest_bezel_$slug") and hasAnyAncestor(hasScrollAction())
            )
            if (scrollable.fetchSemanticsNodes().isNotEmpty()) {
                // Narrow screens intentionally scroll the bezel horizontally.
                scrollable.onFirst().performScrollTo().assertIsDisplayed()
                return@forEach
            }
            val nodes = composeTestRule.onAllNodesWithTag("guest_bezel_$slug")
            val count = nodes.fetchSemanticsNodes().size
            org.junit.Assert.assertTrue("Visible $slug bezel is missing", (0 until count).any { nodes[it].isDisplayed() })
        }

        // Power actions (check presence after opening Actions menu)
        composeTestRule.onNodeWithContentDescription("Actions").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Shutdown", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Shutdown", ignoreCase = true).assertExists()
        composeTestRule.onNodeWithText("Reboot", ignoreCase = true).assertExists()
        composeTestRule.onNodeWithText("Stop", ignoreCase = true).assertExists()
    }

    @Test
    fun stoppedGuest_offersStart() {
        performFakeLogin()
        dismissTour()

        composeTestRule.onNodeWithText("Search name · node · vmid · tag").performTextInput("meteor")
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("meteor").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(hasText("meteor") and !hasSetTextAction()).performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("STOPPED").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithText("meteor").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("STOPPED").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Actions").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Start", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Start", ignoreCase = true).assertExists()
        composeTestRule.onNodeWithText("Shutdown", ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun menu_deployFromTemplate_opensDialog() {
        performFakeLogin()
        dismissTour()

        openOverflowMenu()
        waitForText("Deploy from template")
        composeTestRule.onNodeWithText("Deploy from template", ignoreCase = true).performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Deploy", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Deploy", ignoreCase = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
    }

    @Test
    fun menu_allServers_showsOverview() {
        performFakeLogin()
        dismissTour()

        openOverflowMenu()
        waitForText("All servers")
        composeTestRule.onNodeWithText("All servers", ignoreCase = true).performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Servers").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Servers").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("PXMX").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("PXMX").assertIsDisplayed()
    }

    @Test
    fun bottomLogStrip_opensLogScreen() {
        performFakeLogin()

        // Wait for system log entry to be visible and tap it
        composeTestRule.waitUntil(8000) {
            composeTestRule.onAllNodesWithText("pmxcfs", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("pmxcfs", substring = true).performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Logs").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Logs").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("PXMX").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("PXMX").assertIsDisplayed()
    }

    @Test
    fun guestConsole_inDemoMode_showsGracefulError() {
        performFakeLogin()
        dismissTour()

        composeTestRule.onNode(hasText("nova") and !hasSetTextAction()).performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("RUNNING").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasText("OPEN CONSOLE"))
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("OPEN CONSOLE").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("OPEN CONSOLE").performClick()

        // In fake/demo mode without websocket proxy, shows graceful error view with Retry and Back
        composeTestRule.waitUntil(8000) {
            composeTestRule.onAllNodesWithText("RETRY").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("RETRY").assertIsDisplayed()
        composeTestRule.onNodeWithText("BACK").performClick()

        // Back on detail
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("nova").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithText("nova").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasText("OPEN CONSOLE"))
        composeTestRule.onNodeWithText("OPEN CONSOLE").assertIsDisplayed()
    }

    @Test
    fun tfaFlow_promptsOtpAndConnects() {
        waitForLoginScreen()
        composeTestRule.onNode(hasSetTextAction() and hasText("Host / IP", substring = true))
            .performScrollTo()
            .performTextReplacement("demo")
        composeTestRule.onNode(hasSetTextAction() and hasText("Username", substring = true))
            .performScrollTo()
            .performTextReplacement("tfa-user")
        composeTestRule.onNode(hasSetTextAction() and (hasText("Password", substring = true) or hasText("••••••••")))
            .performScrollTo()
            .performTextReplacement("x")
        composeTestRule.onNodeWithText("Connect").performScrollTo().performClick()

        // TFA screen prompt
        composeTestRule.waitUntil(8000) {
            composeTestRule.onAllNodesWithText("Two-Factor Authentication").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Demo hint: code is 123456", substring = true).assertIsDisplayed()

        // Enter OTP
        composeTestRule.onNode(hasSetTextAction() and (hasText("6-digit code") or hasText("123456")))
            .performScrollTo()
            .performTextReplacement("123456")
        composeTestRule.onNodeWithText("Verify & Connect").performScrollTo().performClick()

        // Reaches dashboard
        composeTestRule.waitUntil(8000) {
            composeTestRule.onAllNodesWithText("GUESTS").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("PXMX").assertIsDisplayed()
    }

    @Test
    fun tasksScreen_listsTasks() {
        performFakeLogin()
        dismissTour()

        // Injected taps on the top bar can be swallowed by a window layer in
        // instrumented runs; invoke the plate's own click action directly.
        composeTestRule.onNodeWithText("TASKS").performSemanticsAction(SemanticsActions.OnClick)

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("VZDUMP").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithText("VZDUMP").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("APTUPDATE").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("OK").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("RUNNING").onFirst().assertIsDisplayed()
    }

    @Test
    fun logoutReturnsToLogin() {
        performFakeLogin()
        dismissTour()

        openOverflowMenu()
        waitForText("Switch account")
        composeTestRule.onNodeWithText("Switch account", ignoreCase = true).performClick()

        // Back to login form
        waitForLoginScreen()
        composeTestRule.onNode(hasSetTextAction() and hasText("Host / IP", substring = true))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Connect your hypervisor").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connect").performScrollTo().assertIsDisplayed()
    }
}

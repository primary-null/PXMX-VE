package com.pxmx.app

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

        // Check header wordmark and subtitle connection context
        composeTestRule.onNodeWithText("PXMX").assertIsDisplayed()
        composeTestRule.onNodeWithText("fake.local:8006 · PVE 8.3.0", substring = true).assertIsDisplayed()

        // Check tabs
        composeTestRule.onNodeWithText("ALL").assertIsDisplayed()
        composeTestRule.onNodeWithText("GUESTS").assertIsDisplayed()
        composeTestRule.onNodeWithText("NODES").assertIsDisplayed()
        composeTestRule.onNodeWithText("STORAGE").assertIsDisplayed()

        // Check guests in list (ResourceCard uses uppercase for display name)
        composeTestRule.onNodeWithText("NOVA").assertIsDisplayed()
        composeTestRule.onNodeWithText("NEBULA").assertIsDisplayed()
    }

    @Test
    fun dashboardTabs_renderWithoutCrash() {
        performFakeLogin()

        // 1. GUESTS Tab (default)
        composeTestRule.onNodeWithText("GUESTS").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("NOVA").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("NOVA").assertIsDisplayed()
        composeTestRule.onNodeWithText("NEBULA").assertIsDisplayed()

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

        composeTestRule.onNodeWithText("NEBULA").assertIsDisplayed()
        composeTestRule.onNodeWithText("NOVA").assertDoesNotExist()
    }

    @Test
    fun guestCard_showsPowerRow_andLongPressOpensMenu() {
        performFakeLogin()

        // Quick action row elements
        composeTestRule.onAllNodesWithText("PWR").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("RST").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("AUTO").onFirst().assertIsDisplayed()

        // Long press PWR to open power menu
        composeTestRule.onAllNodesWithText("PWR").onFirst().performTouchInput {
            longClick()
        }

        // Verify power dropdown options
        composeTestRule.onNodeWithText("Shutdown").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reboot").assertIsDisplayed()
        composeTestRule.onNodeWithText("Stop").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reset").assertIsDisplayed()
        composeTestRule.onNodeWithText("Suspend").assertIsDisplayed()
        composeTestRule.onNodeWithText("Auto-start on boot").assertIsDisplayed()
    }

    @Test
    fun runningGuestDetail_showsSectionsAndActions() {
        performFakeLogin()

        composeTestRule.onNodeWithText("NOVA").performClick()

        // Detail screen header & state
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("RUNNING").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("NOVA").assertIsDisplayed()
        composeTestRule.onNodeWithText("RUNNING").assertIsDisplayed()

        // Detail sections
        composeTestRule.onNodeWithText("HW").assertIsDisplayed()
        composeTestRule.onNodeWithText("NET").assertIsDisplayed()
        composeTestRule.onNodeWithText("OPT").assertIsDisplayed()
        composeTestRule.onNodeWithText("SNAP").assertIsDisplayed()
        composeTestRule.onNodeWithText("BKP").assertIsDisplayed()

        // Power actions (check presence)
        composeTestRule.onNodeWithText("Shutdown", ignoreCase = true).assertExists()
        composeTestRule.onNodeWithText("Reboot", ignoreCase = true).assertExists()
        composeTestRule.onNodeWithText("Stop", ignoreCase = true).assertExists()
    }

    @Test
    fun stoppedGuest_offersStart() {
        performFakeLogin()

        composeTestRule.onNodeWithText("Search name · node · vmid · tag").performTextInput("meteor")
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("METEOR").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("METEOR").performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("STOPPED").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("METEOR").assertIsDisplayed()
        composeTestRule.onNodeWithText("STOPPED").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start", ignoreCase = true).assertExists()
        composeTestRule.onNodeWithText("Shutdown", ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun menu_deployFromTemplate_opensDialog() {
        performFakeLogin()

        composeTestRule.onNodeWithText("MENU").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Deploy from template").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Deploy from template").performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Deploy", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Deploy").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
    }

    @Test
    fun menu_allServers_showsOverview() {
        performFakeLogin()

        composeTestRule.onNodeWithText("MENU").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("All servers").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("All servers").performClick()

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

        composeTestRule.onNodeWithText("NOVA").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("OPEN CONSOLE").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("OPEN CONSOLE").performClick()

        // In fake/demo mode without websocket proxy, shows graceful error view with Retry and Back
        composeTestRule.waitUntil(8000) {
            composeTestRule.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
        composeTestRule.onNodeWithText("Back").performClick()

        // Back on detail
        composeTestRule.onNodeWithText("NOVA").assertIsDisplayed()
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

        composeTestRule.onNodeWithText("TASKS").performClick()

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

        composeTestRule.onNodeWithText("MENU").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Switch account").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Switch account").performClick()

        // Back to login form
        waitForLoginScreen()
        composeTestRule.onNode(hasSetTextAction() and hasText("Host / IP", substring = true))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Connect your hypervisor").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connect").performScrollTo().assertIsDisplayed()
    }
}

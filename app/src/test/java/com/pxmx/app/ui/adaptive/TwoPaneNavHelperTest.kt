package com.pxmx.app.ui.adaptive

import com.pxmx.app.ui.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoPaneNavHelperTest {

    @Test
    fun testResolveRedirectWhenSettingsPresentInBackStack() {
        val action = resolveTwoPaneToolRedirect(
            settingsPresentInBackStack = true,
            toolRoute = Routes.NETWORK,
        )
        assertTrue("Expected PopBackStack action", action is TwoPaneRedirectAction.PopBackStack)
        val pop = action as TwoPaneRedirectAction.PopBackStack
        assertEquals(Routes.SETTINGS, pop.targetRoute)
        assertFalse(pop.inclusive)
    }

    @Test
    fun testResolveRedirectWhenSettingsMissingFromBackStack() {
        val toolRoutes = listOf(
            Routes.NETWORK,
            Routes.SDN,
            Routes.FIREWALL,
            Routes.NOTIFICATIONS,
            Routes.SERVER_LAMPS,
        )

        for (route in toolRoutes) {
            val action = resolveTwoPaneToolRedirect(
                settingsPresentInBackStack = false,
                toolRoute = route,
            )
            assertTrue("Expected ReplaceRoute action for $route", action is TwoPaneRedirectAction.ReplaceRoute)
            val replace = action as TwoPaneRedirectAction.ReplaceRoute
            assertEquals(Routes.SETTINGS, replace.targetRoute)
            assertEquals(route, replace.popUpToRoute)
            assertTrue("popUpTo should be inclusive", replace.inclusive)
            assertTrue("launchSingleTop should be true", replace.launchSingleTop)
        }
    }

    @Test
    fun testExecuteRedirectDispatchesPopWhenSettingsPresent() {
        var popBackStackCalledWithRoute: String? = null
        var popBackStackCalledWithInclusive: Boolean? = null
        var navigateReplaceCalled = false

        val action = executeTwoPaneToolRedirect(
            popBackStack = { route, inclusive ->
                popBackStackCalledWithRoute = route
                popBackStackCalledWithInclusive = inclusive
                true // Settings was on the back stack
            },
            navigateReplace = { _, _, _, _ ->
                navigateReplaceCalled = true
            },
            toolRoute = Routes.NETWORK,
        )

        assertEquals(Routes.SETTINGS, popBackStackCalledWithRoute)
        assertEquals(false, popBackStackCalledWithInclusive)
        assertFalse("navigateReplace must not be called when pop succeeds", navigateReplaceCalled)
        assertTrue(action is TwoPaneRedirectAction.PopBackStack)
    }

    @Test
    fun testExecuteRedirectDispatchesReplaceWhenSettingsMissing() {
        var popBackStackCalledWithRoute: String? = null
        var popBackStackCalledWithInclusive: Boolean? = null
        var replacedTarget: String? = null
        var replacedPopUpTo: String? = null
        var replacedInclusive: Boolean? = null
        var replacedSingleTop: Boolean? = null

        val action = executeTwoPaneToolRedirect(
            popBackStack = { route, inclusive ->
                popBackStackCalledWithRoute = route
                popBackStackCalledWithInclusive = inclusive
                false // Settings was NOT on the back stack
            },
            navigateReplace = { target, popUpTo, inclusive, launchSingleTop ->
                replacedTarget = target
                replacedPopUpTo = popUpTo
                replacedInclusive = inclusive
                replacedSingleTop = launchSingleTop
            },
            toolRoute = Routes.NOTIFICATIONS,
        )

        assertEquals(Routes.SETTINGS, popBackStackCalledWithRoute)
        assertEquals(false, popBackStackCalledWithInclusive)
        assertEquals(Routes.SETTINGS, replacedTarget)
        assertEquals(Routes.NOTIFICATIONS, replacedPopUpTo)
        assertEquals(true, replacedInclusive)
        assertEquals(true, replacedSingleTop)
        assertTrue(action is TwoPaneRedirectAction.ReplaceRoute)
    }
}

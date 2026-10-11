package com.pxmx.app.ui.adaptive

import androidx.navigation.NavController
import com.pxmx.app.ui.navigation.Routes

sealed interface TwoPaneRedirectAction {
    data class PopBackStack(
        val targetRoute: String,
        val inclusive: Boolean = false,
    ) : TwoPaneRedirectAction

    data class ReplaceRoute(
        val targetRoute: String,
        val popUpToRoute: String,
        val inclusive: Boolean = true,
        val launchSingleTop: Boolean = true,
    ) : TwoPaneRedirectAction
}

fun resolveTwoPaneToolRedirect(
    settingsPresentInBackStack: Boolean,
    toolRoute: String,
    targetRoute: String = Routes.SETTINGS,
): TwoPaneRedirectAction = if (settingsPresentInBackStack) {
    TwoPaneRedirectAction.PopBackStack(
        targetRoute = targetRoute,
        inclusive = false,
    )
} else {
    TwoPaneRedirectAction.ReplaceRoute(
        targetRoute = targetRoute,
        popUpToRoute = toolRoute,
        inclusive = true,
        launchSingleTop = true,
    )
}

fun executeTwoPaneToolRedirect(
    popBackStack: (targetRoute: String, inclusive: Boolean) -> Boolean,
    navigateReplace: (targetRoute: String, popUpToRoute: String, inclusive: Boolean, launchSingleTop: Boolean) -> Unit,
    toolRoute: String,
    targetRoute: String = Routes.SETTINGS,
): TwoPaneRedirectAction {
    val popped = popBackStack(targetRoute, false)
    val action = resolveTwoPaneToolRedirect(popped, toolRoute, targetRoute)
    if (action is TwoPaneRedirectAction.ReplaceRoute) {
        navigateReplace(
            action.targetRoute,
            action.popUpToRoute,
            action.inclusive,
            action.launchSingleTop,
        )
    }
    return action
}

fun NavController.redirectTwoPaneTool(
    toolRoute: String,
    targetRoute: String = Routes.SETTINGS,
): TwoPaneRedirectAction = executeTwoPaneToolRedirect(
    popBackStack = { route, inclusive -> popBackStack(route, inclusive) },
    navigateReplace = { target, popUpTo, inclusive, launchSingleTop ->
        navigate(target) {
            popUpTo(popUpTo) { this.inclusive = inclusive }
            this.launchSingleTop = launchSingleTop
        }
    },
    toolRoute = toolRoute,
    targetRoute = targetRoute,
)

package com.pxmx.app.ui.tour

import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TourPanelPlacement {
    TOP,
    BOTTOM,
}

enum class TourStep(
    val stepNumber: Int,
    val title: String,
    val instruction: String,
    val placement: TourPanelPlacement = TourPanelPlacement.BOTTOM,
    val usesNext: Boolean = false,
) {
    GUEST_CARD(
        stepNumber = 1,
        title = "GUEST DETAIL",
        instruction = "Tap any guest card to open its detail.",
        placement = TourPanelPlacement.BOTTOM,
        usesNext = false,
    ),
    PWR_BUTTON(
        stepNumber = 2,
        title = "POWER ACTIONS",
        instruction = "Tap the ⋮ menu for power actions.",
        placement = TourPanelPlacement.BOTTOM,
        usesNext = false,
    ),
    AUTO_BUTTON(
        stepNumber = 3,
        title = "AUTO-START",
        instruction = "AUTO arms the guest to start on boot.",
        placement = TourPanelPlacement.BOTTOM,
        usesNext = false,
    ),
    LOG_STRIP(
        stepNumber = 4,
        title = "CLUSTER LOG",
        instruction = "The log strip at the bottom opens the full cluster log.",
        placement = TourPanelPlacement.TOP,
        usesNext = false,
    ),
    SERVER_LAMPS(
        stepNumber = 5,
        title = "SERVER LAMPS",
        instruction = "One lamp per host. Solid green is idle. Pulse is load. Amber is high. Set the percents in Settings.",
        placement = TourPanelPlacement.TOP,
        usesNext = true,
    ),
    CONSOLE(
        stepNumber = 6,
        title = "GUEST CONSOLE",
        instruction = "OPEN CONSOLE on a guest is a live noVNC session through the app pin.",
        placement = TourPanelPlacement.BOTTOM,
        usesNext = true,
    ),
    HISTORY(
        stepNumber = 7,
        title = "PERFORMANCE HISTORY",
        instruction = "Guest and node HISTORY charts hour and day, including host memory and pressure.",
        placement = TourPanelPlacement.BOTTOM,
        usesNext = true,
    ),
    NUCLEUS(
        stepNumber = 8,
        title = "NUCLEUS",
        instruction = "Settings, Nucleus. The cluster sits around a core you can turn.",
        placement = TourPanelPlacement.BOTTOM,
        usesNext = true,
    ),
    PULL_ROCKET(
        stepNumber = 9,
        title = "PULL TO REFRESH",
        instruction = "Pull the home list. The rocket holds, then lifts.",
        placement = TourPanelPlacement.BOTTOM,
        usesNext = true,
    ),
    BEZEL_HOLD(
        stepNumber = 10,
        title = "SECTION STRIP",
        instruction = "Hold the guest strip and slide. The tab under your finger comes forward.",
        placement = TourPanelPlacement.BOTTOM,
        usesNext = true,
    ),
    SUMMARY(
        stepNumber = 11,
        title = "HIDDEN EXTRAS",
        instruction = "Hidden extras: long-press PWR for shutdown, reboot, suspend. Five taps on the PXMX title at login unlocks demo mode. Creator link lives at the bottom of Settings.",
        placement = TourPanelPlacement.BOTTOM,
        usesNext = false,
    );

    val next: TourStep?
        get() = when (this) {
            GUEST_CARD -> PWR_BUTTON
            PWR_BUTTON -> AUTO_BUTTON
            AUTO_BUTTON -> LOG_STRIP
            LOG_STRIP -> SERVER_LAMPS
            SERVER_LAMPS -> CONSOLE
            CONSOLE -> HISTORY
            HISTORY -> NUCLEUS
            NUCLEUS -> PULL_ROCKET
            PULL_ROCKET -> BEZEL_HOLD
            BEZEL_HOLD -> SUMMARY
            SUMMARY -> null
        }
}

object TourController {
    private val _currentStep = MutableStateFlow<TourStep?>(null)
    val currentStep: StateFlow<TourStep?> = _currentStep.asStateFlow()

    fun startTourIfEligible(sessionStore: SessionStore) {
        if (!sessionStore.tourCompleted.value && _currentStep.value == null) {
            _currentStep.value = TourStep.GUEST_CARD
        }
    }

    fun advance(completedStep: TourStep) {
        if (_currentStep.value == completedStep) {
            _currentStep.value = completedStep.next
        }
    }

    fun skip(sessionStore: SessionStore) {
        sessionStore.markTourCompleted()
        _currentStep.value = null
    }

    fun complete(sessionStore: SessionStore) {
        sessionStore.markTourCompleted()
        _currentStep.value = null
    }

    fun reset() {
        _currentStep.value = null
    }
}

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
) {
    GUEST_CARD(
        stepNumber = 1,
        title = "GUEST DETAIL",
        instruction = "Tap any guest card to open its detail.",
        placement = TourPanelPlacement.BOTTOM,
    ),
    PWR_BUTTON(
        stepNumber = 2,
        title = "POWER ACTIONS",
        instruction = "Tap PWR to see the power menu.",
        placement = TourPanelPlacement.TOP,
    ),
    AUTO_BUTTON(
        stepNumber = 3,
        title = "AUTO-START",
        instruction = "AUTO arms the guest to start on boot.",
        placement = TourPanelPlacement.BOTTOM,
    ),
    LOG_STRIP(
        stepNumber = 4,
        title = "CLUSTER LOG",
        instruction = "The log strip at the bottom opens the full cluster log.",
        placement = TourPanelPlacement.TOP,
    ),
    MENU_BUTTON(
        stepNumber = 5,
        title = "CLUSTER MENU",
        instruction = "MENU holds Updates and Open in browser.",
        placement = TourPanelPlacement.BOTTOM,
    ),
    SUMMARY(
        stepNumber = 6,
        title = "HIDDEN EXTRAS",
        instruction = "Hidden extras: long-press PWR for shutdown, reboot, suspend. Five taps on the PXMX title at login unlocks demo mode. Creator link lives at the bottom of Settings.",
        placement = TourPanelPlacement.BOTTOM,
    );

    val next: TourStep?
        get() = when (this) {
            GUEST_CARD -> PWR_BUTTON
            PWR_BUTTON -> AUTO_BUTTON
            AUTO_BUTTON -> LOG_STRIP
            LOG_STRIP -> MENU_BUTTON
            MENU_BUTTON -> SUMMARY
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

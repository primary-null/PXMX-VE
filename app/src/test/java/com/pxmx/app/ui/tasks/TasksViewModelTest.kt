package com.pxmx.app.ui.tasks

import android.content.ContextWrapper
import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.api.DemoApi
import com.pxmx.app.data.api.ProbeApi
import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.api.ProxmoxApiProvider
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelTest {

    private lateinit var sessionStore: SessionStore
    private lateinit var repository: ProxmoxRepository
    private lateinit var demoApi: DemoApi

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        demoApi = DemoApi()
        val fakePrefs = FakeSharedPreferences()
        sessionStore = SessionStore(injectedPrefs = fakePrefs)
        sessionStore.setSession(
            SessionState(
                config = ServerConfig(host = "192.168.1.10", port = 8006),
                ticket = "ticket",
                csrf = "csrf",
            )
        )
        val apiProvider = object : ProxmoxApiProvider {
            override fun apiFor(config: ServerConfig): ProxmoxApi = demoApi
            override fun apiForProbe(config: ServerConfig): ProbeApi = ProbeApi(demoApi)
            override fun clear() {}
        }
        repository = ProxmoxRepository(
            context = ContextWrapper(null),
            sessionStore = sessionStore,
            clientFactory = apiProvider,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_loadsTasksSortedByStartTimeDesc() {
        val vm = TasksViewModel(repository)
        val state = vm.ui.value

        assertFalse(state.loading)
        assertFalse(state.refreshing)
        assertNull(state.error)
        assertTrue("Tasks list should not be empty", state.tasks.isNotEmpty())

        // Verify sorted descending by startTime
        val startTimes = state.tasks.map { it.startTime }
        assertEquals(startTimes.sortedDescending(), startTimes)

        // Verify task fields mapping
        val first = state.tasks.first()
        assertTrue(first.upid.startsWith("UPID:"))
        assertTrue(first.node.isNotBlank())
        assertTrue(first.type.isNotBlank())
    }

    @Test
    fun refresh_updatesTasksSuccessfully() {
        val vm = TasksViewModel(repository)
        vm.refresh()

        val state = vm.ui.value
        assertFalse(state.loading)
        assertFalse(state.refreshing)
        assertNull(state.error)
        assertTrue(state.tasks.isNotEmpty())
    }
}

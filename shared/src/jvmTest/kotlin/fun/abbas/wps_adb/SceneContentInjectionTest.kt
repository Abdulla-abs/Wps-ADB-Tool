package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.MockAdbRepository
import `fun`.abbas.wps_adb.model.NavTab
import `fun`.abbas.wps_adb.viewmodel.AppViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SceneContentInjectionTest {

    @Test
    fun sceneContentSlot_adapterInjectsViewModelCorrectly() {
        val repo = MockAdbRepository()
        val vm = AppViewModel(repo)
        var receivedVm: AppViewModel? = null

        val slot: (AppViewModel) -> Unit = { injected ->
            receivedVm = injected
        }

        // Mirrors the transformation logic inside App.kt
        val adaptedContent: (() -> Unit)? = slot.let { content -> { content(vm) } }

        assertNotNull(adaptedContent)
        assertNull(receivedVm)

        adaptedContent.invoke()
        assertEquals(vm, receivedVm)
    }

    @Test
    fun sceneContentSlot_whenNull_remainsNull() {
        val nullSlot: ((AppViewModel) -> Unit)? = null
        val repo = MockAdbRepository()
        val vm = AppViewModel(repo)

        val adaptedContent: (() -> Unit)? = nullSlot?.let { content -> { content(vm) } }
        assertNull(adaptedContent)
    }
}

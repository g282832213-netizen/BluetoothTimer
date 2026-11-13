package com.example.bluetoothtimer

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Spinner
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LayoutTest {
    private fun views(v: View): List<View> =
        listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    @Test fun threePresetsSurviveRecreationAndButtonsHaveUsableHeight() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant com.example.bluetoothtimer android.permission.POST_NOTIFICATIONS").use { fd ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
            }
        ctx.getSharedPreferences("presets", 0).edit().clear().commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val all = views(activity.window.decorView)
                val inputs = all.filterIsInstance<EditText>()
                assertEquals(3, inputs.size)
                val minHeight = (48 * activity.resources.displayMetrics.density).toInt()
                for (input in inputs) assertTrue("Input clipped", input.height >= minHeight)
                for (button in all.filterIsInstance<Button>())
                    assertTrue("Button clipped", button.height >= minHeight)
                inputs[0].setText("1"); inputs[1].setText("23"); inputs[2].setText("45")
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(listOf("1", "23", "45"),
                    views(activity.window.decorView).filterIsInstance<EditText>().map { it.text.toString() })
                views(activity.window.decorView).filterIsInstance<Spinner>().single().setSelection(1)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val inputs = views(activity.window.decorView).filterIsInstance<EditText>()
                inputs[1].setText("12")
                views(activity.window.decorView).filterIsInstance<Spinner>().single().setSelection(0)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(listOf("1", "23", "45"),
                    views(activity.window.decorView).filterIsInstance<EditText>().map { it.text.toString() })
            }
        }
    }
}

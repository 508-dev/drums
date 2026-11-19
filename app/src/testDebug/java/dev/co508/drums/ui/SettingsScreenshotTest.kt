package dev.co508.drums.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.co508.drums.settings.AppDefaults
import dev.co508.drums.ui.settings.AboutContent
import dev.co508.drums.ui.settings.LicensesContent
import dev.co508.drums.ui.settings.SettingsContent
import dev.co508.drums.ui.settings.licenseProjects
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun controlsAndOffset() {
        compose.setContent {
            var defaults by remember { mutableStateOf(AppDefaults()) }
            MaterialTheme { SettingsContent(defaults, true, { defaults = it }, {}) }
        }
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/settings-portrait.png")
        compose.onNodeWithContentDescription("Default click").performClick().assertIsOff()
        compose.onNodeWithText("+5 ms").performScrollTo().performClick()
        compose.onNodeWithText("Sound/visual offset: 5 ms").assertIsDisplayed()
        compose.onNodeWithText("Reset").performClick()
        compose.onNodeWithText("Sound/visual offset: 0 ms").assertIsDisplayed()
    }

    @Test fun about() {
        compose.setContent { MaterialTheme { AboutContent("test-version", {}) } }
        compose.onNodeWithText("Ratamatap").assertIsDisplayed()
        compose.onNodeWithText("Source: https://github.com/508-dev/ratamatap").assertIsDisplayed()
        compose.onNodeWithText("Version test-version").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/settings-about.png")
    }

    @Test fun licensesAreBundledAndReadableOffline() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (project in licenseProjects) {
            val text =
                context.assets
                    .open(project.file)
                    .bufferedReader()
                    .use { it.readText() }
            assertTrue("Missing legal text for ${project.title}", text.length > 1000 && !text.contains("<html"))
        }
        compose.setContent { MaterialTheme { LicensesContent() } }
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/settings-licenses.png")
        compose.onNodeWithText("Read SIL Open Font License 1.1").performClick()
        compose.onNodeWithText("Hide license").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun landscapeControlsRemainReachable() {
        compose.setContent { MaterialTheme { SettingsContent(AppDefaults(), true, {}, {}) } }
        compose.onNodeWithText("About").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/settings-landscape.png")
    }
}

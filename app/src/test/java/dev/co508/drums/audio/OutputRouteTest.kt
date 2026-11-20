package dev.co508.drums.audio

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class OutputRouteTest {
    @Test
    fun routesGroupByHowTheyDelaySound() {
        val speaker = OutputRoute.of(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Pixel")
        assertEquals("speaker", speaker.key)
        assertEquals("Phone speaker", speaker.label)
        // Any wired headphones behave alike.
        assertEquals("wired", OutputRoute.of(AudioDeviceInfo.TYPE_WIRED_HEADSET, "Headset A").key)
        assertEquals("wired", OutputRoute.of(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "Other").key)
        // Each Bluetooth device has its own delay.
        val buds = OutputRoute.of(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Buds")
        assertEquals("bluetooth:Buds", buds.key)
        assertEquals("Bluetooth: Buds", buds.label)
        assertEquals(OutputRoute.Kind.BLUETOOTH, OutputRoute.of(26, "LE Buds").kind)
        assertEquals("usb:DAC", OutputRoute.of(AudioDeviceInfo.TYPE_USB_HEADSET, "DAC").key)
        assertEquals("other:type 999", OutputRoute.of(999, "").key)
    }
}

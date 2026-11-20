package dev.co508.drums.audio

import android.media.AudioDeviceInfo

/**
 * Where the output stream is going, as far as timing is concerned. Hit
 * detection is calibrated per route (DECISIONS.md, "Calibration Per Output
 * Route"): every wired jack behaves alike, but each Bluetooth or USB device
 * has its own delay.
 */
data class OutputRoute(
    val kind: Kind,
    /** The device's product name, as Android reports it. */
    val name: String,
) {
    enum class Kind { SPEAKER, WIRED, BLUETOOTH, USB, OTHER }

    /** A stable identity for stored calibrations. */
    val key: String
        get() =
            when (kind) {
                Kind.SPEAKER -> "speaker"
                Kind.WIRED -> "wired"
                Kind.BLUETOOTH -> "bluetooth:$name"
                Kind.USB -> "usb:$name"
                Kind.OTHER -> "other:$name"
            }

    /** For people: "Phone speaker", "Bluetooth: Buds". */
    val label: String
        get() =
            when (kind) {
                Kind.SPEAKER -> "Phone speaker"
                Kind.WIRED -> "Wired headphones"
                Kind.BLUETOOTH -> "Bluetooth: $name"
                Kind.USB -> "USB: $name"
                Kind.OTHER -> name.ifEmpty { "Other output" }
            }

    companion object {
        /** From an [AudioDeviceInfo] type and product name. */
        fun of(
            type: Int,
            productName: String,
        ): OutputRoute {
            val kind =
                when (type) {
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> Kind.SPEAKER
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_LINE_ANALOG,
                    -> Kind.WIRED
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_HEARING_AID, TYPE_BLE_HEADSET, TYPE_BLE_SPEAKER,
                    -> Kind.BLUETOOTH
                    AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_USB_ACCESSORY,
                    -> Kind.USB
                    else -> Kind.OTHER
                }
            val name = if (kind == Kind.OTHER && productName.isBlank()) "type $type" else productName
            return OutputRoute(kind, name)
        }

        // AudioDeviceInfo.TYPE_BLE_HEADSET and TYPE_BLE_SPEAKER, API 31; minSdk is 26.
        private const val TYPE_BLE_HEADSET = 26
        private const val TYPE_BLE_SPEAKER = 27
    }
}

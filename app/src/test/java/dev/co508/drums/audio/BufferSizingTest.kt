package dev.co508.drums.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class BufferSizingTest {
    @Test
    fun initialSizeCoversTheTargetInWholeBursts() {
        // 40 ms at 48 kHz is 1920 frames: exactly 8 bursts of 240.
        assertEquals(1_920, BufferSizing.initialFrames(48_000, 240, 40))
        // 1920 frames rounds up to 8 bursts of 256.
        assertEquals(2_048, BufferSizing.initialFrames(48_000, 256, 40))
        // 40 ms at 44.1 kHz is 1764 frames: rounds up to 9 bursts of 196 = 1764.
        assertEquals(1_764, BufferSizing.initialFrames(44_100, 196, 40))
    }

    @Test
    fun initialSizeIsNeverBelowFourBursts() {
        assertEquals(4 * 960, BufferSizing.initialFrames(48_000, 960, 10))
    }

    @Test
    fun capacityLeavesRoomToGrow() {
        assertEquals(9_600, BufferSizing.capacityFrames(48_000, 240, 1_920, 200, minBufferFrames = 480))
        // A device minimum above the target wins.
        assertEquals(12_000, BufferSizing.capacityFrames(48_000, 240, 1_920, 200, minBufferFrames = 11_900))
    }

    @Test
    fun growthAddsOneBurstUpToCapacity() {
        assertEquals(2_160, BufferSizing.grownFrames(1_920, 240, 9_600))
        assertEquals(9_600, BufferSizing.grownFrames(9_500, 240, 9_600))
    }
}

package com.amod.geotagcamera.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomNoteConfigTest {

    @Test
    fun testDefaultConfigValues() {
        val config = CustomNoteConfig()
        assertEquals("", config.text)
        assertEquals(NoteFont.CLASSIC.id, config.fontId)
        assertEquals(-1, config.textColor) // Color.WHITE is -1
        assertEquals(NoteHighlightMode.SOLID_BOX.id, config.highlightMode)
        assertEquals(-16777216, config.highlightColor) // Color.BLACK is -16777216
        assertEquals(1, config.alignment)
        assertEquals(20f, config.textSizeSp, 0.01f)
        assertFalse(config.isMirrored)
    }

    @Test
    fun testCustomTextAndColorValues() {
        val config = CustomNoteConfig(
            text = "Site Inspection 101",
            fontId = NoteFont.NEON.id,
            textColor = -256, // Color.YELLOW
            highlightMode = NoteHighlightMode.FROSTED.id,
            highlightColor = -16776961, // Color.BLUE
            alignment = 2,
            textSizeSp = 34f,
            isMirrored = true
        )

        assertEquals("Site Inspection 101", config.text)
        assertEquals(NoteFont.NEON.id, config.fontId)
        assertEquals(-256, config.textColor)
        assertEquals(NoteHighlightMode.FROSTED.id, config.highlightMode)
        assertEquals(-16776961, config.highlightColor)
        assertEquals(2, config.alignment)
        assertEquals(34f, config.textSizeSp, 0.01f)
        assertTrue(config.isMirrored)
    }

    @Test
    fun testVerticalSliderRatioCalculations() {
        val minSp = 14f
        val maxSp = 48f
        val usableTrack = 100f

        // When thumb is at the top (clampedThumbY = 0f):
        val topThumbY = 0f
        val topRatio = 1f - (topThumbY / usableTrack)
        val topSize = minSp + (topRatio * (maxSp - minSp))
        assertEquals(maxSp, topSize, 0.01f) // Size must be highest at the top!

        // When thumb is at the bottom (clampedThumbY = usableTrack):
        val bottomThumbY = usableTrack
        val bottomRatio = 1f - (bottomThumbY / usableTrack)
        val bottomSize = minSp + (bottomRatio * (maxSp - minSp))
        assertEquals(minSp, bottomSize, 0.01f) // Size must be lowest at the bottom
    }
}


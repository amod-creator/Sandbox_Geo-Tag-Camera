package com.amod.geotagcamera.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayTemplateAndLanguageTest {

    @Test
    fun testAllOverlayTemplatesExist() {
        val templates = OverlayTemplate.values()
        assertEquals(6, templates.size)
        assertNotNull(OverlayTemplate.DATETIME)
        assertNotNull(OverlayTemplate.SCAN_LOCATION)
        assertNotNull(OverlayTemplate.CLASSIC)
        assertNotNull(OverlayTemplate.REPORTING)
        assertNotNull(OverlayTemplate.NAVIGATION_COMPASS)
        assertNotNull(OverlayTemplate.LOCATION_WATERMARK)

        // Verify fallback of removed advance template to CLASSIC
        assertEquals(OverlayTemplate.CLASSIC, OverlayTemplate.fromId("advance"))
    }

    @Test
    fun testTemplateDisplayNames() {
        assertEquals("DateTime Template", OverlayTemplate.DATETIME.displayName)
        assertEquals("Scan Location Template", OverlayTemplate.SCAN_LOCATION.displayName)
        assertEquals("Classic Template", OverlayTemplate.CLASSIC.displayName)
        assertEquals("Reporting Template", OverlayTemplate.REPORTING.displayName)
        assertEquals("Navigation Compass Template", OverlayTemplate.NAVIGATION_COMPASS.displayName)
        assertEquals("Location Watermark", OverlayTemplate.LOCATION_WATERMARK.displayName)
    }

    @Test
    fun testAppLanguageWatermarks() {
        val english = AppLanguage.ENGLISH
        assertEquals("Portrait Mode", english.portraitLabel)
        assertEquals("Landscape Mode", english.landscapeLabel)
        assertEquals("Ads Free GPS Cam Visit Pro", english.badgeLabel)

        val hindi = AppLanguage.HINDI
        assertEquals("Portrait Mode", hindi.portraitLabel)
        assertEquals("Landscape Mode", hindi.landscapeLabel)
        assertEquals("Ads Free GPS Cam Visit Pro", hindi.badgeLabel)

        val spanish = AppLanguage.SPANISH
        assertEquals("Portrait Mode", spanish.portraitLabel)
        assertEquals("Landscape Mode", spanish.landscapeLabel)
        assertEquals("Ads Free GPS Cam Visit Pro", spanish.badgeLabel)
    }
}

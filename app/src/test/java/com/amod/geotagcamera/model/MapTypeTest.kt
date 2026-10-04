package com.amod.geotagcamera.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MapTypeTest {

    @Test
    fun testDefaultMapTypeIsTerrain() {
        val defaultType = MapType.fromId(null)
        assertEquals(MapType.TERRAIN, defaultType)
    }

    @Test
    fun testFromIdResolvesCorrectly() {
        assertEquals(MapType.TERRAIN, MapType.fromId("terrain"))
        assertEquals(MapType.NORMAL, MapType.fromId("normal"))
        assertEquals(MapType.HYBRID, MapType.fromId("hybrid"))
    }

    @Test
    fun testGoogleMapTypeParameters() {
        assertEquals("terrain", MapType.TERRAIN.googleMapType)
        assertEquals("roadmap", MapType.NORMAL.googleMapType)
        assertEquals("hybrid", MapType.HYBRID.googleMapType)
    }

    @Test
    fun testDefaultZooms() {
        assertEquals(16, MapType.TERRAIN.defaultZoom)
        assertEquals(17, MapType.NORMAL.defaultZoom)
        assertEquals(17, MapType.HYBRID.defaultZoom)
    }
}

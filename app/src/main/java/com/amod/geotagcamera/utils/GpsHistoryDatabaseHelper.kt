package com.amod.geotagcamera.utils

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GpsHistoryItem(
    val id: Long = 0,
    val imageUri: String,
    val latitude: Double,
    val longitude: Double,
    val locationName: String,
    val confidence: String,
    val reasoning: String,
    val source: String,
    val timestamp: Long = System.currentTimeMillis()
)

class GpsHistoryDatabaseHelper private constructor(context: Context) : 
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "gps_scanner_history.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_HISTORY = "scan_history"
        private const val COLUMN_ID = "id"
        private const val COLUMN_IMAGE_URI = "image_uri"
        private const val COLUMN_LATITUDE = "latitude"
        private const val COLUMN_LONGITUDE = "longitude"
        private const val COLUMN_LOCATION_NAME = "location_name"
        private const val COLUMN_CONFIDENCE = "confidence"
        private const val COLUMN_REASONING = "reasoning"
        private const val COLUMN_SOURCE = "source"
        private const val COLUMN_TIMESTAMP = "timestamp"

        // Live stream of database changes for Compose UI reactivity
        private val _historyFlow = MutableStateFlow<List<GpsHistoryItem>>(emptyList())
        val historyFlow: StateFlow<List<GpsHistoryItem>> = _historyFlow.asStateFlow()
        
        private var instance: GpsHistoryDatabaseHelper? = null

        @Synchronized
        fun getInstance(context: Context): GpsHistoryDatabaseHelper {
            if (instance == null) {
                instance = GpsHistoryDatabaseHelper(context.applicationContext)
                instance?.refreshHistory()
            }
            return instance!!
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_HISTORY (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_IMAGE_URI TEXT,
                $COLUMN_LATITUDE REAL,
                $COLUMN_LONGITUDE REAL,
                $COLUMN_LOCATION_NAME TEXT,
                $COLUMN_CONFIDENCE TEXT,
                $COLUMN_REASONING TEXT,
                $COLUMN_SOURCE TEXT,
                $COLUMN_TIMESTAMP INTEGER
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        onCreate(db)
    }

    /**
     * Reads all items from SQL database and updates the reactive StateFlow stream.
     */
    fun refreshHistory() {
        val items = mutableListOf<GpsHistoryItem>()
        try {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_HISTORY,
                null,
                null,
                null,
                null,
                null,
                "$COLUMN_TIMESTAMP DESC"
            )

            cursor?.use { c ->
                val idIdx = c.getColumnIndex(COLUMN_ID)
                val uriIdx = c.getColumnIndex(COLUMN_IMAGE_URI)
                val latIdx = c.getColumnIndex(COLUMN_LATITUDE)
                val lonIdx = c.getColumnIndex(COLUMN_LONGITUDE)
                val nameIdx = c.getColumnIndex(COLUMN_LOCATION_NAME)
                val confIdx = c.getColumnIndex(COLUMN_CONFIDENCE)
                val reasonIdx = c.getColumnIndex(COLUMN_REASONING)
                val srcIdx = c.getColumnIndex(COLUMN_SOURCE)
                val timeIdx = c.getColumnIndex(COLUMN_TIMESTAMP)

                if (idIdx != -1 && uriIdx != -1 && latIdx != -1 && lonIdx != -1 &&
                    nameIdx != -1 && confIdx != -1 && reasonIdx != -1 && srcIdx != -1 && timeIdx != -1) {
                    
                    while (c.moveToNext()) {
                        items.add(
                            GpsHistoryItem(
                                id = c.getLong(idIdx),
                                imageUri = c.getString(uriIdx) ?: "",
                                latitude = c.getDouble(latIdx),
                                longitude = c.getDouble(lonIdx),
                                locationName = c.getString(nameIdx) ?: "",
                                confidence = c.getString(confIdx) ?: "low",
                                reasoning = c.getString(reasonIdx) ?: "",
                                source = c.getString(srcIdx) ?: "",
                                timestamp = c.getLong(timeIdx)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _historyFlow.value = items
    }

    /**
     * Inserts a record and pushes update to the UI.
     */
    fun addHistoryItem(item: GpsHistoryItem): Long {
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COLUMN_IMAGE_URI, item.imageUri)
                put(COLUMN_LATITUDE, item.latitude)
                put(COLUMN_LONGITUDE, item.longitude)
                put(COLUMN_LOCATION_NAME, item.locationName)
                put(COLUMN_CONFIDENCE, item.confidence)
                put(COLUMN_REASONING, item.reasoning)
                put(COLUMN_SOURCE, item.source)
                put(COLUMN_TIMESTAMP, item.timestamp)
            }
            val id = db.insert(TABLE_HISTORY, null, values)
            refreshHistory()
            id
        } catch (e: Exception) {
            e.printStackTrace()
            -1L
        }
    }

    /**
     * Deletes a record and pushes update to the UI.
     */
    fun deleteHistoryItem(id: Long) {
        try {
            val db = writableDatabase
            db.delete(TABLE_HISTORY, "$COLUMN_ID = ?", arrayOf(id.toString()))
            refreshHistory()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Clears all history log entries.
     */
    fun clearAllHistory() {
        try {
            val db = writableDatabase
            db.delete(TABLE_HISTORY, null, null)
            refreshHistory()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

package com.cutcoach.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The local database. One file on this phone, never backed up off it.
 *
 * `allowBackup` is false in the manifest for the same reason: health data does
 * not leave the device.
 */
@Database(
    entities = [
        SessionLogEntity::class,
        SetLogEntity::class,
        ExerciseStateEntity::class,
        BodyMetricEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CutCoachDatabase : RoomDatabase() {

    abstract fun sessionLogDao(): SessionLogDao
    abstract fun setLogDao(): SetLogDao
    abstract fun exerciseStateDao(): ExerciseStateDao
    abstract fun bodyMetricDao(): BodyMetricDao

    companion object {
        private const val NAME = "cutcoach.db"

        @Volatile
        private var instance: CutCoachDatabase? = null

        fun get(context: Context): CutCoachDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CutCoachDatabase::class.java,
                    NAME,
                ).build().also { instance = it }
            }
    }
}

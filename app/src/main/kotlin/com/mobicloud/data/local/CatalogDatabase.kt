package com.mobicloud.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mobicloud.data.local.dao.CatalogDao
import com.mobicloud.data.local.entity.CatalogEntryEntity
import com.mobicloud.data.local.entity.FragmentLocationEntity

@Database(entities = [CatalogEntryEntity::class, FragmentLocationEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
}

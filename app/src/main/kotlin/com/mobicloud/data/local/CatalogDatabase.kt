package com.mobicloud.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mobicloud.data.local.dao.CatalogDao
import com.mobicloud.data.local.dao.DhtDao
import com.mobicloud.data.local.dao.IdentityDao
import com.mobicloud.data.local.dao.PeerDao
import com.mobicloud.data.local.entity.CatalogEntryEntity
import com.mobicloud.data.local.entity.DhtEntryEntity
import com.mobicloud.data.local.entity.FragmentLocationEntity
import com.mobicloud.data.local.entity.NodeIdentityEntity
import com.mobicloud.data.local.entity.PeerNodeEntity

@Database(
    entities = [
        CatalogEntryEntity::class,
        FragmentLocationEntity::class,
        NodeIdentityEntity::class,
        PeerNodeEntity::class,
        DhtEntryEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun identityDao(): IdentityDao
    abstract fun peerDao(): PeerDao
    abstract fun dhtDao(): DhtDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE peer_nodes ADD COLUMN is_super_pair INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS dht_entries (
                        block_id TEXT NOT NULL PRIMARY KEY,
                        node_id TEXT NOT NULL,
                        ip_address TEXT NOT NULL,
                        port INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_dht_entries_block_id ON dht_entries(block_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_dht_entries_node_id ON dht_entries(node_id)")
            }
        }
    }
}

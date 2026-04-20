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
import com.mobicloud.data.local.dao.TombstoneDao
import com.mobicloud.data.local.entity.CatalogEntryEntity
import com.mobicloud.data.local.entity.DhtEntryEntity
import com.mobicloud.data.local.entity.FragmentLocationEntity
import com.mobicloud.data.local.entity.NodeIdentityEntity
import com.mobicloud.data.local.entity.PeerNodeEntity
import com.mobicloud.data.local.entity.TombstoneEntryEntity

@Database(
    entities = [
        CatalogEntryEntity::class,
        FragmentLocationEntity::class,
        NodeIdentityEntity::class,
        PeerNodeEntity::class,
        DhtEntryEntity::class,
        TombstoneEntryEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun identityDao(): IdentityDao
    abstract fun peerDao(): PeerDao
    abstract fun dhtDao(): DhtDao
    abstract fun tombstoneDao(): TombstoneDao

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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS tombstone_entries (
                        block_id TEXT NOT NULL PRIMARY KEY,
                        deleted_at INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}

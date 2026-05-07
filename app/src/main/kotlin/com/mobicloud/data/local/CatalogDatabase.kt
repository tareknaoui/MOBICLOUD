package com.mobicloud.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mobicloud.data.local.dao.CatalogDao
import com.mobicloud.data.local.dao.DhtDao
import com.mobicloud.data.local.dao.HostedBlockDao
import com.mobicloud.data.local.dao.IdentityDao
import com.mobicloud.data.local.dao.NodeSettingsDao
import com.mobicloud.data.local.dao.PeerDao
import com.mobicloud.data.local.dao.TombstoneDao
import com.mobicloud.data.local.entity.CatalogEntryEntity
import com.mobicloud.data.local.entity.DhtEntryEntity
import com.mobicloud.data.local.entity.FragmentLocationEntity
import com.mobicloud.data.local.entity.HostedBlockEntity
import com.mobicloud.data.local.entity.NodeIdentityEntity
import com.mobicloud.data.local.entity.NodeSettingsEntity
import com.mobicloud.data.local.entity.PeerNodeEntity
import com.mobicloud.data.local.entity.TombstoneEntryEntity

@Database(
    entities = [
        CatalogEntryEntity::class,
        FragmentLocationEntity::class,
        NodeIdentityEntity::class,
        PeerNodeEntity::class,
        DhtEntryEntity::class,
        TombstoneEntryEntity::class,
        HostedBlockEntity::class,
        NodeSettingsEntity::class
    ],
    version = 14,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun identityDao(): IdentityDao
    abstract fun peerDao(): PeerDao
    abstract fun dhtDao(): DhtDao
    abstract fun tombstoneDao(): TombstoneDao
    abstract fun hostedBlockDao(): HostedBlockDao
    abstract fun nodeSettingsDao(): NodeSettingsDao

    companion object {
        // Story 1-3 — premier ajout de NodeIdentityEntity + PeerNodeEntity (sans is_super_pair).
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS node_identity (
                        node_id TEXT NOT NULL PRIMARY KEY,
                        public_key_bytes BLOB NOT NULL,
                        reliability_score REAL NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS peer_nodes (
                        node_id TEXT NOT NULL PRIMARY KEY,
                        public_key_bytes BLOB NOT NULL,
                        reliability_score REAL NOT NULL,
                        ip_address TEXT,
                        port INTEGER,
                        last_seen_timestamp_ms INTEGER NOT NULL,
                        is_active INTEGER NOT NULL DEFAULT 1,
                        source TEXT NOT NULL DEFAULT 'LOCAL_UDP'
                    )
                """.trimIndent())
            }
        }

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

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE catalog_entry ADD COLUMN wrapped_master_key_json TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS hosted_blocks (
                        block_id TEXT NOT NULL PRIMARY KEY,
                        owner_id TEXT NOT NULL,
                        fragment_index INTEGER NOT NULL,
                        is_parity INTEGER NOT NULL,
                        file_path TEXT NOT NULL,
                        size_bytes INTEGER NOT NULL,
                        received_at INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        // Story 6.3 — ajout colonne `iv` (12 bytes AES-GCM nonce) sur hosted_blocks.
        // Sentinelle "legacy" (12 × 0x00) pour les blocs pré-existants : le client de
        // téléchargement (BlockDownloadClient) détecte cette sentinelle et lève
        // DownloadException.MasterKeyTransportGap — pas de tentative de déchiffrement
        // sur un IV non fourni.
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE hosted_blocks ADD COLUMN iv BLOB NOT NULL DEFAULT x'000000000000000000000000'"
                )
            }
        }

        // Story 6.3 — `original_file_size` requis par le décodeur Erasure pour trimer le padding.
        // 0L sentinelle "legacy" → DownloadException.MasterKeyTransportGap côté pipeline.
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE catalog_entry ADD COLUMN original_file_size INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // Story 1.6 — quota de stockage alloué au réseau (singleton row id=0).
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS node_settings " +
                    "(id INTEGER NOT NULL PRIMARY KEY, allocated_storage_bytes INTEGER NOT NULL)"
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE catalog_entry ADD COLUMN original_file_name TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        // Story 9.1 — clusterId UUID v4 pour identification de cluster inter-nœuds.
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE node_settings ADD COLUMN cluster_id TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        // Paramètres Erasure Coding dynamiques — k et n varient selon la stabilité réseau
        // au moment de l'upload. DEFAULT 4/2 = profil WiFi stable (backward compat).
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE catalog_entry ADD COLUMN k INTEGER NOT NULL DEFAULT 4")
                db.execSQL("ALTER TABLE catalog_entry ADD COLUMN n INTEGER NOT NULL DEFAULT 2")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE peer_nodes ADD COLUMN free_storage_bytes INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}

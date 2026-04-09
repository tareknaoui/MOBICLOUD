package com.mobicloud.domain.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class CatalogEntryTest {

    // A structure simulating a future version of gossip with an unknown key
    @Serializable
    data class FutureCatalogEntry(
        @ProtoNumber(1) val fileHash: String,
        @ProtoNumber(2) val ownerPubKeyHash: String,
        @ProtoNumber(3) val versionClock: Long,
        @ProtoNumber(4) val fragmentLocations: List<FragmentLocation>,
        @ProtoNumber(5) val newUnknownField: String // New field added in the future
    )

    @Test
    fun testSerializationIgnoresUnknownKeys() {
        val futureEntry = FutureCatalogEntry(
            fileHash = "hash123",
            ownerPubKeyHash = "pubkey456",
            versionClock = 1L,
            fragmentLocations = emptyList(),
            newUnknownField = "some extra data"
        )
        
        // Use a format that ignores unknown keys, simulating our Gossip Protobuf parser configuration.
        // It's a requirement of the task to tolerate a future mismatch in gossip structure.
        // ProtoBuf { ... } doesn't have ignoreUnknownKeys natively like Json, but protobuf by design ignores unknown fields if not mapped.
        val protoBuf = ProtoBuf { }
        
        val bytes = protoBuf.encodeToByteArray(futureEntry)
        
        // Standard decode. Since Protobuf naturally ignores unknown tags, this should succeed 
        // without crashing and just ignore field 5.
        val decodedEntry = protoBuf.decodeFromByteArray<CatalogEntry>(bytes)
        
        assertEquals("hash123", decodedEntry.fileHash)
        assertEquals("pubkey456", decodedEntry.ownerPubKeyHash)
        assertEquals(1L, decodedEntry.versionClock)
        assertEquals(0, decodedEntry.fragmentLocations.size)
    }
}

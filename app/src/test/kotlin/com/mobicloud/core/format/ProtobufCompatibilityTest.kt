package com.mobicloud.core.format

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtobufCompatibilityTest {

    @Serializable
    data class MessageV1(
        @ProtoNumber(1) val id: Int,
        @ProtoNumber(2) val content: String,
    )

    @Serializable
    data class MessageV2(
        @ProtoNumber(1) val id: Int,
        @ProtoNumber(2) val content: String,
        @ProtoNumber(3) val newField: String, // Champ inconnu pour la V1 — tag 3 stable
    )

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun testForwardCompatibility_unknownFieldsAreIgnored() {
        // Given une instance Protobuf par défaut
        val protoBuf = ProtoBuf { }
        
        // When je sérialise un message V2 qui a plus d'attributs
        val messageV2 = MessageV2(id = 42, content = "Data", newField = "Futur")
        val byteArrayV2 = protoBuf.encodeToByteArray(messageV2)
        
        // Et que je le désérialise comme un message V1
        val messageV1 = protoBuf.decodeFromByteArray<MessageV1>(byteArrayV2)
        
        // Then ça ne doit pas crasher et les attributs correspondants sont récupérés
        assertEquals(42, messageV1.id)
        assertEquals("Data", messageV1.content)
    }
}

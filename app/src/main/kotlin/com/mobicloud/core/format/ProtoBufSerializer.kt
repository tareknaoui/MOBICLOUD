package com.mobicloud.core.format

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf

/**
 * Singleton Protobuf configuré pour la sérialisation P2P MobiCloud.
 *
 * ## Compatibilité ascendante (Forward-Compatibility)
 *
 * Le format ProtoBuf binaire (kotlinx-serialization-protobuf) ignore nativement les champs
 * dont le numéro de champ est inconnu lors du décodage. Ce comportement est garanti par la
 * spécification ProtoBuf (wire format) et ne nécessite pas de paramètre spécifique.
 *
 * Ce wrapper centralise l'instance partagée et rend cette garantie explicite et
 * auditable, conformément à l'AC#4 de la Story 1.1 (ignoreUnknownFields = par conception).
 *
 * ## Usage
 *
 * ```kotlin
 * // Encodage
 * val bytes = MobiCloudProtoBuf.encodeToByteArray(myModel)
 *
 * // Décodage (les champs inconnus sont silencieusement ignorés)
 * val model = MobiCloudProtoBuf.decodeFromByteArray<MyModel>(bytes)
 * ```
 *
 * @see [ProtoBuf Wire Format Compatibility](https://protobuf.dev/programming-guides/proto3/#unknowns)
 */
@OptIn(ExperimentalSerializationApi::class)
val MobiCloudProtoBuf: ProtoBuf = ProtoBuf {
    // ProtoBuf binaire ignore les champs de numéro inconnu par défaut (spécification wire format).
    // Ce bloc de configuration est intentionnellement minimal : toute option future sera
    // ajoutée ici pour centraliser les décisions de sérialisation.
    encodeDefaults = true
}

package se.nikohei.kvstore.typing

import kotlinx.serialization.Serializable


@Serializable
sealed interface Transaction {
}

@Serializable
data class SetTransaction(
    val key: String,
    val value: String
) : Transaction

@Serializable
data class DelTransaction(
    val key: String,
) : Transaction


package se.nikohei.kvstore.typing

@JvmInline
value class DatastoreName(
    val value: String
) {
    init {
        require(value.matches(Regex("[A-z][A-z0-9_]{0,63}")))
    }

    override fun toString() = value
}
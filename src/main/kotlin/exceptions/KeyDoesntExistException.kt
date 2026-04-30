package se.nikohei.kvstore.exceptions

class KeyDoesntExistException(key: String) : KVStoreException("$key does not exist.") {
}
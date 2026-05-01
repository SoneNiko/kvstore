package se.nikohei.kvstore

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import se.nikohei.kvstore.exceptions.KeyDoesntExistException
import se.nikohei.kvstore.typing.DatastoreName
import se.nikohei.kvstore.typing.DelTransaction
import se.nikohei.kvstore.typing.SetTransaction
import se.nikohei.kvstore.typing.Transaction
import java.io.File
import java.util.concurrent.ConcurrentHashMap


class DataStore(
    val name: DatastoreName,
) {
    private val internalStore = ConcurrentHashMap(mutableMapOf<String, String>())
    private val transactionBuffer = ArrayDeque<Transaction>()
    private val transactionBufferSize: Long = 64

    private val storageBackendMutex = Mutex()
    private val storageBackend = File("${name}-store.db")

    @OptIn(ExperimentalSerializationApi::class)
    val cbor = Cbor {
        serializersModule = SerializersModule {
            polymorphic(Transaction::class) {
                subclass(SetTransaction::class, SetTransaction.serializer())
                subclass(DelTransaction::class, DelTransaction.serializer())
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun flush() = storageBackendMutex.withLock {

        // we store as encoded cbor entries that are prefixed with the size. that way we can append cleanly
        // size: 4bytes (4bytes = 4x 8bits --> 32bit integer).
        // which means a 32 bit int is the max size of bytes that an entry can be
        //
        // TODO: restrict size

        // TODO: we can make this much better. right now this stores full classpath of the transaction types and the field name.
        // in reality we need only a single bit (or a byte i guess in case we add some) to indicate the type of the
        // transaction.



        for (tx in transactionBuffer) {
            val bytes = cbor.encodeToByteArray(tx)

            val sizeInd = sizeToByte(bytes.size)

            storageBackend.appendBytes(sizeInd)
            storageBackend.appendBytes(bytes)
        }
    }

    fun sizeToByte(sizeInp: Int) : ByteArray {
        // Ive never done this before but in theory to get the value of each byte we can do a bitmask (?)
        // like 0b11111111 or 0xFF i guess and it with the respective parts of the long.
        // do get the next part i guess we can shift right by 8?

        var size = sizeInp
        val sizeInd = ByteArray(4)
        val mask: Int = 0b11111111  // 0xFF
        for (byteIndex in 3 downTo 0) {
            val currByte = (size and mask).toByte()
            sizeInd[byteIndex] = currByte
            size = size shr 8
        }
        return sizeInd
    }

    suspend fun addTransaction(transaction: Transaction) {
        transactionBuffer.add(transaction)
        if (transactionBuffer.size >= transactionBufferSize) {
            flush()
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun restoreFromWal() = storageBackendMutex.withLock {
        if (!storageBackend.exists()) {
            return@withLock
        }

        val reader = storageBackend.inputStream()

        reader.use {
            val transactions = mutableListOf<Transaction>()

            val sizeInfo = ByteArray(4)
            while (reader.read(sizeInfo) != -1) {
                // from this byte array we need to build a long so we just add this and shift it left everytime
                var size: Int = 0 + sizeInfo[0]  // i found this way of doing this typecast. this looks goofy

                for (byteIndex in 1..3) {
                    size = size shl 8
                    size += sizeInfo[byteIndex]
                }

                val transactionBytes = ByteArray(size)
                reader.read(transactionBytes)

                transactions.add(cbor.decodeFromByteArray<Transaction>(transactionBytes))
            }

            // replay transactions
            for (tx in transactions) {
                when (tx) {
                    is SetTransaction -> {
                        internalStore[tx.key] = tx.value
                    }
                    is DelTransaction -> {
                        internalStore.remove(tx.key)
                    }
                }
            }
        }
    }

    suspend fun stop() {
        flush()
    }

    suspend fun set(key: String, value: String) {
        internalStore[key] = value
        addTransaction(SetTransaction(key, value))
    }

    fun get(key: String): String {
        val value = internalStore[key] ?: throw KeyDoesntExistException(key)
        return value
    }

    suspend fun del(key: String) {
        internalStore[key] ?: throw KeyDoesntExistException(key)
        internalStore.remove(key)
        addTransaction(DelTransaction(key))
    }

    fun listKeys(): Set<String> {
        return internalStore.keys
    }

    fun countKeys(): Int {
        return internalStore.count()
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun compact(): Int = storageBackendMutex.withLock {

        // lets keep it simple and go through the internal store instead of reading the file and trying to combine
        // it with the transaction buffer. This means that we also need to clear the buffer so lets start with that

        transactionBuffer.clear()

        val outStream = storageBackend.outputStream()

        outStream.use {
            for (registrations in internalStore) {
                val tx = SetTransaction(registrations.key, registrations.value)

                val txContent = cbor.encodeToByteArray(tx)
                val txSize = sizeToByte(txContent.size)

                it.write(txSize)
                it.write(txContent)
            }
        }

        return internalStore.size
    }
}
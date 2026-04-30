package se.nikohei.kvstore

import com.strumenta.antlrkotlin.runtime.BitSet
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.ClosedByteChannelException
import io.ktor.utils.io.readLine
import io.ktor.utils.io.writeString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.antlr.v4.kotlinruntime.ANTLRErrorListener
import org.antlr.v4.kotlinruntime.BaseErrorListener
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.Parser
import org.antlr.v4.kotlinruntime.RecognitionException
import org.antlr.v4.kotlinruntime.Recognizer
import org.antlr.v4.kotlinruntime.atn.ATNConfigSet
import org.antlr.v4.kotlinruntime.dfa.DFA
import se.nikohei.kvstore.exceptions.InvalidCommandException
import se.nikohei.kvstore.exceptions.KVStoreException
import se.nikohei.kvstore.exceptions.NoDataStoreSelectedException
import se.nikohei.kvstore.grammar.generated.KVStoreLexer
import se.nikohei.kvstore.grammar.generated.KVStoreParser
import se.nikohei.kvstore.typing.DatastoreName
import java.net.SocketException

class CommandHandler(
    private val socket: Socket,
    private val coroutineScope: CoroutineScope,
) {

    private lateinit var writer: ByteWriteChannel;
    private lateinit var reader: ByteReadChannel;

    private val mutex = Mutex()
    private var dataStore: DataStore? = null

    suspend fun requireDataStore(): DataStore {
        return mutex.withLock {
            dataStore ?: throw NoDataStoreSelectedException()
        }
    }

    suspend fun assignDatastore(storeName: String?) = mutex.withLock {

        val value = if (storeName != null) {
            Main.mutex.withLock {
                Main.storeList[storeName] ?: DataStore(DatastoreName(storeName))
            }
        } else {
            null
        }

        if (dataStore != null) {
            if (value == null) {

            } else {
                if (value.name == dataStore!!.name) {
                    transmit("Already connected to datastore. DISCONNECT to force reload")
                    return
                }
            }

            dataStore?.let { transmit("Disconnecting from datastore ${it.name}") }
        } else {

        }
        value?.let { transmit("Connecting to datastore ${value.name}") }

        Main.mutex.withLock {
            value?.restoreFromWal()

            dataStore = value
        }

    }

    suspend fun transmit(content: String) {
        if (content.isBlank()) return
        val toSend = if (content.endsWith("\n")) content else "${content}\n"
        writer.writeString(toSend)
    }

    suspend fun prompt() {
        writer.writeString("[${dataStore?.name ?: "KVSTORE"}]>")
    }

    fun start() = coroutineScope.launch(Dispatchers.IO) {
        reader = socket.openReadChannel()
        writer = socket.openWriteChannel(autoFlush = true)
        try {
            socket.use { socket ->
                while (true) {
                    try {
                        if (parseAndExecute()) break

                    } catch (e: KVStoreException) {
                        transmit(e.message ?: e.toString())
                    }
                }
            }
        } catch (e: ClosedByteChannelException) {
            println("Channel closed: ${e.message}")
        }

    }

    private suspend fun parseAndExecute(): Boolean {
        prompt()

        val line = reader.readLine() ?: return true

        val charStream = CharStreams.fromString(line)
        val lexer = KVStoreLexer(charStream)
        lexer.removeErrorListeners()
        val tokenStream = CommonTokenStream(lexer)
        val parser = KVStoreParser(tokenStream)
        parser.removeErrorListeners()

        val syntaxErrors = mutableListOf<String>()

        parser.addErrorListener(object : BaseErrorListener() {
            override fun syntaxError(
                recognizer: Recognizer<*, *>,
                offendingSymbol: Any?,
                line: Int,
                charPositionInLine: Int,
                msg: String,
                e: RecognitionException?
            ) {
                syntaxErrors.add(msg)
            }
        })

        val tree = parser.command()

        if (syntaxErrors.isNotEmpty()) {
            throw InvalidCommandException(syntaxErrors.joinToString("\n"))
        }

        when {
            tree.cmdSet() != null -> {
                val key = tree.cmdSet()!!.key().text
                val value = tree.cmdSet()!!.value().text
                requireDataStore().set(key, value)
            }

            tree.cmdGet() != null -> {
                val key = tree.cmdGet()!!.key().text
                transmit(requireDataStore().get(key))
            }

            tree.cmdDel() != null -> {
                val key = tree.cmdDel()!!.key().text
                requireDataStore().del(key)
            }

            tree.LIST() != null -> {
                transmit(requireDataStore().listKeys().joinToString(","))
            }

            tree.COUNT() != null -> {
                transmit(requireDataStore().countKeys().toString())
            }

            tree.COMPACT() != null -> {
                transmit(requireDataStore().compact().toString())
            }

            tree.QUIT() != null -> {
                transmit("Disconnecting...")
                dataStore?.stop()
                writer.flushAndClose()
                return true
            }

            tree.cmdConnect() != null -> {
                val name = tree.cmdConnect()!!.dataStoreIdentifier().text
                assignDatastore(name)
            }

            tree.DISCONNECT() != null -> {
                requireDataStore()
                assignDatastore(null)
            }

            tree.FLUSH() != null -> {
                requireDataStore().flush()
            }
        }
        return false
    }


}
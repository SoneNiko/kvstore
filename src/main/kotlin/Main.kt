package se.nikohei.kvstore

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import se.nikohei.kvstore.Main.Companion.storeList
import se.nikohei.kvstore.typing.DatastoreName
import java.util.concurrent.ConcurrentHashMap


class Main {
    companion object {
        val mutex = Mutex()
        val storeList = ConcurrentHashMap(mutableMapOf<String, DataStore>())
    }
}

suspend fun main() = coroutineScope {

    storeList["default"] = DataStore(name = DatastoreName("default"))

    val selectorManager = SelectorManager(Dispatchers.IO)
    val serverSocket = aSocket(selectorManager).tcp().bind("127.0.0.1", 9000)

    try {
        while (true) {
            val socket = serverSocket.accept()
            CommandHandler(socket, this).start()
            println("Client connected")
        }
    } finally {
        storeList.values.forEach { store ->
            store.stop()
        }
    }
}
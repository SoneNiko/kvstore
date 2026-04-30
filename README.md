# kvstore

A small TCP key-value store in Kotlin. Listens on port 9000, speaks a plain-text protocol, persists to disk by appending transactions to a log and replaying them.

It hosts multiple named stores rather than a single global map, so you `CONNECT <name>` before doing anything else.

## Running

```
./gradlew run
```

Then from another terminal:

```
nc localhost 9000
```

You'll get a `[KVSTORE]>` prompt. A `default` store is created at boot, so `CONNECT default` is enough to start.

## Commands

```
CONNECT <name>      attach to a datastore (creates it if it's new)
DISCONNECT          detach from the current one
SET key "value"     values are double-quoted strings
GET key
DEL key
LIST                comma-separated keys
COUNT               number of live keys
FLUSH               force the transaction buffer to disk
COMPACT             rewrite the log from current state, returns key count
QUIT                close the connection
```

Keywords are case-insensitive. Keys match `[A-Za-z][A-Za-z0-9]*`. Values are anything between two double quotes that isn't a newline. The grammar is in `src/main/antlr/`.

## How it works

Each connection runs as a coroutine on `Dispatchers.IO` (Ktor sockets, no raw threads). Lines are fed through an ANTLR-generated lexer and parser; dispatch is a `when` over the parse tree.

A store is a `ConcurrentHashMap<String, String>` plus a transaction buffer. `SET` and `DEL` push a `SetTransaction` or `DelTransaction` onto the buffer. The buffer flushes to `<name>-store.db` when it hits 64 entries, on `FLUSH`, or on `QUIT`.

The on-disk format is length-prefixed CBOR. Each entry is 4 bytes of big-endian size followed by the serialized transaction. Replay happens when a client `CONNECT`s, not at server boot, so the server's effective state is whatever clients have mounted.

`COMPACT` walks the live map, rewrites the file from scratch with one `SetTransaction` per live key, and returns the new entry count.

## Caveats

The response goes back to the client before the bytes hit disk. A crash inside the 64-entry window loses the tail. If you want strict write-ahead behavior, set `transactionBufferSize = 1` in `DataStore.kt`.

There's no graceful shutdown. Ctrl-C drops in-flight clients and any unflushed buffers.

No tests yet.

## Layout

```
src/main/kotlin/
  Main.kt              entry point, accept loop, store registry
  CommandHandler.kt    per-connection coroutine, parser dispatch
  DataStore.kt         map, transaction buffer, file I/O
  typing/              value classes, Transaction sum type
  exceptions/          KVStoreException hierarchy
src/main/antlr/        lexer and parser grammar
```

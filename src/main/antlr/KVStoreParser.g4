parser grammar KVStoreParser;

options { tokenVocab = KVStoreLexer; }

command
    : cmdSet
    | cmdGet
    | cmdDel
    | LIST
    | COUNT
    | COMPACT
    | QUIT
    | cmdConnect
    | DISCONNECT
    | FLUSH
    ;

cmdSet
    : SET key value ;

cmdGet
    : GET key ;

cmdDel
    : DEL key ;

cmdConnect
    : CONNECT dataStoreIdentifier ;

dataStoreIdentifier : KEY ;
key : KEY ;
value : STRINGLITERAL;

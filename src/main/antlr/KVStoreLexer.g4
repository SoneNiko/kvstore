lexer grammar KVStoreLexer;

fragment F : [fF] ;
fragment S : [sS] ;
fragment E : [eE] ;
fragment T : [tT] ;
fragment G : [gG] ;
fragment D : [dD] ;
fragment L : [lL] ;
fragment I : [iI] ;
fragment C : [cC] ;
fragment O : [oO] ;
fragment N : [nN] ;
fragment A : [aA] ;
fragment M : [mM] ;
fragment P : [pP] ;
fragment Q : [qQ] ;
fragment U : [uU] ;
fragment R : [rR] ;
fragment H : [hH] ;
fragment K : [kK] ;
fragment W : [wW] ;

SET        : S E T ;
GET        : G E T ;
DEL        : D E L ;
LIST       : L I S T ;
COUNT      : C O U N T ;
COMPACT    : C O M P A C T ;
QUIT       : Q U I T ;
CONNECT    : C O N N E C T ;
DISCONNECT : D I S C O N N E C T ;
FLUSH      : F L U S H ;

KEY : [A-Za-z] [A-Za-z0-9]* ;
STRINGLITERAL : '"' ~["\r\n]* '"' ;
WS   : [ \t]+ -> skip ;
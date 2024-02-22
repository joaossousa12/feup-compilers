grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

EQUALS : '=';
SEMI : ';' ;
LCURLY : '{' ;
RCURLY : '}' ;
LPAREN : '(' ;
RPAREN : ')' ;
MUL : '*' ;
ADD : '+' ;
DIV : '/' ;
NOT : '!' ;
SUB : '-' ;
AND : '&&' ;
OR : '||' ;
LESS : '<' ;
GREATER : '>' ;
LSQPAREN : '[' ;
RSQPAREN : ']' ;
COMMA : ',' ;
MEMBERCALL : '.' ;
LENGTH : 'length' ;

CLASS : 'class' ;
INT : 'int' ;
PUBLIC : 'public' ;
RETURN : 'return' ;

INTEGER : [0] | [1-9][0-9]* ;
ID : [a-zA-Z_$][a-zA-Z_$0-9]* ;

WS : [ \t\n\r\f]+ -> skip ;

program
    : classDecl EOF
    ;


classDecl
    : CLASS name=ID
        LCURLY
        methodDecl*
        RCURLY
    ;

varDecl
    : type name=ID SEMI
    ;

type
    : name= INT ;

methodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})?
        type name=ID
        LPAREN param RPAREN
        LCURLY varDecl* stmt* RCURLY
    ;

param
    : type name=ID
    ;

stmt
    : expr EQUALS LSQPAREN expr RSQPAREN SEMI #ArrayAssign //
    | expr EQUALS expr SEMI #AssignStmt //
    | RETURN expr SEMI #ReturnStmt
    ;

expr
    : LPAREN expr RPAREN #Paren //
    | expr COMMA expr #Comma //
    | expr LSQPAREN expr RSQPAREN #ArrayAcess //
    | expr MEMBERCALL LENGTH #Length //
    | op= NOT expr #BinaryExpr //
    | expr op= (MUL | DIV) expr #BinaryExpr //
    | expr op= (ADD | SUB) expr #BinaryExpr //
    | expr op= (LESS | GREATER) expr #BinaryExpr //
    | expr op= AND expr #BinaryExpr //
    | expr op= OR expr #BinaryExpr //
    | value=INTEGER #IntegerLiteral //
    | name=ID #VarRefExpr //
    ;




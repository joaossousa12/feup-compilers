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
NEW : 'new' ;
IF : 'if' ;
ELSE : 'else' ;
WHILE : 'while' ;
THIS : 'this' ;
ELLIPSIS : '...' ;

CLASS : 'class' ;
INT : 'int' ;
PUBLIC : 'public' ;
RETURN : 'return' ;
IMPORT : 'import' ;
EXTENDS : 'extends' ;
STATIC : 'static' ;
VOID : 'void' ;
MAIN : 'main' ;
STRING : 'String' ;

INTEGER : [0] | [1-9][0-9]* ;
TRUE : 'true' ;
FALSE : 'false' ;
BOOLEAN : 'boolean';
ID : [a-zA-Z_$][a-zA-Z_$0-9]* ;

WS : [ \t\n\r\f]+ -> skip ;

program
    : importDecl* classDecl EOF
    ;

importDecl
    : IMPORT ID (MEMBERCALL ID)* SEMI
    ;


classDecl
    : CLASS name=ID (EXTENDS ID)?
        LCURLY
        varDecl*
        methodDecl*
        RCURLY
    ;

varDecl
    : type name=ID SEMI
    ;

type
    : type LSQPAREN RSQPAREN #Array //
    | INT ELLIPSIS #EllipsisType //
    | BOOLEAN #BooleanType //
    | INT #IntegerType //
    | STRING #StringType //
    | name= ID #ClassType //
    ;

methodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})?
        type name=ID
        LPAREN param RPAREN
        LCURLY varDecl* stmt* RETURN expr SEMI RCURLY
    | (PUBLIC {$isPublic=true;})?
       STATIC VOID MAIN
       LPAREN STRING LSQPAREN RSQPAREN ID RPAREN
       LCURLY varDecl* stmt* RCURLY
    ;

param
    : (type name=ID (COMMA type name=ID)*)?
    ;

stmt
    : LCURLY stmt* RCURLY #StmtScope //
    | IF LPAREN expr RPAREN stmt ELSE stmt #IfElseStmt //
    | WHILE LPAREN expr RPAREN stmt #WhileStmt //
    | expr SEMI #ExprStmt //
    | ID EQUALS expr SEMI #AssignStmt //
    | ID LSQPAREN expr RSQPAREN EQUALS expr SEMI #ArrayAssign //
    | RETURN expr SEMI #ReturnStmt
    ;

expr
    : LPAREN expr RPAREN #Paren //
    | LSQPAREN (expr (COMMA expr)*)? RSQPAREN #ArrayInit //
    | expr LSQPAREN expr RSQPAREN #ArrayAccess //
    | expr MEMBERCALL LENGTH #Length //
    | expr MEMBERCALL ID LPAREN (expr (COMMA expr)*)? RPAREN #ArrayAccess //
    | expr LPAREN expr RPAREN #MemberCall //
    | value= THIS #This //
    | value= NOT expr #Negation //
    | NEW INT LSQPAREN expr RSQPAREN #NewArray //
    | NEW ID LPAREN RPAREN #NewClass //
    | expr op= (MUL | DIV) expr #BinaryExpr //
    | expr op= (ADD | SUB) expr #BinaryExpr //
    | expr op= (LESS | GREATER) expr #BinaryExpr //
    | expr op= AND expr #BinaryExpr //
    | expr op= OR expr #BinaryExpr //
    | value= INTEGER #IntegerLiteral //
    | value= TRUE #BooleanLiteral //
    | value= FALSE #BooleanLiteral  //
    | name= ID #VarRefExpr //
    ;




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

COMMENT_MULTILINE : '/*' .*? '*/' -> skip;
COMMENT_EOL : '//' .*? '\n' -> skip;
WS : [ \t\n\r\f]+ -> skip ;

program
    : importDecl* classDecl EOF
    ;

importDecl
    : IMPORT (value+=ID | value+=MAIN | value+=LENGTH) (MEMBERCALL (value+=ID | value+=MAIN | value+=LENGTH))* SEMI
    ;


classDecl
    : CLASS (className=ID | className=MAIN | className=LENGTH) (EXTENDS( extendClassName= ID | extendClassName=MAIN | extendClassName=LENGTH))?
        LCURLY
        varDecl*
        methodDecl*
        RCURLY
    ;

varDecl
    : type (name=ID | name=MAIN | name=LENGTH) SEMI
    ;

type
    : type LSQPAREN RSQPAREN #Array //
    | INT name=ELLIPSIS #EllipsisType //
    | name=BOOLEAN #BooleanType //
    | name=INT #IntegerType //
    | name=STRING #StringType //
    | (name=ID| name= LENGTH | name= MAIN) #ClassType //
    ;

methodDecl locals[boolean isPublic=false, boolean isStatic=false]
    : (PUBLIC {$isPublic=true;})?
        returnType name=ID
        LPAREN (param (COMMA param)*)? RPAREN
        LCURLY varDecl* stmt* returnStmt RCURLY
    | (PUBLIC {$isPublic=true;})?
       (STATIC {$isStatic=true;})?
       VOID name=MAIN
       LPAREN STRING LSQPAREN RSQPAREN ID RPAREN
       LCURLY varDecl* stmt* RCURLY
    ;

returnType
    : name= type
    ;

returnStmt
    : RETURN expr SEMI
    ;

param
    : type (name=ID| name= LENGTH | name= MAIN)
    ;

stmt
    : expr SEMI #ExprStmt //
    | LCURLY stmt* RCURLY #StmtScope //
    | IF LPAREN expr RPAREN stmt ELSE stmt #IfElseStmt //
    | WHILE LPAREN expr RPAREN stmt #WhileStmt //
    | (var= ID | var= LENGTH | var= MAIN) EQUALS expr SEMI #AssignStmt //
    | (var= ID | var= LENGTH | var= MAIN) LSQPAREN expr RSQPAREN EQUALS expr SEMI #ArrayAssign //
    ;

expr
    : LPAREN expr RPAREN #Paren //
    | LSQPAREN (expr (COMMA expr)*)? RSQPAREN #ArrayInit //
    | expr LSQPAREN expr RSQPAREN #ArrayAccess //
    | expr MEMBERCALL LENGTH #Length //
    | expr MEMBERCALL name= ID (LPAREN (expr (COMMA expr)*)? RPAREN)? #FunctionCall //
    | expr LPAREN expr RPAREN #MemberCall //
    | value= THIS #Object //
    | value= NOT expr #Negation //
    | NEW INT LSQPAREN expr RSQPAREN #NewArray //
    | NEW name= ID LPAREN RPAREN #NewClass //
    | expr op= (MUL | DIV) expr #BinaryOp //
    | expr op= (ADD | SUB) expr #BinaryOp //
    | expr op= (LESS | GREATER) expr #BinaryOp //
    | expr op= AND expr #BinaryOp //
    | expr op= OR expr #BinaryOp //
    | value= INTEGER #IntegerLiteral //
    | value= (TRUE | FALSE) #BooleanLiteral //
    | (name= ID | name= LENGTH | name= MAIN) #VarRefExpr //
    ;




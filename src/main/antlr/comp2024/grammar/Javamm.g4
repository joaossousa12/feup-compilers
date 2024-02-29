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
    : IMPORT value+=ID (MEMBERCALL value+=ID)* SEMI
    ;


classDecl
    : CLASS className=ID (EXTENDS extendClassName= ID)?
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
    : expr SEMI #ExprStmt //
    | LCURLY stmt* RCURLY #StmtScope //
    | IF LPAREN expr RPAREN stmt ELSE stmt #IfElseStmt //
    | WHILE LPAREN expr RPAREN stmt #WhileStmt //
    | var= ID EQUALS expr SEMI #AssignStmt //
    | var= ID LSQPAREN expr RSQPAREN EQUALS expr SEMI #ArrayAssign //
    | RETURN expr SEMI #ReturnStmt
    ;

expr
    : LPAREN expr RPAREN #Paren //
    | LSQPAREN (expr (COMMA expr)*)? RSQPAREN #ArrayInit //
    | expr LSQPAREN expr RSQPAREN #ArrayAccess //
    | expr MEMBERCALL LENGTH #Length //
    | expr MEMBERCALL name= ID LPAREN (expr (COMMA expr)*)? RPAREN #FunctionCall //
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
    | name= ID #VarRefExpr //
    ;




grammar GQL;

// ====== Parser Rules ======
gqlProgram : statement (K_NEXT statement)* EOF ;

statement : queryStatement | mutationStatement ;

queryStatement : queryConjunctor (K_UNION K_ALL? queryConjunctor)* ;

queryConjunctor : matchClause* whereClause? returnClause orderByClause? skipClause? limitClause? ;

matchClause : K_OPTIONAL? K_MATCH patternList ;

patternList : pattern (',' pattern)* ;

pattern : nodePattern (edgePattern nodePattern)* ;

nodePattern : '(' variable? labelExpression? propertyMap? ')' ;

edgePattern : leftArrow? '-' bracketPattern? '-' rightArrow? 
            | leftArrow? '-' '-' rightArrow?
            ;

leftArrow : '<' ;
rightArrow : '>' ;

bracketPattern : '[' variable? typeExpression? quantifier? propertyMap? ']' ;

labelExpression : ':' identifier (('&' | ':') identifier)* ;

typeExpression : ':' identifier ('|' identifier)* ;

quantifier : '*'
           | '*' INT
           | '*' DOTDOT INT
           | '*' INT DOTDOT
           | '*' INT DOTDOT INT
           ;

propertyMap : '{' propertyKeyValue (',' propertyKeyValue)* '}' ;

propertyKeyValue : identifier ':' expression ;

whereClause : K_WHERE expression ;

returnClause : K_RETURN K_DISTINCT? ('*' | returnItem (',' returnItem)*) ;

returnItem : expression (K_AS identifier)? ;

orderByClause : K_ORDER K_BY sortItem (',' sortItem)* ;

sortItem : expression (K_ASC | K_DESC | K_ASCENDING | K_DESCENDING)? ;

skipClause : K_SKIP expression ;

limitClause : K_LIMIT expression ;

mutationStatement : matchClause* whereClause? mutationClause+ returnClause? ;

mutationClause : insertClause | setClause | deleteClause ;

insertClause : K_INSERT patternList ;

setClause : K_SET setItem (',' setItem)* ;

setItem : propertyAccess '=' expression  # SetProperty
        | variable labelExpression       # SetLabel
        ;

deleteClause : K_DETACH? K_DELETE expression (',' expression)* ;

expression : orExpr ;

orExpr : andExpr (K_OR andExpr)* ;

andExpr : xorExpr (K_AND xorExpr)* ;

xorExpr : notExpr (K_XOR notExpr)* ;

notExpr : K_NOT notExpr
        | comparison
        ;

comparison : addSub (cmpOp addSub)* ;

cmpOp : '=' | '<>' | '<' | '<=' | '>' | '>=' ;

addSub : mulDivMod (addOp mulDivMod)* ;

addOp : '+' | '-' ;

mulDivMod : unary (mulOp unary)* ;

mulOp : '*' | '/' | '%' ;

unary : ('+' | '-') unary
      | postfix
      ;

postfix : atom (K_IS K_NOT? K_NULL)? (K_IN listLiteral)? ;

atom : literal
     | functionCall
     | propertyAccess
     | variable
     | '(' expression ')'
     ;

literal : STRING_LITERAL
        | FLOAT
        | INT
        | K_TRUE
        | K_FALSE
        | K_NULL
        | listLiteral
        ;

listLiteral : '[' (expression (',' expression)*)? ']' ;

variable : identifier ;

propertyAccess : identifier '.' identifier ;

functionCall : identifier '(' K_DISTINCT? ('*' | expression (',' expression)*)? ')' ;

identifier : IDENTIFIER | QUOTED_IDENTIFIER ;

// ====== Lexer Rules ======
// Explicit traps

DOTDOT : '..' ;

// Keywords (case insensitive)
fragment A: [aA]; fragment B: [bB]; fragment C: [cC]; fragment D: [dD]; fragment E: [eE];
fragment F: [fF]; fragment G: [gG]; fragment H: [hH]; fragment I: [iI]; fragment J: [jJ];
fragment K: [kK]; fragment L: [lL]; fragment M: [mM]; fragment N: [nN]; fragment O: [oO];
fragment P: [pP]; fragment Q: [qQ]; fragment R: [rR]; fragment S: [sS]; fragment T: [tT];
fragment U: [uU]; fragment V: [vV]; fragment W: [wW]; fragment X: [xX]; fragment Y: [yY];
fragment Z: [zZ];

K_MATCH : M A T C H ;
K_OPTIONAL : O P T I O N A L ;
K_WHERE : W H E R E ;
K_RETURN : R E T U R N ;
K_DISTINCT : D I S T I N C T ;
K_ORDER : O R D E R ;
K_BY : B Y ;
K_ASC : A S C ;
K_DESC : D E S C ;
K_ASCENDING : A S C E N D I N G ;
K_DESCENDING : D E S C E N D I N G ;
K_SKIP : S K I P ;
K_LIMIT : L I M I T ;
K_AND : A N D ;
K_OR : O R ;
K_NOT : N O T ;
K_XOR : X O R ;
K_IS : I S ;
K_NULL : N U L L ;
K_IN : I N ;
K_AS : A S ;
K_INSERT : I N S E R T ;
K_SET : S E T ;
K_DELETE : D E L E T E ;
K_DETACH : D E T A C H ;
K_NEXT : N E X T ;
K_UNION : U N I O N ;
K_ALL : A L L ;
K_TRUE : T R U E ;
K_FALSE : F A L S E ;

// Identifier
IDENTIFIER : [a-zA-Z_] [a-zA-Z0-9_]* ;
QUOTED_IDENTIFIER : '`' ~'`'* '`' ;

// Literals
INT : DIGIT+ ;
FLOAT : DIGIT+ '.' DIGIT+ ;
fragment DIGIT : [0-9] ;

STRING_LITERAL : '\'' ( ~['\\] | '\\' . | '\'\'' )* '\''
               | '"'  ( ~["\\] | '\\' . | '""'   )* '"'
               ;

// Whitespace and Comments
WS : [ \t\r\n]+ -> skip ;
LINE_COMMENT : '//' ~[\r\n]* -> skip ;
BLOCK_COMMENT : '/*' .*? '*/' -> skip ;

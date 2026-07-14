# Phase 3: GQL Parser (ANTLR4)

**Goal:** Parse the BlazeGraph GQL subset into a typed, parser-technology-agnostic AST with precise error reporting. Output of this phase: `parse(String gql) → GqlProgram` (AST root) or a `SyntaxException` carrying line/column and a readable message.

**Depends on:** nothing at runtime (`parser` module is self-contained; it does NOT depend on `core`). Phase 4 bridges AST → storage.

---

## 1. Module Setup

### [NEW] `parser/pom.xml`
- New Maven module `blazegraph-parser`; add to root `<modules>`.
- Dependencies: `org.antlr:antlr4-runtime:4.13.1` (only runtime dep in the module).
- Build plugin: `antlr4-maven-plugin` (same version), configured with `<visitor>true</visitor>`, `<listener>false</listener>`. Grammar lives in `src/main/antlr4/blazegraph/parser/GQL.g4`; generated sources land in `target/generated-sources/antlr4` (picked up automatically).

---

## 2. Supported GQL Subset (frozen for v1)

| Category | Syntax |
|---|---|
| Query | `MATCH`, `OPTIONAL MATCH`, `WHERE`, `RETURN [DISTINCT]`, `ORDER BY … [ASC\|DESC]`, `SKIP n`, `LIMIT n` |
| Node patterns | `()`, `(n)`, `(n:Person)`, `(n:Person {name: 'Alice'})`, multi-label `(n:A&B)` (also accept `:A:B`) |
| Edge patterns | `-[r:TYPE]->`, `<-[r:TYPE]-`, `-[r]-` (any direction), abbreviated `-->`, `<--`, `--`; type alternation `[r:A\|B]` |
| Quantified paths | `-[r:KNOWS*]->`, `*1..3`, `*..3`, `*2..` (bounded default max, see §4.3) |
| Expressions | property access `n.name`, literals (string/int/double/bool/null/list), `+ - * / %`, comparisons `= <> < <= > >=`, `AND OR NOT XOR`, `IN`, `IS [NOT] NULL`, parenthesized, unary minus |
| Aggregates | `COUNT(*)`, `COUNT(expr)`, `SUM`, `AVG`, `MIN`, `MAX`, `COLLECT`, with `DISTINCT` arg modifier |
| Return items | `RETURN expr [AS alias]`, `RETURN *` |
| Mutation | `INSERT (n:Person {…})-[:KNOWS]->(m)`, `SET n.prop = expr`, `SET n:Label`, `DELETE n`, `DETACH DELETE n` (mutations may be preceded by `MATCH`) |
| Composition | `stmt NEXT stmt`, `UNION [ALL]` between queries |
| Misc | line comments `//`, block `/* */`, keywords case-insensitive, identifiers case-sensitive, backtick-quoted identifiers `` `weird name` `` |

**Explicitly out of scope for v1** (reject with clear error): parameters (`$x`), `CALL`, subqueries `EXISTS {…}`, graph/catalog/session statements, `FILTER`, `LET`, `FOR`, path variables (`p = (a)-[…]->(b)`), `CASE`. Grammar should not silently accept these.

---

## 3. Grammar — `parser/src/main/antlr4/blazegraph/parser/GQL.g4`

Hand-written subset grammar (do **not** attempt to transcribe the full ISO 39075 BNF — it is enormous and left-recursive; a focused ~300-line grammar is more maintainable). Consult the OpenGQL community ANTLR grammar for reference on tricky constructs, but write our own.

### Parser rule inventory
```
gqlProgram      : statement (NEXT statement)* EOF ;
statement       : queryStatement | mutationStatement ;
queryStatement  : queryConjunctor (UNION ALL? queryConjunctor)* ;
queryConjunctor : matchClause+ whereClause? returnClause orderByClause? skipClause? limitClause? ;
matchClause     : OPTIONAL? MATCH patternList ;
patternList     : pattern (',' pattern)* ;
pattern         : nodePattern (edgePattern nodePattern)* ;
nodePattern     : '(' variable? labelExpression? propertyMap? ')' ;
edgePattern     : (fully bracketed and abbreviated forms, 3 directions, quantifier?) ;
labelExpression : ':' labelTerm (('&' | ':') labelTerm)* ;   // conjunction
quantifier      : '*' (INT? ('..' INT?)?)? ;
propertyMap     : '{' propertyKeyValue (',' propertyKeyValue)* '}' ;
whereClause     : WHERE expression ;
returnClause    : RETURN DISTINCT? ('*' | returnItem (',' returnItem)*) ;
returnItem      : expression (AS identifier)? ;
orderByClause   : ORDER BY sortItem (',' sortItem)* ;
mutationStatement : (matchClause+ whereClause?)? (insertClause | setClause | deleteClause)+ returnClause? ;
insertClause    : INSERT patternList ;
setClause       : SET setItem (',' setItem)* ;               // n.p = expr | n:Label
deleteClause    : DETACH? DELETE expression (',' expression)* ;
expression      : precedence-layered: orExpr → xorExpr → andExpr → notExpr
                  → comparison → addSub → mulDivMod → unary → postfix(IS NULL, IN) → atom ;
atom            : literal | variable | propertyAccess | functionCall | '(' expression ')' | listLiteral ;
```

### Known lexer traps (handle explicitly, add tests for each)
1. **Range vs float ambiguity**: in `*1..3`, the lexer must not tokenize `1.` as a float. Fix by ordering: define `DOTDOT: '..';` and make the float rule require a digit after the dot (`DIGIT+ '.' DIGIT+`). `RETURN 1.` is then a syntax error — acceptable.
2. **Case-insensitive keywords**: use fragment-letter idiom (`fragment A: [aA];` … `MATCH: M A T C H;`). Keywords are reserved; back-ticked identifiers escape them.
3. **String literals**: single OR double quotes, escapes `\' \" \\ \n \t`, doubled-quote escape (`'it''s'`). Normalize in the AST builder, not the grammar.
4. **`-` ambiguity**: `-->` / `--` (edge) vs arithmetic minus vs comment `//`… ANTLR handles via longest-match, but add parser tests for `RETURN a.x-1` and `(a)--(b)`.

---

## 4. AST — `parser/src/main/java/blazegraph/parser/ast/`

Plain immutable Java classes (records where possible). **No ANTLR types leak out of the parser package** — Phase 4 must compile against the AST only, so the parser tech can be swapped.

### Inventory
| Class | Key fields |
|---|---|
| `GqlProgram` | `List<Statement> statements` (NEXT-chained) |
| `QueryStatement` | `List<MatchClause>`, `WhereClause?`, `ReturnClause`, `OrderByClause?`, `skip?`, `limit?`, `List<UnionArm>` |
| `MutationStatement` | optional match part + `List<MutationClause>` (Insert/Set/Delete) + optional return |
| `MatchClause` | `boolean optional`, `List<PathPattern>` |
| `PathPattern` | `List<NodePatternAst>`, `List<EdgePatternAst>` (alternating) |
| `NodePatternAst` | `String? variable`, `List<String> labels`, `Map<String, Expression> properties` |
| `EdgePatternAst` | `String? variable`, `List<String> types` (alternation), `Direction (LEFT/RIGHT/ANY)`, `Quantifier? (minHops, maxHops)`, `Map<String, Expression> properties` |
| `ReturnClause` | `boolean distinct`, `boolean star`, `List<ReturnItem>(expr, alias?)` |
| `Expression` (sealed) | `Literal(value, type)`, `ListLiteral`, `Variable(name)`, `PropertyAccess(subject, key)`, `BinaryOp(op, l, r)`, `UnaryOp`, `FunctionCall(name, distinct, args)`, `IsNull(expr, negated)`, `InList(expr, list)` |
| `SetItem` (sealed) | `SetProperty(variable, key, expr)` \| `SetLabel(variable, label)` |
| `DeleteClause` | `boolean detach`, `List<Expression> targets` |
| `SortItem` | `Expression`, `boolean ascending` |

Every AST node carries `line`/`column` (from the ANTLR token) for downstream error messages.

### [NEW] `parser/GqlParser.java` — public facade
- `static GqlProgram parse(String gql)` — wires lexer → parser → `GqlAstBuilder`.
- Installs a custom `ANTLRErrorListener` that **collects** all syntax errors and throws one `SyntaxException` with them (message format: `line 3:14 — mismatched input ')' expecting …`). Remove the default console listener.
- Use default (non-bail) error strategy so multiple errors surface at once.

### [NEW] `parser/GqlAstBuilder.java`
- Extends the generated `GQLBaseVisitor<Object>`; one `visitX` per rule producing AST nodes.
- Performs literal normalization (string unescaping, number parsing with overflow → `SyntaxException`).

---

## 5. Verification

### Unit tests (`parser/src/test/java/…`)
- **`GqlParserCorpusTest`** — parameterized over `src/test/resources/corpus/valid/*.gql`: every file must parse without error. Seed corpus ≥ 40 queries covering every §2 row (this corpus is reused by Phase 4 end-to-end tests).
- **`GqlParserErrorTest`** — parameterized over `corpus/invalid/*.gql`: must throw `SyntaxException`; assert line numbers on a few. Include the out-of-scope constructs (`$param`, `CALL`, `CASE`) to lock in explicit rejection.
- **`AstShapeTest`** — for ~12 representative queries, assert the exact AST structure (labels parsed, direction, quantifier bounds, operator precedence: `1+2*3` and `NOT a AND b` shapes, alias handling, DISTINCT flags).
- **Lexer-trap tests** — `*1..3` vs `1.5`, quoted identifiers, escaped strings, comments, case-insensitivity.

### Acceptance criteria
- [ ] `mvn test -pl parser` green; corpus ≥ 40 valid + ≥ 15 invalid queries.
- [ ] No ANTLR type appears in any public signature outside `blazegraph.parser` internals.
- [ ] Parse of a simple MATCH < 10 ms after JIT warm-up (sanity check, not JMH).

## Estimated effort
| Task | Effort |
|---|---|
| Module + ANTLR build setup | 0.5 day |
| Grammar (incl. lexer traps) | 1.5–2 days |
| AST classes + builder | 1–1.5 days |
| Error handling + facade | 0.5 day |
| Test corpus + tests | 1 day |
| **Total** | **~4.5–5.5 days** |

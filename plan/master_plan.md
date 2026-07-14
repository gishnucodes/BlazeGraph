# BlazeGraph — Master Plan

An in-memory property graph database handling **100K nodes and relationships**, with a **GQL (ISO/IEC 39075) query interpreter**. Implementation language: **Java 17+** (chosen; Phase 1 already implemented in Java).

This is the index document. Each phase has a detailed execution plan in this directory:

| Phase | Plan | Status |
|---|---|---|
| 1 — Core Storage Engine | [phase_1.md](phase_1.md) | ✅ Implemented — ⚠️ untested; hardening tasks pending (see Phase 1 §5) |
| 2 — Traversal & Pattern Matching | [phase_2.md](phase_2.md) | Planned |
| 3 — GQL Parser (ANTLR4) | [phase_3.md](phase_3.md) | Planned |
| 4 — Query Execution Engine | [phase_4.md](phase_4.md) | Planned |
| 5 — REPL, CLI & HTTP Server | [phase_5.md](phase_5.md) | Planned |
| 6 — Advanced Features | [phase_6.md](phase_6.md) | Backlog |

---

## Resolved Decisions

Defaults recorded so execution never blocks on an open question. Change here if you disagree; each phase plan assumes these.

| Question | Decision | Rationale |
|---|---|---|
| Language | **Java 17+** | Already implemented; ANTLR4 first-class support |
| Persistence | **Pure in-memory for v1** | Snapshot/restore deferred to Phase 6 |
| Concurrency | **Single-writer / multi-reader** | Structures are thread-safe (`ConcurrentHashMap`), but compound operations (createEdge, deleteNode, index updates) are not atomic. v1 documents this contract; real transactions are Phase 6 (MVCC or global RW-lock first) |
| API surface | **Embedded library + REPL/CLI first, HTTP server in Phase 5** | gRPC/Bolt-style protocol out of scope |
| Visualization | **Deferred** (Phase 6 backlog) | |
| GQL scope | **Practical subset** (see Phase 3) — MATCH/WHERE/RETURN/ORDER BY/SKIP/LIMIT, INSERT/SET/DELETE, aggregation, UNION, NEXT | Full ISO catalog/session/graph-type features excluded |
| JSON / CLI libs | **Jackson** (server module only), **picocli** (cli module only) | Keep `core`/`parser`/`engine` dependency-free (ANTLR runtime excepted) |

---

## Cross-Cutting Design Contracts

These apply to every phase and are referenced by the phase plans.

### Value system & NULL semantics
- Runtime values in query evaluation: `Node`, `Edge`, `Path`, `PropertyValue` (scalar), `List<Value>`, and **NULL**.
- **Three-valued logic**: any comparison involving NULL yields UNKNOWN; `WHERE` keeps only TRUE rows. `IS NULL` / `IS NOT NULL` are the explicit tests.
- **Numeric coercion**: INTEGER and DOUBLE compare numerically (`30 = 30.0` is TRUE) in *query evaluation*. Note: `PropertyValue.equals()` is type-strict (correct for index keys); the query engine must use a dedicated `ValueComparator` — never `PropertyValue.equals()`/`compareTo()` — for GQL comparison semantics. Defined in Phase 4.
- **ORDER BY**: NULL sorts last (ascending); mixed types order by type precedence, then value.

### Error taxonomy
Single hierarchy in `core`: `BlazeGraphException` → `SyntaxException` (parse errors, with line/column), `SemanticException` (unbound variable, type mismatch detected at plan time), `ExecutionException` (runtime failures). User-facing surfaces (REPL/HTTP) render these; they never leak stack traces of internal errors.

### Concurrency contract (v1)
- Reads may run on any thread concurrently with each other.
- Writes (create/delete/set) must be externally serialized by the caller (the REPL/server executes statements one at a time).
- Iteration during traversal is weakly consistent; a concurrent write may or may not be observed. Documented in `PropertyGraphStore` Javadoc as part of Phase 1 hardening.

### Module dependency graph
```
core  ←  engine  →  parser
           ↑
   server / cli  (both depend on engine)
benchmark → engine (and core)
```
`parser` does not depend on `core` — the AST is self-contained; `engine` bridges AST ↔ storage.

### Repo hygiene (do at start of next execution session)
- `git init` — the project is **not under version control** (high risk). Add `.gitignore` (`target/`, `.idea/`, `*.iml`, `.DS_Store`). Commit Phase 1 as-is before hardening so changes are reviewable.
- `README.md` with build/run instructions.
- Environment: Java 17+ present (Java 23 installed); **Maven is not installed** — `brew install maven` is a prerequisite for everything below.

---

## Verification Strategy

- **Per-phase gate**: each phase plan ends with acceptance criteria; a phase is done only when `mvn test` is green for all modules and the phase's criteria pass.
- **Regression corpus** (from Phase 3 on): a directory of `.gql` files with expected results (golden files) executed as parameterized tests — grows every phase and every bug fix.
- **Shared fixture**: a movie-graph dataset builder (`TestGraphFixture` in core test-jar) used by Phases 2, 4, 5 tests — one definition, reused.
- **Benchmarks**: `benchmark` module (Phase 4+) using JMH or a warmed-up harness; targets:

| Operation | Target |
|---|---|
| Node creation (100K) | < 500 ms |
| Edge creation (100K) | < 1 s |
| Single-hop pattern match (100K graph) | < 10 ms |
| 3-hop pattern match (100K graph) | < 100 ms |
| Shortest path (100K graph) | < 50 ms |
| GQL parse + execute (simple MATCH) | < 10 ms |
| Memory at 100K nodes + 100K edges | < 150 MB |

---

## Execution Order

1. **Phase 1 hardening** (½–1 day): fix build, add missing tests, close code-review findings — see phase_1.md §5. *Do this before Phase 2; everything builds on it.*
2. Phase 2 (~4–5 days) → Phase 3 (~4–5 days) → Phase 4 (~5–6 days) → Phase 5 (~2–3 days).
3. Phase 6 items are pulled individually from the backlog after v1 works end-to-end.

Total to v1 (Phases 1h–5): **~16–20 working days**.

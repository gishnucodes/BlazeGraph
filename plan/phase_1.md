# Phase 1: Core Storage Engine

**Goal:** Build a thread-safe, in-memory property graph data model optimized for managing up to 100K nodes and relationships. The storage will use **index-free adjacency** (direct object references between nodes and edges) to enable O(1) neighbor traversals.

## 1. Core Data Model

We will build the data structures using plain Java objects (POJOs), leveraging thread-safe collections from `java.util.concurrent` to allow for parallel read/write operations in the future.

### `PropertyValue`
A type-safe wrapper for property values to ensure type consistency and ease of serialization.
- **Supported Types:** `String`, `Integer`, `Long`, `Double`, `Boolean`, `List`, `Map`.
- **Implementation:** Abstract base class or an interface with concrete implementations for each type.

### `Node`
Represents an entity in the graph.
- **Fields:**
  - `id` (long): Unique immutable identifier.
  - `labels` (`Set<String>`): Thread-safe set of labels (e.g., `Person`, `Movie`).
  - `properties` (`Map<String, PropertyValue>`): Thread-safe map of properties.
  - `outEdges` (`List<Edge>`): Thread-safe list of outgoing edges.
  - `inEdges` (`List<Edge>`): Thread-safe list of incoming edges.

### `Edge`
Represents a directed relationship between two nodes.
- **Fields:**
  - `id` (long): Unique immutable identifier.
  - `type` (`String`): The relationship type (e.g., `KNOWS`).
  - `properties` (`Map<String, PropertyValue>`): Thread-safe map of properties.
  - `sourceNode` (`Node`): Direct reference to the origin node.
  - `targetNode` (`Node`): Direct reference to the destination node.

## 2. Graph Storage System

### `PropertyGraphStore`
The central registry managing the graph elements and orchestrating ID generation.
- **Fields:**
  - `nodeIdCounter` (`AtomicLong`): Generates unique node IDs.
  - `edgeIdCounter` (`AtomicLong`): Generates unique edge IDs.
  - `nodes` (`ConcurrentHashMap<Long, Node>`): Global node lookup by ID.
  - `edges` (`ConcurrentHashMap<Long, Edge>`): Global edge lookup by ID.
  - `labelIndex` (`LabelIndex`): Manager for node labels.
  - `typeIndex` (`TypeIndex`): Manager for edge types.
  - `propertyIndex` (`PropertyIndex`): Manager for indexed properties.

- **Key Methods:**
  - `createNode(Set<String> labels) -> Node`
  - `createEdge(Node source, Node target, String type) -> Edge`
  - `deleteNode(long id)`: Must also cascade delete attached edges.
  - `deleteEdge(long id)`: Must safely remove references from the source/target nodes.
  - `getNode(long id) -> Optional<Node>`
  - `getEdge(long id) -> Optional<Edge>`

## 3. Indexing Subsystem

To facilitate fast lookups without full-graph scans, we need specialized in-memory indices.

### `LabelIndex`
Maps node labels to sets of node IDs.
- **Structure:** `ConcurrentHashMap<String, ConcurrentHashMap.KeySetView<Long, Boolean>>`
- **Purpose:** Quick retrieval for queries like `MATCH (n:Person)`.

### `TypeIndex`
Maps edge types to sets of edge IDs.
- **Structure:** `ConcurrentHashMap<String, ConcurrentHashMap.KeySetView<Long, Boolean>>`
- **Purpose:** Fast lookup for global edge queries.

### `PropertyIndex`
Maps specific properties to elements containing them.
- **Structure:** `ConcurrentHashMap<String, ConcurrentHashMap<PropertyValue, ConcurrentHashMap.KeySetView<Long, Boolean>>>`
- **Purpose:** Fast retrieval for queries like `MATCH (n:Person {name: 'Alice'})`.

## 4. Phase 1 Verification Plan

### Unit Tests
- `NodeTest`, `EdgeTest`: Verify property and label mutations.
- `PropertyGraphStoreTest`:
  - Verify ID generation monotonicity.
  - Test `createNode` and `createEdge` linkages.
  - Test cascading deletes (deleting a node removes all connected edges from the graph and from the neighbor nodes).
- `IndexTest`: Verify indices update correctly upon node/edge property/label changes.

### Benchmark
- Create a `Phase1Benchmark` application to inject 100K nodes and 200K edges.
- Measure insertion throughput and memory footprint to confirm it comfortably stays within our memory budget (~100 MB).

---

## 5. Post-Implementation Review (2026-07-14) — Status & Hardening Tasks

**Status: implemented, never built or tested.** Code review of the actual sources found the following. Complete this hardening pass before starting Phase 2.

### 5.1 Build is broken — blocking

1. **Maven is not installed** on this machine. Prerequisite: `brew install maven`.
2. **Root `pom.xml` has a bad surefire artifactId**: `maven-plugins-maven-surefire-plugin` does not exist — plugin resolution fails and `mvn test` cannot run. Fix to:
   ```xml
   <groupId>org.apache.maven.plugins</groupId>
   <artifactId>maven-surefire-plugin</artifactId>
   ```
3. Stray `target/` directory at repo root (from a direct `javac` run?) — delete it; add `.gitignore` and `git init` per master plan.

### 5.2 Deviations from this plan

1. **`PropertyValue` is missing `LIST` and `MAP` types** (plan §1 promised them; Phase 3 list literals and Phase 4 `COLLECT()` need at least LIST). Add `LIST` now (`List<PropertyValue>`, defensively copied, immutable); defer MAP until a concrete need.
2. **No `NULL` representation.** Add a `PropertyValue.NULL` singleton (type `NULL`) — Phase 4's three-valued logic needs a NULL that can flow through expressions. Note: absent property ≠ property set to NULL; `getProperty` returning Java `null` still means "absent".
3. **Planned test files missing**: only `PropertyGraphStoreTest` exists. Add `NodeTest`, `EdgeTest`, `PropertyValueTest`, and `IndexTest` (label/type/property index units, including the empty-set-removal paths).
4. **`ScaleBenchmark` lives in the test source set with a `main()`** — it is never executed by `mvn test` and doesn't belong there. Move to the `benchmark` module when it's created (Phase 4); until then leave but exclude from surefire if it grows dependencies.

### 5.3 Code findings (fix or explicitly accept)

1. **`PropertyValue.compareTo`/`equals` are type-strict** — `INTEGER(30)` ≠ `DOUBLE(30.0)`, and cross-type compare orders by enum. Correct for index hash keys; **wrong for GQL comparison semantics**. Decision (see master plan): keep as-is, and Phase 4 introduces `ValueComparator` for query-level comparisons. Add a Javadoc warning on `compareTo` now so it isn't misused later.
2. **Compound operations are not atomic** (`createEdge` links nodes then registers the edge; `createNode` indexes labels before `nodes.put`; `deleteNode` removes the node before unlinking edges). Under concurrent writes this can produce dangling references or index entries. Accepted for v1 under the single-writer contract — **document it in `PropertyGraphStore` class Javadoc**.
3. **Index empty-bucket cleanup race**: `LabelIndex/TypeIndex/PropertyIndex.remove` do `index.remove(key, emptySet)`; a writer holding a reference to the just-removed set can add to it and the entry is lost. Harmless under single-writer; covered by the same documented contract.
4. **Silent no-ops on missing IDs**: `setNodeProperty`/`addNodeLabel`/etc. do nothing when the node doesn't exist. Fine for now, but Phase 4 mutation operators must check existence themselves to report accurate "N properties set" stats — or change these to return `boolean`. Recommendation: return `boolean`.
5. `deleteNode` iterates `getOutEdges()` while `deleteEdge` mutates the underlying set — safe because these are `ConcurrentHashMap` key-set views (weakly consistent iterators), but worth a comment; with `HashSet` this would throw `ConcurrentModificationException`.

### 5.4 Missing convenience APIs (Phase 2 will want these)

- `PropertyGraphStore.getNodesByLabel(label)` returns `Set<Long>`; add `streamNodesByLabel(label) → Stream<Node>` (resolve IDs internally) so callers don't hand-roll ID resolution.
- `nodeCount()` / `edgeCount()` accessors (REPL `:stats`, benchmarks).

### 5.5 Acceptance criteria for Phase 1 closure

- [ ] `mvn test` runs and is green (build fixed, Maven installed).
- [ ] All §5.2 test files exist; cascade-delete, index-update-on-label/property-change, and update/remove paths covered.
- [ ] `PropertyValue` supports LIST and NULL, with tests.
- [ ] Concurrency contract documented in `PropertyGraphStore` Javadoc.
- [ ] `git init` done, `.gitignore` in place, Phase 1 committed.
- [ ] `ScaleBenchmark` run manually once: 100K nodes + 100K edges < 1.5 s total, memory < 150 MB.

**Estimated effort: ½–1 day.**

# BlazeGraph

BlazeGraph is a high-performance, in-memory graph database.

## Quickstart

### Build
You will need JDK 17+ and Maven. Build the project from the root directory:
```bash
mvn clean install -DskipTests
```

### CLI & REPL
The CLI module builds a self-contained executable jar. You can run the interactive REPL:
```bash
java -jar cli/target/blazegraph-cli-1.0-SNAPSHOT.jar
```
Or execute a query directly:
```bash
java -jar cli/target/blazegraph-cli-1.0-SNAPSHOT.jar -q "MATCH (n) RETURN n"
```

### Bulk Import
To import CSV files before dropping into the REPL:
```bash
java -jar cli/target/blazegraph-cli-1.0-SNAPSHOT.jar --import-nodes nodes.csv --import-edges edges.csv
```

### HTTP Server
To start the HTTP server on port 7474:
```bash
java -jar cli/target/blazegraph-cli-1.0-SNAPSHOT.jar serve --port 7474
```
You can then post queries via HTTP:
```bash
curl -X POST http://localhost:7474/query -H "Content-Type: application/json" -d '{"query": "MATCH (n) RETURN n"}'
```

## Supported GQL Syntax

BlazeGraph supports a comprehensive subset of standard GQL (Graph Query Language). Note that BlazeGraph is strictly typed and adheres to Three-Valued Logic (TRUE, FALSE, UNKNOWN) for boolean evaluations.

### Pattern Matching
Extract nodes and edges using Cypher-like pattern matching.
```cypher
// Match all nodes
MATCH (n) RETURN n

// Match nodes with a specific label
MATCH (p:Person) RETURN p

// Match nodes with specific property constraints
MATCH (p:Person {name: 'Alice', age: 30}) RETURN p

// Match a path with directed edges
MATCH (a:Person)-[e:KNOWS]->(b:Person) RETURN a, e, b

// Match an undirected or bidirectional edge
MATCH (a)-[e:KNOWS]-(b) RETURN a, e, b

// Optional matching
OPTIONAL MATCH (p:Person)-[:KNOWS]->(friend) RETURN p.name, friend.name
```

### Mutations
Insert, update, and delete elements in the graph.
```cypher
// Insert a new node with multiple labels and properties
INSERT (n:Person:Developer {name: 'Alice', age: 30})

// Insert edges between existing nodes
MATCH (a:Person {name: 'Alice'}), (b:Person {name: 'Bob'})
INSERT (a)-[:KNOWS {since: 2021}]->(b)

// Update properties and labels
MATCH (p:Person {name: 'Alice'})
SET p.age = 31
SET p:Senior

// Delete properties and labels
MATCH (p:Person)
SET p.age = null
SET p:Senior = null

// Delete nodes and edges
MATCH (p:Person {name: 'Bob'})-[e:KNOWS]->()
DELETE e, p
```

### Filtering (WHERE)
Filter results using complex boolean expressions, property access, and comparison operators.
```cypher
MATCH (p:Person)
WHERE p.age > 20 AND p.age <= 50 OR p.name = 'Alice'
RETURN p
```

### Projection and Aggregation (RETURN)
Format, project, and aggregate the results.
```cypher
// Basic projection with aliases
MATCH (p:Person) RETURN p.name AS fullName, p.age

// DISTINCT results
MATCH (p:Person) RETURN DISTINCT p.age

// Aggregation functions (COUNT, SUM, AVG, MIN, MAX)
MATCH (p:Person) RETURN p.city, COUNT(p) AS population, AVG(p.age) AS averageAge
```

### Result Ordering & Pagination
Sort and paginate results using `ORDER BY`, `SKIP`, and `LIMIT`.
```cypher
MATCH (p:Person)
RETURN p.name, p.age
ORDER BY p.age DESC, p.name ASC
SKIP 10
LIMIT 5
```

### Set Operations (UNION)
Combine results from multiple queries.
```cypher
MATCH (p:Person {city: 'New York'}) RETURN p.name AS name
UNION ALL
MATCH (p:Person {city: 'London'}) RETURN p.name AS name
```

package blazegraph.engine;

import blazegraph.core.storage.PropertyGraphStore;
import blazegraph.engine.result.QueryResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class QueryEngineTest {
    @Test
    public void testBasicInsertAndMatch() {
        PropertyGraphStore store = new PropertyGraphStore();
        QueryEngine engine = new QueryEngine(store);
        
        QueryResult res1 = engine.execute("INSERT (n:Person {name: 'Alice', age: 30})");
        assertEquals(1, res1.stats().nodesCreated());
        assertEquals(1, res1.stats().labelsAdded());
        assertEquals(2, res1.stats().propertiesSet());
        
        QueryResult res2 = engine.execute("MATCH (p:Person) RETURN p.name AS name, p.age AS age");
        assertEquals(2, res2.columns().size());
        assertEquals("name", res2.columns().get(0));
        assertEquals("age", res2.columns().get(1));
        assertEquals(1, res2.rows().size());
        assertEquals("Alice", res2.rows().get(0).get(0));
        assertEquals(30L, res2.rows().get(0).get(1));
    }
    
    @Test
    public void testUnion() {
        PropertyGraphStore store = new PropertyGraphStore();
        QueryEngine engine = new QueryEngine(store);
        
        engine.execute("INSERT (n:Person {name: 'Alice', age: 30})");
        engine.execute("INSERT (n:Person {name: 'Bob', age: 25})");
        
        QueryResult res = engine.execute("MATCH (p:Person) RETURN p.name AS name UNION ALL MATCH (p:Person) RETURN p.name AS name");
        assertEquals(4, res.rows().size()); // Alice, Bob, Alice, Bob
        
        QueryResult res2 = engine.execute("MATCH (p:Person) RETURN p.name AS name UNION MATCH (p:Person) RETURN p.name AS name");
        assertEquals(2, res2.rows().size());
    }

    @Test
    public void testAggregates() {
        PropertyGraphStore store = new PropertyGraphStore();
        QueryEngine engine = new QueryEngine(store);
        
        engine.execute("INSERT (n:Person {age: 10}), (m:Person {age: 20})");
        
        QueryResult res = engine.execute("MATCH (p:Person) RETURN COUNT(p) AS c, SUM(p.age) AS s, AVG(p.age) AS a");
        assertEquals(1, res.rows().size());
        assertEquals(2L, res.rows().get(0).get(0)); // count
        assertEquals(30L, res.rows().get(0).get(1)); // sum
        assertEquals(15.0, res.rows().get(0).get(2)); // avg
    }
}

package blazegraph.core.storage;

import blazegraph.core.model.Edge;
import blazegraph.core.model.Node;
import blazegraph.core.model.PropertyValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PropertyGraphStoreTest {
    private PropertyGraphStore store;

    @BeforeEach
    public void setUp() {
        store = new PropertyGraphStore();
    }

    @Test
    public void testCreateNode() {
        Node node = store.createNode(Set.of("Person"));
        assertNotNull(node);
        assertEquals(1, node.getId());
        assertTrue(node.getLabels().contains("Person"));

        Optional<Node> retrieved = store.getNode(1);
        assertTrue(retrieved.isPresent());
        assertEquals(node, retrieved.get());
    }

    @Test
    public void testCreateEdge() {
        Node n1 = store.createNode(Set.of("Person"));
        Node n2 = store.createNode(Set.of("Movie"));

        Edge edge = store.createEdge(n1, n2, "ACTED_IN");
        assertNotNull(edge);
        assertEquals(1, edge.getId());
        assertEquals("ACTED_IN", edge.getType());
        assertEquals(n1, edge.getSource());
        assertEquals(n2, edge.getTarget());

        assertTrue(n1.getOutEdges().contains(edge));
        assertTrue(n2.getInEdges().contains(edge));

        Optional<Edge> retrieved = store.getEdge(1);
        assertTrue(retrieved.isPresent());
        assertEquals(edge, retrieved.get());
    }

    @Test
    public void testDeleteNodeCascadesToEdges() {
        Node n1 = store.createNode(Set.of("Person"));
        Node n2 = store.createNode(Set.of("Person"));
        Edge edge = store.createEdge(n1, n2, "KNOWS");

        assertTrue(store.getEdge(edge.getId()).isPresent());

        store.deleteNode(n1.getId());

        // Node 1 should be gone
        assertFalse(store.getNode(n1.getId()).isPresent());
        // Edge should be deleted globally
        assertFalse(store.getEdge(edge.getId()).isPresent());
        // Node 2 should still exist but have no incoming edge
        assertTrue(store.getNode(n2.getId()).isPresent());
        assertTrue(n2.getInEdges().isEmpty());
    }

    @Test
    public void testLabelIndex() {
        Node n1 = store.createNode(Set.of("Person", "Actor"));
        Node n2 = store.createNode(Set.of("Movie"));

        Set<Long> persons = store.getNodesByLabel("Person");
        assertEquals(1, persons.size());
        assertTrue(persons.contains(n1.getId()));

        Set<Long> actors = store.getNodesByLabel("Actor");
        assertEquals(1, actors.size());
        assertTrue(actors.contains(n1.getId()));

        Set<Long> movies = store.getNodesByLabel("Movie");
        assertEquals(1, movies.size());
        assertTrue(movies.contains(n2.getId()));

        store.deleteNode(n1.getId());
        assertTrue(store.getNodesByLabel("Person").isEmpty());
    }

    @Test
    public void testPropertyIndex() {
        Node n1 = store.createNode(Set.of("Person"));
        store.setNodeProperty(n1.getId(), "name", PropertyValue.of("Alice"));
        store.setNodeProperty(n1.getId(), "age", PropertyValue.of(30));

        Set<Long> aliceIds = store.getNodesByProperty("name", PropertyValue.of("Alice"));
        assertEquals(1, aliceIds.size());
        assertTrue(aliceIds.contains(n1.getId()));

        Set<Long> age30Ids = store.getNodesByProperty("age", PropertyValue.of(30));
        assertEquals(1, age30Ids.size());
        assertTrue(age30Ids.contains(n1.getId()));

        // Update property
        store.setNodeProperty(n1.getId(), "name", PropertyValue.of("Bob"));
        assertTrue(store.getNodesByProperty("name", PropertyValue.of("Alice")).isEmpty());
        assertEquals(1, store.getNodesByProperty("name", PropertyValue.of("Bob")).size());

        // Remove property
        store.removeNodeProperty(n1.getId(), "name");
        assertTrue(store.getNodesByProperty("name", PropertyValue.of("Bob")).isEmpty());
    }
}

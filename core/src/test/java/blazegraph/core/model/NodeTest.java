package blazegraph.core.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class NodeTest {
    @Test
    public void testNode() {
        Node n = new Node(1L);
        assertEquals(1L, n.getId());
        n.addLabel("Person");
        assertTrue(n.getLabels().contains("Person"));
        n.setProperty("name", PropertyValue.of("Alice"));
        assertEquals("Alice", n.getProperty("name").asString());
    }
}

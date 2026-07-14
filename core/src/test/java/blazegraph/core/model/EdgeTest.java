package blazegraph.core.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class EdgeTest {
    @Test
    public void testEdge() {
        Node source = new Node(1L);
        Node target = new Node(2L);
        Edge e = new Edge(100L, "KNOWS", source, target);
        assertEquals(100L, e.getId());
        assertEquals("KNOWS", e.getType());
        assertEquals(source, e.getSource());
        assertEquals(target, e.getTarget());
        e.setProperty("since", PropertyValue.of(2020L));
        assertEquals(2020L, e.getProperty("since").asLong());
    }
}

package blazegraph.core.pattern;

import blazegraph.core.TestGraphFixture;
import blazegraph.core.model.PropertyValue;
import blazegraph.core.storage.PropertyGraphStore;
import blazegraph.core.traversal.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PatternMatcherTest {
    @Test
    public void testMatch() {
        PropertyGraphStore store = TestGraphFixture.createMovieGraph();
        
        PatternNode personNode = new PatternNode("a", Set.of("Person"), Map.of("name", PropertyValue.of("Keanu Reeves")));
        PatternNode movieNode = new PatternNode("m", Set.of("Movie"), null);
        PatternEdge edge = new PatternEdge("r", "ACTED_IN", Direction.OUTGOING, 1, 1, personNode, movieNode);
        
        GraphPattern pattern = new GraphPattern(List.of(personNode, movieNode), List.of(edge));
        
        PatternMatcher matcher = new PatternMatcher();
        BindingTable table = matcher.match(pattern, store);
        
        assertEquals(1, table.getRows().size());
        assertEquals("The Matrix", ((blazegraph.core.model.Node)table.getRows().get(0).get("m")).getProperty("title").asString());
    }
}

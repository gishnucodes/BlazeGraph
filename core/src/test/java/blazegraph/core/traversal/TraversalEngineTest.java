package blazegraph.core.traversal;

import blazegraph.core.TestGraphFixture;
import blazegraph.core.model.Node;
import blazegraph.core.storage.PropertyGraphStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TraversalEngineTest {
    @Test
    public void testBFS() {
        PropertyGraphStore store = TestGraphFixture.createMovieGraph();
        Node keanu = store.streamNodesByLabel("Person")
                .filter(n -> "Keanu Reeves".equals(n.getProperty("name").asString()))
                .findFirst().orElseThrow();
        
        TraversalEngine engine = TraversalEngine.from(store, keanu).build();
        List<Path> paths = engine.bfs();
        
        assertFalse(paths.isEmpty());
        assertTrue(paths.stream().anyMatch(p -> p.endNode().getLabels().contains("Movie")));
    }
}

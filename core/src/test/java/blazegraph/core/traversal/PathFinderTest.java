package blazegraph.core.traversal;

import blazegraph.core.TestGraphFixture;
import blazegraph.core.model.Node;
import blazegraph.core.storage.PropertyGraphStore;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class PathFinderTest {
    @Test
    public void testShortestPath() {
        PropertyGraphStore store = TestGraphFixture.createMovieGraph();
        Node tom = store.streamNodesByLabel("Person").filter(n -> "Tom Hanks".equals(n.getProperty("name").asString())).findFirst().orElseThrow();
        Node matrix = store.streamNodesByLabel("Movie").findFirst().orElseThrow();
        
        PathFinder finder = new PathFinder(store);
        Optional<Path> path = finder.shortestPath(tom, matrix, Direction.OUTGOING);
        
        assertTrue(path.isPresent());
        assertEquals(2, path.get().length()); // Tom -> Keanu -> Matrix
    }
}

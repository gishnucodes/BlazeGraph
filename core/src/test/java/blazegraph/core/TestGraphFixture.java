package blazegraph.core;

import blazegraph.core.model.Node;
import blazegraph.core.model.PropertyValue;
import blazegraph.core.storage.PropertyGraphStore;

import java.util.Set;

public class TestGraphFixture {
    public static PropertyGraphStore createMovieGraph() {
        PropertyGraphStore store = new PropertyGraphStore();
        
        Node tom = store.createNode(Set.of("Person"));
        store.setNodeProperty(tom.getId(), "name", PropertyValue.of("Tom Hanks"));
        
        Node matrix = store.createNode(Set.of("Movie"));
        store.setNodeProperty(matrix.getId(), "title", PropertyValue.of("The Matrix"));
        
        Node keanu = store.createNode(Set.of("Person"));
        store.setNodeProperty(keanu.getId(), "name", PropertyValue.of("Keanu Reeves"));
        
        Node hugo = store.createNode(Set.of("Person"));
        store.setNodeProperty(hugo.getId(), "name", PropertyValue.of("Hugo Weaving"));

        store.createEdge(keanu, matrix, "ACTED_IN");
        store.createEdge(hugo, matrix, "ACTED_IN");
        
        Node lilly = store.createNode(Set.of("Person"));
        store.setNodeProperty(lilly.getId(), "name", PropertyValue.of("Lilly Wachowski"));
        store.createEdge(lilly, matrix, "DIRECTED");
        
        store.createEdge(tom, keanu, "KNOWS");
        
        return store;
    }
}

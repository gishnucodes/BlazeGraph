package blazegraph.cli;

import blazegraph.core.model.Edge;
import blazegraph.core.model.Node;
import blazegraph.core.storage.PropertyGraphStore;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CsvImporterTest {
    @Test
    public void testImportNodesAndEdges() throws Exception {
        PropertyGraphStore store = new PropertyGraphStore();
        CsvImporter importer = new CsvImporter(store);
        
        Path nodesCsv = Files.createTempFile("nodes", ".csv");
        Files.writeString(nodesCsv, "id:ID,name,age:int,:LABEL\nn1,Alice,30,Person;Developer\nn2,Bob,25,Person\nn3,Charlie,bad_age,Person");
        
        importer.importNodes(nodesCsv);
        
        assertEquals(2, store.nodeCount()); // n3 skipped due to bad age
        
        Node n1 = store.streamNodesByLabel("Alice").findFirst().orElse(null); // Actually label is Person
        boolean aliceFound = false;
        for (Node n : store.getAllNodes()) {
            if ("Alice".equals(n.getProperty("name").getValue())) {
                aliceFound = true;
                assertEquals(30L, n.getProperty("age").getValue());
                assertTrue(n.getLabels().contains("Person"));
                assertTrue(n.getLabels().contains("Developer"));
            }
        }
        assertTrue(aliceFound);
        
        Path edgesCsv = Files.createTempFile("edges", ".csv");
        Files.writeString(edgesCsv, ":START_ID,:END_ID,:TYPE,since:int\nn1,n2,KNOWS,2020\nn2,n3,KNOWS,2021\n");
        
        importer.importEdges(edgesCsv);
        
        assertEquals(1, store.edgeCount()); // n2->n3 skipped due to dangling n3
        
        Edge e = store.getAllEdges().iterator().next();
        assertEquals("KNOWS", e.getType());
        assertEquals(2020L, e.getProperty("since").getValue());
    }
}

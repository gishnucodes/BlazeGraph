package blazegraph.cli;

import blazegraph.core.storage.PropertyGraphStore;
import blazegraph.engine.QueryEngine;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReplSmokeTest {
    @Test
    public void testRepl() {
        String input = ":help\n" +
                "INSERT (n:Person {name: 'Alice'});\n" +
                "MATCH (p:Person) RETURN p.name;\n" +
                ":stats\n" +
                "MATCH (n) RETURN n\n" + // no semicolon, should buffer
                ";\n" + // now execute
                "INVALID QUERY;\n" +
                ":quit\n";
        
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes());
        System.setIn(in);
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(out);
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        System.setOut(ps);
        System.setErr(ps);
        
        try {
            PropertyGraphStore store = new PropertyGraphStore();
            QueryEngine engine = new QueryEngine(store);
            BlazeGraphRepl repl = new BlazeGraphRepl(engine, store);
            repl.run();
            
            String output = out.toString();
            assertTrue(output.contains("Meta commands:"));
            assertTrue(output.contains("Created 1 nodes"));
            assertTrue(output.contains("Alice"));
            assertTrue(output.contains("Nodes: 1"));
            assertTrue(output.contains("SyntaxException") || output.contains("SemanticException") || output.contains("Error: "));
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
            System.setIn(System.in);
        }
    }
}

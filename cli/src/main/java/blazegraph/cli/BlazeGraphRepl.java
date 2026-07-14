package blazegraph.cli;

import blazegraph.core.model.Edge;
import blazegraph.core.model.Node;
import blazegraph.core.model.PropertyValue;
import blazegraph.core.storage.PropertyGraphStore;
import blazegraph.engine.QueryEngine;
import blazegraph.engine.result.QueryResult;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class BlazeGraphRepl {
    private final QueryEngine engine;
    private final PropertyGraphStore store;

    public BlazeGraphRepl(QueryEngine engine, PropertyGraphStore store) {
        this.engine = engine;
        this.store = store;
    }

    public void run() {
        System.out.println("BlazeGraph REPL");
        System.out.println("Type :help for commands, or enter GQL queries ending with ';'");

        try (Terminal terminal = TerminalBuilder.builder().build()) {
            Path historyPath = Paths.get(System.getProperty("user.home"), ".blazegraph_history");
            
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .history(new DefaultHistory())
                    .variable(LineReader.HISTORY_FILE, historyPath)
                    .build();

            StringBuilder buffer = new StringBuilder();

            while (true) {
                try {
                    String prompt = buffer.length() == 0 ? "blaze> " : "...... ";
                    String line = reader.readLine(prompt);

                    if (line == null) continue;
                    line = line.trim();

                    if (buffer.length() == 0 && line.startsWith(":")) {
                        if (handleMetaCommand(line)) break; // break means quit
                        continue;
                    }

                    if (line.isEmpty()) continue;

                    buffer.append(line).append(" ");
                    
                    if (line.endsWith(";")) {
                        String query = buffer.toString().trim();
                        // Remove trailing semicolon
                        query = query.substring(0, query.length() - 1);
                        buffer.setLength(0); // clear
                        
                        executeQuery(query);
                    }
                } catch (UserInterruptException | EndOfFileException e) {
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to start terminal: " + e.getMessage());
        }
    }

    private boolean handleMetaCommand(String cmd) {
        String[] parts = cmd.split("\\s+");
        String command = parts[0].toLowerCase();
        
        switch (command) {
            case ":quit":
            case ":exit":
                return true;
            case ":help":
                System.out.println("Meta commands:");
                System.out.println("  :help          Show this help");
                System.out.println("  :schema        Show labels, edge types, and property keys");
                System.out.println("  :stats         Show graph statistics");
                System.out.println("  :clear         Clear the graph store");
                System.out.println("  :load <file>   Execute GQL script from file");
                System.out.println("  :quit | :exit  Exit REPL");
                break;
            case ":schema":
                printSchema();
                break;
            case ":stats":
                printStats();
                break;
            case ":clear":
                System.out.print("Are you sure you want to clear the graph? (y/N) ");
                Scanner scanner = new Scanner(System.in);
                String ans = scanner.nextLine().trim();
                if (ans.equalsIgnoreCase("y")) {
                    store.clear();
                    System.out.println("Graph cleared.");
                } else {
                    System.out.println("Aborted.");
                }
                break;
            case ":load":
                if (parts.length < 2) {
                    System.out.println("Usage: :load <file.gql>");
                } else {
                    try {
                        String content = Files.readString(Paths.get(parts[1]));
                        executeQuery(content);
                    } catch (IOException e) {
                        System.out.println("Error reading file: " + e.getMessage());
                    }
                }
                break;
            default:
                System.out.println("Unknown command: " + command);
        }
        return false;
    }

    private void printSchema() {
        Set<String> labels = new HashSet<>();
        Set<String> edgeTypes = new HashSet<>();
        Set<String> propKeys = new HashSet<>();

        for (Node n : store.getAllNodes()) {
            labels.addAll(n.getLabels());
            propKeys.addAll(n.getProperties().keySet());
        }
        for (Edge e : store.getAllEdges()) {
            edgeTypes.add(e.getType());
            propKeys.addAll(e.getProperties().keySet());
        }

        System.out.println("Labels: " + String.join(", ", labels));
        System.out.println("Edge Types: " + String.join(", ", edgeTypes));
        System.out.println("Property Keys: " + String.join(", ", propKeys));
    }

    private void printStats() {
        Runtime rt = Runtime.getRuntime();
        long heapMb = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        System.out.println("Nodes: " + store.nodeCount());
        System.out.println("Edges: " + store.edgeCount());
        System.out.println("Heap Used: " + heapMb + " MB");
    }

    private void executeQuery(String query) {
        try {
            QueryResult result = engine.execute(query, 0);
            printResult(result);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    public static void printResult(QueryResult res) {
        if (!res.columns().isEmpty()) {
            // Simple table
            List<String> cols = res.columns();
            System.out.println(String.join("\t|\t", cols));
            System.out.println("-".repeat(cols.size() * 16));
            for (List<Object> row : res.rows()) {
                List<String> strRow = row.stream().map(BlazeGraphRepl::formatValue).collect(Collectors.toList());
                System.out.println(String.join("\t|\t", strRow));
            }
        }

        // Status line
        blazegraph.engine.result.QueryStats s = res.stats();
        if (s.nodesCreated() > 0 || s.edgesCreated() > 0 || s.labelsAdded() > 0 || s.propertiesSet() > 0 || s.nodesDeleted() > 0 || s.edgesDeleted() > 0) {
            System.out.printf("Created %d nodes, %d edges, set %d properties, added %d labels, deleted %d nodes, %d edges \u00b7 %d ms\n",
                    s.nodesCreated(), s.edgesCreated(), s.propertiesSet(), s.labelsAdded(), s.nodesDeleted(), s.edgesDeleted(), s.executionTimeMs());
        } else {
            System.out.printf("%d rows \u00b7 %d ms\n", res.rows().size(), s.executionTimeMs());
        }
    }

    private static String formatValue(Object val) {
        if (val == null) return "null";
        if (val instanceof PropertyValue pv) return String.valueOf(pv.getValue());
        if (val instanceof Node n) return "Node(" + n.getId() + ")";
        if (val instanceof Edge e) return "Edge(" + e.getId() + ")";
        return val.toString();
    }
}

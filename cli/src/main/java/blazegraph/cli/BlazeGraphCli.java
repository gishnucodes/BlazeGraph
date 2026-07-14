package blazegraph.cli;

import blazegraph.core.storage.PropertyGraphStore;
import blazegraph.engine.QueryEngine;
import blazegraph.engine.result.QueryResult;
import blazegraph.server.BlazeGraphServer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "blazegraph", mixinStandardHelpOptions = true, version = "1.0",
        description = "BlazeGraph Graph Database CLI")
public class BlazeGraphCli implements Callable<Integer> {

    @Option(names = {"-q", "--query"}, description = "Execute a single GQL query and exit")
    private String query;

    @Option(names = {"-f", "--file"}, description = "Execute GQL statements from a file and exit")
    private Path file;

    @Option(names = {"--import-nodes"}, description = "Path to nodes CSV file")
    private Path importNodes;

    @Option(names = {"--import-edges"}, description = "Path to edges CSV file")
    private Path importEdges;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new BlazeGraphCli())
                .addSubcommand("serve", new ServeCommand())
                .execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        PropertyGraphStore store = new PropertyGraphStore();
        
        if (importNodes != null || importEdges != null) {
            CsvImporter importer = new CsvImporter(store);
            if (importNodes != null) {
                System.out.println("Importing nodes from " + importNodes + "...");
                importer.importNodes(importNodes);
            }
            if (importEdges != null) {
                System.out.println("Importing edges from " + importEdges + "...");
                importer.importEdges(importEdges);
            }
        }

        QueryEngine engine = new QueryEngine(store);

        if (query != null) {
            return executeAndPrint(engine, query);
        } else if (file != null) {
            String content = Files.readString(file);
            return executeAndPrint(engine, content);
        } else {
            // Interactive REPL
            BlazeGraphRepl repl = new BlazeGraphRepl(engine, store);
            repl.run();
            return 0;
        }
    }

    private int executeAndPrint(QueryEngine engine, String gql) {
        try {
            QueryResult result = engine.execute(gql, 0); // No timeout
            BlazeGraphRepl.printResult(result);
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    @Command(name = "serve", description = "Start the HTTP server")
    static class ServeCommand implements Callable<Integer> {
        @Option(names = {"-p", "--port"}, description = "Port to listen on (default: 7474)", defaultValue = "7474")
        private int port;

        @Override
        public Integer call() throws Exception {
            PropertyGraphStore store = new PropertyGraphStore();
            BlazeGraphServer server = new BlazeGraphServer(store, port);
            server.start();
            System.out.println("Press Ctrl+C to stop.");
            Thread.currentThread().join();
            return 0;
        }
    }
}

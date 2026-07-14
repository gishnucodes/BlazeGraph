package blazegraph.server;

import blazegraph.core.model.Edge;
import blazegraph.core.model.Node;
import blazegraph.core.model.PropertyValue;
import blazegraph.core.storage.PropertyGraphStore;
import blazegraph.engine.QueryEngine;
import blazegraph.engine.result.QueryResult;
import blazegraph.engine.eval.ExecutionException;
import blazegraph.engine.planner.SemanticException;
import blazegraph.parser.BlazeParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class BlazeGraphServer {
    private final PropertyGraphStore store;
    private final QueryEngine engine;
    private final HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();
    private final long startTime;
    private final ExecutorService queryExecutor = Executors.newSingleThreadExecutor();

    public BlazeGraphServer(PropertyGraphStore store, int port) throws IOException {
        this.store = store;
        this.engine = new QueryEngine(store);
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.startTime = System.currentTimeMillis();

        server.createContext("/query", new QueryHandler());
        server.createContext("/schema", new SchemaHandler());
        server.createContext("/stats", new StatsHandler());
        server.createContext("/health", new HealthHandler());
        
        server.setExecutor(Executors.newCachedThreadPool()); // HTTP requests are parallel, but queries queue
    }

    public void start() {
        server.start();
        System.out.println("BlazeGraph server started on port " + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
        queryExecutor.shutdown();
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 7474;
        BlazeGraphServer srv = new BlazeGraphServer(new PropertyGraphStore(), port);
        srv.start();
    }

    private void sendResponse(HttpExchange exchange, int statusCode, Object response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = mapper.writeValueAsBytes(response);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    private void sendError(HttpExchange exchange, int statusCode, String type, String message) throws IOException {
        Map<String, Object> error = Map.of("error", Map.of("type", type, "message", message));
        sendResponse(exchange, statusCode, error);
    }

    private class QueryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "MethodNotAllowed", "Only POST is supported");
                return;
            }
            
            try (InputStream is = exchange.getRequestBody()) {
                Map<?, ?> requestBody = mapper.readValue(is, Map.class);
                String query = (String) requestBody.get("query");
                if (query == null) {
                    sendError(exchange, 400, "BadRequest", "Missing 'query' field");
                    return;
                }
                
                queryExecutor.submit(() -> {
                    try {
                        QueryResult result = engine.execute(query, 30000); // 30s timeout
                        sendResponse(exchange, 200, formatResult(result));
                    } catch (blazegraph.parser.SyntaxException | SemanticException e) {
                        try { sendError(exchange, 400, e.getClass().getSimpleName(), e.getMessage()); } catch (IOException ex) {}
                    } catch (ExecutionException e) {
                        int code = e.getMessage().contains("cancelled") ? 408 : 422;
                        try { sendError(exchange, code, e.getClass().getSimpleName(), e.getMessage()); } catch (IOException ex) {}
                    } catch (Exception e) {
                        try { sendError(exchange, 500, "InternalError", e.getMessage()); } catch (IOException ex) {}
                    }
                });
            } catch (Exception e) {
                sendError(exchange, 400, "BadRequest", "Invalid JSON body");
            }
        }
    }

    private Object formatResult(QueryResult result) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("columns", result.columns());
        
        List<List<Object>> rows = new ArrayList<>();
        for (List<Object> row : result.rows()) {
            List<Object> outRow = new ArrayList<>();
            for (Object val : row) {
                outRow.add(formatValue(val));
            }
            rows.add(outRow);
        }
        out.put("rows", rows);
        out.put("stats", result.stats());
        return out;
    }

    private Object formatValue(Object val) {
        if (val == null) return null;
        if (val instanceof PropertyValue pv) return pv.getValue();
        if (val instanceof Node n) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("~id", n.getId());
            m.put("~labels", new ArrayList<>(n.getLabels()));
            for (Map.Entry<String, PropertyValue> entry : n.getProperties().entrySet()) {
                m.put(entry.getKey(), entry.getValue().getValue());
            }
            return m;
        }
        if (val instanceof Edge e) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("~id", e.getId());
            m.put("~type", e.getType());
            m.put("~start", e.getSource().getId());
            m.put("~end", e.getTarget().getId());
            for (Map.Entry<String, PropertyValue> entry : e.getProperties().entrySet()) {
                m.put(entry.getKey(), entry.getValue().getValue());
            }
            return m;
        }
        if (val instanceof List<?> l) {
            return l.stream().map(this::formatValue).collect(Collectors.toList());
        }
        return val.toString();
    }

    private class SchemaHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Set<String> labels = new HashSet<>();
            Set<String> edgeTypes = new HashSet<>();
            Set<String> propertyKeys = new HashSet<>();
            
            for (Node n : store.getAllNodes()) {
                labels.addAll(n.getLabels());
                propertyKeys.addAll(n.getProperties().keySet());
            }
            for (Edge e : store.getAllEdges()) {
                edgeTypes.add(e.getType());
                propertyKeys.addAll(e.getProperties().keySet());
            }
            
            Map<String, Object> schema = Map.of(
                "labels", labels,
                "edgeTypes", edgeTypes,
                "propertyKeys", propertyKeys
            );
            sendResponse(exchange, 200, schema);
        }
    }

    private class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Runtime rt = Runtime.getRuntime();
            long heapUsed = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
            long uptime = (System.currentTimeMillis() - startTime) / 1000;
            
            Map<String, Object> stats = Map.of(
                "nodes", store.nodeCount(),
                "edges", store.edgeCount(),
                "heapUsedMb", heapUsed,
                "uptimeSec", uptime
            );
            sendResponse(exchange, 200, stats);
        }
    }

    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendResponse(exchange, 200, Map.of("status", "ok"));
        }
    }
}

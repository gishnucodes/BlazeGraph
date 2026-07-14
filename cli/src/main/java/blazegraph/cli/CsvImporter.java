package blazegraph.cli;

import blazegraph.core.model.Node;
import blazegraph.core.model.PropertyValue;
import blazegraph.core.storage.PropertyGraphStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CsvImporter {
    private final PropertyGraphStore store;
    private final Map<String, Long> idMap = new HashMap<>();

    public CsvImporter(PropertyGraphStore store) {
        this.store = store;
    }

    public void importNodes(Path file) throws IOException {
        long startTime = System.currentTimeMillis();
        int rowCount = 0;
        int badRowCount = 0;
        List<String> badRows = new ArrayList<>();
        
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;
            
            String[] headers = splitCsv(headerLine);
            int idCol = -1;
            int labelCol = -1;
            
            for (int i = 0; i < headers.length; i++) {
                if (headers[i].equalsIgnoreCase(":ID") || headers[i].equalsIgnoreCase("id:ID")) idCol = i;
                else if (headers[i].equalsIgnoreCase(":LABEL")) labelCol = i;
            }
            
            if (idCol == -1) {
                System.err.println("Error: No :ID column found in node CSV");
                return;
            }
            
            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.trim().isEmpty()) continue;
                
                String[] parts = splitCsv(line);
                if (parts.length > headers.length) {
                    if (badRowCount < 10) badRows.add("Line " + lineNum + ": length mismatch");
                    badRowCount++;
                    continue;
                }
                
                try {
                    String fileId = parts[idCol];
                    Node node = store.createNode(null);
                    idMap.put(fileId, node.getId());
                    
                    if (labelCol != -1 && labelCol < parts.length && !parts[labelCol].isEmpty()) {
                        for (String lbl : parts[labelCol].split(";")) {
                            store.addNodeLabel(node.getId(), lbl);
                        }
                    }
                    
                    for (int i = 0; i < parts.length; i++) {
                        if (i == idCol || i == labelCol || parts[i].isEmpty()) continue;
                        String header = headers[i];
                        parseAndSetProperty(header, parts[i], node.getId(), true);
                    }
                    rowCount++;
                } catch (Exception e) {
                    if (badRowCount < 10) badRows.add("Line " + lineNum + ": " + e.getMessage());
                    badRowCount++;
                }
            }
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("Imported %d nodes in %d ms (%.1f rows/sec)\n", rowCount, elapsed, elapsed > 0 ? (rowCount * 1000.0 / elapsed) : 0);
        if (badRowCount > 0) {
            System.out.println("Skipped " + badRowCount + " bad rows:");
            badRows.forEach(r -> System.out.println("  " + r));
        }
    }

    public void importEdges(Path file) throws IOException {
        long startTime = System.currentTimeMillis();
        int rowCount = 0;
        int badRowCount = 0;
        List<String> badRows = new ArrayList<>();
        
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;
            
            String[] headers = splitCsv(headerLine);
            int startCol = -1, endCol = -1, typeCol = -1;
            
            for (int i = 0; i < headers.length; i++) {
                if (headers[i].equalsIgnoreCase(":START_ID")) startCol = i;
                else if (headers[i].equalsIgnoreCase(":END_ID")) endCol = i;
                else if (headers[i].equalsIgnoreCase(":TYPE")) typeCol = i;
            }
            
            if (startCol == -1 || endCol == -1 || typeCol == -1) {
                System.err.println("Error: Missing :START_ID, :END_ID, or :TYPE in edge CSV");
                return;
            }
            
            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.trim().isEmpty()) continue;
                
                String[] parts = splitCsv(line);
                if (parts.length > headers.length) {
                    if (badRowCount < 10) badRows.add("Line " + lineNum + ": length mismatch");
                    badRowCount++;
                    continue;
                }
                
                try {
                    Long startId = idMap.get(parts[startCol]);
                    Long endId = idMap.get(parts[endCol]);
                    if (startId == null || endId == null) {
                        throw new RuntimeException("Dangling edge reference");
                    }
                    
                    String type = parts[typeCol];
                    blazegraph.core.model.Edge edge = store.createEdge(store.getNode(startId).get(), store.getNode(endId).get(), type);
                    
                    for (int i = 0; i < parts.length; i++) {
                        if (i == startCol || i == endCol || i == typeCol || parts[i].isEmpty()) continue;
                        String header = headers[i];
                        parseAndSetProperty(header, parts[i], edge.getId(), false);
                    }
                    rowCount++;
                } catch (Exception e) {
                    if (badRowCount < 10) badRows.add("Line " + lineNum + ": " + e.getMessage());
                    badRowCount++;
                }
            }
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("Imported %d edges in %d ms (%.1f rows/sec)\n", rowCount, elapsed, elapsed > 0 ? (rowCount * 1000.0 / elapsed) : 0);
        if (badRowCount > 0) {
            System.out.println("Skipped " + badRowCount + " bad rows:");
            badRows.forEach(r -> System.out.println("  " + r));
        }
    }
    
    private void parseAndSetProperty(String header, String value, long elemId, boolean isNode) {
        String key = header;
        String type = "string";
        int colonIdx = header.indexOf(':');
        if (colonIdx != -1) {
            key = header.substring(0, colonIdx);
            type = header.substring(colonIdx + 1).toLowerCase();
        }
        
        PropertyValue pv = null;
        switch (type) {
            case "int":
            case "integer":
            case "long":
                pv = PropertyValue.of(Long.parseLong(value));
                break;
            case "double":
            case "float":
                pv = PropertyValue.of(Double.parseDouble(value));
                break;
            case "boolean":
            case "bool":
                pv = PropertyValue.of(Boolean.parseBoolean(value));
                break;
            default:
                pv = PropertyValue.of(value);
        }
        
        if (isNode) store.setNodeProperty(elemId, key, pv);
        else store.setEdgeProperty(elemId, key, pv);
    }
    
    // Very simple CSV split. For production, use OpenCSV or similar.
    private String[] splitCsv(String line) {
        return line.split(",");
    }
}

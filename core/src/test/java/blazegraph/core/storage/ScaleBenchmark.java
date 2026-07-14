package blazegraph.core.storage;

import blazegraph.core.model.Edge;
import blazegraph.core.model.Node;
import blazegraph.core.model.PropertyValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ScaleBenchmark {
    public static void main(String[] args) {
        System.out.println("=== BlazeGraph Storage Scale Benchmark ===");
        PropertyGraphStore store = new PropertyGraphStore();

        int nodeCount = 100_000;
        int edgeCount = 100_000;

        // Force GC before measurement
        System.gc();
        long memoryBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        System.out.println("Inserting " + nodeCount + " nodes...");
        long startTime = System.nanoTime();
        List<Node> nodes = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            Node node = store.createNode(Set.of("Person"));
            store.setNodeProperty(node.getId(), "name", PropertyValue.of("Person_" + i));
            store.setNodeProperty(node.getId(), "age", PropertyValue.of(20 + (i % 50)));
            nodes.add(node);
        }
        long nodeEndTime = System.nanoTime();

        System.out.println("Inserting " + edgeCount + " edges...");
        long edgeStartTime = System.nanoTime();
        for (int i = 0; i < edgeCount; i++) {
            Node source = nodes.get(i);
            Node target = nodes.get((i + 1) % nodeCount);
            Edge edge = store.createEdge(source, target, "KNOWS");
            store.setEdgeProperty(edge.getId(), "since", PropertyValue.of(2000 + (i % 25)));
        }
        long endTime = System.nanoTime();

        System.gc();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long memoryAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        double nodeDurationMs = (nodeEndTime - startTime) / 1_000_000.0;
        double edgeDurationMs = (endTime - edgeStartTime) / 1_000_000.0;
        double totalDurationMs = (endTime - startTime) / 1_000_000.0;
        double memoryMb = (memoryAfter - memoryBefore) / (1024.0 * 1024.0);

        System.out.println("\n=== Results ===");
        System.out.printf("Node creation: %.2f ms (%.2f nodes/ms)%n", nodeDurationMs, nodeCount / nodeDurationMs);
        System.out.printf("Edge creation: %.2f ms (%.2f edges/ms)%n", edgeDurationMs, edgeCount / edgeDurationMs);
        System.out.printf("Total duration: %.2f ms%n", totalDurationMs);
        System.out.printf("Memory overhead: %.2f MB%n", memoryMb);
        System.out.println("===============");
    }
}

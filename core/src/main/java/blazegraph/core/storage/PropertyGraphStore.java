package blazegraph.core.storage;

import blazegraph.core.index.LabelIndex;
import blazegraph.core.index.PropertyIndex;
import blazegraph.core.index.TypeIndex;
import blazegraph.core.model.Edge;
import blazegraph.core.model.Node;
import blazegraph.core.model.PropertyValue;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import java.util.stream.Stream;

/**
 * The central registry managing the graph elements.
 * 
 * Concurrency Contract:
 * - Thread-safe for point reads and isolated updates.
 * - Compound operations (like createEdge, deleteNode) are NOT atomic. Under concurrent writes,
 *   this can produce dangling references or index entries.
 * - Index empty-bucket cleanup is subject to a race condition where a concurrent writer might 
 *   add to a bucket being removed.
 * - These limitations are accepted for v1 under a single-writer contract.
 */
public final class PropertyGraphStore {
    private final AtomicLong nodeIdCounter = new AtomicLong(1);
    private final AtomicLong edgeIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<Long, Node> nodes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Edge> edges = new ConcurrentHashMap<>();

    private final LabelIndex labelIndex = new LabelIndex();
    private final TypeIndex typeIndex = new TypeIndex();
    private final PropertyIndex nodePropertyIndex = new PropertyIndex();
    private final PropertyIndex edgePropertyIndex = new PropertyIndex();

    // Node Operations
    public Node createNode(Set<String> labels) {
        long id = nodeIdCounter.getAndIncrement();
        Node node = new Node(id);
        if (labels != null) {
            for (String label : labels) {
                node.addLabel(label);
                labelIndex.add(label, id);
            }
        }
        nodes.put(id, node);
        return node;
    }

    public Optional<Node> getNode(long id) {
        return Optional.ofNullable(nodes.get(id));
    }

    public Collection<Node> getAllNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public int nodeCount() {
        return nodes.size();
    }

    public boolean deleteNode(long id) {
        Node node = nodes.remove(id);
        if (node != null) {
            // Clean up attached edges
            for (Edge edge : node.getOutEdges()) {
                deleteEdge(edge.getId());
            }
            for (Edge edge : node.getInEdges()) {
                deleteEdge(edge.getId());
            }

            // Clean up label index
            for (String label : node.getLabels()) {
                labelIndex.remove(label, id);
            }

            // Clean up property index
            for (Map.Entry<String, PropertyValue> entry : node.getProperties().entrySet()) {
                nodePropertyIndex.remove(entry.getKey(), entry.getValue(), id);
            }
            return true;
        }
        return false;
    }

    public boolean addNodeLabel(long nodeId, String label) {
        Node node = nodes.get(nodeId);
        if (node != null) {
            node.addLabel(label);
            labelIndex.add(label, nodeId);
            return true;
        }
        return false;
    }

    public boolean removeNodeLabel(long nodeId, String label) {
        Node node = nodes.get(nodeId);
        if (node != null) {
            node.removeLabel(label);
            labelIndex.remove(label, nodeId);
            return true;
        }
        return false;
    }

    public boolean setNodeProperty(long nodeId, String key, PropertyValue value) {
        Node node = nodes.get(nodeId);
        if (node != null) {
            PropertyValue oldValue = node.getProperty(key);
            if (oldValue != null) {
                nodePropertyIndex.remove(key, oldValue, nodeId);
            }
            node.setProperty(key, value);
            nodePropertyIndex.add(key, value, nodeId);
            return true;
        }
        return false;
    }

    public boolean removeNodeProperty(long nodeId, String key) {
        Node node = nodes.get(nodeId);
        if (node != null) {
            PropertyValue oldValue = node.getProperty(key);
            if (oldValue != null) {
                nodePropertyIndex.remove(key, oldValue, nodeId);
                node.removeProperty(key);
            }
            return true;
        }
        return false;
    }

    // Edge Operations
    public Edge createEdge(Node source, Node target, String type) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(target);
        Objects.requireNonNull(type);

        long id = edgeIdCounter.getAndIncrement();
        Edge edge = new Edge(id, type, source, target);

        // Link nodes
        source.addOutEdge(edge);
        target.addInEdge(edge);

        // Add to global structures and index
        edges.put(id, edge);
        typeIndex.add(type, id);

        return edge;
    }

    public Optional<Edge> getEdge(long id) {
        return Optional.ofNullable(edges.get(id));
    }

    public Collection<Edge> getAllEdges() {
        return Collections.unmodifiableCollection(edges.values());
    }

    public int edgeCount() {
        return edges.size();
    }

    public boolean deleteEdge(long id) {
        Edge edge = edges.remove(id);
        if (edge != null) {
            // Unlink source and target
            edge.getSource().removeOutEdge(edge);
            edge.getTarget().removeInEdge(edge);

            // Clean up type index
            typeIndex.remove(edge.getType(), id);

            // Clean up property index
            for (Map.Entry<String, PropertyValue> entry : edge.getProperties().entrySet()) {
                edgePropertyIndex.remove(entry.getKey(), entry.getValue(), id);
            }
            return true;
        }
        return false;
    }

    public boolean setEdgeProperty(long edgeId, String key, PropertyValue value) {
        Edge edge = edges.get(edgeId);
        if (edge != null) {
            PropertyValue oldValue = edge.getProperty(key);
            if (oldValue != null) {
                edgePropertyIndex.remove(key, oldValue, edgeId);
            }
            edge.setProperty(key, value);
            edgePropertyIndex.add(key, value, edgeId);
            return true;
        }
        return false;
    }

    public boolean removeEdgeProperty(long edgeId, String key) {
        Edge edge = edges.get(edgeId);
        if (edge != null) {
            PropertyValue oldValue = edge.getProperty(key);
            if (oldValue != null) {
                edgePropertyIndex.remove(key, oldValue, edgeId);
                edge.removeProperty(key);
            }
            return true;
        }
        return false;
    }

    // Index Lookup Accessors
    public Set<Long> getNodesByLabel(String label) {
        return labelIndex.get(label);
    }

    public Stream<Node> streamNodesByLabel(String label) {
        Set<Long> ids = getNodesByLabel(label);
        if (ids == null || ids.isEmpty()) return Stream.empty();
        return ids.stream().map(nodes::get).filter(Objects::nonNull);
    }

    public Set<Long> getEdgesByType(String type) {
        return typeIndex.get(type);
    }

    public Set<Long> getNodesByProperty(String key, PropertyValue value) {
        return nodePropertyIndex.get(key, value);
    }

    public Set<Long> getEdgesByProperty(String key, PropertyValue value) {
        return edgePropertyIndex.get(key, value);
    }

    public void clear() {
        nodes.clear();
        edges.clear();
        labelIndex.clear();
        typeIndex.clear();
        nodePropertyIndex.clear();
        edgePropertyIndex.clear();
        nodeIdCounter.set(1);
        edgeIdCounter.set(1);
    }
}

package blazegraph.core.model;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class Node {
    private final long id;
    private final Set<String> labels = ConcurrentHashMap.newKeySet();
    private final Map<String, PropertyValue> properties = new ConcurrentHashMap<>();
    private final Set<Edge> outEdges = ConcurrentHashMap.newKeySet();
    private final Set<Edge> inEdges = ConcurrentHashMap.newKeySet();

    public Node(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public Set<String> getLabels() {
        return Collections.unmodifiableSet(labels);
    }

    public void addLabel(String label) {
        labels.add(label);
    }

    public void removeLabel(String label) {
        labels.remove(label);
    }

    public Map<String, PropertyValue> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    public PropertyValue getProperty(String key) {
        return properties.get(key);
    }

    public void setProperty(String key, PropertyValue value) {
        properties.put(key, value);
    }

    public void removeProperty(String key) {
        properties.remove(key);
    }

    public Set<Edge> getOutEdges() {
        return Collections.unmodifiableSet(outEdges);
    }

    public Set<Edge> getInEdges() {
        return Collections.unmodifiableSet(inEdges);
    }

    public void addOutEdge(Edge edge) {
        outEdges.add(edge);
    }

    public void addInEdge(Edge edge) {
        inEdges.add(edge);
    }

    public void removeOutEdge(Edge edge) {
        outEdges.remove(edge);
    }

    public void removeInEdge(Edge edge) {
        inEdges.remove(edge);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return id == node.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "Node{" + "id=" + id + ", labels=" + labels + ", properties=" + properties + '}';
    }
}

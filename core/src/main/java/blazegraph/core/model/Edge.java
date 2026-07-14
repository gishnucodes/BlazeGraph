package blazegraph.core.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class Edge {
    private final long id;
    private final String type;
    private final Node source;
    private final Node target;
    private final Map<String, PropertyValue> properties = new ConcurrentHashMap<>();

    public Edge(long id, String type, Node source, Node target) {
        this.id = id;
        this.type = Objects.requireNonNull(type);
        this.source = Objects.requireNonNull(source);
        this.target = Objects.requireNonNull(target);
    }

    public long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public Node getSource() {
        return source;
    }

    public Node getTarget() {
        return target;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Edge edge = (Edge) o;
        return id == edge.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "Edge{" +
                "id=" + id +
                ", type='" + type + '\'' +
                ", source=" + source.getId() +
                ", target=" + target.getId() +
                ", properties=" + properties +
                '}';
    }
}

package blazegraph.core.traversal;

import blazegraph.core.model.Edge;
import blazegraph.core.model.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class Path {
    private final List<Node> nodes;
    private final List<Edge> edges;

    public Path(Node startNode) {
        this.nodes = List.of(Objects.requireNonNull(startNode));
        this.edges = Collections.emptyList();
    }

    private Path(List<Node> nodes, List<Edge> edges) {
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        this.edges = Collections.unmodifiableList(new ArrayList<>(edges));
    }

    public Node startNode() {
        return nodes.get(0);
    }

    public Node endNode() {
        return nodes.get(nodes.size() - 1);
    }

    public int length() {
        return edges.size();
    }

    public boolean contains(Node node) {
        return nodes.contains(node);
    }

    public Path append(Edge edge, Node node) {
        List<Node> newNodes = new ArrayList<>(this.nodes);
        newNodes.add(node);
        List<Edge> newEdges = new ArrayList<>(this.edges);
        newEdges.add(edge);
        return new Path(newNodes, newEdges);
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public List<Edge> getEdges() {
        return edges;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Path path = (Path) o;
        return nodes.equals(path.nodes) && edges.equals(path.edges);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodes, edges);
    }
}

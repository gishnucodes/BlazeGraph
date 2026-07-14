package blazegraph.core.traversal;

import blazegraph.core.model.Edge;
import blazegraph.core.model.Node;
import blazegraph.core.storage.PropertyGraphStore;

import java.util.*;

public final class TraversalEngine {
    private final PropertyGraphStore store;
    private final Node startNode;
    private final Direction direction;
    private final int maxDepth;
    private final Set<String> edgeTypeFilter;
    private final Set<String> nodeLabelFilter;
    private final boolean uniqueNodes;

    private TraversalEngine(Builder builder) {
        this.store = builder.store;
        this.startNode = builder.startNode;
        this.direction = builder.direction;
        this.maxDepth = builder.maxDepth;
        this.edgeTypeFilter = builder.edgeTypeFilter;
        this.nodeLabelFilter = builder.nodeLabelFilter;
        this.uniqueNodes = builder.uniqueNodes;
    }

    public static Builder from(PropertyGraphStore store, Node startNode) {
        return new Builder(store, startNode);
    }

    public static class Builder {
        private final PropertyGraphStore store;
        private final Node startNode;
        private Direction direction = Direction.OUTGOING;
        private int maxDepth = Integer.MAX_VALUE;
        private Set<String> edgeTypeFilter = null;
        private Set<String> nodeLabelFilter = null;
        private boolean uniqueNodes = false;

        public Builder(PropertyGraphStore store, Node startNode) {
            this.store = Objects.requireNonNull(store);
            this.startNode = Objects.requireNonNull(startNode);
        }

        public Builder direction(Direction direction) {
            this.direction = Objects.requireNonNull(direction);
            return this;
        }

        public Builder maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public Builder edgeTypeFilter(String... types) {
            this.edgeTypeFilter = new HashSet<>(Arrays.asList(types));
            return this;
        }

        public Builder nodeLabelFilter(String... labels) {
            this.nodeLabelFilter = new HashSet<>(Arrays.asList(labels));
            return this;
        }

        public Builder uniqueNodes(boolean uniqueNodes) {
            this.uniqueNodes = uniqueNodes;
            return this;
        }

        public TraversalEngine build() {
            return new TraversalEngine(this);
        }
    }

    public List<Path> bfs() {
        List<Path> results = new ArrayList<>();
        traverse(new TraversalVisitor() {
            @Override
            public boolean onNodeVisited(Node node, Path pathToNode) {
                results.add(pathToNode);
                return true;
            }

            @Override
            public boolean onEdgeTraversed(Edge edge, Path currentPath) {
                return true;
            }
        }, true);
        return results;
    }

    public List<Path> dfs() {
        List<Path> results = new ArrayList<>();
        traverse(new TraversalVisitor() {
            @Override
            public boolean onNodeVisited(Node node, Path pathToNode) {
                results.add(pathToNode);
                return true;
            }

            @Override
            public boolean onEdgeTraversed(Edge edge, Path currentPath) {
                return true;
            }
        }, false);
        return results;
    }

    public void traverse(TraversalVisitor visitor) {
        traverse(visitor, true);
    }

    private void traverse(TraversalVisitor visitor, boolean useBfs) {
        if (!checkNodeLabels(startNode)) {
            return;
        }

        Path startPath = new Path(startNode);
        if (!visitor.onNodeVisited(startNode, startPath)) {
            return;
        }

        if (maxDepth == 0) return;

        Set<Long> visitedNodes = uniqueNodes ? new HashSet<>() : null;
        if (visitedNodes != null) {
            visitedNodes.add(startNode.getId());
        }

        Deque<Path> frontier = new ArrayDeque<>();
        frontier.add(startPath);

        while (!frontier.isEmpty()) {
            Path currentPath = useBfs ? frontier.pollFirst() : frontier.pollLast();
            Node currentNode = currentPath.endNode();

            if (currentPath.length() >= maxDepth) {
                continue;
            }

            List<Edge> edgesToFollow = getEdgesToFollow(currentNode);
            for (Edge edge : edgesToFollow) {
                if (edgeTypeFilter != null && !edgeTypeFilter.isEmpty() && !edgeTypeFilter.contains(edge.getType())) {
                    continue;
                }
                
                if (!visitor.onEdgeTraversed(edge, currentPath)) {
                    continue;
                }

                Node nextNode = (edge.getSource().equals(currentNode)) ? edge.getTarget() : edge.getSource();

                if (!checkNodeLabels(nextNode)) {
                    continue;
                }

                if (uniqueNodes) {
                    if (!visitedNodes.add(nextNode.getId())) {
                        continue;
                    }
                } else {
                    if (currentPath.contains(nextNode)) {
                        continue;
                    }
                }

                Path nextPath = currentPath.append(edge, nextNode);
                if (visitor.onNodeVisited(nextNode, nextPath)) {
                    frontier.addLast(nextPath);
                }
            }
        }
    }

    private List<Edge> getEdgesToFollow(Node node) {
        List<Edge> edges = new ArrayList<>();
        if (direction == Direction.OUTGOING || direction == Direction.BOTH) {
            edges.addAll(node.getOutEdges());
        }
        if (direction == Direction.INCOMING || direction == Direction.BOTH) {
            edges.addAll(node.getInEdges());
        }
        return edges;
    }

    private boolean checkNodeLabels(Node node) {
        if (nodeLabelFilter == null || nodeLabelFilter.isEmpty()) {
            return true;
        }
        return node.getLabels().containsAll(nodeLabelFilter);
    }
}

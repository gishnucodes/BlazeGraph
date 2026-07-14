package blazegraph.core.traversal;

import blazegraph.core.model.Edge;
import blazegraph.core.model.Node;
import blazegraph.core.storage.PropertyGraphStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PathFinder {
    private final PropertyGraphStore store;

    public PathFinder(PropertyGraphStore store) {
        this.store = store;
    }

    public Optional<Path> shortestPath(Node source, Node target, Direction dir) {
        List<Path> paths = new ArrayList<>();
        TraversalEngine.from(store, source)
                .direction(dir)
                .uniqueNodes(true)
                .build()
                .traverse(new TraversalVisitor() {
                    @Override
                    public boolean onNodeVisited(Node node, Path pathToNode) {
                        if (node.equals(target)) {
                            paths.add(pathToNode);
                            return false;
                        }
                        return true;
                    }

                    @Override
                    public boolean onEdgeTraversed(Edge edge, Path currentPath) {
                        return paths.isEmpty();
                    }
                });
        return paths.isEmpty() ? Optional.empty() : Optional.of(paths.get(0));
    }

    public List<Path> allShortestPaths(Node source, Node target, Direction dir) {
        List<Path> results = new ArrayList<>();
        int[] minLength = {Integer.MAX_VALUE};

        TraversalEngine.from(store, source)
                .direction(dir)
                .uniqueNodes(false)
                .build()
                .traverse(new TraversalVisitor() {
                    @Override
                    public boolean onNodeVisited(Node node, Path pathToNode) {
                        if (node.equals(target)) {
                            if (pathToNode.length() < minLength[0]) {
                                results.clear();
                                minLength[0] = pathToNode.length();
                                results.add(pathToNode);
                            } else if (pathToNode.length() == minLength[0]) {
                                results.add(pathToNode);
                            }
                            return false;
                        }
                        return pathToNode.length() < minLength[0];
                    }

                    @Override
                    public boolean onEdgeTraversed(Edge edge, Path currentPath) {
                        return currentPath.length() < minLength[0];
                    }
                });
        return results;
    }

    public List<Path> variableLengthPaths(Node source, Node target, Direction dir, int minHops, int maxHops) {
        List<Path> results = new ArrayList<>();
        TraversalEngine.from(store, source)
                .direction(dir)
                .maxDepth(maxHops)
                .uniqueNodes(false)
                .build()
                .traverse(new TraversalVisitor() {
                    @Override
                    public boolean onNodeVisited(Node node, Path pathToNode) {
                        int len = pathToNode.length();
                        if (len >= minHops && len <= maxHops) {
                            if (target == null || node.equals(target)) {
                                results.add(pathToNode);
                            }
                        }
                        return len < maxHops;
                    }

                    @Override
                    public boolean onEdgeTraversed(Edge edge, Path currentPath) {
                        return currentPath.length() < maxHops;
                    }
                });
        return results;
    }

    public List<Path> variableLengthPaths(Node source, Direction dir, int minHops, int maxHops) {
        return variableLengthPaths(source, null, dir, minHops, maxHops);
    }
}

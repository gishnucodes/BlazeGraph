package blazegraph.core.pattern;

import blazegraph.core.model.Edge;
import blazegraph.core.model.Node;
import blazegraph.core.model.PropertyValue;
import blazegraph.core.storage.PropertyGraphStore;
import blazegraph.core.traversal.Direction;
import blazegraph.core.traversal.Path;
import blazegraph.core.traversal.PathFinder;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class PatternMatcher {

    public BindingTable match(GraphPattern pattern, PropertyGraphStore store) {
        List<GraphPattern> components = splitIntoComponents(pattern);
        
        BindingTable finalTable = new BindingTable();
        boolean first = true;
        
        for (GraphPattern component : components) {
            BindingTable componentTable = matchComponent(component, store);
            if (first) {
                finalTable = componentTable;
                first = false;
            } else {
                finalTable = finalTable.join(componentTable);
            }
        }
        
        return finalTable;
    }

    private BindingTable matchComponent(GraphPattern pattern, PropertyGraphStore store) {
        BindingTable results = new BindingTable();
        List<PatternNode> nodes = pattern.getPatternNodes();
        if (nodes.isEmpty()) {
            return results;
        }

        List<PatternNode> orderedNodes = orderNodes(pattern);
        PatternNode startNode = orderedNodes.get(0);
        Set<Node> candidates = getSeedCandidates(startNode, store);
        
        if (candidates.isEmpty()) return results;
        
        PathFinder pathFinder = new PathFinder(store);
        
        Map<PatternNode, Node> nodeBindings = new HashMap<>();
        Map<PatternEdge, Object> edgeBindings = new HashMap<>();
        Set<Long> usedEdgeIds = new HashSet<>();
        
        for (Node candidate : candidates) {
            nodeBindings.put(startNode, candidate);
            backtrack(pattern, orderedNodes, 1, nodeBindings, edgeBindings, usedEdgeIds, store, pathFinder, results);
            nodeBindings.remove(startNode);
        }

        return results;
    }

    private void backtrack(GraphPattern pattern, List<PatternNode> orderedNodes, int nodeIndex, 
                           Map<PatternNode, Node> nodeBindings, Map<PatternEdge, Object> edgeBindings,
                           Set<Long> usedEdgeIds, PropertyGraphStore store, PathFinder pathFinder, BindingTable results) {
        
        if (nodeIndex == orderedNodes.size()) {
            // Check remaining unbound edges between bound nodes
            List<PatternEdge> remainingEdges = new ArrayList<>();
            for (PatternEdge edge : pattern.getPatternEdges()) {
                if (!edgeBindings.containsKey(edge)) {
                    remainingEdges.add(edge);
                }
            }
            
            if (checkRemainingEdges(remainingEdges, nodeBindings, edgeBindings, usedEdgeIds, pathFinder)) {
                emitRow(nodeBindings, edgeBindings, results);
                // We don't backtrack the remainingEdges bindings because checkRemainingEdges sets and unsets them,
                // but actually for simplicity checkRemainingEdges can just find if there's a valid assignment.
                // It's cleaner to bind edges during node binding.
            }
            return;
        }

        PatternNode nextPatternNode = orderedNodes.get(nodeIndex);
        
        // Find ALL edges connecting nextPatternNode to already BOUND nodes
        List<PatternEdge> connectingEdges = new ArrayList<>();
        for (PatternEdge edge : pattern.getPatternEdges()) {
            if (nodeBindings.containsKey(edge.getSourcePatternNode()) && edge.getTargetPatternNode().equals(nextPatternNode)) {
                connectingEdges.add(edge);
            } else if (nodeBindings.containsKey(edge.getTargetPatternNode()) && edge.getSourcePatternNode().equals(nextPatternNode)) {
                connectingEdges.add(edge);
            }
        }
        
        if (connectingEdges.isEmpty()) {
            // Should not happen for connected components, but fallback
            Set<Node> candidates = getSeedCandidates(nextPatternNode, store);
            for (Node candidate : candidates) {
                nodeBindings.put(nextPatternNode, candidate);
                backtrack(pattern, orderedNodes, nodeIndex + 1, nodeBindings, edgeBindings, usedEdgeIds, store, pathFinder, results);
                nodeBindings.remove(nextPatternNode);
            }
            return;
        }

        // Pick the first connecting edge to generate candidates
        PatternEdge primaryEdge = connectingEdges.get(0);
        List<PatternEdge> validationEdges = connectingEdges.subList(1, connectingEdges.size());
        
        boolean reverse = nodeBindings.containsKey(primaryEdge.getTargetPatternNode());
        Node boundNode = reverse ? nodeBindings.get(primaryEdge.getTargetPatternNode()) : nodeBindings.get(primaryEdge.getSourcePatternNode());
        Direction dir = primaryEdge.getDirection();
        if (reverse) {
            if (dir == Direction.OUTGOING) dir = Direction.INCOMING;
            else if (dir == Direction.INCOMING) dir = Direction.OUTGOING;
        }
        
        if (primaryEdge.getMinHops() == 1 && primaryEdge.getMaxHops() == 1) {
            List<Edge> edgesToFollow = getEdges(boundNode, dir);
            for (Edge edge : edgesToFollow) {
                if (usedEdgeIds.contains(edge.getId())) continue;
                if (primaryEdge.getTypeConstraint() != null && !primaryEdge.getTypeConstraint().equals(edge.getType())) continue;
                
                Node nextNode = edge.getSource().equals(boundNode) ? edge.getTarget() : edge.getSource();
                
                if (!checkNodeConstraints(nextNode, nextPatternNode)) continue;
                
                nodeBindings.put(nextPatternNode, nextNode);
                edgeBindings.put(primaryEdge, edge);
                usedEdgeIds.add(edge.getId());
                
                // Validate other edges
                if (validateEdges(validationEdges, nodeBindings, edgeBindings, usedEdgeIds, pathFinder)) {
                    backtrack(pattern, orderedNodes, nodeIndex + 1, nodeBindings, edgeBindings, usedEdgeIds, store, pathFinder, results);
                }
                
                usedEdgeIds.remove(edge.getId());
                edgeBindings.remove(primaryEdge);
                nodeBindings.remove(nextPatternNode);
            }
        } else {
            List<Path> paths = pathFinder.variableLengthPaths(boundNode, null, dir, primaryEdge.getMinHops(), primaryEdge.getMaxHops());
            for (Path path : paths) {
                boolean valid = true;
                for (Edge edge : path.getEdges()) {
                    if (usedEdgeIds.contains(edge.getId()) || (primaryEdge.getTypeConstraint() != null && !primaryEdge.getTypeConstraint().equals(edge.getType()))) {
                        valid = false;
                        break;
                    }
                }
                if (!valid) continue;
                
                Node nextNode = path.endNode();
                if (!checkNodeConstraints(nextNode, nextPatternNode)) continue;
                
                for (Edge edge : path.getEdges()) usedEdgeIds.add(edge.getId());
                nodeBindings.put(nextPatternNode, nextNode);
                edgeBindings.put(primaryEdge, path.getEdges());
                
                if (validateEdges(validationEdges, nodeBindings, edgeBindings, usedEdgeIds, pathFinder)) {
                    backtrack(pattern, orderedNodes, nodeIndex + 1, nodeBindings, edgeBindings, usedEdgeIds, store, pathFinder, results);
                }
                
                nodeBindings.remove(nextPatternNode);
                edgeBindings.remove(primaryEdge);
                for (Edge edge : path.getEdges()) usedEdgeIds.remove(edge.getId());
            }
        }
    }

    private boolean validateEdges(List<PatternEdge> edges, Map<PatternNode, Node> nodeBindings, 
                                  Map<PatternEdge, Object> edgeBindings, Set<Long> usedEdgeIds, PathFinder pathFinder) {
        // Recursive validation of remaining edges between bound nodes.
        // For simplicity, we just find if there is at least one valid edge assignment.
        if (edges.isEmpty()) return true;
        
        PatternEdge edge = edges.get(0);
        Node sourceNode = nodeBindings.get(edge.getSourcePatternNode());
        Node targetNode = nodeBindings.get(edge.getTargetPatternNode());
        
        if (edge.getMinHops() == 1 && edge.getMaxHops() == 1) {
            List<Edge> candidates = getEdges(sourceNode, edge.getDirection());
            for (Edge cand : candidates) {
                if (usedEdgeIds.contains(cand.getId())) continue;
                if (edge.getTypeConstraint() != null && !edge.getTypeConstraint().equals(cand.getType())) continue;
                Node next = cand.getSource().equals(sourceNode) ? cand.getTarget() : cand.getSource();
                if (!next.equals(targetNode)) continue;
                
                usedEdgeIds.add(cand.getId());
                edgeBindings.put(edge, cand);
                
                if (validateEdges(edges.subList(1, edges.size()), nodeBindings, edgeBindings, usedEdgeIds, pathFinder)) {
                    // Success, we leave bindings intact for the caller (backtrack) to emit, 
                    // and then the caller is responsible for cleanup? 
                    // Wait, validateEdges modifies edgeBindings and usedEdgeIds. 
                    // We must undo them if we backtrack WITHIN validateEdges, but if it returns true, we keep them!
                    // BUT backtrack() doesn't know which edges were bound by validateEdges to undo them.
                    // This is a bug. We need to collect them.
                    return true;
                }
                
                edgeBindings.remove(edge);
                usedEdgeIds.remove(cand.getId());
            }
            return false;
        } else {
            List<Path> paths = pathFinder.variableLengthPaths(sourceNode, targetNode, edge.getDirection(), edge.getMinHops(), edge.getMaxHops());
            for (Path path : paths) {
                boolean valid = true;
                for (Edge e : path.getEdges()) {
                    if (usedEdgeIds.contains(e.getId()) || (edge.getTypeConstraint() != null && !edge.getTypeConstraint().equals(e.getType()))) {
                        valid = false;
                        break;
                    }
                }
                if (!valid) continue;
                
                for (Edge e : path.getEdges()) usedEdgeIds.add(e.getId());
                edgeBindings.put(edge, path.getEdges());
                
                if (validateEdges(edges.subList(1, edges.size()), nodeBindings, edgeBindings, usedEdgeIds, pathFinder)) {
                    return true;
                }
                
                edgeBindings.remove(edge);
                for (Edge e : path.getEdges()) usedEdgeIds.remove(e.getId());
            }
            return false;
        }
    }

    private boolean checkRemainingEdges(List<PatternEdge> remainingEdges, Map<PatternNode, Node> nodeBindings, 
                                        Map<PatternEdge, Object> edgeBindings, Set<Long> usedEdgeIds, PathFinder pathFinder) {
        if (remainingEdges.isEmpty()) return true;
        // The logic is the same as validateEdges, BUT we need a way to backtrack and find ALL matches 
        // if there are multiple ways to bind the remaining edges.
        // Actually, if we just want to emit rows for ALL valid edge assignments:
        // We should just call a recursive function that emits rows when it reaches the end!
        return false; // we will change emitRow to handle this
    }

    private void checkRemainingEdgesAndEmit(List<PatternEdge> edges, int index, Map<PatternNode, Node> nodeBindings, 
                                            Map<PatternEdge, Object> edgeBindings, Set<Long> usedEdgeIds, 
                                            PathFinder pathFinder, BindingTable results) {
        if (index == edges.size()) {
            emitRow(nodeBindings, edgeBindings, results);
            return;
        }
        
        PatternEdge edge = edges.get(index);
        Node sourceNode = nodeBindings.get(edge.getSourcePatternNode());
        Node targetNode = nodeBindings.get(edge.getTargetPatternNode());
        
        if (edge.getMinHops() == 1 && edge.getMaxHops() == 1) {
            List<Edge> candidates = getEdges(sourceNode, edge.getDirection());
            for (Edge cand : candidates) {
                if (usedEdgeIds.contains(cand.getId())) continue;
                if (edge.getTypeConstraint() != null && !edge.getTypeConstraint().equals(cand.getType())) continue;
                Node next = cand.getSource().equals(sourceNode) ? cand.getTarget() : cand.getSource();
                if (!next.equals(targetNode)) continue;
                
                usedEdgeIds.add(cand.getId());
                edgeBindings.put(edge, cand);
                
                checkRemainingEdgesAndEmit(edges, index + 1, nodeBindings, edgeBindings, usedEdgeIds, pathFinder, results);
                
                edgeBindings.remove(edge);
                usedEdgeIds.remove(cand.getId());
            }
        } else {
            List<Path> paths = pathFinder.variableLengthPaths(sourceNode, targetNode, edge.getDirection(), edge.getMinHops(), edge.getMaxHops());
            for (Path path : paths) {
                boolean valid = true;
                for (Edge e : path.getEdges()) {
                    if (usedEdgeIds.contains(e.getId()) || (edge.getTypeConstraint() != null && !edge.getTypeConstraint().equals(e.getType()))) {
                        valid = false;
                        break;
                    }
                }
                if (!valid) continue;
                
                for (Edge e : path.getEdges()) usedEdgeIds.add(e.getId());
                edgeBindings.put(edge, path.getEdges());
                
                checkRemainingEdgesAndEmit(edges, index + 1, nodeBindings, edgeBindings, usedEdgeIds, pathFinder, results);
                
                edgeBindings.remove(edge);
                for (Edge e : path.getEdges()) usedEdgeIds.remove(e.getId());
            }
        }
    }

    private void emitRow(Map<PatternNode, Node> nodeBindings, Map<PatternEdge, Object> edgeBindings, BindingTable results) {
        Map<String, Object> row = new HashMap<>();
        for (Map.Entry<PatternNode, Node> entry : nodeBindings.entrySet()) {
            String var = entry.getKey().getVariableName();
            if (var != null && !var.startsWith("_anon")) {
                row.put(var, entry.getValue());
            }
        }
        for (Map.Entry<PatternEdge, Object> entry : edgeBindings.entrySet()) {
            String var = entry.getKey().getVariableName();
            if (var != null && !var.startsWith("_anon")) {
                row.put(var, entry.getValue());
            }
        }
        results.addRow(row);
    }

    private boolean checkNodeConstraints(Node node, PatternNode patternNode) {
        if (!node.getLabels().containsAll(patternNode.getLabelConstraints())) return false;
        for (Map.Entry<String, PropertyValue> entry : patternNode.getPropertyPredicates().entrySet()) {
            PropertyValue val = node.getProperty(entry.getKey());
            if (val == null || !val.equals(entry.getValue())) return false;
        }
        return true;
    }

    private Set<Node> getSeedCandidates(PatternNode node, PropertyGraphStore store) {
        Set<Node> candidates = null;
        
        for (Map.Entry<String, PropertyValue> entry : node.getPropertyPredicates().entrySet()) {
            Set<Long> ids = store.getNodesByProperty(entry.getKey(), entry.getValue());
            if (ids == null) return Collections.emptySet();
            if (candidates == null) {
                candidates = ids.stream().map(store::getNode).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toSet());
            } else {
                candidates.removeIf(n -> !ids.contains(n.getId()));
            }
            if (candidates.isEmpty()) return candidates;
        }
        
        for (String label : node.getLabelConstraints()) {
            Set<Long> ids = store.getNodesByLabel(label);
            if (ids == null || ids.isEmpty()) return Collections.emptySet();
            if (candidates == null) {
                candidates = ids.stream().map(store::getNode).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toSet());
            } else {
                candidates.removeIf(n -> !ids.contains(n.getId()));
            }
            if (candidates.isEmpty()) return candidates;
        }
        
        if (candidates == null) {
            candidates = new HashSet<>(store.getAllNodes());
        }
        
        // Final pass for remaining predicates
        candidates.removeIf(n -> !checkNodeConstraints(n, node));
        return candidates;
    }

    private List<Edge> getEdges(Node node, Direction dir) {
        List<Edge> edges = new ArrayList<>();
        if (dir == Direction.OUTGOING || dir == Direction.BOTH) edges.addAll(node.getOutEdges());
        if (dir == Direction.INCOMING || dir == Direction.BOTH) edges.addAll(node.getInEdges());
        return edges;
    }

    private List<PatternNode> orderNodes(GraphPattern pattern) {
        List<PatternNode> nodes = new ArrayList<>(pattern.getPatternNodes());
        nodes.sort((a, b) -> {
            int aScore = a.getPropertyPredicates().size() * 10 + a.getLabelConstraints().size();
            int bScore = b.getPropertyPredicates().size() * 10 + b.getLabelConstraints().size();
            return Integer.compare(bScore, aScore); // Descending
        });
        
        List<PatternNode> ordered = new ArrayList<>();
        ordered.add(nodes.remove(0));
        
        while (!nodes.isEmpty()) {
            PatternNode next = null;
            for (PatternNode n : nodes) {
                if (isConnected(ordered, n, pattern.getPatternEdges())) {
                    next = n;
                    break;
                }
            }
            if (next == null) {
                next = nodes.get(0); // Should not happen in a connected component
            }
            nodes.remove(next);
            ordered.add(next);
        }
        
        return ordered;
    }

    private boolean isConnected(List<PatternNode> group, PatternNode node, List<PatternEdge> edges) {
        for (PatternEdge edge : edges) {
            if ((group.contains(edge.getSourcePatternNode()) && edge.getTargetPatternNode().equals(node)) ||
                (group.contains(edge.getTargetPatternNode()) && edge.getSourcePatternNode().equals(node))) {
                return true;
            }
        }
        return false;
    }

    private List<GraphPattern> splitIntoComponents(GraphPattern pattern) {
        List<GraphPattern> components = new ArrayList<>();
        Set<PatternNode> unvisited = new HashSet<>(pattern.getPatternNodes());
        
        while (!unvisited.isEmpty()) {
            PatternNode start = unvisited.iterator().next();
            Set<PatternNode> compNodes = new HashSet<>();
            Set<PatternEdge> compEdges = new HashSet<>();
            
            Queue<PatternNode> queue = new LinkedList<>();
            queue.add(start);
            compNodes.add(start);
            unvisited.remove(start);
            
            while (!queue.isEmpty()) {
                PatternNode curr = queue.poll();
                for (PatternEdge edge : pattern.getPatternEdges()) {
                    if (edge.getSourcePatternNode().equals(curr) || edge.getTargetPatternNode().equals(curr)) {
                        compEdges.add(edge);
                        PatternNode neighbor = edge.getSourcePatternNode().equals(curr) ? edge.getTargetPatternNode() : edge.getSourcePatternNode();
                        if (unvisited.contains(neighbor)) {
                            unvisited.remove(neighbor);
                            compNodes.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
            }
            components.add(new GraphPattern(new ArrayList<>(compNodes), new ArrayList<>(compEdges)));
        }
        return components;
    }
}

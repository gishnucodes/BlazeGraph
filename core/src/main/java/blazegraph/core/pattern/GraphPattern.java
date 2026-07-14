package blazegraph.core.pattern;

import java.util.*;

public final class GraphPattern {
    private final List<PatternNode> patternNodes;
    private final List<PatternEdge> patternEdges;

    public GraphPattern(List<PatternNode> patternNodes, List<PatternEdge> patternEdges) {
        this.patternNodes = Collections.unmodifiableList(new ArrayList<>(patternNodes));
        this.patternEdges = Collections.unmodifiableList(new ArrayList<>(patternEdges));
    }

    public List<PatternNode> getPatternNodes() { return patternNodes; }
    public List<PatternEdge> getPatternEdges() { return patternEdges; }

    public Set<String> getBindingVariables() {
        Set<String> vars = new HashSet<>();
        for (PatternNode node : patternNodes) {
            if (node.getVariableName() != null && !node.getVariableName().startsWith("_anon")) {
                vars.add(node.getVariableName());
            }
        }
        for (PatternEdge edge : patternEdges) {
            if (edge.getVariableName() != null && !edge.getVariableName().startsWith("_anon")) {
                vars.add(edge.getVariableName());
            }
        }
        return vars;
    }

    public List<PatternNode> getPatternNodesForLabel(String label) {
        List<PatternNode> result = new ArrayList<>();
        for (PatternNode node : patternNodes) {
            if (node.getLabelConstraints().contains(label)) {
                result.add(node);
            }
        }
        return result;
    }
}

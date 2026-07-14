package blazegraph.core.pattern;

import blazegraph.core.traversal.Direction;
import java.util.Objects;

public final class PatternEdge {
    private final String variableName;
    private final String typeConstraint;
    private final Direction direction;
    private final int minHops;
    private final int maxHops;
    private final PatternNode sourcePatternNode;
    private final PatternNode targetPatternNode;

    public PatternEdge(String variableName, String typeConstraint, Direction direction, int minHops, int maxHops, PatternNode sourcePatternNode, PatternNode targetPatternNode) {
        this.variableName = variableName;
        this.typeConstraint = typeConstraint;
        this.direction = Objects.requireNonNull(direction);
        this.minHops = minHops;
        this.maxHops = maxHops;
        this.sourcePatternNode = Objects.requireNonNull(sourcePatternNode);
        this.targetPatternNode = Objects.requireNonNull(targetPatternNode);
    }

    public String getVariableName() { return variableName; }
    public String getTypeConstraint() { return typeConstraint; }
    public Direction getDirection() { return direction; }
    public int getMinHops() { return minHops; }
    public int getMaxHops() { return maxHops; }
    public PatternNode getSourcePatternNode() { return sourcePatternNode; }
    public PatternNode getTargetPatternNode() { return targetPatternNode; }
}

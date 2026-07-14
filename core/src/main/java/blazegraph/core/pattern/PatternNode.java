package blazegraph.core.pattern;

import blazegraph.core.model.PropertyValue;
import java.util.*;

public final class PatternNode {
    private final String variableName;
    private final Set<String> labelConstraints;
    private final Map<String, PropertyValue> propertyPredicates;

    public PatternNode(String variableName, Set<String> labelConstraints, Map<String, PropertyValue> propertyPredicates) {
        this.variableName = variableName;
        this.labelConstraints = labelConstraints == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(labelConstraints));
        this.propertyPredicates = propertyPredicates == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(propertyPredicates));
    }

    public String getVariableName() { return variableName; }
    public Set<String> getLabelConstraints() { return labelConstraints; }
    public Map<String, PropertyValue> getPropertyPredicates() { return propertyPredicates; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PatternNode that = (PatternNode) o;
        return Objects.equals(variableName, that.variableName) &&
                labelConstraints.equals(that.labelConstraints) &&
                propertyPredicates.equals(that.propertyPredicates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variableName, labelConstraints, propertyPredicates);
    }
}

package blazegraph.engine.executor;

import java.util.*;

public class UnionOp implements Operator {
    private final List<Operator> children;
    private final boolean distinct;
    private int currentChildIndex = 0;
    private final Set<List<Object>> seen = new HashSet<>();
    private final List<String> columns;

    public UnionOp(List<Operator> children, boolean distinct, List<String> columns) {
        this.children = children;
        this.distinct = distinct;
        this.columns = columns;
    }

    @Override
    public void open(ExecutionContext ctx) {
        for (Operator c : children) c.open(ctx);
    }

    private Object toKey(Object val) {
        if (val == null) return null;
        if (val instanceof blazegraph.core.model.PropertyValue pv) {
            if (pv.getType() == blazegraph.core.model.PropertyValue.Type.INTEGER) return ((Long) pv.getValue()).doubleValue();
            return pv.getValue();
        }
        return val;
    }

    @Override
    public Optional<Map<String, Object>> next() {
        while (currentChildIndex < children.size()) {
            Optional<Map<String, Object>> row = children.get(currentChildIndex).next();
            if (row.isPresent()) {
                if (distinct) {
                    List<Object> key = new ArrayList<>();
                    for (String col : columns) {
                        key.add(toKey(row.get().get(col)));
                    }
                    if (seen.add(key)) {
                        return row;
                    }
                } else {
                    return row;
                }
            } else {
                currentChildIndex++;
            }
        }
        return Optional.empty();
    }

    @Override
    public void close() {
        for (Operator c : children) c.close();
    }
}

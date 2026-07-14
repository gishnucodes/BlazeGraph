package blazegraph.engine.executor;

import blazegraph.core.model.PropertyValue;

import java.util.*;

public class DistinctOp implements Operator {
    private final Operator child;
    private final Set<List<Object>> seen = new HashSet<>();
    private final List<String> columns;

    public DistinctOp(Operator child, List<String> columns) {
        this.child = child;
        this.columns = columns;
    }

    @Override
    public void open(ExecutionContext ctx) {
        child.open(ctx);
    }

    private Object toKey(Object val) {
        if (val == null) return null;
        if (val instanceof PropertyValue pv) {
            if (pv.getType() == PropertyValue.Type.INTEGER) {
                return ((Long) pv.getValue()).doubleValue();
            }
            if (pv.getType() == PropertyValue.Type.DOUBLE) {
                return pv.getValue();
            }
            return pv.getValue();
        }
        return val;
    }

    @Override
    public Optional<Map<String, Object>> next() {
        while (true) {
            Optional<Map<String, Object>> row = child.next();
            if (row.isEmpty()) return row;
            
            List<Object> key = new ArrayList<>();
            for (String col : columns) {
                key.add(toKey(row.get().get(col)));
            }
            
            if (seen.add(key)) {
                return row;
            }
        }
    }

    @Override
    public void close() {
        child.close();
    }
}

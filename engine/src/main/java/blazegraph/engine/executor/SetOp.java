package blazegraph.engine.executor;

import blazegraph.core.model.Edge;
import blazegraph.core.model.Node;
import blazegraph.core.model.PropertyValue;
import blazegraph.engine.eval.ExpressionEvaluator;
import blazegraph.parser.ast.Ast.SetItem;

import java.util.*;

public class SetOp implements Operator {
    private final Operator child;
    private final List<SetItem> items;
    private final ExpressionEvaluator evaluator = new ExpressionEvaluator();
    private Iterator<Map<String, Object>> iterator;

    public SetOp(Operator child, List<SetItem> items) {
        this.child = child;
        this.items = items;
    }

    @Override
    public void open(ExecutionContext ctx) {
        child.open(ctx);
        List<Map<String, Object>> inputRows = new ArrayList<>();
        while (true) {
            Optional<Map<String, Object>> r = child.next();
            if (r.isEmpty()) break;
            inputRows.add(r.get());
        }

        for (Map.Entry<String, Object> ignored : inputRows.get(0).entrySet()) {
        } // Just suppressing if we needed to access row differently
        
        for (Map.Entry<String, Object> rowEntry : inputRows.get(0).entrySet()) {
        }
        
        for (Map.Entry<String, Object> rowEntry2 : inputRows.get(0).entrySet()) {
        }
        // Actually, we must iterate over rows and items.
        for (Map<String, Object> row : inputRows) {
            for (SetItem item : items) {
                if (item instanceof blazegraph.parser.ast.Ast.SetLabel sl) {
                    Object target = row.get(sl.variable());
                    if (target == null) continue;
                    if (target instanceof Node n) {
                        for (String l : sl.labels()) {
                            ctx.getStore().addNodeLabel(n.getId(), l);
                            ctx.incrementLabelsAdded();
                        }
                    } else {
                        throw new blazegraph.engine.eval.ExecutionException("Cannot add label to non-node");
                    }
                } else if (item instanceof blazegraph.parser.ast.Ast.SetProperty sp) {
                    Object target = row.get(sp.variable());
                    if (target == null) continue;
                    Object val = evaluator.evaluate(sp.expr(), row);
                    
                    if (target instanceof Node n) {
                        if (val == null) {
                            ctx.getStore().removeNodeProperty(n.getId(), sp.key());
                        } else if (val instanceof PropertyValue pv) {
                            ctx.getStore().setNodeProperty(n.getId(), sp.key(), pv);
                            ctx.incrementPropertiesSet();
                        }
                    } else if (target instanceof Edge e) {
                        if (val == null) {
                            ctx.getStore().removeEdgeProperty(e.getId(), sp.key());
                        } else if (val instanceof PropertyValue pv) {
                            ctx.getStore().setEdgeProperty(e.getId(), sp.key(), pv);
                            ctx.incrementPropertiesSet();
                        }
                    } else {
                        throw new blazegraph.engine.eval.ExecutionException("Cannot set property on non-element");
                    }
                }
            }
        }
        iterator = inputRows.iterator();
    }

    @Override
    public Optional<Map<String, Object>> next() {
        if (iterator.hasNext()) return Optional.of(iterator.next());
        return Optional.empty();
    }

    @Override
    public void close() {
        child.close();
    }
}

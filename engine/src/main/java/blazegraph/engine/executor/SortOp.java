package blazegraph.engine.executor;

import blazegraph.engine.eval.ExpressionEvaluator;
import blazegraph.engine.value.ValueComparator;
import blazegraph.parser.ast.Ast.SortItem;

import java.util.*;

public class SortOp implements Operator {
    private final Operator child;
    private final List<SortItem> sortItems;
    private final ExpressionEvaluator evaluator = new ExpressionEvaluator();
    private Iterator<Map<String, Object>> iterator;

    public SortOp(Operator child, List<SortItem> sortItems) {
        this.child = child;
        this.sortItems = sortItems;
    }

    @Override
    public void open(ExecutionContext ctx) {
        child.open(ctx);
        List<Map<String, Object>> rows = new ArrayList<>();
        while (true) {
            Optional<Map<String, Object>> r = child.next();
            if (r.isEmpty()) break;
            rows.add(r.get());
        }

        rows.sort((r1, r2) -> {
            for (SortItem item : sortItems) {
                Object v1 = evaluator.evaluate(item.expr(), r1);
                Object v2 = evaluator.evaluate(item.expr(), r2);
                int cmp = ValueComparator.INSTANCE.compare(v1, v2);
                if (cmp != 0) {
                    return item.ascending() ? cmp : -cmp;
                }
            }
            return 0;
        });
        iterator = rows.iterator();
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

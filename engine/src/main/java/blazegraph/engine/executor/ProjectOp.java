package blazegraph.engine.executor;

import blazegraph.engine.eval.ExpressionEvaluator;
import blazegraph.parser.ast.Ast.Expression;
import blazegraph.parser.ast.Ast.ReturnItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProjectOp implements Operator {
    private final Operator child;
    private final List<ReturnItem> items;
    private final ExpressionEvaluator evaluator = new ExpressionEvaluator();

    public ProjectOp(Operator child, List<ReturnItem> items) {
        this.child = child;
        this.items = items;
    }

    @Override
    public void open(ExecutionContext ctx) {
        child.open(ctx);
    }

    @Override
    public Optional<Map<String, Object>> next() {
        Optional<Map<String, Object>> row = child.next();
        if (row.isEmpty()) return row;
        
        Map<String, Object> out = new HashMap<>();
        for (ReturnItem item : items) {
            Object val = evaluator.evaluate(item.expr(), row.get());
            out.put(item.alias(), val);
        }
        return Optional.of(out);
    }

    @Override
    public void close() {
        child.close();
    }
}

package blazegraph.engine.executor;

import blazegraph.engine.eval.ExpressionEvaluator;
import blazegraph.core.model.PropertyValue;
import blazegraph.parser.ast.Ast.Expression;

import java.util.Map;
import java.util.Optional;

public class FilterOp implements Operator {
    private final Operator child;
    private final Expression expr;
    private final ExpressionEvaluator evaluator = new ExpressionEvaluator();
    private ExecutionContext ctx;

    public FilterOp(Operator child, Expression expr) {
        this.child = child;
        this.expr = expr;
    }

    @Override
    public void open(ExecutionContext ctx) {
        this.ctx = ctx;
        child.open(ctx);
    }

    @Override
    public Optional<Map<String, Object>> next() {
        while (true) {
            ctx.checkCancelled();
            Optional<Map<String, Object>> row = child.next();
            if (row.isEmpty()) return row;
            Object res = evaluator.evaluate(expr, row.get());
            if (res instanceof PropertyValue pv && pv.getType() == PropertyValue.Type.BOOLEAN && (Boolean) pv.getValue()) {
                return row;
            }
        }
    }

    @Override
    public void close() {
        child.close();
    }
}

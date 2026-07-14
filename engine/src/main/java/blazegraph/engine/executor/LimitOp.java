package blazegraph.engine.executor;

import java.util.Map;
import java.util.Optional;

public class LimitOp implements Operator {
    private final Operator child;
    private final int limit;
    private int emitted = 0;

    public LimitOp(Operator child, int limit) {
        this.child = child;
        this.limit = limit;
    }

    @Override
    public void open(ExecutionContext ctx) {
        child.open(ctx);
    }

    @Override
    public Optional<Map<String, Object>> next() {
        if (emitted >= limit) return Optional.empty();
        Optional<Map<String, Object>> r = child.next();
        if (r.isPresent()) emitted++;
        return r;
    }

    @Override
    public void close() {
        child.close();
    }
}

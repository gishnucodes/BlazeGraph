package blazegraph.engine.executor;

import java.util.Map;
import java.util.Optional;

public class SkipOp implements Operator {
    private final Operator child;
    private final int skip;
    private int skipped = 0;

    public SkipOp(Operator child, int skip) {
        this.child = child;
        this.skip = skip;
    }

    @Override
    public void open(ExecutionContext ctx) {
        child.open(ctx);
    }

    @Override
    public Optional<Map<String, Object>> next() {
        while (skipped < skip) {
            Optional<Map<String, Object>> r = child.next();
            if (r.isEmpty()) return r;
            skipped++;
        }
        return child.next();
    }

    @Override
    public void close() {
        child.close();
    }
}

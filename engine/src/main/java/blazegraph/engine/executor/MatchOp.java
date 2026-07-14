package blazegraph.engine.executor;

import blazegraph.core.pattern.BindingTable;
import blazegraph.core.pattern.GraphPattern;
import blazegraph.core.pattern.PatternMatcher;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

public class MatchOp implements Operator {
    private final GraphPattern pattern;
    private final Operator child;
    private Iterator<Map<String, Object>> iterator;

    public MatchOp(GraphPattern pattern, Operator child) {
        this.pattern = pattern;
        this.child = child; 
    }

    @Override
    public void open(ExecutionContext ctx) {
        if (child != null) child.open(ctx);
        PatternMatcher matcher = new PatternMatcher();
        BindingTable right = matcher.match(pattern, ctx.getStore());
        
        BindingTable result;
        if (child != null) {
            BindingTable left = new BindingTable();
            while (true) {
                Optional<Map<String, Object>> row = child.next();
                if (row.isEmpty()) break;
                left.addRow(row.get());
            }
            result = left.join(right);
        } else {
            result = right;
        }
        iterator = result.getRows().iterator();
    }

    @Override
    public Optional<Map<String, Object>> next() {
        if (iterator != null && iterator.hasNext()) {
            return Optional.of(iterator.next());
        }
        return Optional.empty();
    }

    @Override
    public void close() {
        if (child != null) child.close();
    }
}

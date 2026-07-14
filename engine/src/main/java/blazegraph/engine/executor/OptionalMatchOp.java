package blazegraph.engine.executor;

import blazegraph.core.pattern.BindingTable;
import blazegraph.core.pattern.GraphPattern;
import blazegraph.core.pattern.PatternMatcher;

import java.util.*;

public class OptionalMatchOp implements Operator {
    private final GraphPattern pattern;
    private final Operator child;
    private final Set<String> patternVariables;
    private Iterator<Map<String, Object>> iterator;

    public OptionalMatchOp(GraphPattern pattern, Operator child, Set<String> patternVariables) {
        this.pattern = pattern;
        this.child = child;
        this.patternVariables = patternVariables;
    }

    @Override
    public void open(ExecutionContext ctx) {
        child.open(ctx);
        PatternMatcher matcher = new PatternMatcher();
        BindingTable right = matcher.match(pattern, ctx.getStore());
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        while (true) {
            Optional<Map<String, Object>> optRow = child.next();
            if (optRow.isEmpty()) break;
            Map<String, Object> leftRow = optRow.get();
            
            boolean matched = false;
            for (Map<String, Object> rightRow : right.getRows()) {
                if (canJoin(leftRow, rightRow)) {
                    Map<String, Object> combined = new HashMap<>(leftRow);
                    combined.putAll(rightRow);
                    results.add(combined);
                    matched = true;
                }
            }
            
            if (!matched) {
                Map<String, Object> padded = new HashMap<>(leftRow);
                for (String v : patternVariables) {
                    if (!padded.containsKey(v)) padded.put(v, null);
                }
                results.add(padded);
            }
        }
        iterator = results.iterator();
    }

    private boolean canJoin(Map<String, Object> r1, Map<String, Object> r2) {
        for (String k : r1.keySet()) {
            if (r2.containsKey(k) && !r1.get(k).equals(r2.get(k))) return false;
        }
        return true;
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

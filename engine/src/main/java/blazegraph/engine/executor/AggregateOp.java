package blazegraph.engine.executor;

import blazegraph.engine.eval.ExpressionEvaluator;
import blazegraph.engine.eval.ExecutionException;
import blazegraph.engine.value.ValueComparator;
import blazegraph.core.model.PropertyValue;
import blazegraph.parser.ast.Ast.Expression;
import blazegraph.parser.ast.Ast.FunctionCall;

import java.util.*;

public class AggregateOp implements Operator {
    private final Operator child;
    private final List<Expression> groupKeys;
    private final Map<String, FunctionCall> aggregates; // Alias -> FunctionCall
    private final ExpressionEvaluator evaluator = new ExpressionEvaluator();
    private Iterator<Map<String, Object>> iterator;

    public AggregateOp(Operator child, List<Expression> groupKeys, Map<String, FunctionCall> aggregates) {
        this.child = child;
        this.groupKeys = groupKeys;
        this.aggregates = aggregates;
    }

    @Override
    public void open(ExecutionContext ctx) {
        child.open(ctx);
        Map<List<Object>, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        boolean hasRows = false;
        
        while (true) {
            Optional<Map<String, Object>> r = child.next();
            if (r.isEmpty()) break;
            hasRows = true;
            Map<String, Object> row = r.get();
            List<Object> key = new ArrayList<>();
            for (Expression gk : groupKeys) {
                Object val = evaluator.evaluate(gk, row);
                key.add(toHashKey(val));
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        List<Map<String, Object>> outRows = new ArrayList<>();
        if (!hasRows) {
            if (groupKeys.isEmpty()) { 
                groups.put(Collections.emptyList(), Collections.emptyList());
            }
        }

        for (Map.Entry<List<Object>, List<Map<String, Object>>> entry : groups.entrySet()) {
            Map<String, Object> outRow = new HashMap<>();
            List<Map<String, Object>> groupRows = entry.getValue();
            
            if (!groupRows.isEmpty()) {
                outRow.putAll(groupRows.get(0));
            }
            
            for (Map.Entry<String, FunctionCall> agg : aggregates.entrySet()) {
                String alias = agg.getKey();
                FunctionCall call = agg.getValue();
                Object val = computeAggregate(call, groupRows);
                outRow.put(alias, val);
            }
            outRows.add(outRow);
        }
        
        iterator = outRows.iterator();
    }
    
    private Object toHashKey(Object val) {
        if (val == null) return null;
        if (val instanceof PropertyValue pv) {
            if (pv.getType() == PropertyValue.Type.INTEGER) return ((Long) pv.getValue()).doubleValue();
            return pv.getValue();
        }
        return val;
    }
    
    private Object computeAggregate(FunctionCall call, List<Map<String, Object>> rows) {
        String func = call.name().toUpperCase();
        boolean isCountStar = func.equals("COUNT") && call.args().isEmpty(); 
        
        if (isCountStar) {
            return PropertyValue.of((long) rows.size());
        }
        
        boolean distinct = call.distinct();
        Expression arg = call.args().get(0);
        
        List<Object> values = new ArrayList<>();
        Set<Object> seen = new HashSet<>();
        
        for (Map<String, Object> row : rows) {
            Object v = evaluator.evaluate(arg, row);
            if (v != null) {
                if (distinct) {
                    if (seen.add(toHashKey(v))) {
                        values.add(v);
                    }
                } else {
                    values.add(v);
                }
            }
        }
        
        if (func.equals("COUNT")) {
            return PropertyValue.of((long) values.size());
        }
        if (func.equals("COLLECT")) {
            return values; 
        }
        
        if (values.isEmpty()) return null;
        
        if (func.equals("SUM")) {
            double sum = 0;
            boolean hasDouble = false;
            long sumInt = 0;
            boolean allInts = true;
            for (Object v : values) {
                if (v instanceof PropertyValue pv) {
                    if (pv.getType() == PropertyValue.Type.DOUBLE) {
                        hasDouble = true;
                        allInts = false;
                        sum += (Double) pv.getValue();
                    } else if (pv.getType() == PropertyValue.Type.INTEGER) {
                        sum += (Long) pv.getValue();
                        sumInt += (Long) pv.getValue();
                    } else {
                        throw new ExecutionException("SUM on non-number");
                    }
                } else {
                    throw new ExecutionException("SUM on non-number");
                }
            }
            return allInts ? PropertyValue.of(sumInt) : PropertyValue.of(sum);
        }
        
        if (func.equals("AVG")) {
            double sum = 0;
            for (Object v : values) {
                if (v instanceof PropertyValue pv) {
                    if (pv.getType() == PropertyValue.Type.DOUBLE) {
                        sum += (Double) pv.getValue();
                    } else if (pv.getType() == PropertyValue.Type.INTEGER) {
                        sum += (Long) pv.getValue();
                    } else {
                        throw new ExecutionException("AVG on non-number");
                    }
                } else {
                    throw new ExecutionException("AVG on non-number");
                }
            }
            return PropertyValue.of(sum / values.size());
        }
        
        if (func.equals("MIN") || func.equals("MAX")) {
            Object best = values.get(0);
            for (int i = 1; i < values.size(); i++) {
                Object v = values.get(i);
                int cmp = ValueComparator.INSTANCE.compare(v, best);
                if (func.equals("MIN") && cmp < 0) best = v;
                if (func.equals("MAX") && cmp > 0) best = v;
            }
            return best;
        }
        
        throw new ExecutionException("Unknown aggregate function: " + func);
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

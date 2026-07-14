package blazegraph.engine.eval;

import blazegraph.core.model.Edge;
import blazegraph.core.model.Node;
import blazegraph.core.model.PropertyValue;
import blazegraph.engine.value.Values.Truth;
import blazegraph.engine.value.ValueComparator;
import blazegraph.parser.ast.Ast.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExpressionEvaluator {

    public Object evaluate(Expression expr, Map<String, Object> row) {
        if (expr instanceof Literal lit) {
            if (lit.value() == null) return null;
            Object v = lit.value();
            if (v instanceof Long) return PropertyValue.of((Long) v);
            if (v instanceof Double) return PropertyValue.of((Double) v);
            if (v instanceof Boolean) return PropertyValue.of((Boolean) v);
            if (v instanceof String) return PropertyValue.of((String) v);
            throw new ExecutionException("Unsupported literal type");
        } else if (expr instanceof Variable var) {
            return row.get(var.name());
        } else if (expr instanceof PropertyAccess pa) {
            Object subj = evaluate(new Variable(pa.subject(), pa.line(), pa.col()), row);
            if (subj == null) return null;
            if (subj instanceof Node n) {
                return n.getProperty(pa.key());
            } else if (subj instanceof Edge e) {
                return e.getProperty(pa.key());
            } else {
                throw new ExecutionException("Line " + pa.line() + ": Property access on non-element");
            }
        } else if (expr instanceof BinaryOp bin) {
            return evalBinary(bin, row);
        } else if (expr instanceof UnaryOp un) {
            return evalUnary(un, row);
        } else if (expr instanceof IsNull inull) {
            Object val = evaluate(inull.expr(), row);
            boolean isNull = (val == null);
            return PropertyValue.of(inull.negated() ? !isNull : isNull);
        } else if (expr instanceof InList inl) {
            Object val = evaluate(inl.expr(), row);
            if (val == null) return null;
            ListLiteral list = inl.list();
            boolean found = false;
            boolean hasNull = false;
            for (Expression e : list.elements()) {
                Object le = evaluate(e, row);
                if (le == null) {
                    hasNull = true;
                } else if (ValueComparator.equalsWithCoercion(val, le)) {
                    found = true;
                    break;
                }
            }
            if (found) return PropertyValue.of(true);
            if (hasNull) return null;
            return PropertyValue.of(false);
        } else if (expr instanceof ListLiteral ll) {
            List<Object> res = new ArrayList<>();
            for (Expression e : ll.elements()) {
                res.add(evaluate(e, row));
            }
            return res; 
        } else if (expr instanceof FunctionCall) {
            throw new ExecutionException("Line " + expr.line() + ": Nested aggregate evaluation not supported in scalar evaluator");
        }
        return null;
    }

    private Object evalBinary(BinaryOp bin, Map<String, Object> row) {
        String op = bin.op().toUpperCase();
        if (op.equals("AND") || op.equals("OR") || op.equals("XOR")) {
            Object l = evaluate(bin.left(), row);
            Object r = evaluate(bin.right(), row);
            Truth tl = toTruth(l);
            Truth tr = toTruth(r);
            Truth res = switch(op) {
                case "AND" -> tl.and(tr);
                case "OR" -> tl.or(tr);
                case "XOR" -> tl.xor(tr);
                default -> Truth.UNKNOWN;
            };
            if (res == Truth.UNKNOWN) return null;
            return PropertyValue.of(res == Truth.TRUE);
        }

        Object l = evaluate(bin.left(), row);
        Object r = evaluate(bin.right(), row);

        if (op.equals("=") || op.equals("<>") || op.equals("<") || op.equals("<=") || op.equals(">") || op.equals(">=")) {
            if (l == null || r == null) return null;
            
            Integer cmp = ValueComparator.compareForPredicate(l, r);
            if (cmp == null) return null;
            
            return switch(op) {
                case "=" -> PropertyValue.of(cmp == 0);
                case "<>" -> PropertyValue.of(cmp != 0);
                case "<" -> PropertyValue.of(cmp < 0);
                case "<=" -> PropertyValue.of(cmp <= 0);
                case ">" -> PropertyValue.of(cmp > 0);
                case ">=" -> PropertyValue.of(cmp >= 0);
                default -> null;
            };
        }

        if (l == null || r == null) return null;
        if (!(l instanceof PropertyValue pl) || !(r instanceof PropertyValue pr)) return null;

        if (pl.getType() == PropertyValue.Type.INTEGER && pr.getType() == PropertyValue.Type.INTEGER) {
            long vl = (Long) pl.getValue();
            long vr = (Long) pr.getValue();
            try {
                return switch(op) {
                    case "+" -> PropertyValue.of(Math.addExact(vl, vr));
                    case "-" -> PropertyValue.of(Math.subtractExact(vl, vr));
                    case "*" -> PropertyValue.of(Math.multiplyExact(vl, vr));
                    case "/" -> PropertyValue.of(vl / vr); 
                    case "%" -> PropertyValue.of(vl % vr);
                    default -> null;
                };
            } catch (ArithmeticException e) {
                throw new ExecutionException("Arithmetic error: " + e.getMessage());
            }
        } else if (isNumber(pl) && isNumber(pr)) {
            double vl = asDouble(pl);
            double vr = asDouble(pr);
            return switch(op) {
                case "+" -> PropertyValue.of(vl + vr);
                case "-" -> PropertyValue.of(vl - vr);
                case "*" -> PropertyValue.of(vl * vr);
                case "/" -> PropertyValue.of(vl / vr);
                case "%" -> PropertyValue.of(vl % vr);
                default -> null;
            };
        }
        return null;
    }

    private Object evalUnary(UnaryOp un, Map<String, Object> row) {
        Object val = evaluate(un.expr(), row);
        if (val == null) return null;
        if (un.op().equalsIgnoreCase("NOT")) {
            Truth t = toTruth(val);
            t = t.not();
            if (t == Truth.UNKNOWN) return null;
            return PropertyValue.of(t == Truth.TRUE);
        }
        if (un.op().equals("-")) {
            if (val instanceof PropertyValue pv) {
                if (pv.getType() == PropertyValue.Type.INTEGER) return PropertyValue.of(-((Long) pv.getValue()));
                if (pv.getType() == PropertyValue.Type.DOUBLE) return PropertyValue.of(-((Double) pv.getValue()));
            }
        }
        if (un.op().equals("+")) {
            return val;
        }
        return null;
    }

    private Truth toTruth(Object o) {
        if (o == null) return Truth.UNKNOWN;
        if (o instanceof PropertyValue pv && pv.getType() == PropertyValue.Type.BOOLEAN) {
            return Truth.from((Boolean) pv.getValue());
        }
        return Truth.UNKNOWN;
    }
    
    private boolean isNumber(PropertyValue p) {
        return p.getType() == PropertyValue.Type.INTEGER || p.getType() == PropertyValue.Type.DOUBLE;
    }

    private double asDouble(PropertyValue p) {
        if (p.getType() == PropertyValue.Type.INTEGER) return ((Long) p.getValue()).doubleValue();
        return (Double) p.getValue();
    }
}

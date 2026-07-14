package blazegraph.engine.planner;

import blazegraph.engine.executor.*;
import blazegraph.parser.ast.Ast.*;
import blazegraph.core.storage.PropertyGraphStore;

import java.util.*;

public class QueryPlanner {
    
    public Operator plan(Statement stmt) {
        if (stmt instanceof QueryStatement q) {
            if (q.unionArms() != null && !q.unionArms().isEmpty()) {
                List<Operator> ops = new ArrayList<>();
                List<String> cols = new ArrayList<>();
                for (int i = 0; i < q.unionArms().size(); i++) {
                    QueryStatement arm = q.unionArms().get(i);
                    ops.add(planSingleQuery(arm, cols));
                }
                
                Operator current = ops.get(0);
                for (int i = 1; i < ops.size(); i++) {
                    boolean unionAll = q.unionAlls().get(i - 1);
                    current = new UnionOp(Arrays.asList(current, ops.get(i)), !unionAll, cols);
                }
                return current;
            }
            return planSingleQuery(q, new ArrayList<>());
        } else if (stmt instanceof MutationStatement m) {
            return planMutation(m);
        }
        return null;
    }

    private Operator planSingleQuery(QueryStatement stmt, List<String> outCols) {
        Operator current = null;
        Set<String> boundVars = new HashSet<>();
        
        for (MatchClause match : stmt.matchClauses()) {
            if (match.optional()) {
                Set<String> newVars = extractVars(match.patterns());
                current = new OptionalMatchOp(toCorePattern(match.patterns()), current, newVars);
                boundVars.addAll(newVars);
            } else {
                current = new MatchOp(toCorePattern(match.patterns()), current);
                boundVars.addAll(extractVars(match.patterns()));
            }
        }
        
        if (stmt.whereClause() != null) {
            checkVars(stmt.whereClause(), boundVars);
            current = new FilterOp(current, stmt.whereClause());
        }
        
        ReturnClause ret = stmt.returnClause();
        if (ret != null) {
            Map<String, FunctionCall> aggregates = new LinkedHashMap<>();
            List<ReturnItem> newItems = new ArrayList<>();
            List<Expression> groupKeys = new ArrayList<>();
            int[] aggId = {0};
            
            for (ReturnItem item : ret.items()) {
                Expression expr = item.expr();
                Expression newExpr = extractAggregates(expr, aggregates, boundVars, aggId);
                newItems.add(new ReturnItem(newExpr, item.alias(), item.line(), item.col()));
                
                if (expr.equals(newExpr)) {
                    groupKeys.add(expr);
                }
            }
            
            if (!aggregates.isEmpty()) {
                current = new AggregateOp(current, groupKeys, aggregates);
            }
            
            for (ReturnItem item : ret.items()) {
                checkVars(item.expr(), boundVars); 
            }
            
            current = new ProjectOp(current, newItems);
            
            for (ReturnItem item : newItems) {
                if (outCols != null && !outCols.contains(item.alias())) outCols.add(item.alias());
            }
            
            if (ret.distinct()) {
                List<String> cols = new ArrayList<>();
                for (ReturnItem item : newItems) cols.add(item.alias());
                current = new DistinctOp(current, cols);
            }
        }
        
        if (stmt.orderBy() != null) {
            current = new SortOp(current, stmt.orderBy());
        }
        
        if (stmt.skip() != null) {
            if (stmt.skip() instanceof Literal lit && lit.value() instanceof Long v) {
                current = new SkipOp(current, v.intValue());
            } else {
                throw new SemanticException("SKIP must be an integer literal");
            }
        }
        
        if (stmt.limit() != null) {
            if (stmt.limit() instanceof Literal lit && lit.value() instanceof Long v) {
                current = new LimitOp(current, v.intValue());
            } else {
                throw new SemanticException("LIMIT must be an integer literal");
            }
        }
        
        return current;
    }

    private Operator planMutation(MutationStatement stmt) {
        Operator current = null;
        Set<String> boundVars = new HashSet<>();
        
        if (stmt.matchClauses() != null) {
            for (MatchClause match : stmt.matchClauses()) {
                if (match.optional()) {
                    Set<String> newVars = extractVars(match.patterns());
                    current = new OptionalMatchOp(toCorePattern(match.patterns()), current, newVars);
                    boundVars.addAll(newVars);
                } else {
                    current = new MatchOp(toCorePattern(match.patterns()), current);
                    boundVars.addAll(extractVars(match.patterns()));
                }
            }
        }
        
        if (stmt.whereClause() != null) {
            checkVars(stmt.whereClause(), boundVars);
            current = new FilterOp(current, stmt.whereClause());
        }
        
        for (MutationClause mut : stmt.mutationClauses()) {
            if (mut instanceof InsertClause ins) {
                current = new InsertOp(current, ins.patterns());
                boundVars.addAll(extractVars(ins.patterns()));
            } else if (mut instanceof SetClause set) {
                for (SetItem item : set.items()) {
                    String v = item instanceof SetProperty sp ? sp.variable() : ((SetLabel) item).variable();
                    if (!boundVars.contains(v)) throw new SemanticException("Variable " + v + " not bound");
                    if (item instanceof SetProperty sp && sp.expr() != null) checkVars(sp.expr(), boundVars);
                }
                current = new SetOp(current, set.items());
            } else if (mut instanceof DeleteClause del) {
                for (Expression tgt : del.targets()) {
                    if (tgt instanceof Variable var) {
                        if (!boundVars.contains(var.name())) throw new SemanticException("Variable " + var.name() + " not bound");
                    } else {
                        throw new SemanticException("Delete targets must be variables");
                    }
                }
                List<String> delVars = new ArrayList<>();
                for (Expression tgt : del.targets()) delVars.add(((Variable) tgt).name());
                current = new DeleteOp(current, delVars, del.detach());
            }
        }
        
        ReturnClause ret = stmt.returnClause();
        if (ret != null) {
            Map<String, FunctionCall> aggregates = new LinkedHashMap<>();
            List<ReturnItem> newItems = new ArrayList<>();
            List<Expression> groupKeys = new ArrayList<>();
            int[] aggId = {0};
            
            for (ReturnItem item : ret.items()) {
                Expression expr = item.expr();
                Expression newExpr = extractAggregates(expr, aggregates, boundVars, aggId);
                newItems.add(new ReturnItem(newExpr, item.alias(), item.line(), item.col()));
                
                if (expr.equals(newExpr)) {
                    groupKeys.add(expr);
                }
            }
            
            if (!aggregates.isEmpty()) {
                current = new AggregateOp(current, groupKeys, aggregates);
            }
            
            for (ReturnItem item : ret.items()) {
                checkVars(item.expr(), boundVars); 
            }
            
            current = new ProjectOp(current, newItems);
            
            if (ret.distinct()) {
                List<String> cols = new ArrayList<>();
                for (ReturnItem item : newItems) cols.add(item.alias());
                current = new DistinctOp(current, cols);
            }
        }
        return current;
    }
    
    private Expression extractAggregates(Expression expr, Map<String, FunctionCall> aggregates, Set<String> boundVars, int[] id) {
        if (expr instanceof FunctionCall call) {
            String name = call.name().toUpperCase();
            if (name.equals("COUNT") || name.equals("SUM") || name.equals("AVG") || name.equals("MIN") || name.equals("MAX") || name.equals("COLLECT")) {
                String alias = "_agg_" + (id[0]++);
                aggregates.put(alias, call);
                boundVars.add(alias); // So ProjectOp evaluation succeeds
                return new Variable(alias, expr.line(), expr.col());
            }
        } else if (expr instanceof BinaryOp bin) {
            return new BinaryOp(bin.op(), extractAggregates(bin.left(), aggregates, boundVars, id), extractAggregates(bin.right(), aggregates, boundVars, id), bin.line(), bin.col());
        }
        return expr;
    }

    private void checkVars(Expression expr, Set<String> bound) {
        if (expr instanceof Variable var) {
            if (!bound.contains(var.name())) {
                throw new SemanticException("Line " + var.line() + ": Variable " + var.name() + " is not bound");
            }
        } else if (expr instanceof BinaryOp bin) {
            checkVars(bin.left(), bound);
            checkVars(bin.right(), bound);
        } else if (expr instanceof UnaryOp un) {
            checkVars(un.expr(), bound);
        } else if (expr instanceof FunctionCall call) {
            for (Expression arg : call.args()) checkVars(arg, bound);
        } else if (expr instanceof PropertyAccess pa) {
            if (!bound.contains(pa.subject())) {
                throw new SemanticException("Line " + pa.line() + ": Variable " + pa.subject() + " is not bound");
            }
        }
    }

    private blazegraph.core.pattern.GraphPattern toCorePattern(List<PathPattern> paths) {
        List<blazegraph.core.pattern.PatternNode> nodes = new ArrayList<>();
        List<blazegraph.core.pattern.PatternEdge> edges = new ArrayList<>();
        Map<NodePatternAst, blazegraph.core.pattern.PatternNode> nodeMap = new HashMap<>();

        for (PathPattern path : paths) {
            for (NodePatternAst n : path.nodes()) {
                if (!nodeMap.containsKey(n)) {
                    Map<String, blazegraph.core.model.PropertyValue> props = new HashMap<>();
                    for (Map.Entry<String, Expression> prop : n.properties().entrySet()) {
                        if (prop.getValue() instanceof Literal lit) {
                            Object v = lit.value();
                            if (v instanceof Long) props.put(prop.getKey(), blazegraph.core.model.PropertyValue.of((Long) v));
                            else if (v instanceof Double) props.put(prop.getKey(), blazegraph.core.model.PropertyValue.of((Double) v));
                            else if (v instanceof Boolean) props.put(prop.getKey(), blazegraph.core.model.PropertyValue.of((Boolean) v));
                            else if (v instanceof String) props.put(prop.getKey(), blazegraph.core.model.PropertyValue.of((String) v));
                        } else {
                            throw new SemanticException("Only literals allowed in pattern properties");
                        }
                    }
                    blazegraph.core.pattern.PatternNode coreNode = new blazegraph.core.pattern.PatternNode(n.variable(), new HashSet<>(n.labels()), props);
                    nodes.add(coreNode);
                    nodeMap.put(n, coreNode);
                }
            }
            for (int i = 0; i < path.edges().size(); i++) {
                EdgePatternAst e = path.edges().get(i);
                blazegraph.core.pattern.PatternNode source = nodeMap.get(path.nodes().get(i));
                blazegraph.core.pattern.PatternNode target = nodeMap.get(path.nodes().get(i+1));
                
                blazegraph.core.traversal.Direction dir = switch(e.direction()) {
                    case "OUTGOING" -> blazegraph.core.traversal.Direction.OUTGOING;
                    case "INCOMING" -> blazegraph.core.traversal.Direction.INCOMING;
                    default -> blazegraph.core.traversal.Direction.BOTH;
                };
                
                if (e.types().size() > 1) {
                    throw new SemanticException("Multiple edge types in pattern are not supported");
                }
                
                String typeConstraint = e.types().isEmpty() ? null : e.types().get(0);
                
                blazegraph.core.pattern.PatternEdge coreEdge = new blazegraph.core.pattern.PatternEdge(
                    e.variable(), typeConstraint, dir, e.minHops(), e.maxHops(), source, target
                );
                edges.add(coreEdge);
            }
        }
        return new blazegraph.core.pattern.GraphPattern(nodes, edges);
    }
    
    private Set<String> extractVars(List<PathPattern> paths) {
        Set<String> vars = new HashSet<>();
        for (PathPattern p : paths) {
            for (NodePatternAst n : p.nodes()) {
                if (n.variable() != null && !n.variable().startsWith("_anon")) vars.add(n.variable());
            }
            for (EdgePatternAst e : p.edges()) {
                if (e.variable() != null && !e.variable().startsWith("_anon")) vars.add(e.variable());
            }
        }
        return vars;
    }
}

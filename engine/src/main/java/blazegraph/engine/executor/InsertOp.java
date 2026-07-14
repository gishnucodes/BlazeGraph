package blazegraph.engine.executor;

import blazegraph.core.model.Edge;
import blazegraph.core.model.Node;
import blazegraph.core.model.PropertyValue;
import blazegraph.core.storage.PropertyGraphStore;
import blazegraph.engine.eval.ExpressionEvaluator;
import blazegraph.parser.ast.Ast.*;

import java.util.*;

public class InsertOp implements Operator {
    private final Operator child;
    private final List<PathPattern> patterns;
    private final ExpressionEvaluator evaluator = new ExpressionEvaluator();
    private Iterator<Map<String, Object>> iterator;

    public InsertOp(Operator child, List<PathPattern> patterns) {
        this.child = child;
        this.patterns = patterns;
    }

    @Override
    public void open(ExecutionContext ctx) {
        if (child != null) child.open(ctx);
        List<Map<String, Object>> inputRows = new ArrayList<>();
        if (child != null) {
            while (true) {
                Optional<Map<String, Object>> r = child.next();
                if (r.isEmpty()) break;
                inputRows.add(r.get());
            }
        } else {
            inputRows.add(new HashMap<>()); 
        }

        PropertyGraphStore store = ctx.getStore();
        
        for (Map<String, Object> row : inputRows) {
            for (PathPattern pattern : patterns) {
                Map<NodePatternAst, Node> nodeMap = new HashMap<>();
                for (NodePatternAst np : pattern.nodes()) {
                    Node node = null;
                    if (np.variable() != null && row.containsKey(np.variable())) {
                        Object existing = row.get(np.variable());
                        if (existing instanceof Node n) {
                            node = n; 
                        } else {
                            throw new blazegraph.engine.eval.ExecutionException("Variable " + np.variable() + " is not a node");
                        }
                    } else {
                        node = store.createNode(null);
                        ctx.incrementNodesCreated();
                        for (String label : np.labels()) {
                            store.addNodeLabel(node.getId(), label);
                            ctx.incrementLabelsAdded();
                        }
                        for (Map.Entry<String, Expression> prop : np.properties().entrySet()) {
                            Object val = evaluator.evaluate(prop.getValue(), row);
                            if (val instanceof PropertyValue pv) {
                                store.setNodeProperty(node.getId(), prop.getKey(), pv);
                                ctx.incrementPropertiesSet();
                            } else if (val != null) {
                                throw new blazegraph.engine.eval.ExecutionException("Invalid property value");
                            }
                        }
                        if (np.variable() != null && !np.variable().startsWith("_anon")) {
                            row.put(np.variable(), node);
                        }
                    }
                    nodeMap.put(np, node);
                }
                
                for (EdgePatternAst ep : pattern.edges()) {
                    Node source = nodeMap.get(pattern.nodes().get(pattern.edges().indexOf(ep)));
                    Node target = nodeMap.get(pattern.nodes().get(pattern.edges().indexOf(ep) + 1));
                    
                    if (ep.direction().equals("INCOMING")) {
                        Node tmp = source;
                        source = target;
                        target = tmp;
                    } else if (ep.direction().equals("BOTH") || ep.direction().equals("UNDIRECTED")) {
                        throw new blazegraph.engine.planner.SemanticException("INSERT requires directed edges");
                    }
                    
                    if (ep.types().size() != 1) {
                        throw new blazegraph.engine.planner.SemanticException("INSERT requires exactly one edge type");
                    }
                    
                    Edge edge = store.createEdge(source, target, ep.types().get(0));
                    ctx.incrementEdgesCreated();
                    
                    for (Map.Entry<String, Expression> prop : ep.properties().entrySet()) {
                        Object val = evaluator.evaluate(prop.getValue(), row);
                        if (val instanceof PropertyValue pv) {
                            store.setEdgeProperty(edge.getId(), prop.getKey(), pv);
                            ctx.incrementPropertiesSet();
                        } else if (val != null) {
                            throw new blazegraph.engine.eval.ExecutionException("Invalid property value");
                        }
                    }
                    if (ep.variable() != null && !ep.variable().startsWith("_anon")) {
                        row.put(ep.variable(), edge);
                    }
                }
            }
        }
        iterator = inputRows.iterator();
    }

    @Override
    public Optional<Map<String, Object>> next() {
        if (iterator.hasNext()) return Optional.of(iterator.next());
        return Optional.empty();
    }

    @Override
    public void close() {
        if (child != null) child.close();
    }
}

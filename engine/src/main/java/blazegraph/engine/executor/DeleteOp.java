package blazegraph.engine.executor;

import blazegraph.core.model.Edge;
import blazegraph.core.model.Node;

import java.util.*;

public class DeleteOp implements Operator {
    private final Operator child;
    private final List<String> variables;
    private final boolean detach;
    private Iterator<Map<String, Object>> iterator;

    public DeleteOp(Operator child, List<String> variables, boolean detach) {
        this.child = child;
        this.variables = variables;
        this.detach = detach;
    }

    @Override
    public void open(ExecutionContext ctx) {
        child.open(ctx);
        List<Map<String, Object>> inputRows = new ArrayList<>();
        while (true) {
            Optional<Map<String, Object>> r = child.next();
            if (r.isEmpty()) break;
            inputRows.add(r.get());
        }

        Set<Long> deletedNodes = new HashSet<>();
        Set<Long> deletedEdges = new HashSet<>();

        for (Map<String, Object> row : inputRows) {
            for (String var : variables) {
                Object target = row.get(var);
                if (target == null) continue;
                
                if (target instanceof Node n) {
                    if (!deletedNodes.contains(n.getId())) {
                        if (!detach && (!n.getOutEdges().isEmpty() || !n.getInEdges().isEmpty())) {
                            throw new blazegraph.engine.eval.ExecutionException("Cannot delete node with edges (use DETACH DELETE)");
                        }
                        if (detach) {
                            for (Edge e : n.getOutEdges()) {
                                if (deletedEdges.add(e.getId())) ctx.incrementEdgesDeleted();
                            }
                            for (Edge e : n.getInEdges()) {
                                if (deletedEdges.add(e.getId())) ctx.incrementEdgesDeleted();
                            }
                        }
                        ctx.getStore().deleteNode(n.getId());
                        deletedNodes.add(n.getId());
                        ctx.incrementNodesDeleted();
                    }
                } else if (target instanceof Edge e) {
                    if (deletedEdges.add(e.getId())) {
                        ctx.getStore().deleteEdge(e.getId());
                        ctx.incrementEdgesDeleted();
                    }
                } else {
                    throw new blazegraph.engine.eval.ExecutionException("Cannot delete non-element");
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
        child.close();
    }
}

package blazegraph.engine;

import blazegraph.core.storage.PropertyGraphStore;
import blazegraph.engine.executor.ExecutionContext;
import blazegraph.engine.executor.Operator;
import blazegraph.engine.executor.UnionOp;
import blazegraph.engine.planner.QueryPlanner;
import blazegraph.engine.result.QueryResult;
import blazegraph.engine.result.QueryStats;
import blazegraph.parser.BlazeParser;
import blazegraph.parser.ast.Ast.GqlProgram;
import blazegraph.parser.ast.Ast.QueryStatement;
import blazegraph.parser.ast.Ast.ReturnItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

public class QueryEngine {
    private final PropertyGraphStore store;

    public QueryEngine(PropertyGraphStore store) {
        this.store = store;
    }

    public QueryResult execute(String gql) {
        return execute(gql, 0);
    }
    
    public QueryResult execute(String gql, long timeoutMs) {
        long start = System.currentTimeMillis();
        GqlProgram program = BlazeParser.parse(gql);
        
        QueryPlanner planner = new QueryPlanner();
        Operator lastOp = null;
        ExecutionContext ctx = new ExecutionContext(store);
        
        Timer timer = null;
        if (timeoutMs > 0) {
            timer = new Timer(true);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    ctx.cancel();
                }
            }, timeoutMs);
        }
        
        try {
        
        List<String> columns = new ArrayList<>();
        
        for (int i = 0; i < program.statements().size(); i++) {
            blazegraph.parser.ast.Ast.Statement stmt = program.statements().get(i);
            Operator op = planner.plan(stmt);
            
            // Collect columns from final return clause
            if (i == program.statements().size() - 1) {
                blazegraph.parser.ast.Ast.ReturnClause ret = null;
                if (stmt instanceof blazegraph.parser.ast.Ast.QueryStatement q) {
                    if (q.unionArms() != null && !q.unionArms().isEmpty()) {
                        ret = q.unionArms().get(0).returnClause();
                    } else {
                        ret = q.returnClause();
                    }
                }
                else if (stmt instanceof blazegraph.parser.ast.Ast.MutationStatement m) ret = m.returnClause();
                
                if (ret != null) {
                    for (ReturnItem item : ret.items()) {
                        columns.add(item.alias());
                    }
                }
            }
            
            // Execute statement completely
            op.open(ctx);
            List<List<Object>> rows = new ArrayList<>();
            while (true) {
                Optional<Map<String, Object>> r = op.next();
                if (r.isEmpty()) break;
                if (i == program.statements().size() - 1 && !columns.isEmpty()) {
                    List<Object> outRow = new ArrayList<>();
                    for (String col : columns) {
                        Object val = r.get().get(col);
                        if (val instanceof blazegraph.core.model.PropertyValue pv) val = pv.getValue();
                        outRow.add(val);
                    }
                    rows.add(outRow);
                }
            }
            op.close();
            
            if (i == program.statements().size() - 1) {
                long timeMs = System.currentTimeMillis() - start;
                QueryStats stats = new QueryStats(
                    timeMs, ctx.getNodesCreated(), ctx.getNodesDeleted(),
                    ctx.getEdgesCreated(), ctx.getEdgesDeleted(),
                    ctx.getPropertiesSet(), ctx.getLabelsAdded(),
                    rows.size()
                );
                return new QueryResult(columns, rows, stats);
            }
        }
        
        long timeMs = System.currentTimeMillis() - start;
        QueryStats stats = new QueryStats(
            timeMs, ctx.getNodesCreated(), ctx.getNodesDeleted(),
            ctx.getEdgesCreated(), ctx.getEdgesDeleted(),
            ctx.getPropertiesSet(), ctx.getLabelsAdded(),
            0
        );
        return new QueryResult(Collections.emptyList(), Collections.emptyList(), stats);
        } finally {
            if (timer != null) timer.cancel();
        }
    }
}

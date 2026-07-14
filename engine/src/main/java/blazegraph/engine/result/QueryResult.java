package blazegraph.engine.result;

import blazegraph.core.model.Edge;
import blazegraph.core.model.Node;
import blazegraph.core.model.PropertyValue;

import java.util.List;

public record QueryResult(
    List<String> columns,
    List<List<Object>> rows,
    QueryStats stats
) {
    public String renderText() {
        StringBuilder sb = new StringBuilder();
        if (columns != null && !columns.isEmpty()) {
            sb.append(String.join(" | ", columns)).append("\n");
            sb.append("-".repeat(Math.max(10, columns.size() * 10))).append("\n");
        }
        for (List<Object> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                sb.append(renderValue(row.get(i)));
                if (i < row.size() - 1) sb.append(" | ");
            }
            sb.append("\n");
        }
        sb.append(String.format("Rows: %d, Time: %d ms", stats.rowCount(), stats.executionTimeMs()));
        if (stats.nodesCreated() > 0) sb.append(String.format(", Nodes created: %d", stats.nodesCreated()));
        if (stats.edgesCreated() > 0) sb.append(String.format(", Edges created: %d", stats.edgesCreated()));
        if (stats.propertiesSet() > 0) sb.append(String.format(", Properties set: %d", stats.propertiesSet()));
        if (stats.labelsAdded() > 0) sb.append(String.format(", Labels added: %d", stats.labelsAdded()));
        if (stats.nodesDeleted() > 0) sb.append(String.format(", Nodes deleted: %d", stats.nodesDeleted()));
        if (stats.edgesDeleted() > 0) sb.append(String.format(", Edges deleted: %d", stats.edgesDeleted()));
        sb.append("\n");
        return sb.toString();
    }

    private String renderValue(Object val) {
        if (val == null) return "null";
        if (val instanceof PropertyValue pv) return renderValue(pv.getValue());
        if (val instanceof Node n) {
            StringBuilder sb = new StringBuilder("(:");
            if (!n.getLabels().isEmpty()) {
                sb.append(String.join(":", n.getLabels()));
            }
            if (!n.getProperties().isEmpty()) {
                sb.append(" {");
                boolean first = true;
                for (var e : n.getProperties().entrySet()) {
                    if (!first) sb.append(", ");
                    sb.append(e.getKey()).append(": ").append(renderValue(e.getValue()));
                    first = false;
                }
                sb.append("}");
            }
            sb.append(")");
            return sb.toString();
        }
        if (val instanceof Edge e) {
            StringBuilder sb = new StringBuilder("[:").append(e.getType());
            if (!e.getProperties().isEmpty()) {
                sb.append(" {");
                boolean first = true;
                for (var p : e.getProperties().entrySet()) {
                    if (!first) sb.append(", ");
                    sb.append(p.getKey()).append(": ").append(renderValue(p.getValue()));
                    first = false;
                }
                sb.append("}");
            }
            sb.append("]");
            return sb.toString();
        }
        if (val instanceof String s) return "'" + s + "'";
        if (val instanceof List<?> l) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < l.size(); i++) {
                sb.append(renderValue(l.get(i)));
                if (i < l.size() - 1) sb.append(", ");
            }
            sb.append("]");
            return sb.toString();
        }
        return val.toString();
    }
}

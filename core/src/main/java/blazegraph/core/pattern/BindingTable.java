package blazegraph.core.pattern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BindingTable {
    private final List<Map<String, Object>> rows = new ArrayList<>();

    public void addRow(Map<String, Object> row) {
        rows.add(new HashMap<>(row));
    }

    public List<Map<String, Object>> getRows() {
        return Collections.unmodifiableList(rows);
    }

    public BindingTable project(List<String> variables) {
        BindingTable result = new BindingTable();
        for (Map<String, Object> row : rows) {
            Map<String, Object> newRow = new HashMap<>();
            for (String var : variables) {
                if (row.containsKey(var)) {
                    newRow.put(var, row.get(var));
                }
            }
            result.addRow(newRow);
        }
        return result;
    }

    public BindingTable join(BindingTable other) {
        BindingTable result = new BindingTable();
        if (this.rows.isEmpty()) return other;
        if (other.rows.isEmpty()) return this;

        for (Map<String, Object> row1 : this.rows) {
            for (Map<String, Object> row2 : other.rows) {
                boolean match = true;
                for (String k : row1.keySet()) {
                    if (row2.containsKey(k) && !Objects.equals(row1.get(k), row2.get(k))) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    Map<String, Object> combined = new HashMap<>(row1);
                    combined.putAll(row2);
                    result.addRow(combined);
                }
            }
        }
        return result;
    }
}

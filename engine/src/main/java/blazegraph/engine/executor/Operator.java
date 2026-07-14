package blazegraph.engine.executor;

import java.util.Map;
import java.util.Optional;

public interface Operator {
    void open(ExecutionContext ctx);
    Optional<Map<String, Object>> next();
    void close();
}

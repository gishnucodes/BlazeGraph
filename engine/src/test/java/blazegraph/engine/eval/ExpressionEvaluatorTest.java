package blazegraph.engine.eval;

import blazegraph.core.model.PropertyValue;
import blazegraph.parser.ast.Ast.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class ExpressionEvaluatorTest {
    @Test
    public void testArithmetic() {
        ExpressionEvaluator eval = new ExpressionEvaluator();
        Map<String, Object> row = Map.of("x", PropertyValue.of(10L), "y", PropertyValue.of(2.5));
        
        BinaryOp add = new BinaryOp("+", new Variable("x", 1, 1), new Variable("y", 1, 1), 1, 1);
        Object res = eval.evaluate(add, row);
        assertEquals(PropertyValue.of(12.5), res);

        BinaryOp div0 = new BinaryOp("/", new Variable("x", 1, 1), new Literal(0L, "INTEGER", 1, 1), 1, 1);
        assertThrows(ExecutionException.class, () -> eval.evaluate(div0, row));
    }

    @Test
    public void testPredicates() {
        ExpressionEvaluator eval = new ExpressionEvaluator();
        Map<String, Object> row = Map.of("x", PropertyValue.of(10L), "s", PropertyValue.of("str"));

        BinaryOp eq = new BinaryOp("=", new Variable("x", 1, 1), new Literal(10.0, "DOUBLE", 1, 1), 1, 1);
        assertEquals(PropertyValue.of(true), eval.evaluate(eq, row));

        BinaryOp gt = new BinaryOp(">", new Variable("x", 1, 1), new Variable("s", 1, 1), 1, 1);
        assertNull(eval.evaluate(gt, row)); // Incompatible types -> UNKNOWN (null)
    }
}

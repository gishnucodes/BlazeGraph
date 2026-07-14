package blazegraph.parser;

import blazegraph.parser.ast.Ast.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AstShapeTest {
    @Test
    public void testAstShape() {
        GqlProgram prog = BlazeParser.parse("MATCH (n:Person {name: 'Alice'})-[r:KNOWS*1..3]->(m) RETURN n.name AS name");
        QueryStatement query = (QueryStatement) prog.statements().get(0);
        MatchClause match = query.matchClauses().get(0);
        PathPattern pattern = match.patterns().get(0);
        
        NodePatternAst n = pattern.nodes().get(0);
        assertEquals("n", n.variable());
        assertTrue(n.labels().contains("Person"));
        assertTrue(n.properties().containsKey("name"));
        
        EdgePatternAst r = pattern.edges().get(0);
        assertEquals("r", r.variable());
        assertTrue(r.types().contains("KNOWS"));
        assertEquals("OUTGOING", r.direction());
        assertEquals(1, r.minHops());
        assertEquals(3, r.maxHops());
        
        NodePatternAst m = pattern.nodes().get(1);
        assertEquals("m", m.variable());
        
        ReturnClause ret = query.returnClause();
        assertFalse(ret.distinct());
        assertEquals("name", ret.items().get(0).alias());
        assertTrue(ret.items().get(0).expr() instanceof PropertyAccess);
    }

    @Test
    public void testPrecedence() {
        GqlProgram prog = BlazeParser.parse("RETURN 1 + 2 * 3");
        QueryStatement q = (QueryStatement) prog.statements().get(0);
        BinaryOp add = (BinaryOp) q.returnClause().items().get(0).expr();
        assertEquals("+", add.op());
        assertTrue(add.left() instanceof Literal);
        assertTrue(add.right() instanceof BinaryOp);
        assertEquals("*", ((BinaryOp)add.right()).op());
    }
}

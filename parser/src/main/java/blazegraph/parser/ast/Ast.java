package blazegraph.parser.ast;

import java.util.List;
import java.util.Map;

public final class Ast {
    public sealed interface AstNode {
        int line();
        int col();
    }

    public record GqlProgram(List<Statement> statements, int line, int col) implements AstNode {}
    
    public sealed interface Statement extends AstNode permits QueryStatement, MutationStatement {}
    
    public record QueryStatement(
        List<MatchClause> matchClauses,
        Expression whereClause,
        ReturnClause returnClause,
        List<SortItem> orderBy,
        Expression skip,
        Expression limit,
        List<QueryStatement> unionArms,
        int line, int col
    ) implements Statement {}
    
    public record MutationStatement(
        List<MatchClause> matchClauses,
        Expression whereClause,
        List<MutationClause> mutationClauses,
        ReturnClause returnClause,
        int line, int col
    ) implements Statement {}
    
    public sealed interface MutationClause extends AstNode permits InsertClause, SetClause, DeleteClause {}
    public record InsertClause(List<PathPattern> patterns, int line, int col) implements MutationClause {}
    public record SetClause(List<SetItem> items, int line, int col) implements MutationClause {}
    public record DeleteClause(boolean detach, List<Expression> targets, int line, int col) implements MutationClause {}
    
    public sealed interface SetItem extends AstNode permits SetProperty, SetLabel {}
    public record SetProperty(String variable, String key, Expression expr, int line, int col) implements SetItem {}
    public record SetLabel(String variable, List<String> labels, int line, int col) implements SetItem {}
    
    public record MatchClause(boolean optional, List<PathPattern> patterns, int line, int col) implements AstNode {}
    public record PathPattern(List<NodePatternAst> nodes, List<EdgePatternAst> edges, int line, int col) implements AstNode {}
    public record NodePatternAst(String variable, List<String> labels, Map<String, Expression> properties, int line, int col) implements AstNode {}
    public record EdgePatternAst(String variable, List<String> types, String direction, Integer minHops, Integer maxHops, Map<String, Expression> properties, int line, int col) implements AstNode {}
    
    public record ReturnClause(boolean distinct, boolean star, List<ReturnItem> items, int line, int col) implements AstNode {}
    public record ReturnItem(Expression expr, String alias, int line, int col) implements AstNode {}
    public record SortItem(Expression expr, boolean ascending, int line, int col) implements AstNode {}
    
    public sealed interface Expression extends AstNode permits 
        Literal, ListLiteral, Variable, PropertyAccess, BinaryOp, UnaryOp, FunctionCall, IsNull, InList {}
    public record Literal(Object value, String type, int line, int col) implements Expression {}
    public record ListLiteral(List<Expression> elements, int line, int col) implements Expression {}
    public record Variable(String name, int line, int col) implements Expression {}
    public record PropertyAccess(String subject, String key, int line, int col) implements Expression {}
    public record BinaryOp(String op, Expression left, Expression right, int line, int col) implements Expression {}
    public record UnaryOp(String op, Expression expr, int line, int col) implements Expression {}
    public record FunctionCall(String name, boolean distinct, boolean star, List<Expression> args, int line, int col) implements Expression {}
    public record IsNull(Expression expr, boolean negated, int line, int col) implements Expression {}
    public record InList(Expression expr, ListLiteral list, int line, int col) implements Expression {}
}

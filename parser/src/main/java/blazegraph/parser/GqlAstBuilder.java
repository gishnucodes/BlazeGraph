package blazegraph.parser;

import blazegraph.parser.ast.Ast.*;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.*;

public class GqlAstBuilder extends GQLBaseVisitor<Object> {

    @Override
    public Object visitGqlProgram(GQLParser.GqlProgramContext ctx) {
        List<Statement> statements = new ArrayList<>();
        for (GQLParser.StatementContext sCtx : ctx.statement()) {
            statements.add((Statement) visit(sCtx));
        }
        return new GqlProgram(statements, ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public Object visitStatement(GQLParser.StatementContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Object visitQueryStatement(GQLParser.QueryStatementContext ctx) {
        List<QueryStatement> arms = new ArrayList<>();
        for (GQLParser.QueryConjunctorContext cCtx : ctx.queryConjunctor()) {
            arms.add((QueryStatement) visit(cCtx));
        }
        if (arms.size() == 1) {
            return arms.get(0);
        }
        return new QueryStatement(null, null, null, null, null, null, arms, ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public Object visitQueryConjunctor(GQLParser.QueryConjunctorContext ctx) {
        List<MatchClause> matches = new ArrayList<>();
        for (GQLParser.MatchClauseContext mCtx : ctx.matchClause()) {
            matches.add((MatchClause) visit(mCtx));
        }
        Expression where = ctx.whereClause() != null ? (Expression) visit(ctx.whereClause()) : null;
        ReturnClause ret = ctx.returnClause() != null ? (ReturnClause) visit(ctx.returnClause()) : null;
        List<SortItem> orderBy = ctx.orderByClause() != null ? (List<SortItem>) visit(ctx.orderByClause()) : null;
        Expression skip = ctx.skipClause() != null ? (Expression) visit(ctx.skipClause()) : null;
        Expression limit = ctx.limitClause() != null ? (Expression) visit(ctx.limitClause()) : null;
        
        return new QueryStatement(matches, where, ret, orderBy, skip, limit, null, ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public Object visitMatchClause(GQLParser.MatchClauseContext ctx) {
        boolean optional = ctx.K_OPTIONAL() != null;
        List<PathPattern> patterns = (List<PathPattern>) visit(ctx.patternList());
        return new MatchClause(optional, patterns, ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public Object visitPatternList(GQLParser.PatternListContext ctx) {
        List<PathPattern> list = new ArrayList<>();
        for (GQLParser.PatternContext pCtx : ctx.pattern()) {
            list.add((PathPattern) visit(pCtx));
        }
        return list;
    }

    @Override
    public Object visitPattern(GQLParser.PatternContext ctx) {
        List<NodePatternAst> nodes = new ArrayList<>();
        List<EdgePatternAst> edges = new ArrayList<>();
        
        nodes.add((NodePatternAst) visit(ctx.nodePattern(0)));
        for (int i = 0; i < ctx.edgePattern().size(); i++) {
            edges.add((EdgePatternAst) visit(ctx.edgePattern(i)));
            nodes.add((NodePatternAst) visit(ctx.nodePattern(i + 1)));
        }
        return new PathPattern(nodes, edges, ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public Object visitNodePattern(GQLParser.NodePatternContext ctx) {
        String var = ctx.variable() != null ? ((Variable) visit(ctx.variable())).name() : null;
        List<String> labels = ctx.labelExpression() != null ? (List<String>) visit(ctx.labelExpression()) : Collections.emptyList();
        Map<String, Expression> props = ctx.propertyMap() != null ? (Map<String, Expression>) visit(ctx.propertyMap()) : Collections.emptyMap();
        return new NodePatternAst(var, labels, props, ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public Object visitEdgePattern(GQLParser.EdgePatternContext ctx) {
        boolean hasLeft = ctx.leftArrow() != null;
        boolean hasRight = ctx.rightArrow() != null;
        String dir = hasLeft && !hasRight ? "INCOMING" :
                     hasRight && !hasLeft ? "OUTGOING" : "BOTH";
                     
        if (ctx.bracketPattern() != null) {
            Object[] bp = (Object[]) visit(ctx.bracketPattern());
            return new EdgePatternAst((String) bp[0], (List<String>) bp[1], dir, (Integer) bp[2], (Integer) bp[3], (Map<String, Expression>) bp[4], ctx.start.getLine(), ctx.start.getCharPositionInLine());
        } else {
            return new EdgePatternAst(null, Collections.emptyList(), dir, 1, 1, Collections.emptyMap(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
    }

    @Override
    public Object visitBracketPattern(GQLParser.BracketPatternContext ctx) {
        String var = ctx.variable() != null ? ((Variable) visit(ctx.variable())).name() : null;
        List<String> types = ctx.typeExpression() != null ? (List<String>) visit(ctx.typeExpression()) : Collections.emptyList();
        Integer[] quant = ctx.quantifier() != null ? (Integer[]) visit(ctx.quantifier()) : new Integer[]{1, 1};
        Map<String, Expression> props = ctx.propertyMap() != null ? (Map<String, Expression>) visit(ctx.propertyMap()) : Collections.emptyMap();
        return new Object[]{var, types, quant[0], quant[1], props};
    }

    @Override
    public Object visitLabelExpression(GQLParser.LabelExpressionContext ctx) {
        List<String> labels = new ArrayList<>();
        for (GQLParser.IdentifierContext idCtx : ctx.identifier()) {
            labels.add(parseId(idCtx));
        }
        return labels;
    }

    @Override
    public Object visitTypeExpression(GQLParser.TypeExpressionContext ctx) {
        List<String> types = new ArrayList<>();
        for (GQLParser.IdentifierContext idCtx : ctx.identifier()) {
            types.add(parseId(idCtx));
        }
        return types;
    }

    @Override
    public Object visitQuantifier(GQLParser.QuantifierContext ctx) {
        String text = ctx.getText();
        if (text.equals("*")) return new Integer[]{0, Integer.MAX_VALUE};
        if (ctx.DOTDOT() != null) {
            if (ctx.INT().size() == 2) {
                return new Integer[]{Integer.parseInt(ctx.INT(0).getText()), Integer.parseInt(ctx.INT(1).getText())};
            } else if (ctx.INT().size() == 1) {
                if (text.indexOf("..") < text.indexOf(ctx.INT(0).getText())) {
                    return new Integer[]{0, Integer.parseInt(ctx.INT(0).getText())};
                } else {
                    return new Integer[]{Integer.parseInt(ctx.INT(0).getText()), Integer.MAX_VALUE};
                }
            } else {
                return new Integer[]{0, Integer.MAX_VALUE};
            }
        } else {
            int val = Integer.parseInt(ctx.INT(0).getText());
            return new Integer[]{val, val};
        }
    }

    @Override
    public Object visitPropertyMap(GQLParser.PropertyMapContext ctx) {
        Map<String, Expression> props = new HashMap<>();
        for (GQLParser.PropertyKeyValueContext kv : ctx.propertyKeyValue()) {
            props.put(parseId(kv.identifier()), (Expression) visit(kv.expression()));
        }
        return props;
    }

    @Override
    public Object visitWhereClause(GQLParser.WhereClauseContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public Object visitReturnClause(GQLParser.ReturnClauseContext ctx) {
        boolean distinct = ctx.K_DISTINCT() != null;
        boolean star = ctx.getText().contains("*");
        List<ReturnItem> items = new ArrayList<>();
        for (GQLParser.ReturnItemContext ri : ctx.returnItem()) {
            items.add((ReturnItem) visit(ri));
        }
        return new ReturnClause(distinct, star, items, ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public Object visitReturnItem(GQLParser.ReturnItemContext ctx) {
        Expression expr = (Expression) visit(ctx.expression());
        String alias = ctx.identifier() != null ? parseId(ctx.identifier()) : null;
        return new ReturnItem(expr, alias, ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public Object visitOrderByClause(GQLParser.OrderByClauseContext ctx) {
        List<SortItem> items = new ArrayList<>();
        for (GQLParser.SortItemContext si : ctx.sortItem()) {
            items.add((SortItem) visit(si));
        }
        return items;
    }

    @Override
    public Object visitSortItem(GQLParser.SortItemContext ctx) {
        Expression expr = (Expression) visit(ctx.expression());
        boolean asc = ctx.K_DESC() == null && ctx.K_DESCENDING() == null;
        return new SortItem(expr, asc, ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public Object visitSkipClause(GQLParser.SkipClauseContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public Object visitLimitClause(GQLParser.LimitClauseContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public Object visitMutationStatement(GQLParser.MutationStatementContext ctx) {
        List<MatchClause> matches = new ArrayList<>();
        for (GQLParser.MatchClauseContext mCtx : ctx.matchClause()) {
            matches.add((MatchClause) visit(mCtx));
        }
        Expression where = ctx.whereClause() != null ? (Expression) visit(ctx.whereClause()) : null;
        List<MutationClause> muts = new ArrayList<>();
        for (GQLParser.MutationClauseContext muCtx : ctx.mutationClause()) {
            muts.add((MutationClause) visit(muCtx));
        }
        ReturnClause ret = ctx.returnClause() != null ? (ReturnClause) visit(ctx.returnClause()) : null;
        return new MutationStatement(matches, where, muts, ret, ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public Object visitInsertClause(GQLParser.InsertClauseContext ctx) {
        List<PathPattern> patterns = (List<PathPattern>) visit(ctx.patternList());
        return new InsertClause(patterns, ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public Object visitSetClause(GQLParser.SetClauseContext ctx) {
        List<SetItem> items = new ArrayList<>();
        for (GQLParser.SetItemContext si : ctx.setItem()) {
            items.add((SetItem) visit(si));
        }
        return new SetClause(items, ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public Object visitSetProperty(GQLParser.SetPropertyContext ctx) {
        String var = parseId(ctx.propertyAccess().identifier(0));
        String key = parseId(ctx.propertyAccess().identifier(1));
        Expression expr = (Expression) visit(ctx.expression());
        return new SetProperty(var, key, expr, ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public Object visitSetLabel(GQLParser.SetLabelContext ctx) {
        String var = ((Variable) visit(ctx.variable())).name();
        List<String> labels = (List<String>) visit(ctx.labelExpression());
        return new SetLabel(var, labels, ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public Object visitDeleteClause(GQLParser.DeleteClauseContext ctx) {
        boolean detach = ctx.K_DETACH() != null;
        List<Expression> targets = new ArrayList<>();
        for (GQLParser.ExpressionContext eCtx : ctx.expression()) {
            targets.add((Expression) visit(eCtx));
        }
        return new DeleteClause(detach, targets, ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    // Expressions
    @Override
    public Object visitOrExpr(GQLParser.OrExprContext ctx) {
        Expression res = (Expression) visit(ctx.andExpr(0));
        for (int i = 1; i < ctx.andExpr().size(); i++) {
            res = new BinaryOp("OR", res, (Expression) visit(ctx.andExpr(i)), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
        return res;
    }

    @Override
    public Object visitAndExpr(GQLParser.AndExprContext ctx) {
        Expression res = (Expression) visit(ctx.xorExpr(0));
        for (int i = 1; i < ctx.xorExpr().size(); i++) {
            res = new BinaryOp("AND", res, (Expression) visit(ctx.xorExpr(i)), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
        return res;
    }

    @Override
    public Object visitXorExpr(GQLParser.XorExprContext ctx) {
        Expression res = (Expression) visit(ctx.notExpr(0));
        for (int i = 1; i < ctx.notExpr().size(); i++) {
            res = new BinaryOp("XOR", res, (Expression) visit(ctx.notExpr(i)), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
        return res;
    }

    @Override
    public Object visitNotExpr(GQLParser.NotExprContext ctx) {
        if (ctx.K_NOT() != null) {
            return new UnaryOp("NOT", (Expression) visit(ctx.notExpr()), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
        return visit(ctx.comparison());
    }

    @Override
    public Object visitComparison(GQLParser.ComparisonContext ctx) {
        Expression res = (Expression) visit(ctx.addSub(0));
        for (int i = 0; i < ctx.cmpOp().size(); i++) {
            res = new BinaryOp(ctx.cmpOp(i).getText(), res, (Expression) visit(ctx.addSub(i + 1)), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
        return res;
    }

    @Override
    public Object visitAddSub(GQLParser.AddSubContext ctx) {
        Expression res = (Expression) visit(ctx.mulDivMod(0));
        for (int i = 0; i < ctx.addOp().size(); i++) {
            res = new BinaryOp(ctx.addOp(i).getText(), res, (Expression) visit(ctx.mulDivMod(i + 1)), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
        return res;
    }

    @Override
    public Object visitMulDivMod(GQLParser.MulDivModContext ctx) {
        Expression res = (Expression) visit(ctx.unary(0));
        for (int i = 0; i < ctx.mulOp().size(); i++) {
            res = new BinaryOp(ctx.mulOp(i).getText(), res, (Expression) visit(ctx.unary(i + 1)), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
        return res;
    }

    @Override
    public Object visitUnary(GQLParser.UnaryContext ctx) {
        if (ctx.unary() != null) {
            String op = ctx.getChild(0).getText();
            return new UnaryOp(op, (Expression) visit(ctx.unary()), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
        return visit(ctx.postfix());
    }

    @Override
    public Object visitPostfix(GQLParser.PostfixContext ctx) {
        Expression atom = (Expression) visit(ctx.atom());
        if (ctx.K_IS() != null) {
            atom = new IsNull(atom, ctx.K_NOT() != null, ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
        if (ctx.K_IN() != null) {
            atom = new InList(atom, (ListLiteral) visit(ctx.listLiteral()), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
        return atom;
    }

    @Override
    public Object visitAtom(GQLParser.AtomContext ctx) {
        if (ctx.expression() != null) {
            return visit(ctx.expression());
        }
        return visitChildren(ctx);
    }

    @Override
    public Object visitLiteral(GQLParser.LiteralContext ctx) {
        if (ctx.STRING_LITERAL() != null) {
            String s = ctx.STRING_LITERAL().getText();
            s = s.substring(1, s.length() - 1);
            s = s.replace("\\\"", "\"").replace("\\'", "'").replace("''", "'");
            return new Literal(s, "STRING", ctx.start.getLine(), ctx.start.getCharPositionInLine());
        } else if (ctx.FLOAT() != null) {
            try {
                return new Literal(Double.parseDouble(ctx.FLOAT().getText()), "DOUBLE", ctx.start.getLine(), ctx.start.getCharPositionInLine());
            } catch (NumberFormatException e) {
                throw new SyntaxException("Line " + ctx.start.getLine() + " - Double overflow: " + ctx.FLOAT().getText());
            }
        } else if (ctx.INT() != null) {
            try {
                return new Literal(Long.parseLong(ctx.INT().getText()), "INTEGER", ctx.start.getLine(), ctx.start.getCharPositionInLine());
            } catch (NumberFormatException e) {
                throw new SyntaxException("Line " + ctx.start.getLine() + " - Integer overflow: " + ctx.INT().getText());
            }
        } else if (ctx.K_TRUE() != null) {
            return new Literal(true, "BOOLEAN", ctx.start.getLine(), ctx.start.getCharPositionInLine());
        } else if (ctx.K_FALSE() != null) {
            return new Literal(false, "BOOLEAN", ctx.start.getLine(), ctx.start.getCharPositionInLine());
        } else if (ctx.K_NULL() != null) {
            return new Literal(null, "NULL", ctx.start.getLine(), ctx.start.getCharPositionInLine());
        } else {
            return visit(ctx.listLiteral());
        }
    }

    @Override
    public Object visitListLiteral(GQLParser.ListLiteralContext ctx) {
        List<Expression> elements = new ArrayList<>();
        for (GQLParser.ExpressionContext eCtx : ctx.expression()) {
            elements.add((Expression) visit(eCtx));
        }
        return new ListLiteral(elements, ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public Object visitVariable(GQLParser.VariableContext ctx) {
        return new Variable(parseId(ctx.identifier()), ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public Object visitPropertyAccess(GQLParser.PropertyAccessContext ctx) {
        return new PropertyAccess(parseId(ctx.identifier(0)), parseId(ctx.identifier(1)), ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public Object visitFunctionCall(GQLParser.FunctionCallContext ctx) {
        String name = parseId(ctx.identifier());
        boolean distinct = ctx.K_DISTINCT() != null;
        boolean star = ctx.getText().contains("*");
        List<Expression> args = new ArrayList<>();
        for (GQLParser.ExpressionContext eCtx : ctx.expression()) {
            args.add((Expression) visit(eCtx));
        }
        return new FunctionCall(name, distinct, star, args, ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    private String parseId(GQLParser.IdentifierContext ctx) {
        if (ctx.IDENTIFIER() != null) {
            return ctx.IDENTIFIER().getText();
        } else {
            String s = ctx.QUOTED_IDENTIFIER().getText();
            return s.substring(1, s.length() - 1);
        }
    }
}

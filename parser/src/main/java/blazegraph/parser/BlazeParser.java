package blazegraph.parser;

import blazegraph.parser.ast.Ast.GqlProgram;
import org.antlr.v4.runtime.*;

import java.util.ArrayList;
import java.util.List;

public class BlazeParser {
    public static GqlProgram parse(String gql) {
        CharStream input = CharStreams.fromString(gql);
        GQLLexer lexer = new GQLLexer(input);
        
        List<String> errors = new ArrayList<>();
        BaseErrorListener errorListener = new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                errors.add("line " + line + ":" + charPositionInLine + " — " + msg);
            }
        };
        
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);
        
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        GQLParser parser = new GQLParser(tokens);
        
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);
        
        GQLParser.GqlProgramContext tree = parser.gqlProgram();
        
        if (!errors.isEmpty()) {
            throw new SyntaxException(String.join("\n", errors));
        }
        
        GqlAstBuilder builder = new GqlAstBuilder();
        return (GqlProgram) builder.visit(tree);
    }
}

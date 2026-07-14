package blazegraph.parser;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GqlParserCorpusTest {
    static Stream<Path> validQueries() throws IOException {
        return Files.list(Paths.get("src/test/resources/corpus/valid"));
    }

    @ParameterizedTest
    @MethodSource("validQueries")
    public void testValidQueries(Path p) throws IOException {
        String gql = Files.readString(p);
        blazegraph.parser.ast.Ast.GqlProgram prog = BlazeParser.parse(gql);
        assertNotNull(prog);
    }
}

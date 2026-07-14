package blazegraph.parser;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class GqlParserErrorTest {
    static Stream<Path> invalidQueries() throws IOException {
        return Files.list(Paths.get("src/test/resources/corpus/invalid"));
    }

    @ParameterizedTest
    @MethodSource("invalidQueries")
    public void testInvalidQueries(Path p) throws IOException {
        String gql = Files.readString(p);
        assertThrows(SyntaxException.class, () -> {
            BlazeParser.parse(gql);
        });
    }
}

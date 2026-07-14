package blazegraph.server;

import blazegraph.core.storage.PropertyGraphStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServerIntegrationTest {

    private static BlazeGraphServer server;
    private static HttpClient client;

    @BeforeAll
    public static void setUp() throws Exception {
        server = new BlazeGraphServer(new PropertyGraphStore(), 0); // ephemeral port
        server.start();
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    public static void tearDown() {
        server.stop();
    }

    private int getPort() throws Exception {
        java.lang.reflect.Field f = server.getClass().getDeclaredField("server");
        f.setAccessible(true);
        com.sun.net.httpserver.HttpServer s = (com.sun.net.httpserver.HttpServer) f.get(server);
        return s.getAddress().getPort();
    }

    @Test
    public void testHealth() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + getPort() + "/health"))
                .GET()
                .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"ok\"") || response.body().contains("\"status\": \"ok\""));
    }

    @Test
    public void testQueryAndSchema() throws Exception {
        HttpRequest insertReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + getPort() + "/query"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"query\": \"INSERT (n:Person {name: 'Alice'})\"}"))
                .build();
        
        HttpResponse<String> response = client.send(insertReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"nodesCreated\":1"));
        
        HttpRequest schemaReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + getPort() + "/schema"))
                .GET()
                .build();
        
        HttpResponse<String> schemaRes = client.send(schemaReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, schemaRes.statusCode());
        assertTrue(schemaRes.body().contains("Person"));
        assertTrue(schemaRes.body().contains("name"));
    }

    @Test
    public void testErrorHandling() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + getPort() + "/query"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"query\": \"MATCH (n) RETURN n\"}")) // syntax error, missing semicolon? No, wait, syntax error: malformed GQL. Actually `MATCH (n) RETURN n` is fine.
                .build();
        // let's send a syntax error
        HttpRequest errReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + getPort() + "/query"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"query\": \"INVALID GQL\"}"))
                .build();
                
        HttpResponse<String> response = client.send(errReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("SyntaxException"));
    }
}

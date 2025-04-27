// src/test/java/com/example/AppHttpTest.java
package com.example;

import org.junit.jupiter.api.*;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AppHttpTest {

    private static com.sun.net.httpserver.HttpServer server;
    private static int port;

    @BeforeAll
    static void startServer() throws Exception {
        // bind to random free port
        server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/", exchange -> {
            String body = "Hello, Jenkins!";
            exchange.sendResponseHeaders(200, body.length());
            try (var os = exchange.getResponseBody()) {
                os.write(body.getBytes());
            }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void returnsHelloJenkins() throws Exception {
        URL url = new URL("http://localhost:" + port + "/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        try (InputStream in = conn.getInputStream()) {
            byte[] body = in.readAllBytes();
            assertEquals("Hello, Jenkins!", new String(body));
        }
    }
}

package com.example;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppTest {

    private static Thread serverThread;

    @BeforeAll
    static void startServer() throws Exception {
        // launch the HTTP server in a daemon thread
        serverThread = new Thread(() -> {
            try {
                App.main(new String[] {});
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        // give it a moment to bind to port 15000
        Thread.sleep(2000);
    }

    @AfterAll
    static void stopServer() {
        // daemon thread will exit when tests complete
    }

    @Test
    void helloEndpointReturnsFunkyHtml() throws Exception {
        URL url = new URL("http://localhost:15000/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        // verify status code
        int status = conn.getResponseCode();
        assertEquals(200, status, "Expected HTTP 200 OK");

        // verify Content-Type header
        String contentType = conn.getHeaderField("Content-Type");
        assertEquals("text/html; charset=UTF-8", contentType);

        // read entire body
        String body;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            body = reader.lines().collect(Collectors.joining("\n"));
        }

        // assert that the H1 heading is present
        assertTrue(body.contains("<h1>Hello, Jenkins!</h1>"),
                "Response should contain the main heading");
    }
}

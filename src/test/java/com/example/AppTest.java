package com.example;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void helloEndpointReturnsHelloJenkins() throws Exception {
        URL url = new URL("http://localhost:15000/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            String response = reader.readLine();
            assertEquals("Hello, Jenkins!", response);
        }
    }
}

package com.example;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AppTest {

    private static Thread serverThread;
    private static final String BASE = "http://localhost:15000";

    @BeforeAll
    static void startServer() throws Exception {
        serverThread = new Thread(() -> {
            try {
                App.main(new String[] {});
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
        Thread.sleep(2000); // give server time to bind
    }

    @AfterAll
    static void stopServer() {
        /* daemon thread exits with JVM */ }

    /* helpers */
    private static String getBody(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
            return r.lines().collect(Collectors.joining("\n"));
        }
    }

    @Test
    void indexReturns200AndHtml() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + "/").openConnection();
        assertEquals(200, c.getResponseCode());
        assertEquals("text/html; charset=UTF-8", c.getHeaderField("Content-Type"));
    }

    @Test
    void pageContainsHeadingAndButton() throws Exception {
        String body = getBody("/");
        assertTrue(body.contains("<h1>Roll the Dice!</h1>"));
        assertTrue(body.contains("<button onclick=\"roll()\">"));
    }

    @Test
    void rollEndpointReturnsNumberOneToSix() throws Exception {
        String result = getBody("/roll").trim();
        int value = Integer.parseInt(result);
        assertTrue(value >= 1 && value <= 6, "roll should be between 1 and 6");
    }
}

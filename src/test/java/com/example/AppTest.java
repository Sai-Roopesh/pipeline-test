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
    private static final String BASE_URL = "http://localhost:15000/";

    @BeforeAll
    static void startServer() throws Exception {
        serverThread = new Thread(() -> {
            try {
                App.main(new String[]{});
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
        // wait for server to bind
        Thread.sleep(2000);
    }

    @AfterAll
    static void stopServer() {
        // daemon thread will exit when JVM shuts down
    }

    private String fetchBody() throws Exception {
        URL url = new URL(BASE_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    @Test
    void respondsWith200AndHtmlContentType() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL).openConnection();
        conn.setRequestMethod("GET");

        assertEquals(200, conn.getResponseCode(), "Expected HTTP 200 OK");
        assertEquals("text/html; charset=UTF-8",
                     conn.getHeaderField("Content-Type"),
                     "Expected HTML content type with UTF-8");
    }

    @Test
    void pageContainsUpdatedHeading() throws Exception {
        String body = fetchBody();
        assertTrue(body.contains(
                "<h1>This WebPage is deployed by Jenkins CI/CD Pipelines</h1>"),
                "Page should contain the new main heading");
    }

    @Test
    void pageContainsShowDateTimeButton() throws Exception {
        String body = fetchBody();
        assertTrue(body.contains(
                "<button onclick=\"showDateTime()\">Show Date & Time (IST)</button>"),
                "Page should have a button to show date & time in IST");
    }

    @Test
    void pageContainsDateTimeContainer() throws Exception {
        String body = fetchBody();
        assertTrue(body.contains("id=\"datetime\""),
                "Page should include a <div id=\"datetime\"> for displaying date/time");
    }
}

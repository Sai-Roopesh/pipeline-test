// src/main/java/com/example/App.java
package com.example;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

/**
 * Ultra-light web server – serves “Hello, Jenkins!” at /
 * and logs startup via java.util.logging.
 */
public class App {
    private static final Logger log = Logger.getLogger(App.class.getName());

    public static void main(String[] args) throws Exception {
        // read PORT from env, default to 15000
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "15000"));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> {
            String body = "Hello, Jenkins!";
            exchange.sendResponseHeaders(200, body.length());
            try (var os = exchange.getResponseBody()) {
                os.write(body.getBytes());
            }
        });

        server.start();
        log.info("Server started at http://0.0.0.0:" + port);
    }
}

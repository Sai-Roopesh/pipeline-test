package com.example;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;

/**
 * Ultra-light demo server – serves “Hello, Jenkins!” at /
 * and keeps the JVM alive.
 */
public class App {
    public static void main(String[] args) throws Exception {

        // listen on 0.0.0.0:8080 so it is reachable inside the pod
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/", exchange -> {
            String body = "Hello, Jenkins!";
            exchange.sendResponseHeaders(200, body.length());
            try (var os = exchange.getResponseBody()) {
                os.write(body.getBytes());
            }
        });

        server.start();                      //  <-- keeps running
        System.out.println("Server started at http://0.0.0.0:8080");
    }
}

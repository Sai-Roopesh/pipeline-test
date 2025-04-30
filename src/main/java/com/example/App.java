// src/main/java/com/example/App.java
package com.example;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

public class App {
    private static final Logger log = Logger.getLogger(App.class.getName());

    public static void main(String[] args) throws Exception {
        // read PORT from env, default to 15000
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "15000"));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> {
            String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Hello, Jenkins!</title>
                  <style>
                    body {
                      margin: 0;
                      height: 100vh;
                      display: flex;
                      flex-direction: column;
                      align-items: center;
                      justify-content: center;
                      background: linear-gradient(135deg, #ff6ec4, #7873f5);
                      font-family: 'Segoe UI', sans-serif;
                      color: #fff;
                    }
                    h1 {
                      font-size: 4rem;
                      text-shadow: 2px 2px rgba(0,0,0,0.2);
                      animation: pulse 2s infinite ease-in-out;
                    }
                    @keyframes pulse {
                      0%,100% { transform: scale(1); }
                      50%     { transform: scale(1.1); }
                    }
                    button {
                      margin-top: 2rem;
                      padding: 0.75rem 1.5rem;
                      font-size: 1.25rem;
                      border: none;
                      border-radius: 0.5rem;
                      background: rgba(255,255,255,0.3);
                      color: #fff;
                      cursor: pointer;
                      transition: background 0.3s;
                    }
                    button:hover {
                      background: rgba(255,255,255,0.5);
                    }
                    #time {
                      margin-top: 1rem;
                      font-size: 1.5rem;
                      font-weight: bold;
                    }
                  </style>
                </head>
                <body>
                  <h1>Hello, Jenkins!</h1>
                  <button onclick="showTime()">What time is it?</button>
                  <div id="time"></div>
                  <script>
                    function showTime() {
                      const now = new Date();
                      document.getElementById('time').textContent =
                        now.toLocaleTimeString();
                    }
                  </script>
                </body>
                </html>
                """;

            byte[] bytes = html.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        server.start();
        log.info("Server started at http://0.0.0.0:" + port);
    }
}

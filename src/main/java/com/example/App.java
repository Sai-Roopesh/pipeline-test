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
                  <title>Jenkins Deployment</title>
                  <style>
                    /* full-viewport gradient + border */
                    body {
                      margin: 0;
                      height: 100vh;
                      display: flex;
                      flex-direction: column;
                      align-items: center;
                      justify-content: center;
                      background: linear-gradient(135deg, #ff6ec4, #7873f5);
                      border: 10px solid rgba(255, 255, 255, 0.4);
                      box-sizing: border-box;
                      font-family: 'Segoe UI', sans-serif;
                      color: #fff;
                    }
                    h1 {
                      font-size: 3rem;
                      text-align: center;
                      text-shadow: 2px 2px rgba(0,0,0,0.3);
                      margin: 0;
                    }
                    button {
                      margin-top: 2rem;
                      padding: 0.75rem 1.5rem;
                      font-size: 1.25rem;
                      border: 2px solid #fff;
                      border-radius: 0.5rem;
                      background: rgba(255,255,255,0.2);
                      color: #fff;
                      cursor: pointer;
                      transition: background 0.3s, transform 0.2s;
                    }
                    button:hover {
                      background: rgba(255,255,255,0.4);
                      transform: scale(1.05);
                    }
                    #datetime {
                      margin-top: 1.5rem;
                      font-size: 1.25rem;
                      font-weight: bold;
                    }
                  </style>
                </head>
                <body>
                  <h1>This WebPage is deployed by Jenkins CI/CD Pipelines</h1>
                  <button onclick="showDateTime()">Show Date & Time (IST)</button>
                  <div id="datetime"></div>
                  <script>
                    function showDateTime() {
                      const now = new Date();
                      // format date+time in IST
                      const opts = {
                        timeZone: 'Asia/Kolkata',
                        year: 'numeric',
                        month: 'long',
                        day: 'numeric',
                        hour: '2-digit',
                        minute: '2-digit',
                        second: '2-digit'
                      };
                      const formatted = now.toLocaleString('en-GB', opts);
                      document.getElementById('datetime').textContent =
                        `Today Date & Time in IST: ${formatted}`;
                    }
                    // show immediately on load
                    window.onload = showDateTime;
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

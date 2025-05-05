package com.example;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.logging.Logger;

public class App {
  private static final Logger log = Logger.getLogger(App.class.getName());
  private static final SecureRandom RNG = new SecureRandom();

  public static void main(String[] args) throws Exception {
    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "15000"));

    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/", exchange -> {
      String html = """
          <!DOCTYPE html>
          <html lang="en">
          <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>BoardGame – Roll the Dice</title>
            <style>
              body {
                margin: 0;
                height: 100vh;
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                font-family: 'Segoe UI', sans-serif;
                background: linear-gradient(135deg,#09c6f9,#045de9);
                color: #fff;
              }
              h1   { margin: 0 0 1.5rem 0; font-size: 2.5rem; }
              #result { font-size: 4rem; margin-top: 1rem; }
              button {
                padding: .75rem 2rem;
                font-size: 1.25rem;
                border: 2px solid #fff;
                border-radius: 8px;
                background: rgba(255,255,255,.2);
                color:#fff;
                cursor:pointer;
                transition:background .3s,transform .2s;
              }
              button:hover { background:rgba(255,255,255,.4); transform:scale(1.05); }
            </style>
          </head>
          <body>
            <h1>Roll the Dice!</h1>
            <button onclick="roll()">Roll</button>
            <div id="result">–</div>

            <script>
              function roll() {
                fetch('/roll')
                  .then(r => r.text())
                  .then(num => document.getElementById('result').textContent = num);
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

    /* /roll endpoint returns a random number 1‑6 as plain text */
    server.createContext("/roll", exchange -> {
      String roll = Integer.toString(RNG.nextInt(6) + 1);
      byte[] bytes = roll.getBytes();
      exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
      exchange.sendResponseHeaders(200, bytes.length);
      try (var os = exchange.getResponseBody()) {
        os.write(bytes);
      }
    });

    server.start();
    log.info("BoardGame server started at http://0.0.0.0:" + port);
  }
}

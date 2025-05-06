package com.example;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;
import java.util.logging.Logger;

public class App {
  private static final Logger log = Logger.getLogger(App.class.getName());
  private static final SecureRandom RNG = new SecureRandom();
  private static final String[] MOVES = { "rock", "paper", "scissors" };

  public static void main(String[] args) throws Exception {
    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "15000"));

    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

    /* ---------- HTML UI ---------- */
    server.createContext("/", exchange -> {
      String html = """
          <!DOCTYPE html>
          <html lang="en">
          <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Rock – Paper – Scissors</title>
            <style>
              body{margin:0;height:100vh;display:flex;flex-direction:column;
                   align-items:center;justify-content:center;font-family:Segoe UI,sans-serif;
                   background:linear-gradient(135deg,#ff9966,#ff5e62);color:#fff}
              h1{margin-bottom:1.5rem}
              button{margin:.25rem;padding:.75rem 1.5rem;font-size:1.1rem;border:2px solid #fff;
                     background:rgba(255,255,255,.2);border-radius:6px;color:#fff;cursor:pointer}
              button:hover{background:rgba(255,255,255,.4)}
              #result{margin-top:1.5rem;font-size:1.5rem;font-weight:bold}
            </style>
          </head>
          <body>
            <h1>Rock – Paper – Scissors</h1>
            <div>
              <button onclick="play('rock')">Rock</button>
              <button onclick="play('paper')">Paper</button>
              <button onclick="play('scissors')">Scissors</button>
            </div>
            <div id="result">Choose your move!</div>

            <script>
              function play(move){
                fetch('/play?move='+move)
                  .then(r=>r.text())
                  .then(txt=>document.getElementById('result').textContent = txt);
              }
            </script>
          </body>
          </html>
          """;
      send(exchange, 200, "text/html; charset=UTF-8", html);
    });

    /* ---------- /play endpoint ---------- */
    server.createContext("/play", exchange -> {
      String query = exchange.getRequestURI().getRawQuery(); // move=scissors
      Map<String,String> qs = java.util.Arrays.stream(query.split("&"))
          .map(s -> s.split("="))
          .filter(p -> p.length == 2)
          .collect(java.util.stream.Collectors.toMap(
              p -> URLDecoder.decode(p[0], StandardCharsets.UTF_8),
              p -> URLDecoder.decode(p[1], StandardCharsets.UTF_8)));

      String player = qs.getOrDefault("move", "rock").toLowerCase();
      String cpu    = MOVES[RNG.nextInt(3)];

      String outcome =
          player.equals(cpu)                  ? "It's a draw!" :
          player.equals("rock")     && cpu.equals("scissors") ||
          player.equals("paper")    && cpu.equals("rock")     ||
          player.equals("scissors") && cpu.equals("paper")    ? "You win!" : "CPU wins!";

      String message = "You: " + player + " — CPU: " + cpu + " → " + outcome;
      send(exchange, 200, "text/plain; charset=UTF-8", message);
    });

    server.start();
    log.info("RPS server started at http://0.0.0.0:" + port);
  }

  private static void send(com.sun.net.httpserver.HttpExchange ex, int code,
                           String ctype, String body) throws java.io.IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().add("Content-Type", ctype);
    ex.sendResponseHeaders(code, bytes.length);
    try (var os = ex.getResponseBody()) { os.write(bytes); }
  }
}

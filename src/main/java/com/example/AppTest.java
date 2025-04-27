package com.example;

import org.junit.jupiter.api.*;
import java.net.*;
import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

class AppTest {
  private Process server;

  @BeforeEach
  void startServer() throws Exception {
    // launch your App on an ephemeral port
    server = new ProcessBuilder("java",
                                "-cp", "target/app.jar",
                                "com.example.App")
              .inheritIO()
              .start();
    // give it a moment to bind
    Thread.sleep(500);
  }

  @AfterEach
  void stopServer() {
    server.destroy();
  }

  @Test
  void respondsHelloJenkins() throws Exception {
    URL u = new URL("http://localhost:15000/");
    try ( var in = new BufferedReader(new InputStreamReader(u.openStream())) ) {
      String body = in.readLine();
      assertEquals("Hello, Jenkins!", body);
    }
  }
}

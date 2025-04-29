from http.server import BaseHTTPRequestHandler, HTTPServer

hostName = "localhost"
serverPort = 9200

class MyServer(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header("Content-type", "text/html")
        self.end_headers()

        html = f"""
        <html>
        <head>
            <title>Python Basics Web Server</title>
            <style>
                body {{
                    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                    background: linear-gradient(to right, #74ebd5, #acb6e5);
                    color: #333;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    height: 100vh;
                    margin: 0;
                }}
                .container {{
                    background-color: white;
                    padding: 40px;
                    border-radius: 10px;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.1);
                    max-width: 600px;
                    text-align: center;
                }}
                h1 {{
                    color: #4a90e2;
                }}
                p {{
                    font-size: 1.1rem;
                }}
                .footer {{
                    margin-top: 20px;
                    font-size: 0.9rem;
                    color: #777;
                }}
            </style>
        </head>
        <body>
            <div class="container">
                <h1>Welcome to Python Basics Web Server</h1>
                <p><strong>Request Path:</strong> {self.path}</p>
                <p>This is an example web server built with Python's <code>http.server</code>.</p>
                <div class="footer">
                    <p>Learn more at <a href="https://pythonbasics.org" target="_blank">pythonbasics.org</a></p>
                </div>
            </div>
        </body>
        </html>
        """
        self.wfile.write(bytes(html, "utf-8"))

if __name__ == "__main__":        
    webServer = HTTPServer((hostName, serverPort), MyServer)
    print("Server started http://%s:%s" % (hostName, serverPort))

    try:
        webServer.serve_forever()
    except KeyboardInterrupt:
        pass

    webServer.server_close()
    print("Server stopped.")

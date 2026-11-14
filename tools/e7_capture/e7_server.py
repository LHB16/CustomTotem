"""Local CORS proxy and static host for Epic Seven Spine 60FPS Studio."""

import argparse
import http.server
import os
import re
import socketserver
import threading
import urllib.error
import urllib.request
import webbrowser

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
STUDIO_HTML = os.path.join(BASE_DIR, "e7_studio.html")
SAFE_PATH_RE = re.compile(r"^[A-Za-z0-9_./-]+$")

UPSTREAM_MAP = {
    "/e7/": "https://e7codex.com/",
    "/e7cdn/": "https://assets.e7codex.com/",
}

CACHE = {}
CACHE_LOCK = threading.Lock()


def guess_content_type(path: str) -> str:
    lower = path.lower()
    if lower.endswith(".json"):
        return "application/json; charset=utf-8"
    if lower.endswith(".js"):
        return "application/javascript; charset=utf-8"
    if lower.endswith(".png"):
        return "image/png"
    if lower.endswith(".webp"):
        return "image/webp"
    if lower.endswith(".atlas") or lower.endswith(".txt"):
        return "text/plain; charset=utf-8"
    return "application/octet-stream"


class E7ProxyHandler(http.server.BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass

    def send_cors_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "*")

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_cors_headers()
        self.end_headers()

    def do_GET(self):
        req_path = self.path.split("?", 1)[0]
        if req_path == "/favicon.ico":
            self.send_response(204)
            self.send_cors_headers()
            self.end_headers()
            return

        if req_path in ("/", "/index.html", "/e7_studio.html"):
            try:
                with open(STUDIO_HTML, "rb") as f:
                    data = f.read()
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(data)))
                self.send_cors_headers()
                self.end_headers()
                self.wfile.write(data)
            except OSError as exc:
                self.send_error(500, f"Cannot read e7_studio.html: {exc}")
            return

        for prefix, base_url in UPSTREAM_MAP.items():
            if req_path.startswith(prefix):
                rel = req_path[len(prefix) :]
                if not rel or ".." in rel or rel.startswith("/") or not SAFE_PATH_RE.match(rel):
                    self.send_error(400, "Invalid path")
                    return
                target_url = base_url + rel
                self.proxy_fetch(target_url, rel)
                return

        self.send_error(404, "Not found")

    def proxy_fetch(self, url: str, rel: str):
        with CACHE_LOCK:
            cached = CACHE.get(url)
        if cached is not None:
            ctype, data = cached
            self.send_response(200)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(data)))
            self.send_cors_headers()
            self.end_headers()
            self.wfile.write(data)
            return

        req = urllib.request.Request(
            url,
            headers={"User-Agent": "CustomTotemMod-E7Studio/1.0"},
        )
        try:
            with urllib.request.urlopen(req, timeout=45) as resp:
                data = resp.read()
                ctype = resp.headers.get("Content-Type") or guess_content_type(rel)
            if len(data) <= 32 * 1024 * 1024:
                with CACHE_LOCK:
                    CACHE[url] = (ctype, data)
            self.send_response(200)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(data)))
            self.send_cors_headers()
            self.end_headers()
            self.wfile.write(data)
        except urllib.error.HTTPError as exc:
            self.send_response(exc.code)
            self.send_cors_headers()
            self.end_headers()
        except Exception as exc:
            self.send_error(502, f"Upstream error: {exc}")


class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True


def main():
    parser = argparse.ArgumentParser(description="Local CORS proxy for Epic Seven Spine 60FPS Studio")
    parser.add_argument("positional_port", nargs="?", type=int, help="Optional port number")
    parser.add_argument("--port", type=int, default=0, help="Port to listen on (0 = auto)")
    parser.add_argument("--no-browser", action="store_true", help="Do not open browser automatically")
    args = parser.parse_args()
    port = args.port if args.port else (args.positional_port or 0)
    server = ThreadedHTTPServer(("127.0.0.1", port), E7ProxyHandler)
    actual_port = server.server_address[1]
    url = f"http://127.0.0.1:{actual_port}/e7_studio.html"
    print(f"Epic Seven Studio đang chạy tại: {url}", flush=True)
    print("Giữ cửa sổ này mở trong khi dùng Studio. Nhấn Ctrl+C để thoát.", flush=True)
    if not args.no_browser:
        threading.Timer(0.4, lambda: webbrowser.open(url)).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()

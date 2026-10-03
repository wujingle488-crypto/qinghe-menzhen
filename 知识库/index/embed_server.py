# -*- coding: utf-8 -*-
"""本地嵌入服务。问句走检索前缀，供 Java 在查 Chroma 之前调用。"""
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from kb_common import MODEL_NAME, embed_query, load_model

HOST = "127.0.0.1"
PORT = 8001
MODEL = load_model()


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self):
        if self.path.split("?", 1)[0] != "/health":
            self.send_error(404)
            return
        body = json.dumps({"ok": True, "model": MODEL_NAME}).encode("utf-8")
        self._send(200, body)

    def do_POST(self):
        if self.path.split("?", 1)[0] != "/embed":
            self.send_error(404)
            return
        raw = self._read_body().decode("utf-8")
        try:
            payload = json.loads(raw) if raw else {}
            text = str(payload.get("text", "")).strip()
            if not text:
                raise ValueError("empty")
            vector = embed_query(MODEL, text)
        except Exception:
            self._send(400, b'{"error":"bad request"}')
            return
        body = json.dumps({"vector": vector, "model": MODEL_NAME}).encode("utf-8")
        self._send(200, body)

    def _read_body(self):
        length = self.headers.get("Content-Length")
        if length is not None:
            return self.rfile.read(int(length))
        if "chunked" not in self.headers.get("Transfer-Encoding", "").lower():
            return b""
        chunks = []
        while True:
            line = self.rfile.readline().strip()
            size = int(line or b"0", 16)
            if size == 0:
                self.rfile.readline()
                break
            chunks.append(self.rfile.read(size))
            self.rfile.readline()
        return b"".join(chunks)

    def _send(self, status, body):
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        return


if __name__ == "__main__":
    print(f"embed {MODEL_NAME} on http://{HOST}:{PORT}", flush=True)
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()

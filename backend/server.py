#!/usr/bin/env python3
"""Tempo本地后端：Python 标准库 + SQLite，无需安装第三方依赖。"""
import json
import sqlite3
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

DB = "calendar.db"

def db():
    conn = sqlite3.connect(DB)
    conn.row_factory = sqlite3.Row
    conn.execute("""CREATE TABLE IF NOT EXISTS events (
      id INTEGER PRIMARY KEY AUTOINCREMENT, user_id TEXT NOT NULL, title TEXT NOT NULL,
      start TEXT NOT NULL, end TEXT NOT NULL, category TEXT DEFAULT '工作',
      status TEXT NOT NULL DEFAULT 'HARD', flexible_tail_minutes INTEGER DEFAULT 0,
      created_at TEXT NOT NULL)""")
    conn.execute("""CREATE TABLE IF NOT EXISTS invites (
      id INTEGER PRIMARY KEY AUTOINCREMENT, sender_id TEXT NOT NULL, receiver_id TEXT NOT NULL,
      title TEXT NOT NULL, start TEXT NOT NULL, end TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'PENDING',
      created_at TEXT NOT NULL)""")
    conn.commit()
    return conn

def now(): return datetime.now().isoformat(timespec="seconds")
def row_json(row): return dict(row) if row else None

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_): pass
    def send_json(self, value, status=200):
        body = json.dumps(value, ensure_ascii=False).encode()
        self.send_response(status); self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body))); self.send_header("Access-Control-Allow-Origin", "*"); self.end_headers(); self.wfile.write(body)
    def read_json(self):
        length = int(self.headers.get("Content-Length", 0)); return json.loads(self.rfile.read(length) or b"{}")
    def do_OPTIONS(self): self.send_json({"ok": True})
    def do_GET(self):
        parsed = urlparse(self.path); query = parse_qs(parsed.query); conn = db()
        if parsed.path == "/api/health": return self.send_json({"ok": True, "service": "tempo-calendar-local"})
        if parsed.path == "/api/events":
            user_id = query.get("user_id", ["me"])[0]
            rows = conn.execute("SELECT * FROM events WHERE user_id=? ORDER BY start", (user_id,)).fetchall()
            return self.send_json([row_json(r) for r in rows])
        if parsed.path == "/api/invites":
            user_id = query.get("user_id", ["me"])[0]
            rows = conn.execute("SELECT * FROM invites WHERE receiver_id=? OR sender_id=? ORDER BY created_at DESC", (user_id, user_id)).fetchall()
            return self.send_json([row_json(r) for r in rows])
        self.send_json({"error": "not found"}, 404)
    def do_POST(self):
        parsed = urlparse(self.path); data = self.read_json(); conn = db()
        if parsed.path == "/api/events":
            required = ["user_id", "title", "start", "end", "status"]
            if any(not data.get(k) for k in required): return self.send_json({"error": "user_id/title/start/end/status required"}, 400)
            cur = conn.execute("INSERT INTO events(user_id,title,start,end,category,status,flexible_tail_minutes,created_at) VALUES(?,?,?,?,?,?,?,?)", (data["user_id"], data["title"], data["start"], data["end"], data.get("category", "工作"), data["status"], data.get("flexible_tail_minutes", 0), now()))
            conn.commit(); return self.send_json(row_json(conn.execute("SELECT * FROM events WHERE id=?", (cur.lastrowid,)).fetchone()), 201)
        if parsed.path == "/api/matching/scan":
            start = datetime.fromisoformat(data["start"]); end = datetime.fromisoformat(data["end"]); duration = int(data.get("duration_minutes", 60)); slots=[]; cursor=start
            while cursor + __import__("datetime").timedelta(minutes=duration) <= end:
                finish = cursor + __import__("datetime").timedelta(minutes=duration)
                busy = conn.execute("SELECT 1 FROM events WHERE user_id=? AND status='HARD' AND start < ? AND end > ? LIMIT 1", (data.get("user_id", "me"), finish.isoformat(), cursor.isoformat())).fetchone()
                slots.append({"start": cursor.isoformat(timespec="minutes"), "end": finish.isoformat(timespec="minutes"), "available": not busy}); cursor += __import__("datetime").timedelta(minutes=15)
            return self.send_json({"duration_minutes": duration, "recommendations": [s for s in slots if s["available"]][:3]})
        if parsed.path == "/api/invites":
            cur = conn.execute("INSERT INTO invites(sender_id,receiver_id,title,start,end,status,created_at) VALUES(?,?,?,?,?,?,?)", (data["sender_id"], data["receiver_id"], data["title"], data["start"], data["end"], "PENDING", now()))
            conn.commit(); return self.send_json(row_json(conn.execute("SELECT * FROM invites WHERE id=?", (cur.lastrowid,)).fetchone()), 201)
        self.send_json({"error": "not found"}, 404)

if __name__ == "__main__":
    db().close(); print("Tempo Calendar backend: http://127.0.0.1:8765")
    ThreadingHTTPServer(("0.0.0.0", 8765), Handler).serve_forever()

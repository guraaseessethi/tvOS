"""Lumen bridge: lets the Lumen channel list and open apps on the TV it runs on.

Roku OS 16 refuses ECP requests a channel sends to its own TV (403 even in Permissive mode),
but accepts them from other devices on the network. This helper runs on such a device and
relays a small, fixed subset of ECP for Lumen:

  GET  /query/apps              installed apps and inputs (XML, as ECP returns it)
  GET  /query/device-info       device info (XML)
  GET  /query/icon/<id>         an app's icon as a logo on a transparent background (PNG),
                                made by lumen-img and cached; ?raw=1 returns Roku's original
  POST /launch/<id>             open an app or input
  POST /keypress/<key>          a whitelisted key
  POST /sleep/<minutes>         sleep timer (0 cancels, max 240); GET /sleep -> {"minutes": left}
  POST /zeus                    {message, context} -> {reply, actions, chips} from Gemini (see zeus.py)
  GET  /phone                   pairing page for the phone (until the Android app exists)
  POST /phone/hello             device + name (+ the current one-time code for a new phone)
  POST /phone/ping              heartbeat from an open phone page; "connected" = a ping in the last 45 s
  GET  /qr.png                  QR for /phone?code=<one-time code>; a new code after every pairing and
                                every disconnect. Remembered phones reconnect without a code.
  Lumen hears about it as /input?phone={"connected", "name", "code"}.
  GET  /memories                {"photos": [names]} from the memories folder (newest first)
  GET  /memories/<name>         that photo as a JPEG, at most 1280 px (HEIC/PNG converted), cached;
                                ?card=1 returns it cropped to a rounded 576x384 card (PNG)

It also tells Lumen where to find it: whenever Lumen is the active app, it posts
/input?bridge=<this helper's URL> to the TV once per Lumen session. And it keeps Lumen as the
home screen: backing out of an app onto Roku's home reopens Lumen; pressing Home in Lumen stays
on Roku's home (see watcher()).

Usage: python3 lumen_bridge.py [--tv 192.168.2.33] [--port 8977] [--memories "~/Pictures/Lumen Memories"]
The Android app will implement the same contract (PLAN.md, bridge section).
"""

import argparse
import hashlib
import sys
import secrets
import http.server
import json
import os
import subprocess
import tempfile
import re
import socket
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

KEYS = {
    "Home", "Back", "Select", "Up", "Down", "Left", "Right", "Info", "Play", "Rev", "Fwd",
    "InstantReplay", "VolumeUp", "VolumeDown", "VolumeMute", "PowerOff", "PowerOn", "Backspace",
}
APP_ID = re.compile(r"^(?:\d{1,10}|tvinput\.[a-z0-9]{1,20}|dev)$")
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)   # zeus.py sits next to this file (python3 -I leaves it off the path)
import zeus  # noqa: E402
LUMEN_IMG = os.path.join(HERE, "lumen-img")
CACHE = os.path.join(tempfile.gettempdir(), "lumen-bridge-icons")
PHOTO_CACHE = os.path.join(tempfile.gettempdir(), "lumen-bridge-photos")
STATE = os.path.expanduser("~/.lumen-bridge.json")


def load_state():
    try:
        with open(STATE) as f:
            return json.load(f)
    except (OSError, ValueError):
        return {}


def save_state(state):
    with open(STATE, "w") as f:
        json.dump(state, f)
PHOTO_EXT = (".jpg", ".jpeg", ".png", ".heic", ".heif")
PHOTO_NAME = re.compile(r"^[^/\\]{1,200}$")


def ecp(tv, method, path, timeout=5):
    req = urllib.request.Request(f"http://{tv}:8060{path}", method=method, data=b"" if method == "POST" else None)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.headers.get("Content-Type", "application/octet-stream"), r.read()
    except urllib.error.HTTPError as e:
        return e.code, "text/plain", b""
    except (urllib.error.URLError, socket.timeout, OSError):
        return 502, "text/plain", b""


def keyed_icon(tv, app_id):
    """Roku's icon with its background removed, cached per app and icon bytes."""
    status, ctype, raw = ecp(tv, "GET", f"/query/icon/{app_id}")
    if status != 200 or not os.path.exists(LUMEN_IMG):
        return status, ctype, raw
    os.makedirs(CACHE, exist_ok=True)
    stem = os.path.join(CACHE, f"v5-{app_id}-{len(raw)}")
    if not os.path.exists(stem + ".png"):
        with open(stem + ".src", "wb") as f:
            f.write(raw)
        # Owner choice: every logo in ink, except YouTube keeps its red play badge.
        mode = "mono-red" if app_id == "837" else "mono"
        r = subprocess.run([LUMEN_IMG, "key", stem + ".src", stem + ".png", mode], capture_output=True, timeout=20)
        if r.returncode != 0:
            return status, ctype, raw
    with open(stem + ".png", "rb") as f:
        return 200, "image/png", f.read()


def list_photos(folder):
    if not os.path.isdir(folder):
        return []
    names = [n for n in os.listdir(folder) if n.lower().endswith(PHOTO_EXT) and not n.startswith(".")]
    names.sort(key=lambda n: os.path.getmtime(os.path.join(folder, n)), reverse=True)
    return names[:200]


def photo(folder, name, card=False):
    """A TV-sized JPEG of one memories photo (made with macOS sips), or a rounded card PNG."""
    src = os.path.join(folder, name)
    if not PHOTO_NAME.match(name) or not os.path.isfile(src):
        return 404, "text/plain", b""
    os.makedirs(PHOTO_CACHE, exist_ok=True)
    out = os.path.join(PHOTO_CACHE, f"{int(os.path.getmtime(src))}-{hashlib.md5(name.encode()).hexdigest()}.jpg")
    if not os.path.exists(out):
        r = subprocess.run(["sips", "-s", "format", "jpeg", "-s", "formatOptions", "85", "-Z", "1280", src, "--out", out],
                           capture_output=True, timeout=30)
        if r.returncode != 0:
            return 500, "text/plain", b""
    if card:
        card_out = out[:-4] + "-card.png"
        if not os.path.exists(card_out):
            r = subprocess.run([LUMEN_IMG, "photocard", out, card_out, "576", "384", "68"], capture_output=True, timeout=30)
            if r.returncode != 0:
                return 500, "text/plain", b""
        with open(card_out, "rb") as f:
            return 200, "image/png", f.read()
    with open(out, "rb") as f:
        return 200, "image/jpeg", f.read()


DEVICE_ID = re.compile(r"^[A-Za-z0-9_-]{8,64}$")


class Pairing:
    """Phones that said hello, the rotating one-time code in the QR, and who's connected now."""

    def __init__(self, tv, url):
        self.tv, self.url = tv, url
        self.lock = threading.Lock()
        st = load_state()
        self.devices = st.get("devices", {})
        self.name = st.get("name", "")
        self.seen = {}
        self.connected = False
        self.rotate()

    def rotate(self):
        self.code = secrets.token_urlsafe(6)[:8]
        out = os.path.join(tempfile.gettempdir(), "lumen-bridge-qr.png")
        r = subprocess.run([LUMEN_IMG, "qr", f"{self.url}/phone?code={self.code}", out, "320"], capture_output=True, timeout=20)
        self.qr = out if r.returncode == 0 else None

    def save(self):
        state = load_state()
        state["devices"], state["name"] = self.devices, self.name
        save_state(state)

    def status(self):
        return {"connected": self.connected, "name": self.name, "code": self.code}

    def known(self, device):
        return device in self.devices

    def hello(self, device, name, code):
        with self.lock:
            if not DEVICE_ID.match(device or ""):
                return 400
            if device in self.devices:
                if name:
                    self.devices[device] = self.name = name
            elif name and code == self.code:
                self.devices[device] = self.name = name
                self.rotate()
            else:
                return 403
            self.seen[device] = time.time()
            self.save()
        self.tick(force=True)
        return 200

    def ping(self, device):
        if device not in self.devices:
            return 403
        self.seen[device] = time.time()
        self.tick()
        return 200

    def tick(self, force=False):
        now = self.devices and any(time.time() - t < 45 for d, t in self.seen.items() if d in self.devices)
        now = bool(now)
        if now != self.connected or force:
            if self.connected and not now:
                self.rotate()          # a fresh QR every time the phone goes away
            self.connected = now
            print(f"[bridge] phone {'connected' if now else 'disconnected'} ({self.name})", flush=True)
            q = urllib.parse.urlencode({"phone": json.dumps(self.status())})
            ecp(self.tv, "POST", f"/input?{q}", timeout=3)

    def monitor(self):
        while True:
            time.sleep(5)
            self.tick()


def own_ip_towards(tv):
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect((tv, 8060))
        return s.getsockname()[0]
    finally:
        s.close()


def tv_apps(tv):
    """Installed apps and inputs from the TV, for Zeus's ID checks."""
    status, _, body = ecp(tv, "GET", "/query/apps")
    apps, inputs = [], []
    if status == 200:
        for m in re.finditer(rb'<app id="([^"]+)" type="([^"]+)"[^>]*>([^<]*)</app>', body):
            item = {"id": m.group(1).decode(), "name": m.group(3).decode("utf-8", "replace")}
            if m.group(2) == b"tvin":
                inputs.append(item)
            elif m.group(2) == b"appl" and item["id"] != "dev":
                apps.append(item)
    return apps, inputs


def make_handler(tv, memories, sleeper, pairing, gemini_model):
    class Handler(http.server.BaseHTTPRequestHandler):
        def log_message(self, fmt, *args):
            print(f"[bridge] {self.address_string()} {fmt % args}", flush=True)

        def reply(self, status, ctype, body):
            self.send_response(status)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            u = urllib.parse.urlparse(self.path)
            p = u.path
            if p in ("/query/apps", "/query/device-info", "/query/active-app"):
                self.reply(*ecp(tv, "GET", p))
            elif p.startswith("/query/icon/") and APP_ID.match(p[len("/query/icon/"):]):
                if "raw=1" in u.query:
                    self.reply(*ecp(tv, "GET", p))
                else:
                    self.reply(*keyed_icon(tv, p[len("/query/icon/"):]))
            elif p == "/memories":
                body = json.dumps({"photos": list_photos(memories)}).encode()
                self.reply(200, "application/json", body)
            elif p.startswith("/memories/"):
                self.reply(*photo(memories, urllib.parse.unquote(p[len("/memories/"):]), "card=1" in u.query))
            elif p == "/phone":
                with open(os.path.join(HERE, "phone.html"), "rb") as f:
                    self.reply(200, "text/html; charset=utf-8", f.read())
            elif p == "/qr.png" and pairing.qr:
                with open(pairing.qr, "rb") as f:
                    self.reply(200, "image/png", f.read())
            elif p == "/phone/status":
                d = urllib.parse.parse_qs(u.query).get("device", [""])[0]
                body = {"known": pairing.known(d), "name": pairing.devices.get(d, "")}
                self.reply(200, "application/json", json.dumps(body).encode())
            elif p == "/sleep":
                self.reply(200, "application/json", json.dumps({"minutes": sleeper.minutes_left()}).encode())
            elif p == "/ping":
                self.reply(200, "text/plain", b"lumen-bridge")
            else:
                self.reply(404, "text/plain", b"")

        def do_POST(self):
            p = urllib.parse.urlparse(self.path).path
            if p.startswith("/launch/") and APP_ID.match(p[len("/launch/"):]):
                status, _, _ = ecp(tv, "POST", p)
            elif p.startswith("/keypress/") and p[len("/keypress/"):] in KEYS:
                status, _, _ = ecp(tv, "POST", p)
            elif p == "/zeus":
                n = int(self.headers.get("Content-Length") or 0)
                try:
                    req = json.loads(self.rfile.read(min(n, 16384)) or b"{}")
                except ValueError:
                    return self.reply(400, "text/plain", b"")
                ctx = req.get("context") or {}
                ctx["userName"] = pairing.name
                ctx["sleepMinutes"] = sleeper.minutes_left()
                apps, inputs = tv_apps(tv)
                answer = zeus.ask(str(req.get("message") or ""), ctx, apps, inputs, gemini_model)
                print(f"[zeus] {req.get('message')!r} -> {answer['reply']!r} {answer['actions']}", flush=True)
                return self.reply(200, "application/json", json.dumps(answer).encode())
            elif p in ("/phone/hello", "/phone/ping"):
                n = int(self.headers.get("Content-Length") or 0)
                form = urllib.parse.parse_qs(self.rfile.read(min(n, 2048)).decode("utf-8", "replace"))
                device = (form.get("device") or [""])[0]
                if p == "/phone/ping":
                    status = pairing.ping(device)
                else:
                    name = re.sub(r"[\x00-\x1f<>]", "", (form.get("name") or [""])[0]).strip()[:24]
                    status = pairing.hello(device, name, (form.get("code") or [""])[0])
            elif p.startswith("/sleep/") and p[len("/sleep/"):].isdigit() and int(p[len("/sleep/"):]) <= 240:
                sleeper.set(int(p[len("/sleep/"):]))
                status = 200
            else:
                status = 404
            self.reply(status, "text/plain", b"")

    return Handler


def screen_of(body):
    """'lumen', 'home' (Roku's own home screen), 'other' (an app or input), or None if unknown."""
    if b'id="dev"' in body:
        return "lumen"
    if re.search(rb"<app>\s*Roku\s*</app>", body) or b'type="home"' in body:
        return "home"
    if b"<app" in body:
        return "other"
    return None


def power_mode(tv):
    status, _, body = ecp(tv, "GET", "/query/device-info", timeout=2)
    if status != 200:
        return None
    m = re.search(rb"<power-mode>([^<]+)</power-mode>", body)
    return m.group(1).decode() if m else None


def watcher(tv, url, home_return, pairing):
    """Tells Lumen where the bridge is, and keeps Lumen as the home screen.

    Roku never reports Home presses, so this watches what's on screen instead:
      - TV turns on onto Roku's home -> open Lumen straight away (never over an input or app)
      - leaving an app (Back or Home) lands on Roku's home -> reopen Lumen
      - pressing Home while in Lumen (i.e. Home twice from an app) -> stay on Roku's home
        until another app or input is opened
    """
    announced = False
    last = None
    stay_home = False
    was_on = None
    boot_until = 0.0
    tick = 0
    while True:
        # Power state every ~1.2 s; a switch to PowerOn starts a boot window.
        if tick % 3 == 0:
            pm = power_mode(tv)
            on = pm == "PowerOn"
            if on and was_on is False:
                print("[bridge] TV turned on: opening Lumen as soon as Roku's home is up", flush=True)
                boot_until = time.time() + 25
                stay_home = False
                last = None
            if pm is not None:
                was_on = on
            elif was_on:
                was_on = False   # unreachable: treat as off, so the next answer counts as a power-on
        tick += 1

        status, _, body = ecp(tv, "GET", "/query/active-app", timeout=3)
        now = screen_of(body) if status == 200 else None
        if now == "lumen" and not announced:
            # The saved name rides along so Lumen can greet even after a reinstall.
            q = urllib.parse.urlencode({"bridge": url, "phone": json.dumps(pairing.status())})
            s, _, _ = ecp(tv, "POST", f"/input?{q}", timeout=3)
            print(f"[bridge] told Lumen about {url} -> {s}", flush=True)
            announced = s == 200
        elif now != "lumen":
            announced = False

        if time.time() < boot_until and home_return:
            # Boot window: launch the moment Roku's home is showing; stand down on anything else.
            if now == "home":
                s, _, _ = ecp(tv, "POST", "/launch/dev", timeout=3)
                print(f"[bridge] power-on: opening Lumen -> {s}", flush=True)
                if s == 200:
                    boot_until = 0
            elif now in ("lumen", "other"):
                boot_until = 0
        elif now == "other":
            stay_home = False
        elif now == "home" and last != "home" and home_return:
            if last == "lumen":
                stay_home = True
                print("[bridge] Home pressed in Lumen: staying on Roku home", flush=True)
            elif not stay_home:
                s, _, _ = ecp(tv, "POST", "/launch/dev", timeout=3)
                print(f"[bridge] back on Roku home ({last or 'start'}): reopening Lumen -> {s}", flush=True)
        if now is not None:
            last = now
        time.sleep(0.4)


class SleepTimer:
    """Turns the TV off at a set time, whatever app is open (spec 9.2 set_sleep_timer)."""

    def __init__(self, tv):
        self.tv = tv
        self.timer = None
        self.at = 0.0

    def set(self, minutes):
        if self.timer:
            self.timer.cancel()
            self.timer = None
        self.at = 0.0
        if minutes > 0:
            self.at = time.time() + minutes * 60
            self.timer = threading.Timer(minutes * 60, self.fire)
            self.timer.daemon = True
            self.timer.start()
        print(f"[bridge] sleep timer: {minutes} min", flush=True)

    def fire(self):
        ecp(self.tv, "POST", "/keypress/PowerOff")
        print("[bridge] sleep timer: TV off", flush=True)
        self.timer = None
        self.at = 0.0

    def minutes_left(self):
        return max(0, int(round((self.at - time.time()) / 60))) if self.at else 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tv", default="192.168.2.33")
    ap.add_argument("--port", type=int, default=8977)
    ap.add_argument("--memories", default=os.path.expanduser("~/Pictures/Lumen Memories"))
    ap.add_argument("--gemini-model", default="gemini-2.5-flash", help="Gemini model for Zeus")
    ap.add_argument("--no-home-return", action="store_true", help="don't reopen Lumen when Roku's home screen shows")
    a = ap.parse_args()
    os.makedirs(a.memories, exist_ok=True)
    url = f"http://{own_ip_towards(a.tv)}:{a.port}"
    pairing = Pairing(a.tv, url)
    threading.Thread(target=pairing.monitor, daemon=True).start()
    server = http.server.ThreadingHTTPServer(("0.0.0.0", a.port), make_handler(a.tv, a.memories, SleepTimer(a.tv), pairing, a.gemini_model))
    threading.Thread(target=watcher, args=(a.tv, url, not a.no_home_return, pairing), daemon=True).start()
    print(f"[bridge] serving {url} for TV {a.tv}; memories from {a.memories}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()

"""Zeus, Lumen's assistant (spec 7.3 voice, 9.2 contract), answered by Google Gemini.

The API key is read from the macOS Keychain (service "lumen-gemini"), saved once with:
    security add-generic-password -s lumen-gemini -a lumen -w
It never reaches the TV or the repo. Replies follow the spec contract:
    {"reply": str, "actions": [...], "chips": [str]}
and every action is checked against the TV's real apps and inputs before it is returned.
"""

import json
import re
import subprocess
import time
import urllib.error
import urllib.request

THEMES = ["starry", "sunflower", "irises", "blossom", "snow"]
KEYS = ["VolumeUp", "VolumeDown", "VolumeMute", "Play", "Rev", "Fwd", "Home", "Back", "InstantReplay"]
ACTION_TYPES = ["launch_app", "switch_input", "keypress", "power", "set_theme", "set_sleep_timer",
                "cancel_sleep_timer", "show_message"]

SYSTEM = """You are Zeus, the assistant built into Lumen, a calm Van Gogh-styled home screen on a Roku TV.
Voice: warm, brief and specific. One or two sentences, under 30 words, no emoji, no exclamation marks,
no lists. Replies may be read aloud. Use the person's name now and then if you know it.
Be honest about limits: you cannot see inside apps or know what is playing, and you cannot change
picture mode (say: press the star button while watching to change picture mode).
You can act on the TV with the actions in the schema. Rules:
- Only use app and input IDs from the lists you are given. Never invent IDs.
- Use "power" with state "off" only when the person explicitly asks to turn the TV off.
- Volume: "a bit" means repeat 3, "a lot" means repeat 8. Repeat is 1 to 10.
- set_theme changes Lumen's painting: starry (Starry Night), sunflower (Sunflower Field),
  irises (Irises in the Rain), blossom (Almond Blossom), snow (Snowy Evening).
- set_sleep_timer takes minutes from 5 to 240.
- If you do something, say what you did in the reply. If nothing fits, say so instead of guessing.
Always return 2 to 4 short follow-up chips (under 5 words each) the person might tap next."""

SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "reply": {"type": "STRING"},
        "actions": {
            "type": "ARRAY",
            "items": {
                "type": "OBJECT",
                "properties": {
                    "type": {"type": "STRING", "enum": ACTION_TYPES},
                    "appId": {"type": "STRING"},
                    "inputId": {"type": "STRING"},
                    "key": {"type": "STRING", "enum": KEYS},
                    "repeat": {"type": "INTEGER"},
                    "state": {"type": "STRING", "enum": ["off"]},
                    "theme": {"type": "STRING", "enum": THEMES},
                    "minutes": {"type": "INTEGER"},
                    "text": {"type": "STRING"},
                },
                "required": ["type"],
            },
        },
        "chips": {"type": "ARRAY", "items": {"type": "STRING"}},
    },
    "required": ["reply", "actions", "chips"],
}

_key = {"value": None, "at": 0.0}


def api_key():
    # Re-read at most once a minute so a newly saved key is picked up without a restart.
    if _key["value"] is None and time.time() - _key["at"] > 60:
        _key["at"] = time.time()
        r = subprocess.run(["security", "find-generic-password", "-s", "lumen-gemini", "-a", "lumen", "-w"],
                           capture_output=True, text=True)
        if r.returncode == 0 and r.stdout.strip():
            _key["value"] = r.stdout.strip()
    return _key["value"]


def clean(result, apps, inputs):
    """Keeps only well-formed actions with real IDs (spec 9.2: ignore anything else)."""
    app_ids = {a["id"] for a in apps}
    input_ids = {i["id"] for i in inputs}
    out = []
    for a in result.get("actions") or []:
        t = a.get("type")
        if t == "launch_app" and a.get("appId") in app_ids:
            out.append({"type": t, "appId": a["appId"]})
        elif t == "switch_input" and a.get("inputId") in input_ids:
            out.append({"type": t, "inputId": a["inputId"]})
        elif t == "keypress" and a.get("key") in KEYS:
            out.append({"type": t, "key": a["key"], "repeat": max(1, min(10, int(a.get("repeat") or 1)))})
        elif t == "power" and a.get("state") == "off":
            out.append({"type": t, "state": "off"})
        elif t == "set_theme" and a.get("theme") in THEMES:
            out.append({"type": t, "theme": a["theme"]})
        elif t == "set_sleep_timer" and isinstance(a.get("minutes"), int) and 5 <= a["minutes"] <= 240:
            out.append({"type": t, "minutes": a["minutes"]})
        elif t == "cancel_sleep_timer":
            out.append({"type": t})
        elif t == "show_message" and a.get("text"):
            out.append({"type": t, "text": str(a["text"])[:80]})
    chips = [str(c)[:28] for c in (result.get("chips") or []) if c][:4]
    reply = re.sub(r"\s+", " ", str(result.get("reply") or "")).strip()[:240]
    return {"reply": reply, "actions": out, "chips": chips}


def ask(message, context, apps, inputs, model):
    key = api_key()
    if not key:
        return {"reply": "I need a Gemini key on the Mac helper before I can think. It takes one command.",
                "actions": [], "chips": []}

    facts = {
        "person": context.get("userName") or "",
        "localTime": context.get("localTime") or time.strftime("%Y-%m-%d %H:%M"),
        "painting": context.get("theme") or "",
        "sleepTimerMinutesLeft": context.get("sleepMinutes") or 0,
        "apps": [{"id": a["id"], "name": a["name"]} for a in apps],
        "inputs": [{"id": i["id"], "name": i["name"]} for i in inputs],
    }
    contents = []
    for turn in (context.get("history") or [])[-6:]:
        role = "model" if turn.get("role") == "zeus" else "user"
        contents.append({"role": role, "parts": [{"text": str(turn.get("text", ""))[:500]}]})
    contents.append({"role": "user", "parts": [{"text": f"TV state: {json.dumps(facts)}\n\n{message[:500]}"}]})

    body = {
        "systemInstruction": {"parts": [{"text": SYSTEM}]},
        "contents": contents,
        "generationConfig": {"responseMimeType": "application/json", "responseSchema": SCHEMA,
                             "maxOutputTokens": 400, "temperature": 0.6},
    }
    req = urllib.request.Request(
        f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
        data=json.dumps(body).encode(), method="POST",
        headers={"Content-Type": "application/json", "x-goog-api-key": key})
    try:
        with urllib.request.urlopen(req, timeout=12) as r:
            data = json.load(r)
        text = data["candidates"][0]["content"]["parts"][0]["text"]
        return clean(json.loads(text), apps, inputs)
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", "replace")[:300]
        print(f"[zeus] Gemini HTTP {e.code}: {detail}", flush=True)
        if e.code in (401, 403):
            _key["value"] = None
            return {"reply": "Gemini didn't accept the key on the Mac helper. Check it and try again.", "actions": [], "chips": []}
        return {"reply": "That didn't go through. Try again?", "actions": [], "chips": ["Try again"]}
    except (urllib.error.URLError, TimeoutError, OSError):
        return {"reply": "I can't reach the internet right now. Apps, volume and timers still work from the remote.",
                "actions": [], "chips": []}
    except (KeyError, IndexError, ValueError) as e:
        print(f"[zeus] unexpected Gemini response: {e}", flush=True)
        return {"reply": "I lost my train of thought. Ask me again?", "actions": [], "chips": ["Try again"]}

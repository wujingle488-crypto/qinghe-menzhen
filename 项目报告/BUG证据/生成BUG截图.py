import base64
import json
import shutil
import subprocess
import tempfile
import time
from pathlib import Path

import requests
import websocket


BASE = "http://127.0.0.1:8082"
WEB = "http://127.0.0.1:5173"
HERE = Path(__file__).resolve().parent
CHROME = Path(r"C:\Program Files\Google\Chrome\Application\chrome.exe")
PORT = 9333


def post(path, body=None):
    response = requests.post(BASE + path, json=body, timeout=120)
    response.raise_for_status()
    response.encoding = "utf-8"
    return response


def put(path, body):
    response = requests.put(BASE + path, json=body, timeout=30)
    response.raise_for_status()
    response.encoding = "utf-8"
    return response


def get(path):
    response = requests.get(BASE + path, timeout=30)
    response.raise_for_status()
    response.encoding = "utf-8"
    return response


def sse_result(raw):
    found = None
    for block in raw.split("\n\n"):
        if "event:result" not in block:
            continue
        data = "".join(line[5:].strip() for line in block.splitlines() if line.startswith("data:"))
        if data:
            try:
                found = json.loads(data)
            except json.JSONDecodeError:
                (HERE / "SSE解析失败.txt").write_text(repr(block), encoding="utf-8")
                raise
    return found or {}


class Cdp:
    def __init__(self, ws_url):
        self.ws = websocket.create_connection(ws_url, timeout=20)
        self.serial = 0

    def call(self, method, params=None):
        self.serial += 1
        request_id = self.serial
        self.ws.send(json.dumps({"id": request_id, "method": method, "params": params or {}}))
        while True:
            message = json.loads(self.ws.recv())
            if message.get("id") == request_id:
                if "error" in message:
                    raise RuntimeError(message["error"])
                return message.get("result", {})

    def evaluate(self, expression):
        return self.call(
            "Runtime.evaluate",
            {"expression": expression, "awaitPromise": True, "returnByValue": True},
        ).get("result", {}).get("value")

    def screenshot(self, path):
        encoded = self.call(
            "Page.captureScreenshot",
            {"format": "png", "captureBeyondViewport": False},
        )["data"]
        Path(path).write_bytes(base64.b64decode(encoded))

    def close(self):
        self.ws.close()


def wait_for_debugger():
    for _ in range(60):
        try:
            return requests.get(f"http://127.0.0.1:{PORT}/json/version", timeout=1).json()
        except Exception:
            time.sleep(0.25)
    raise RuntimeError("Chrome 调试端口未就绪")


def wait_js(cdp, expression, timeout=15):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if cdp.evaluate(expression):
            return
        time.sleep(0.25)
    raise RuntimeError(f"等待页面元素超时：{expression}")


def prepare_red_flag_evidence():
    text = "我不是没有胸口疼，只是没有出冷汗"
    attempts = []
    screenshot_session = None
    for index in range(3):
        session_id = post("/api/consult/sessions", {}).json()["id"]
        response = post(
            f"/api/consult/sessions/{session_id}/messages",
            {"text": text, "webSearch": False},
        )
        result = sse_result(response.text)
        card = result.get("card") or {}
        reply = result.get("reply") or ""
        leaked = card.get("redFlag") is not True and "120" not in reply and "马上就医" not in reply
        attempts.append(
            {
                "attempt": index + 1,
                "sessionId": session_id,
                "redFlag": card.get("redFlag"),
                "reply": reply,
                "reproduced": leaked,
            }
        )
        if screenshot_session is None and leaked:
            screenshot_session = session_id
    return text, attempts, screenshot_session


def prepare_invalid_profile():
    existing = [
        item for item in get("/api/profile/cards").json()
        if item.get("name") == "BUG-02复现卡"
    ]
    card_id = existing[0]["id"] if existing else post("/api/profile/cards", {"name": "BUG-02复现卡"}).json()["id"]
    profile = {
        "gender": "女",
        "age": "-5",
        "height_cm": "165",
        "weight_kg": "-50",
        "allergies": "无",
        "chronic": "无",
        "medications": "无",
        "pregnancy": "无",
    }
    attempts = []
    for index in range(3):
        saved = put(
            "/api/profile",
            {"id": card_id, "name": "BUG-02复现卡", "profile": profile},
        ).json()
        attempts.append(
            {
                "attempt": index + 1,
                "age": saved.get("profile", {}).get("age"),
                "weight": saved.get("profile", {}).get("weight_kg"),
                "bmi": saved.get("bmi"),
                "completeness": saved.get("completeness"),
                "reproduced": (saved.get("bmi") or 0) < 0,
            }
        )
    return card_id, attempts


def verify_category_bug():
    attempts = []
    for index in range(3):
        catalog = get("/api/kb").json()["catalog"]
        entry = next(item for item in catalog["entries"] if item["title"].startswith("普通感冒"))
        attempts.append(
            {
                "attempt": index + 1,
                "title": entry["title"],
                "category": entry["categoryLabel"],
                "keywords": entry.get("keywords"),
                "reproduced": entry["category"] == "drug",
            }
        )
    return attempts


def prepare_first_message_profile_bug():
    cards = [
        item for item in get("/api/profile/cards").json()
        if item.get("id") != 10 and item.get("completeness", 0) > 0
    ]
    if not cards:
        raise RuntimeError("没有可用于关联测试的正常就诊卡")
    card = cards[0]
    attempts = []
    for index in range(3):
        session_id = post("/api/consult/sessions", {}).json()["id"]
        response = post(
            f"/api/consult/sessions/{session_id}/messages",
            {"text": "我叫什么名字？", "webSearch": False, "useProfile": True},
        )
        result = sse_result(response.text)
        context = get(f"/api/consult/sessions/{session_id}/context").json()
        reply = result.get("reply") or ""
        attempts.append(
            {
                "attempt": index + 1,
                "sessionId": session_id,
                "selectedProfileId": card["id"],
                "profileLinked": context.get("profileLinked"),
                "reply": reply,
                "reproduced": context.get("profileLinked") is not True and card.get("name", "") not in reply,
            }
        )
    return card, attempts


def main():
    HERE.mkdir(parents=True, exist_ok=True)
    if not CHROME.exists():
        raise RuntimeError("未找到 Chrome")
    health = requests.get(BASE + "/api/health", timeout=5)
    health.raise_for_status()
    requests.get(WEB, timeout=5).raise_for_status()

    category_attempts = verify_category_bug()
    linked_card, link_attempts = prepare_first_message_profile_bug()

    user_data = tempfile.mkdtemp(prefix="qinghe-bug-evidence-")
    process = subprocess.Popen(
        [
            str(CHROME),
            "--headless=new",
            f"--remote-debugging-port={PORT}",
            "--remote-allow-origins=*",
            f"--user-data-dir={user_data}",
            "--window-size=1440,900",
            "--hide-scrollbars",
            "--disable-gpu",
            "--no-first-run",
            "--no-default-browser-check",
            WEB,
        ],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    cdp = None
    try:
        wait_for_debugger()
        tabs = requests.get(f"http://127.0.0.1:{PORT}/json", timeout=5).json()
        page = next(item for item in tabs if item.get("type") == "page")
        cdp = Cdp(page["webSocketDebuggerUrl"])
        cdp.call("Page.enable")
        cdp.call("Runtime.enable")
        wait_js(cdp, "document.readyState === 'complete'")
        wait_js(cdp, "document.querySelector('.qh-shell') !== null")
        cdp.call(
            "Emulation.setDeviceMetricsOverride",
            {"width": 1440, "height": 900, "deviceScaleFactor": 1, "mobile": False},
        )

        # Bug-01：打开文章后，左侧搜索框改变关键词，详情却不退出。
        cdp.evaluate(
            "[...document.querySelectorAll('nav button')].find(b=>b.innerText.includes('知识库'))?.click(); true"
        )
        wait_js(cdp, "document.body.innerText.includes('腹泻先补液')", timeout=20)
        clicked = cdp.evaluate(
            "const b=[...document.querySelectorAll('button')].find(x=>x.innerText.includes('腹泻先补液'));"
            "if(b){b.click(); true} else {document.body.innerText.includes('腹泻')}"
        )
        if clicked is not True:
            raise RuntimeError(f"没有点开文章：{clicked}")
        wait_js(cdp, "document.querySelector('.qh-kb-read h2') !== null")
        search_attempts = []
        for index in range(3):
            cdp.evaluate(
                "const e=document.querySelector('.qh-kb-search input');"
                "const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;"
                "s.call(e,'流感'); e.dispatchEvent(new Event('input',{bubbles:true})); true"
            )
            time.sleep(0.4)
            reproduced = bool(cdp.evaluate(
                "document.querySelector('.qh-kb-read h2')?.innerText.includes('腹泻')"
                " && document.querySelector('.qh-kb-search input')?.value==='流感'"
            ))
            search_attempts.append({"attempt": index + 1, "query": "流感", "reproduced": reproduced})
            if index == 0:
                cdp.screenshot(HERE / "Bug-01-阅读文章时搜索不生效.png")
            cdp.evaluate(
                "const e=document.querySelector('.qh-kb-search input');"
                "const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;"
                "s.call(e,''); e.dispatchEvent(new Event('input',{bubbles:true})); true"
            )

        # Bug-02：重新进入知识库，正常点开“用药指南”。
        cdp.evaluate("location.reload(); true")
        wait_js(cdp, "document.querySelector('.qh-shell') !== null")
        cdp.evaluate(
            "[...document.querySelectorAll('nav button')].find(b=>b.innerText.includes('知识库'))?.click(); true"
        )
        wait_js(cdp, "document.body.innerText.includes('普通感冒')", timeout=20)
        cdp.evaluate(
            "[...document.querySelectorAll('.qh-kb-cats button')].find(b=>b.innerText.includes('用药指南'))?.click(); true"
        )
        wait_js(cdp, "[...document.querySelectorAll('button')].some(b=>b.innerText.includes('普通感冒'))")
        cdp.evaluate(
            "[...document.querySelectorAll('button')].find(b=>b.innerText.includes('普通感冒'))?.scrollIntoView({block:'center'}); true"
        )
        time.sleep(0.5)
        cdp.screenshot(HERE / "Bug-02-感冒文章被分到用药指南.png")

        # Bug-03：新会话发送首条消息前选择就诊卡，界面显示已关联但后端未关联。
        cdp.evaluate(
            "[...document.querySelectorAll('nav button')].find(b=>b.innerText.includes('问诊'))?.click(); true"
        )
        wait_js(cdp, "document.querySelector('.qh-log') !== null")
        cdp.evaluate("localStorage.removeItem('qinghe-consult-session'); location.reload(); true")
        wait_js(cdp, "document.querySelector('.qh-addcard') !== null")
        cdp.evaluate("document.querySelector('.qh-addcard')?.click(); true")
        wait_js(cdp, "document.querySelector('.qh-cardpick') !== null")
        profile_id = linked_card["id"]
        cdp.evaluate(
            f"[...document.querySelectorAll('.qh-cardpick')].find(x=>x.innerText.includes('{linked_card['name']}'))"
            "?.querySelector('.qh-pick')?.click(); true"
        )
        wait_js(cdp, "document.querySelector('.qh-linked') !== null")
        cdp.evaluate("document.querySelector('#symptom')?.focus(); true")
        cdp.call("Input.insertText", {"text": "我叫什么名字？"})
        wait_js(cdp, "document.querySelector('.qh-send:not(:disabled)') !== null")
        cdp.evaluate("document.querySelector('.qh-send')?.click(); true")
        wait_js(
            cdp,
            "document.querySelectorAll('.qh-row').length >= 2 && document.querySelector('.qh-wait') === null",
            timeout=30,
        )
        cdp.evaluate(
            "const e=document.querySelector('.qh-log'); if(e){e.scrollTop=e.scrollHeight;} true"
        )
        time.sleep(0.6)
        cdp.screenshot(HERE / "Bug-03-新会话首条消息未应用就诊卡.png")

        record = {
            "generatedAt": time.strftime("%Y-%m-%d %H:%M:%S"),
            "bug01": {"attempts": search_attempts},
            "bug02": {"attempts": category_attempts},
            "bug03": {
                "profileId": profile_id,
                "profileName": linked_card["name"],
                "attempts": link_attempts,
            },
        }
        (HERE / "复现结果.json").write_text(
            json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        print(json.dumps(record, ensure_ascii=False, indent=2))
    finally:
        if cdp is not None:
            cdp.close()
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
        shutil.rmtree(user_data, ignore_errors=True)


if __name__ == "__main__":
    main()

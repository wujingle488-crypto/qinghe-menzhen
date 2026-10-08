import { useEffect, useRef, useState, type ChangeEvent } from "react";
import { createPortal } from "react-dom";
import { MarkdownBody, SourceCites, cardToMarkdown } from "../components/MarkdownBody";
import { HistorySideList, type HistoryItem } from "../components/HistorySideList";
import { Ico } from "../components/Ico";
import { api, consultSessionKey } from "../api";

export type ConsultCard = {
  redFlag?: boolean;
  degraded?: boolean;
  modeNote?: string;
  disclaimer?: string;
  plan?: string;
  diagnoses?: { name: string; summary: string }[];
  drugs?: { name: string; usage: string; caution: string }[];
  evidences?: { title: string; snippet: string; sourceUrl?: string; channel?: string }[];
  memory?: string;
  chronicCaution?: string;
};

type Line = {
  role: "user" | "assistant" | "progress";
  text: string;
  card?: ConsultCard | null;
  images?: string[];
  webHits?: number;
  webSearch?: boolean;
};

function PersonMark() {
  return (
    <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" aria-hidden="true">
      <circle cx="12" cy="8" r="3.1" stroke="currentColor" strokeWidth="1.7" />
      <path
        d="M5.4 19.2c.7-3.1 3-4.7 6.6-4.7s5.9 1.6 6.6 4.7"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
      />
    </svg>
  );
}

/** 青禾助手：保留机器人外形，加听诊器耳件与胸前十字，仍可与人形区分 */
function ClinicBotMark() {
  return (
    <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" aria-hidden="true">
      <path d="M12 2.4v2.2" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
      <path d="M12 2.9c1.6-.35 2.5.85 1.35 1.8" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
      <rect x="5" y="5.8" width="14" height="10.4" rx="3.6" stroke="currentColor" strokeWidth="1.6" />
      <circle cx="9.1" cy="10.4" r="1" fill="currentColor" />
      <circle cx="14.9" cy="10.4" r="1" fill="currentColor" />
      <path d="M11.2 13.2h1.6M12 12.4v1.6" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
      <path
        d="M7.2 16.8c0 1.5 1.1 2.7 2.6 2.9"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
      />
      <circle cx="10.2" cy="20.1" r="1.15" stroke="currentColor" strokeWidth="1.6" />
      <path d="M16.8 16.6h1.4" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  );
}

function StethoscopeMark({ className = "h-7 w-7" }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="none" aria-hidden="true">
      <path
        d="M7 3.5v6.2a5 5 0 0 0 10 0V3.5"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
      />
      <path d="M7 3.5H5.2M17 3.5h1.8" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
      <path d="M12 14.5v2.2a3.2 3.2 0 0 0 3.2 3.2h.3" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
      <circle cx="17.8" cy="19.9" r="1.85" stroke="currentColor" strokeWidth="1.7" />
    </svg>
  );
}

function ThroatMark({ className = "h-5 w-5" }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="none" aria-hidden="true">
      <path
        d="M8.2 4.5c1.2 1.4 2.4 2.1 3.8 2.1s2.6-.7 3.8-2.1"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
      />
      <path
        d="M7 8.2c1.6 2.2 3.2 3.3 5 3.3s3.4-1.1 5-3.3"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
      />
      <path d="M10.2 14.2h3.6" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
      <path
        d="M9.4 17.8c.8 1.4 1.7 2.1 2.6 2.1s1.8-.7 2.6-2.1"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
      />
      <circle cx="12" cy="12.2" r="1.1" fill="currentColor" />
    </svg>
  );
}

function RashMark({ className = "h-5 w-5" }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="none" aria-hidden="true">
      <ellipse cx="12" cy="13.2" rx="6.2" ry="5.4" stroke="currentColor" strokeWidth="1.7" />
      <circle cx="9.6" cy="12.2" r="1.05" stroke="currentColor" strokeWidth="1.6" />
      <circle cx="13.8" cy="11.6" r="1.2" stroke="currentColor" strokeWidth="1.6" />
      <circle cx="12.2" cy="15.2" r="0.95" stroke="currentColor" strokeWidth="1.6" />
      <path d="M10.5 5.2l1.5 2.4 1.5-2.4" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function ChestPainMark({ className = "h-5 w-5" }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="none" aria-hidden="true">
      <path
        d="M12 20.2s-6.4-4.1-6.4-9.1A3.6 3.6 0 0 1 12 8.2a3.6 3.6 0 0 1 6.4 2.9c0 5-6.4 9.1-6.4 9.1z"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinejoin="round"
      />
      <path
        d="M8.6 12.4h2.1l1.2-2.2 1.6 4.2 1.1-2h2.2"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function Speaker({ who }: { who: "user" | "assistant" }) {
  const mine = who === "user";
  return (
    <span
      className={`grid h-10 w-10 shrink-0 place-items-center rounded-full ring-2 ring-white ${
        mine ? "bg-ink text-cyan-50" : "bg-leaf text-white"
      }`}
      title={mine ? "你" : "青禾"}
    >
      {mine ? <PersonMark /> : <ClinicBotMark />}
    </span>
  );
}

const GREETING =
  "你好呀！我是青禾，你的门诊助手。\n\n"
  + "有什么我可以帮你的吗？身体不舒服、用药能不能吃、想了解常见病注意事项，都可以跟我说。\n\n"
  + "也可以直接告诉我，例如：\n"
  + "- 最近哪里不舒服，想了解一下怎么回事\n"
  + "- 想查查某个药能不能吃、怎么吃\n"
  + "- 喉咙痛、低烧、起风团这类症状持续了多久\n\n"
  + "你尽管说。胸痛、大出血、叫不醒请直接去急诊。";

type CardBrief = {
  id: number;
  name: string;
  title: string;
  included: string[];
  available: boolean;
};

type CardDigest = CardBrief & {
  items: { label: string; value: string }[];
};

type Drawer = "pick" | "summary" | null;

type PendingImage = { id: string; url: string; name: string };

type RecorderHold = {
  ctx: AudioContext;
  source: MediaStreamAudioSourceNode;
  processor: ScriptProcessorNode;
  mute: GainNode;
  stream: MediaStream;
  chunks: Float32Array[];
  timer: number;
};

function humanizeError(raw: string): string {
  const text = raw.trim();
  if (!text) return "发送失败";
  try {
    const data = JSON.parse(text) as { error?: string };
    if (typeof data.error === "string" && data.error.trim()) return data.error.trim();
  } catch {
    // 普通文本错误
  }
  return text;
}

export function ConsultPage({
  onOpenProfile,
  consultIntent
}: {
  onOpenProfile?: () => void;
  consultIntent?: { id: number | null; draft?: string; nonce: number } | null;
}) {
  const [sessionId, setSessionId] = useState<number | null>(null);
  const [lines, setLines] = useState<Line[]>([{ role: "assistant", text: GREETING }]);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [webSearch, setWebSearch] = useState(true);
  const [resumable, setResumable] = useState(false);
  const [resumeHint, setResumeHint] = useState("");
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [images, setImages] = useState<PendingImage[]>([]);
  const [recording, setRecording] = useState(false);
  const [sideOpen, setSideOpen] = useState(false);
  const [profileLinked, setProfileLinked] = useState(false);
  const [profileId, setProfileId] = useState<number | null>(null);
  const [profileTitle, setProfileTitle] = useState("");
  const [cardIssue, setCardIssue] = useState(false);
  const [cards, setCards] = useState<CardBrief[]>([]);
  const [cardName, setCardName] = useState("");
  const [digest, setDigest] = useState<CardDigest | null>(null);
  const [drawer, setDrawer] = useState<Drawer>(null);
  const logRef = useRef<HTMLDivElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const recorderRef = useRef<RecorderHold | null>(null);

  useEffect(() => {
    logRef.current?.scrollTo({ top: logRef.current.scrollHeight, behavior: "smooth" });
  }, [lines, busy]);

  useEffect(() => {
    if (!drawer) return;
    const close = (event: KeyboardEvent) => {
      if (event.key === "Escape") setDrawer(null);
    };
    window.addEventListener("keydown", close);
    return () => window.removeEventListener("keydown", close);
  }, [drawer]);

  useEffect(() => {
    void loadHistory();
    if (consultIntent) return;
    const saved = localStorage.getItem(consultSessionKey());
    if (!saved) return;
    const id = Number(saved);
    if (!Number.isFinite(id)) return;
    void openHistory(id);
  }, []);

  async function loadHistory() {
    try {
      const response = await api("/cs-api/api/consult/sessions");
      if (!response.ok) return;
      setHistory((await response.json()) as HistoryItem[]);
    } catch {
      // 列表失败时仍可新开对话
    }
  }

  function startNew() {
    localStorage.removeItem(consultSessionKey());
    setSessionId(null);
    setResumable(false);
    setResumeHint("");
    setText("");
    setImages([]);
    setProfileLinked(false);
    setProfileId(null);
    setProfileTitle("");
    setCardIssue(false);
    setDrawer(null);
    setLines([{ role: "assistant", text: GREETING }]);
  }

  async function loadContext(id: number) {
    setCardIssue(false);
    try {
      const response = await api(`/cs-api/api/consult/sessions/${id}/context`);
      if (!response.ok) {
        setProfileLinked(false);
        return;
      }
      const data = (await response.json()) as { profileLinked?: boolean; profileId?: number; title?: string };
      const linked = Boolean(data.profileLinked && data.profileId);
      setProfileLinked(linked);
      setProfileId(linked ? data.profileId ?? null : null);
      setProfileTitle(linked ? data.title || "" : "");
      if (linked && data.profileId) void loadDigest(data.profileId);
    } catch {
      setProfileLinked(false);
      setProfileId(null);
    }
  }

  async function loadCards() {
    try {
      const response = await api("/cs-api/api/profile/cards");
      if (!response.ok) throw new Error("cards");
      setCards((await response.json()) as CardBrief[]);
    } catch {
      setCards([]);
    }
  }

  async function loadDigest(id: number): Promise<CardDigest | null> {
    try {
      const response = await api(`/cs-api/api/profile/cards/${id}/digest`);
      if (!response.ok) throw new Error("digest");
      const data = (await response.json()) as CardDigest;
      setDigest(data);
      return data;
    } catch {
      setDigest(null);
      return null;
    }
  }

  async function saveLink(nextId: number | null) {
    if (sessionId == null) return true;
    try {
      const response = await api(`/cs-api/api/consult/sessions/${sessionId}/context`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(nextId == null ? { profileLinked: false } : { profileId: nextId })
      });
      return response.ok;
    } catch {
      return false;
    }
  }

  async function openPicker() {
    setDrawer("pick");
    await loadCards();
  }

  async function chooseCard(card: CardBrief) {
    setDrawer(null);
    if (!(await saveLink(card.id))) {
      setProfileLinked(false);
      setProfileId(null);
      setCardIssue(true);
      return;
    }
    setCardIssue(false);
    setProfileLinked(true);
    setProfileId(card.id);
    setProfileTitle(card.title);
    void loadDigest(card.id);
  }

  async function createCard() {
    const name = cardName.trim();
    if (!name) return;
    try {
      const response = await api("/cs-api/api/profile/cards", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name })
      });
      if (!response.ok) throw new Error(humanizeError(await response.text()));
      setCardName("");
      await loadCards();
    } catch (err) {
      setLines((prev) => [...prev, { role: "assistant", text: err instanceof Error ? err.message : "新建就诊卡失败" }]);
    }
  }

  async function removeCard() {
    setDrawer(null);
    setProfileLinked(false);
    setProfileId(null);
    setProfileTitle("");
    setCardIssue(false);
    await saveLink(null);
  }

  useEffect(() => {
    if (!consultIntent) return;
    if (consultIntent.id == null) {
      startNew();
      if (consultIntent.draft) setText(consultIntent.draft);
      return;
    }
    void openHistory(consultIntent.id);
  }, [consultIntent]);

  async function openHistory(id: number) {
    setSessionId(id);
    localStorage.setItem(consultSessionKey(), String(id));
    setDrawer(null);
    void loadContext(id);
    try {
      const response = await api(`/cs-api/api/consult/sessions/${id}/messages`);
      if (!response.ok) {
        if ((await response.text()).includes("会话不存在")) startNew();
        return;
      }
      const rows = (await response.json()) as { role: string; content: string }[];
      const next: Line[] = rows
        .filter((row) => row.role === "user" || row.role === "assistant")
        .map((row) => ({ role: row.role as "user" | "assistant", text: row.content || "" }));
      setLines(next.length > 0 ? next : [{ role: "assistant", text: GREETING }]);
      await refreshTask(id);
    } catch {
      setLines([{ role: "assistant", text: GREETING }]);
    }
  }

  async function removeHistory(id: number) {
    if (!window.confirm("删除这次问诊？聊天记录、这次的进度和只从这次写出的长期记忆会一起清掉。就诊卡会保留。")) {
      return;
    }
    const response = await api(`/cs-api/api/consult/sessions/${id}`, { method: "DELETE" });
    if (!response.ok) return;
    if (sessionId === id || localStorage.getItem(consultSessionKey()) === String(id)) {
      startNew();
    }
    await loadHistory();
  }

  async function removeManyHistory(ids: number[]) {
    if (ids.length === 0) return;
    if (!window.confirm(`删除选中的 ${ids.length} 次问诊？聊天记录与进度会一起清掉，就诊卡会保留。`)) {
      return;
    }
    const response = await api("/cs-api/api/consult/sessions", {
      method: "DELETE",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ids })
    });
    if (!response.ok) return;
    if (sessionId != null && ids.includes(sessionId)) {
      startNew();
    } else if (ids.some((id) => localStorage.getItem(consultSessionKey()) === String(id))) {
      startNew();
    }
    await loadHistory();
  }

  async function renameHistory(id: number, title: string) {
    const response = await api(`/cs-api/api/consult/sessions/${id}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ title })
    });
    if (!response.ok) return;
    await loadHistory();
  }

  function forgetSession() {
    localStorage.removeItem(consultSessionKey());
    setSessionId(null);
    setResumable(false);
    setResumeHint("");
  }

  async function refreshTask(id: number) {
    try {
      const response = await api(`/cs-api/api/consult/sessions/${id}/task`);
      if (!response.ok) {
        const raw = await response.text();
        if (raw.includes("会话不存在") && localStorage.getItem(consultSessionKey()) === String(id)) {
          forgetSession();
        }
        return;
      }
      const data = (await response.json()) as {
        resumable?: boolean;
        status?: string;
        coreIntent?: string;
        resumeNode?: string;
      };
      setResumable(Boolean(data.resumable));
      if (data.resumable) {
        setResumeHint(
          `上次问诊在「${data.resumeNode || "中间节点"}」中断${data.coreIntent ? `（${data.coreIntent}）` : ""}，可继续。`
        );
      } else {
        setResumeHint("");
      }
    } catch {
      // 任务状态读失败时不挡问诊
    }
  }

  async function openSession(): Promise<number> {
    const response = await api("/cs-api/api/consult/sessions", { method: "POST" });
    if (!response.ok) throw new Error(humanizeError(await response.text()));
    const data = (await response.json()) as { id: number };
    setSessionId(data.id);
    localStorage.setItem(consultSessionKey(), String(data.id));
    return data.id;
  }

  async function ensureSession(): Promise<number> {
    if (sessionId != null) return sessionId;
    return openSession();
  }

  async function postMessage(id: number, content: string, imageIds: string[]) {
    return api(`/cs-api/api/consult/sessions/${id}/messages`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ text: content, webSearch, imageIds, useProfile: profileLinked })
    });
  }

  async function readSse(response: Response) {
    if (!response.ok || !response.body) throw new Error(await response.text());
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    let eventName = "message";
    while (true) {
      const chunk = await reader.read();
      if (chunk.done) break;
      buffer += decoder.decode(chunk.value, { stream: true });
      const parts = buffer.split("\n");
      buffer = parts.pop() ?? "";
      for (const line of parts) {
        if (line.startsWith("event:")) eventName = line.slice(6).trim();
        if (!line.startsWith("data:")) continue;
        const data = line.slice(5).trim();
        if (eventName === "progress" || eventName === "retrieving" || eventName === "generating") {
          setLines((prev) => {
            const withoutOld = prev.filter((line) => line.role !== "progress");
            return [...withoutOld, { role: "progress", text: data }];
          });
        }
        if (eventName === "error") {
          throw new Error(data || "这次问诊没有完成，请再发一次。");
        }
        if (eventName === "result") {
          const body = JSON.parse(data) as {
            reply?: string;
            card?: ConsultCard | null;
            webSearch?: boolean;
            webHits?: number;
          };
          setLines((prev) => [
            ...prev.filter((line) => line.role !== "progress"),
            {
              role: "assistant",
              text: body.reply || "（无回复）",
              card: body.card,
              webSearch: Boolean(body.webSearch),
              webHits: Number(body.webHits ?? 0)
            }
          ]);
        }
      }
    }
  }

  async function send(raw?: string) {
    const content = (raw ?? text).trim();
    const imageIds = raw ? [] : images.map((item) => item.id);
    const previews = raw ? [] : images.map((item) => item.url);
    if ((!content && imageIds.length === 0) || busy || recording) return;
    setBusy(true);
    setText("");
    setImages([]);
    setLines((prev) => {
      const base = prev.length === 1 && prev[0].text === GREETING && prev[0].role === "assistant" ? [] : prev;
      return [...base, { role: "user", text: content || "（附了一张图片）", images: previews }];
    });
    try {
      let id = await ensureSession();
      let response = await postMessage(id, content, imageIds);
      if (!response.ok) {
        const rawError = await response.text();
        if (!rawError.includes("会话不存在")) throw new Error(humanizeError(rawError));
        forgetSession();
        id = await openSession();
        response = await postMessage(id, content, imageIds);
      }
      await readSse(response);
      await refreshTask(id);
      await loadHistory();
    } catch (err) {
      const id = sessionId;
      if (id != null) {
        try {
          await api(`/cs-api/api/consult/sessions/${id}/interrupt`, { method: "POST" });
          await refreshTask(id);
        } catch {
          // ignore
        }
      }
      const message = humanizeError(err instanceof Error ? err.message : "发送失败");
      const hint = message.includes("会话不存在") ? "请再发一次。" : "若网络中断，可点「继续上次」。";
      setLines((prev) => [
        ...prev,
        {
          role: "assistant",
          text: `${message}。\n\n${hint}`
        }
      ]);
    } finally {
      setBusy(false);
    }
  }

  async function resumeLast() {
    if (busy || sessionId == null) return;
    setBusy(true);
    setLines((prev) => [...prev, { role: "progress", text: "正在从中断处继续…" }]);
    try {
      const response = await api(`/cs-api/api/consult/sessions/${sessionId}/resume`, { method: "POST" });
      await readSse(response);
      await refreshTask(sessionId);
    } catch (err) {
      setLines((prev) => [
        ...prev,
        { role: "assistant", text: err instanceof Error ? err.message : "继续失败" }
      ]);
      await refreshTask(sessionId);
    } finally {
      setBusy(false);
    }
  }

  async function uploadImage(file: File) {
    if (images.length >= 3) {
      setLines((prev) => [...prev, { role: "assistant", text: "一次最多三张图片。" }]);
      return;
    }
    const body = new FormData();
    body.append("file", file);
    const response = await api("/cs-api/api/media/images", { method: "POST", body });
    if (!response.ok) throw new Error(humanizeError(await response.text()));
    const saved = (await response.json()) as PendingImage;
    setImages((prev) => [...prev, { ...saved, url: `/cs-api${saved.url}` }]);
  }

  async function onPickImage(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    try {
      await uploadImage(file);
    } catch (err) {
      setLines((prev) => [...prev, { role: "assistant", text: err instanceof Error ? err.message : "图片上传失败" }]);
    }
  }

  function encodeWav(chunks: Float32Array[], sampleRate: number) {
    const length = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
    const input = new Float32Array(length);
    let offset = 0;
    for (const chunk of chunks) {
      input.set(chunk, offset);
      offset += chunk.length;
    }
    const ratio = sampleRate / 16000;
    const count = Math.max(1, Math.floor(input.length / ratio));
    const pcm = new Int16Array(count);
    for (let i = 0; i < count; i++) {
      const start = Math.floor(i * ratio);
      const end = Math.min(input.length, Math.max(start + 1, Math.floor((i + 1) * ratio)));
      let sum = 0;
      for (let j = start; j < end; j++) sum += input[j];
      const sample = Math.max(-1, Math.min(1, sum / (end - start)));
      pcm[i] = sample < 0 ? sample * 0x8000 : sample * 0x7fff;
    }
    const buffer = new ArrayBuffer(44 + pcm.length * 2);
    const view = new DataView(buffer);
    const write = (at: number, text: string) => {
      for (let i = 0; i < text.length; i++) view.setUint8(at + i, text.charCodeAt(i));
    };
    write(0, "RIFF");
    view.setUint32(4, 36 + pcm.length * 2, true);
    write(8, "WAVE");
    write(12, "fmt ");
    view.setUint32(16, 16, true);
    view.setUint16(20, 1, true);
    view.setUint16(22, 1, true);
    view.setUint32(24, 16000, true);
    view.setUint32(28, 32000, true);
    view.setUint16(32, 2, true);
    view.setUint16(34, 16, true);
    write(36, "data");
    view.setUint32(40, pcm.length * 2, true);
    let index = 44;
    for (const value of pcm) {
      view.setInt16(index, value, true);
      index += 2;
    }
    return new Blob([buffer], { type: "audio/wav" });
  }

  async function stopMic() {
    const hold = recorderRef.current;
    recorderRef.current = null;
    setRecording(false);
    if (!hold) return;
    window.clearTimeout(hold.timer);
    hold.processor.disconnect();
    hold.source.disconnect();
    hold.mute.disconnect();
    hold.stream.getTracks().forEach((track) => track.stop());
    const rate = hold.ctx.sampleRate;
    await hold.ctx.close();
    const wav = encodeWav(hold.chunks, rate);
    const body = new FormData();
    body.append("file", wav, "speech.wav");
    const response = await api("/cs-api/api/media/speech", { method: "POST", body });
    if (!response.ok) throw new Error(humanizeError(await response.text()));
    const data = (await response.json()) as { text?: string };
    if (!data.text?.trim()) throw new Error("没有听清，请再说一次");
    setText((prev) => (prev.trim() ? `${prev.trim()} ${data.text}` : data.text || ""));
  }

  async function toggleMic() {
    if (recording) {
      try {
        await stopMic();
      } catch (err) {
        setLines((prev) => [...prev, { role: "assistant", text: err instanceof Error ? err.message : "语音识别失败" }]);
      }
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const ctx = new AudioContext();
      const source = ctx.createMediaStreamSource(stream);
      const processor = ctx.createScriptProcessor(4096, 1, 1);
      const mute = ctx.createGain();
      mute.gain.value = 0;
      const chunks: Float32Array[] = [];
      processor.onaudioprocess = (event) => {
        chunks.push(new Float32Array(event.inputBuffer.getChannelData(0)));
      };
      source.connect(processor);
      processor.connect(mute);
      mute.connect(ctx.destination);
      const timer = window.setTimeout(() => {
        void stopMic().catch((err: unknown) => {
          setLines((prev) => [...prev, { role: "assistant", text: err instanceof Error ? err.message : "语音识别失败" }]);
        });
      }, 20000);
      recorderRef.current = { ctx, source, processor, mute, stream, chunks, timer };
      setRecording(true);
    } catch {
      setLines((prev) => [...prev, { role: "assistant", text: "没有拿到麦克风权限。" }]);
    }
  }

  const welcome = lines.length === 1 && lines[0]?.text === GREETING;
  const canSend = Boolean(text.trim() || images.length > 0);

  return (
    <div className="qh-consult">
      {sideOpen ? <button className="qh-mask" type="button" aria-label="关闭历史" onClick={() => setSideOpen(false)} /> : null}
      <aside className={`qh-side ${sideOpen ? "open" : ""}`}>
        <div className="qh-side-head">
          <Ico name="stethoscope.svg" />
          <div>
            <strong>医疗助手</strong>
            <span>您的智能问诊小帮手</span>
          </div>
        </div>
        <button type="button" className="qh-new" onClick={() => { startNew(); setSideOpen(false); }}>
          <Ico name="sparkles.svg" />
          开启新对话
          <Ico name="arrow_right.svg" />
        </button>
        <p className="qh-side-label"><Ico name="clock.svg" />历史对话</p>
        <HistorySideList
          history={history}
          activeId={sessionId}
          onOpen={(id) => { void openHistory(id); setSideOpen(false); }}
          onRename={renameHistory}
          onDelete={removeHistory}
          onDeleteMany={removeManyHistory}
          whenLabel={whenLabel}
          historyIcon={historyIcon}
          historyTone={historyTone}
        />
        <p className="qh-side-foot">关爱健康 · 从问诊开始</p>
      </aside>
      <section className="qh-main">
        <header className="qh-chat-head">
          <button type="button" className="qh-menu" onClick={() => setSideOpen(true)} aria-label="打开历史">
            <Ico name="clock.svg" />
          </button>
          <span className="qh-bot-badge"><img src="/brand/robot.svg" alt="" /></span>
          <div className="qh-chat-id">
            <strong>青禾</strong>
            <em><i />AI 医疗助手</em>
          </div>
          <span className="qh-trust"><Ico name="shield.svg" />专业 · 贴心 · 可信赖</span>
        </header>
        <div className={welcome ? "qh-log" : "qh-log is-chat"} ref={logRef}>
          {welcome ? (
            <div className="qh-welcome">
              <div>
                <p>你好呀！我是青禾，你的门诊助手。</p>
                <p>有什么我可以帮你的吗？身体不舒服、用药能不能吃、想了解常见病注意事项，都可以跟我说。</p>
                <p>也可以直接告诉我，例如：</p>
                <ul>
                  <li><span className="qh-tick" /><Ico name="heart_pulse.svg" />最近哪里不舒服，想了解一下怎么回事</li>
                  <li><span className="qh-tick" /><Ico name="capsule.svg" />想查查某个药能不能吃、怎么吃</li>
                  <li><span className="qh-tick" /><Ico name="lungs.svg" />喉咙痛、低烧、起风团这类症状持续了多久</li>
                </ul>
                <p className="qh-urgent">你尽管说。胸痛、大出血、叫不醒请直接去急诊。</p>
              </div>
              <img className="qh-robot" src="/brand/ai_robot.png" alt="" />
            </div>
          ) : (
            lines.map((line, index) =>
              line.role === "progress" ? (
                <div key={index} className="qh-row assistant">
                  <Speaker who="assistant" />
                  <div>
                    <p className="qh-who">青禾</p>
                    <div className="qh-bubble qh-progress" aria-live="polite">
                      <span className="qh-dots"><i /><i /><i /></span>
                      <em>{line.text}</em>
                    </div>
                  </div>
                </div>
              ) : (
                <div key={index} className={`qh-row ${line.role}`}>
                  <Speaker who={line.role === "user" ? "user" : "assistant"} />
                  <div>
                    <p className="qh-who">
                      {line.role === "user" ? "你" : "青禾"}
                      {line.role === "assistant" && line.webSearch ? (
                        <span className={`qh-web-tag${(line.webHits ?? 0) > 0 ? " used" : ""}`}>
                          {(line.webHits ?? 0) > 0 ? `已联网 · ${line.webHits} 条` : "已尝试联网"}
                        </span>
                      ) : null}
                    </p>
                    <div className={`qh-bubble ${line.card?.redFlag ? "danger" : ""}`}>
                      {line.role === "assistant" ? (
                        <MarkdownBody text={line.text} className="prose-p:my-2 prose-headings:text-ink" />
                      ) : (
                        <p>{line.text}</p>
                      )}
                      {line.images && line.images.length > 0 ? (
                        <div className="qh-thumbs">
                          {line.images.map((url) => <img key={url} src={url} alt="上传的症状图片" />)}
                        </div>
                      ) : null}
                      {line.card ? (
                        <div className="qh-card">
                          <MarkdownBody text={cardToMarkdown(line.card)} className="prose-sm" />
                          <SourceCites evidences={line.card.evidences} />
                        </div>
                      ) : null}
                    </div>
                  </div>
                </div>
              )
            )
          )}
          {!welcome && busy && !lines.some((line) => line.role === "progress") ? (
            <div className="qh-row assistant">
              <Speaker who="assistant" />
              <div>
                <p className="qh-who">青禾</p>
                <div className="qh-bubble qh-wait" aria-live="polite" aria-label="正在回复">
                  <span className="qh-dots"><i /><i /><i /></span>
                </div>
              </div>
            </div>
          ) : null}
        </div>
        <form
          className="qh-composer"
          onSubmit={(event) => {
            event.preventDefault();
            void send();
          }}
        >
          {resumable ? (
            <div className="qh-resume">
              <p>{resumeHint || "有未完成的问诊任务。"}</p>
              <button type="button" disabled={busy} onClick={() => void resumeLast()}>继续上次</button>
            </div>
          ) : null}
          <div className="qh-compose-box">
            <div className="qh-compose-top">
              <label htmlFor="symptom"><Ico name="medical_card.svg" />症状描述</label>
              <label className={`qh-web${webSearch ? " is-on" : ""}`} title="开启后，回答时可检索公开网页资料">
                <Ico name="globe.svg" />
                <span className="qh-web-copy">
                  <strong>联网搜索</strong>
                  <small>{webSearch ? "开 · 回答时可查网页" : "关 · 仅用本地知识"}</small>
                </span>
                <input type="checkbox" checked={webSearch} onChange={(event) => setWebSearch(event.target.checked)} />
                <span className={`qh-web-switch${webSearch ? " on" : ""}`} />
              </label>
            </div>
            {images.length > 0 ? (
              <div className="qh-thumbs">
                {images.map((item) => (
                  <figure key={item.id}>
                    <img src={item.url} alt={item.name} />
                    <button type="button" aria-label="移除图片" onClick={() => setImages((prev) => prev.filter((image) => image.id !== item.id))}>×</button>
                  </figure>
                ))}
              </div>
            ) : null}
            <div className="qh-field">
              <textarea
                id="symptom"
                value={text}
                onChange={(event) => setText(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter" && !event.shiftKey) {
                    event.preventDefault();
                    if (!busy && !recording && canSend) void send();
                  }
                }}
                placeholder="例如：喉咙痛，低烧两天（回车发送，Shift+回车换行）"
                rows={3}
              />
              <div className="qh-tools">
                <button type="button" className={recording ? "rec" : ""} aria-label={recording ? "停止录音" : "语音输入"} onClick={() => void toggleMic()}>
                  <Ico name="microphone.svg" />
                </button>
                <button type="button" aria-label="上传图片" onClick={() => fileRef.current?.click()}>
                  <Ico name="image.svg" />
                </button>
                <input ref={fileRef} type="file" accept="image/jpeg,image/png,image/gif,image/webp" hidden onChange={(event) => void onPickImage(event)} />
              </div>
            </div>
            <div className="qh-cardrow">
              {profileLinked ? (
                <div className="qh-linked">
                  <span className="qh-linked-ico"><Ico name="check_circle.svg" /></span>
                  <div>
                    <b>{profileTitle || "就诊卡已关联"}</b>
                    <small>
                      青禾将结合您的就诊摘要辅助本次分析
                      {digest && digest.included.length > 0 ? ` · 已包含：${digest.included.join(" · ")}` : ""}
                    </small>
                  </div>
                  <button type="button" onClick={() => { if (profileId) { setDrawer("summary"); void loadDigest(profileId); } }}>
                    <Ico name="book.svg" />查看摘要
                  </button>
                  <button type="button" className="qh-unlink" onClick={() => void removeCard()}>
                    <Ico name="close.svg" />移除
                  </button>
                </div>
              ) : cardIssue ? (
                <div className="qh-linked issue">
                  <span className="qh-linked-ico"><Ico name="medical_card.svg" /></span>
                  <div>
                    <b>就诊卡暂时无法用于本次分析</b>
                    <small>不影响问诊，直接描述症状就可以</small>
                  </div>
                  <button type="button" onClick={() => setCardIssue(false)}>继续普通问诊</button>
                </div>
              ) : (
                <>
                  <button type="button" className="qh-addcard" onClick={() => void openPicker()}>
                    <Ico name="plus.svg" />添加就诊卡
                  </button>
                  <span className="qh-cardhint">可选 · 帮助 AI 更了解您的情况</span>
                </>
              )}
            </div>
            <div className="qh-compose-actions">
              <div className="qh-sends">
                <button className="qh-send" type="submit" disabled={busy || recording || !canSend}>
                  <Ico name="send.svg" />
                  发送
                </button>
              </div>
            </div>
          </div>
        </form>
        <p className="qh-slogan">— AI 让医疗更有温度 —</p>
      </section>
      {drawer ? createPortal(
        <>
          <button className="qh-drawer-mask" type="button" aria-label="关闭" onClick={() => setDrawer(null)} />
          <aside className="qh-drawer" role="dialog" aria-modal="true" aria-labelledby="qh-drawer-title">
            <header>
              <div>
                <h2 id="qh-drawer-title">{drawer === "pick" ? "添加就诊卡" : "本次问诊辅助摘要"}</h2>
                <p>
                  {drawer === "pick"
                    ? "按姓名选择一张就诊卡，用于本次问诊。"
                    : "下面是就诊摘要。姓名只用来区分卡片，不会写进分析内容。"}
                </p>
              </div>
              <button type="button" className="qh-drawer-close" aria-label="关闭" onClick={() => setDrawer(null)}>
                <Ico name="close.svg" />
              </button>
            </header>
            {drawer === "pick" ? (
              <div className="qh-drawer-body">
                <form className="qh-cardnew" onSubmit={(event) => { event.preventDefault(); void createCard(); }}>
                  <input
                    value={cardName}
                    placeholder="姓名，例如 张三"
                    onChange={(event) => setCardName(event.target.value)}
                  />
                  <button type="submit" disabled={!cardName.trim()}>新建</button>
                </form>
                {cards.length === 0 ? (
                  <p className="qh-drawer-empty">还没有就诊卡。填上姓名后点新建，就会出现在下面。</p>
                ) : cards.map((card) => (
                  <div className="qh-cardpick" key={card.id}>
                    <span className="qh-cardpick-ico"><Ico name="medical_card.svg" /></span>
                    <div>
                      <b>{card.title}</b>
                      <small>{card.included.length > 0 ? card.included.join(" · ") : "还没有填写内容"}</small>
                    </div>
                    <button type="button" className="qh-pick" onClick={() => void chooseCard(card)}>选择</button>
                  </div>
                ))}
                <button type="button" className="qh-newcard" onClick={() => { setDrawer(null); onOpenProfile?.(); }}>
                  <Ico name="plus.svg" />管理就诊卡
                </button>
              </div>
            ) : (
              <div className="qh-drawer-body">
                <dl className="qh-digest">
                  {(digest?.items ?? []).map((row) => (
                    <div key={row.label}>
                      <dt>{row.label}</dt>
                      <dd>{row.value}</dd>
                    </div>
                  ))}
                </dl>
                <button type="button" className="qh-newcard" onClick={() => void removeCard()}>
                  <Ico name="close.svg" />移除关联
                </button>
              </div>
            )}
            <footer>
              {drawer === "pick"
                ? "就诊卡信息仅在您主动关联后用于本次问诊辅助分析。"
                : "以上内容来自您主动关联的就诊卡，仅用于本次问诊辅助分析。"}
            </footer>
          </aside>
        </>,
        document.body
      ) : null}
    </div>
  );
}

function historyIcon(title: string) {
  if (title.includes("胸")) return "heart_pulse.svg";
  if (title.includes("喉") || title.includes("咳") || title.includes("烧")) return "capsule.svg";
  if (title.includes("风团") || title.includes("痒") || title.includes("疹")) return "plus.svg";
  if (title.includes("药")) return "capsule.svg";
  return "chat.svg";
}

function historyTone(title: string) {
  const icon = historyIcon(title);
  if (icon === "capsule.svg") return "tone-pill";
  if (icon === "plus.svg") return "tone-plus";
  return "tone-heart";
}

function whenLabel(iso?: string) {
  if (!iso) return "";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const day = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const diff = Math.round((start.getTime() - day.getTime()) / 86400000);
  const hm = date.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false });
  if (diff <= 0) return `今天 ${hm}`;
  if (diff === 1) return `昨天 ${hm}`;
  if (diff === 2) return `前天 ${hm}`;
  return `${date.getMonth() + 1}月${date.getDate()}日 ${hm}`;
}

import { useEffect, useState, type FormEvent, type KeyboardEvent } from "react";
import { Ico } from "../components/Ico";
import { MarkdownBody } from "../components/MarkdownBody";
import { api } from "../api";

type CardBrief = { id: number; name: string; title: string; completeness?: number };
type HistoryItem = { id: number; title: string; updatedAt?: string };
type ProfileView = {
  id?: number;
  name?: string;
  title?: string;
  completeness?: number;
  requiredFilled?: number;
  requiredTotal?: number;
  bmi?: number | null;
  bmiClass?: string;
  summary?: string;
  profile?: Record<string, string | number>;
};

type Tab = "basic" | "past" | "meds" | "allergy" | "exam";

const TABS: { id: Tab; label: string }[] = [
  { id: "basic", label: "基本信息" },
  { id: "past", label: "既往病史" },
  { id: "meds", label: "用药记录" },
  { id: "allergy", label: "过敏史" },
  { id: "exam", label: "检查报告" }
];

const TAB_ICONS: Record<Tab, string> = {
  basic: "stethoscope.svg",
  past: "medical_card.svg",
  meds: "capsule.svg",
  allergy: "shield.svg",
  exam: "chart.svg"
};

const EMPTY_FORM: Record<string, string> = {
  name: "",
  gender: "",
  age: "",
  height_cm: "",
  weight_kg: "",
  allergies: "",
  chronic: "",
  medications: "",
  pregnancy: "",
  surgery_history: "",
  family_history: "",
  bp_sys: "",
  bp_dia: "",
  heart_rate: "",
  temperature: "",
  glucose: "",
  spo2: ""
};

export function ProfilePage({
  onConsult,
  onBack
}: {
  onConsult?: (id: number | null, draft?: string) => void;
  onBack?: () => void;
}) {
  const [cards, setCards] = useState<CardBrief[]>([]);
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [activeId, setActiveId] = useState<number | null>(null);
  const [data, setData] = useState<ProfileView | null>(null);
  const [form, setForm] = useState<Record<string, string>>(EMPTY_FORM);
  const [savedForm, setSavedForm] = useState<Record<string, string>>(EMPTY_FORM);
  const [tab, setTab] = useState<Tab>("basic");
  const [full, setFull] = useState(false);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [newName, setNewName] = useState("");
  const [sideOpen, setSideOpen] = useState(false);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [guiding, setGuiding] = useState(false);
  const [assistant, setAssistant] = useState("");
  const [reply, setReply] = useState("");

  async function loadList() {
    const response = await api("/cs-api/api/profile/cards");
    if (!response.ok) throw new Error(await response.text());
    const list = (await response.json()) as CardBrief[];
    setCards(list);
    return list;
  }

  function applyView(view: ProfileView) {
    setData(view);
    setActiveId(view.id ?? null);
    const next: Record<string, string> = { ...EMPTY_FORM, name: view.name || "" };
    for (const key of Object.keys(EMPTY_FORM)) {
      if (key === "name") continue;
      next[key] = String(view.profile?.[key] ?? "");
    }
    setForm(next);
    setSavedForm(next);
  }

  async function load(id: number) {
    const response = await api(`/cs-api/api/profile?id=${id}`);
    if (!response.ok) throw new Error(await response.text());
    applyView((await response.json()) as ProfileView);
  }

  useEffect(() => {
    loadList()
      .then((list) => {
        const ranked = [...list].sort((a, b) => (b.completeness ?? 0) - (a.completeness ?? 0));
        const best = ranked.find((item) => item.name && !item.name.startsWith("BUG")) ?? ranked[0];
        return best ? load(best.id) : undefined;
      })
      .catch((err: unknown) => setMessage(err instanceof Error ? err.message : "读取失败"));
    api("/cs-api/api/consult/sessions")
      .then(async (response) => (response.ok ? ((await response.json()) as HistoryItem[]) : []))
      .then(setHistory)
      .catch(() => setHistory([]));
  }, []);

  async function createCard() {
    const name = newName.trim();
    if (!name) return;
    setBusy(true);
    setMessage("");
    try {
      const response = await api("/cs-api/api/profile/cards", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name })
      });
      if (!response.ok) throw new Error(await response.text());
      const created = (await response.json()) as CardBrief;
      setNewName("");
      setCreating(false);
      await loadList();
      await load(created.id);
      setMessage(`已新建${created.title}。`);
    } catch (err) {
      setMessage(err instanceof Error ? err.message : "新建失败");
    } finally {
      setBusy(false);
    }
  }

  async function save(event?: FormEvent) {
    event?.preventDefault();
    setBusy(true);
    setMessage("");
    try {
      if (activeId == null) throw new Error("请先新建一张就诊卡");
      const { name, ...profile } = form;
      const response = await api("/cs-api/api/profile", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ id: activeId, name, profile })
      });
      if (!response.ok) throw new Error(await response.text());
      applyView((await response.json()) as ProfileView);
      await loadList();
      setMessage("就诊卡已保存。");
    } catch (err) {
      setMessage(err instanceof Error ? err.message : "保存失败");
    } finally {
      setBusy(false);
    }
  }

  async function askAssistant(input: string) {
    if (activeId == null) return;
    setBusy(true);
    try {
      const response = await api("/cs-api/api/profile/assistant", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ id: activeId, mode: "collect", input })
      });
      if (!response.ok) throw new Error(await response.text());
      const body = (await response.json()) as { reply?: string };
      setGuiding(true);
      setAssistant(body.reply || "");
      setReply("");
      await load(activeId);
      await loadList();
    } catch (err) {
      setMessage(err instanceof Error ? err.message : "引导失败");
    } finally {
      setBusy(false);
    }
  }

  function onReplyKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      const text = reply.trim();
      if (text) void askAssistant(text);
    }
  }

  const bmi = liveBmi(form) ?? data?.bmi ?? null;
  const chips = activeId == null ? [] : summaryChips(form, bmi);

  return (
    <div className="qh-consult qh-vc">
      {sideOpen ? <button className="qh-mask" type="button" aria-label="关闭历史" onClick={() => setSideOpen(false)} /> : null}
      <aside className={`qh-side ${sideOpen ? "open" : ""}`}>
        <div className="qh-side-head">
          <Ico name="stethoscope.svg" />
          <div>
            <strong>医疗助手</strong>
            <span>您的智能问诊小帮手</span>
          </div>
        </div>
        <button type="button" className="qh-new" onClick={() => onConsult?.(null)}>
          <Ico name="sparkles.svg" />
          开启新对话
          <Ico name="arrow_right.svg" />
        </button>
        <p className="qh-side-label"><Ico name="clock.svg" />历史对话</p>
        <ul className="qh-history">
          {history.length > 0
            ? history.map((item) => (
                <li key={item.id}>
                  <button type="button" onClick={() => onConsult?.(item.id)}>
                    <span className={`qh-hist-ico ${historyTone(item.title)}`}><Ico name={historyIcon(item.title)} /></span>
                    <span className="qh-hist-copy">
                      <b>{item.title}</b>
                      <small>{whenLabel(item.updatedAt)}</small>
                    </span>
                    <Ico name="arrow_right.svg" />
                  </button>
                </li>
              ))
            : <li className="qh-hist-empty">还没有问诊记录</li>}
        </ul>
        <p className="qh-side-foot">关爱健康 · 从问诊开始</p>
      </aside>
      <section className="qh-vc-card">
        <header className="qh-vc-top">
          <button type="button" className="qh-menu" aria-label="打开历史" onClick={() => setSideOpen(true)}>
            <Ico name="clock.svg" />
          </button>
          <button type="button" className="qh-vc-back" aria-label="返回问诊" onClick={() => onBack?.()}>
            <Ico name="arrow_right.svg" />
          </button>
          <h2>就诊卡</h2>
        </header>
        <div className="qh-vc-scroll">
          <div className="qh-vc-hero">
            <div>
              <div className="qh-vc-kicker">
                <span className="qh-vc-badge"><Ico name="medical_card.svg" /></span>
                <div>
                  <strong>健康档案</strong>
                  <em><Ico name="heart_pulse.svg" />电子就诊卡</em>
                </div>
              </div>
              <p>
                按姓名区分就诊卡，例如「张三的就诊卡」。不存身份证和电话。
                {data ? ` 当前完整度 ${data.completeness ?? 0}%（必填 ${data.requiredFilled ?? 0}/${data.requiredTotal ?? 7}）。` : ""}
              </p>
            </div>
            <div className="qh-vc-art">
              <img src="/brand/ai_robot.png" alt="" />
              <span>让就医更简单</span>
            </div>
          </div>
          <div className="qh-vc-tools">
            <div className={`qh-vc-picker ${pickerOpen ? "open" : ""}`}>
              <button
                type="button"
                className="qh-vc-select"
                aria-haspopup="listbox"
                aria-expanded={pickerOpen}
                onClick={() => setPickerOpen((value) => !value)}
              >
              <Ico name="stethoscope.svg" />
                <span>{data?.title || "还没有就诊卡"}</span>
                <svg viewBox="0 0 16 16" aria-hidden="true">
                  <path d="m4 6 4 4 4-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              </button>
              {pickerOpen ? (
                <div className="qh-vc-options" role="listbox" aria-label="选择就诊卡">
                {cards.map((card) => (
                    <button
                      key={card.id}
                      type="button"
                      role="option"
                      aria-selected={card.id === activeId}
                      className={card.id === activeId ? "on" : ""}
                      onClick={() => {
                        setPickerOpen(false);
                        void load(card.id);
                      }}
                    >
                      <span><Ico name="medical_card.svg" />{card.title}</span>
                      <small>{card.completeness ?? 0}%</small>
                    </button>
                ))}
                </div>
              ) : null}
            </div>
            <button type="button" className="qh-vc-add" onClick={() => setCreating((value) => !value)}>
              <Ico name="plus.svg" />新建就诊卡
            </button>
          </div>
          {creating ? (
            <div className="qh-vc-create">
              <input
                value={newName}
                placeholder="姓名，例如 张三"
                onChange={(event) => setNewName(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") void createCard();
                }}
              />
              <button type="button" disabled={busy || !newName.trim()} onClick={() => void createCard()}>确定</button>
              <button type="button" className="ghost" onClick={() => setCreating(false)}>取消</button>
            </div>
          ) : null}
          <div className="qh-vc-tabs" role="tablist">
            {TABS.map((item) => (
              <button
                key={item.id}
                type="button"
                role="tab"
                aria-selected={tab === item.id}
                className={tab === item.id ? "on" : ""}
                onClick={() => setTab(item.id)}
              >
                {item.label}
              </button>
            ))}
          </div>
          <div className="qh-vc-sum">
            <div>
              <p><i />就诊卡信息摘要<span>（已自动提取）</span></p>
              <div className="qh-vc-chips">
                {chips.map((chip) => <span key={chip}>{chip}</span>)}
              </div>
            </div>
            <button type="button" onClick={() => setFull((value) => !value)}>
              {full ? "收起" : "查看完整信息"} <Ico name="arrow_right.svg" />
            </button>
          </div>
          {full ? (
            <div className="qh-vc-full">
              <div className="qh-vc-full-head">
                <span><Ico name="medical_card.svg" /></span>
                <div>
                  <strong>完整档案摘要</strong>
                  <p>用于核对已保存的结构化信息，不会自动关联到新问诊。</p>
                </div>
                <button type="button" disabled={busy || activeId == null} onClick={() => void askAssistant("")}>
                  <Ico name="sparkles.svg" />智能补全
                </button>
              </div>
              <dl className="qh-vc-facts">
                <Fact label="姓名" value={form.name} />
                <Fact label="基本信息" value={[form.gender, form.age ? `${form.age}岁` : ""].filter(Boolean).join(" · ")} />
                <Fact label="身高 / 体重" value={[form.height_cm ? `${form.height_cm}cm` : "", form.weight_kg ? `${form.weight_kg}kg` : ""].filter(Boolean).join(" / ")} />
                <Fact label="BMI" value={bmi == null ? "" : `${bmi}${data?.bmiClass ? `（${data.bmiClass}）` : ""}`} />
                <Fact label="过敏史" value={form.allergies} />
                <Fact label="既往病史" value={form.chronic} />
                <Fact label="长期用药" value={form.medications} />
                <Fact label="妊娠相关" value={form.pregnancy} />
              </dl>
              {guiding || assistant ? (
                <div className="qh-vc-guide">
                  <div className="qh-vc-guide-copy">
                    <span><Ico name="robot.svg" /></span>
                    {assistant ? <MarkdownBody text={assistant} className="prose-sm" /> : null}
                  </div>
                  <textarea
                    value={reply}
                    rows={3}
                    placeholder="例如：身高165，体重55，无药物过敏。回车发送，Shift+回车换行"
                    onChange={(event) => setReply(event.target.value)}
                    onKeyDown={onReplyKeyDown}
                  />
                  <div>
                    <small>回车发送 · Shift+回车换行</small>
                    <button type="button" disabled={busy || !reply.trim()} onClick={() => void askAssistant(reply.trim())}>
                      {busy ? "整理中…" : "发送回答"}
                    </button>
                  </div>
                </div>
              ) : null}
            </div>
          ) : null}
          <form onSubmit={(event) => void save(event)}>
            <h3 className="qh-vc-section">
              <Ico name={TAB_ICONS[tab]} />
              {TABS.find((item) => item.id === tab)?.label}
            </h3>
            {tab === "basic" ? (
              <div className="qh-vc-grid">
                <Field icon="stethoscope.svg" label="姓名" required value={form.name} onChange={(value) => setField(setForm, "name", value)} />
                <Field icon="clock.svg" label="年龄" required suffix="岁" value={form.age} onChange={(value) => setField(setForm, "age", value)} />
                <SelectField icon="stethoscope.svg" label="性别" required value={form.gender} options={["男", "女", "其他"]} onChange={(value) => setField(setForm, "gender", value)} />
                <Field icon="chart.svg" label="体重" required suffix="kg" value={form.weight_kg} onChange={(value) => setField(setForm, "weight_kg", value)} />
                <Field icon="chart.svg" label="身高" required suffix="cm" value={form.height_cm} onChange={(value) => setField(setForm, "height_cm", value)} />
                <Field icon="heart_pulse.svg" label="体重指数（BMI）" value={bmi == null ? "" : String(bmi)} readOnly />
                <SelectField icon="shield.svg" label="过敏史" value={form.allergies} options={["无过敏", "药物过敏", "食物过敏", "其他"]} onChange={(value) => setField(setForm, "allergies", value)} />
                <SelectField icon="medical_card.svg" label="既往病史" value={form.chronic} options={["无", "高血压", "糖尿病", "哮喘"]} onChange={(value) => setField(setForm, "chronic", value)} />
                <SelectField icon="capsule.svg" label="长期用药" value={form.medications} options={["无", "有长期用药"]} onChange={(value) => setField(setForm, "medications", value)} />
                <SelectField icon="heart_pulse.svg" label="妊娠/哺乳/备孕" value={form.pregnancy} options={["无", "备孕", "妊娠", "哺乳"]} onChange={(value) => setField(setForm, "pregnancy", value)} />
              </div>
            ) : null}
            {tab === "past" ? (
              <div className="qh-vc-grid">
                <Field icon="medical_card.svg" label="既往病史" value={form.chronic} onChange={(value) => setField(setForm, "chronic", value)} />
                <Field icon="medical_card.svg" label="手术史" value={form.surgery_history} onChange={(value) => setField(setForm, "surgery_history", value)} />
                <Field icon="heart_pulse.svg" label="家族史" wide value={form.family_history} onChange={(value) => setField(setForm, "family_history", value)} />
              </div>
            ) : null}
            {tab === "meds" ? (
              <label className="qh-vc-area">
                <span><Ico name="capsule.svg" />用药记录</span>
                <textarea value={form.medications} placeholder="无，或写上药名、剂量和频次" onChange={(event) => setField(setForm, "medications", event.target.value)} />
              </label>
            ) : null}
            {tab === "allergy" ? (
              <label className="qh-vc-area">
                <span><Ico name="shield.svg" />过敏史</span>
                <textarea value={form.allergies} placeholder="无，或写上药物、食物过敏" onChange={(event) => setField(setForm, "allergies", event.target.value)} />
              </label>
            ) : null}
            {tab === "exam" ? (
              <div className="qh-vc-grid">
                <Field icon="heart_pulse.svg" label="收缩压" suffix="mmHg" value={form.bp_sys} onChange={(value) => setField(setForm, "bp_sys", value)} />
                <Field icon="heart_pulse.svg" label="舒张压" suffix="mmHg" value={form.bp_dia} onChange={(value) => setField(setForm, "bp_dia", value)} />
                <Field icon="heart_pulse.svg" label="心率" value={form.heart_rate} onChange={(value) => setField(setForm, "heart_rate", value)} />
                <Field icon="chart.svg" label="体温" suffix="℃" value={form.temperature} onChange={(value) => setField(setForm, "temperature", value)} />
                <Field icon="chart.svg" label="血糖" value={form.glucose} onChange={(value) => setField(setForm, "glucose", value)} />
                <Field icon="lungs.svg" label="血氧" suffix="%" value={form.spo2} onChange={(value) => setField(setForm, "spo2", value)} />
              </div>
            ) : null}
            <footer className="qh-vc-foot">
              <button type="submit" className="qh-vc-save" disabled={busy || activeId == null}>
                <Ico name="check_circle.svg" />保存就诊卡
              </button>
              <p>您填，轻点 ♥ 爱了就能问病</p>
              <button
                type="button"
                className="qh-vc-cancel"
                onClick={() => {
                  setForm(savedForm);
                  setMessage("");
                }}
              >
                取消
              </button>
            </footer>
          </form>
          {message ? <p className="qh-vc-toast">{message}</p> : null}
        </div>
      </section>
    </div>
  );
}

function Field({
  icon, label, value, onChange, required, suffix, readOnly, placeholder, suggestions, wide
}: {
  icon: string;
  label: string;
  value: string;
  onChange?: (value: string) => void;
  required?: boolean;
  suffix?: string;
  readOnly?: boolean;
  placeholder?: string;
  suggestions?: string[];
  wide?: boolean;
}) {
  const listId = suggestions ? `opt-${label}` : undefined;
  return (
    <label className={wide ? "qh-vc-field wide" : "qh-vc-field"}>
      <span><Ico name={icon} />{label}{required ? <em>*</em> : null}</span>
      <div>
        <input
          value={value}
          readOnly={readOnly}
          placeholder={placeholder}
          list={listId}
          onChange={(event) => onChange?.(event.target.value)}
        />
        {suffix ? <small>{suffix}</small> : null}
        {suggestions ? (
          <datalist id={listId}>
            {suggestions.map((item) => <option key={item} value={item} />)}
          </datalist>
        ) : null}
      </div>
    </label>
  );
}

function SelectField({
  icon, label, value, options, onChange, required
}: {
  icon: string;
  label: string;
  value: string;
  options: string[];
  onChange: (value: string) => void;
  required?: boolean;
}) {
  const known = options.includes(value) || value === "";
  return (
    <label className="qh-vc-field">
      <span><Ico name={icon} />{label}{required ? <em>*</em> : null}</span>
      <div>
        <select value={value} onChange={(event) => onChange(event.target.value)}>
          <option value="">请选择</option>
          {!known ? <option value={value}>{value}</option> : null}
          {options.map((item) => <option key={item} value={item}>{item}</option>)}
        </select>
      </div>
    </label>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value.trim() || "未填写"}</dd>
    </div>
  );
}

function setField(setForm: (updater: (prev: Record<string, string>) => Record<string, string>) => void, key: string, value: string) {
  setForm((prev) => ({ ...prev, [key]: value }));
}

function liveBmi(form: Record<string, string>) {
  const height = Number(form.height_cm);
  const weight = Number(form.weight_kg);
  if (!height || !weight || height <= 0) return null;
  const meters = height / 100;
  return Math.round((weight / (meters * meters)) * 10) / 10;
}

function blank(value: string) {
  const text = value.trim();
  return !text || text === "无" || text === "无过敏" || text === "没有" || text === "未知";
}

function summaryChips(form: Record<string, string>, bmi: number | null) {
  const chips: string[] = [];
  const who = [form.gender, form.age ? `${form.age}岁` : ""].filter(Boolean).join(" · ");
  if (who) chips.push(who);
  if (form.weight_kg.trim()) chips.push(`体重 ${form.weight_kg.trim()}kg`);
  if (bmi != null) chips.push(`BMI ${bmi}`);
  chips.push(blank(form.chronic) ? "无既往病史" : `${form.chronic.trim()}（既往）`);
  chips.push(blank(form.allergies) ? "无药物过敏" : form.allergies.trim());
  chips.push(`长期用药：${blank(form.medications) ? "无" : form.medications.trim()}`);
  return chips;
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

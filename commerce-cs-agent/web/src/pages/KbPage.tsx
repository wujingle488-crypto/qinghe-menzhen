import { useEffect, useMemo, useState } from "react";
import { Ico } from "../components/Ico";
import { api } from "../api";

type Article = { id: number; title: string; body: string; diseaseCode?: string; sourceName?: string; sourceUrl?: string };
type Category = { id: string; label: string; blurb: string; count: number };
type Entry = {
  id: number;
  title: string;
  summary: string;
  category: string;
  categoryLabel: string;
  minutes: number;
  sourceName: string;
  keywords?: string;
  hot?: boolean;
};
type Snapshot = {
  articles: Article[];
  catalog?: { categories: Category[]; entries: Entry[] };
};

const ICONS: Record<string, string> = {
  all: "book.svg",
  disease: "lungs.svg",
  symptom: "stethoscope.svg",
  drug: "capsule.svg",
  exam: "chart.svg",
  health: "shield.svg",
  emergency: "heart_pulse.svg",
  tcm: "sparkles.svg"
};

const SORTS = ["最新", "最热", "推荐"] as const;

export function KbPage({ onOpenProfile }: { onOpenProfile?: () => void }) {
  const [data, setData] = useState<Snapshot | null>(null);
  const [category, setCategory] = useState("all");
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<(typeof SORTS)[number]>("最新");
  const [openId, setOpenId] = useState<number | null>(null);
  const [sideOpen, setSideOpen] = useState(false);
  const [message, setMessage] = useState("");

  useEffect(() => {
    api("/cs-api/api/kb")
      .then(async (response) => {
        if (!response.ok) throw new Error(await response.text());
        return (await response.json()) as Snapshot;
      })
      .then(setData)
      .catch((err: unknown) => setMessage(err instanceof Error ? err.message : "读取失败"));
  }, []);

  const entries = useMemo(() => decorate(data?.catalog?.entries ?? []), [data]);
  const categories = useMemo(() => withCounts(entries), [entries]);
  const sideCategories = categories.filter((item) => item.id !== "all");
  const shortcuts = sideCategories;
  const hot = ["disease", "drug", "exam", "health"]
    .map((id) => entries.find((item) => item.hot && item.category === id))
    .filter((item): item is Entry => Boolean(item));

  const visible = useMemo(() => {
    const needle = query.trim();
    const matched = entries.filter((item) => {
      if (category !== "all" && item.category !== category) return false;
      if (!needle) return true;
      return `${item.title} ${item.summary} ${item.keywords ?? ""} ${item.categoryLabel}`.includes(needle);
    });
    const sorted = [...matched];
    if (sort === "最新") sorted.sort((a, b) => (b.id ?? 0) - (a.id ?? 0));
    if (sort === "最热") sorted.sort((a, b) => b.minutes - a.minutes || (b.id ?? 0) - (a.id ?? 0));
    if (sort === "推荐") sorted.sort((a, b) => Number(b.hot) - Number(a.hot));
    return sorted;
  }, [entries, category, query, sort]);

  const article = data?.articles.find((item) => item.id === openId) ?? null;
  const entry = entries.find((item) => item.id === openId) ?? null;

  function pickCategory(id: string) {
    setCategory(id);
    setOpenId(null);
    setSideOpen(false);
  }

  function search(value: string) {
    setQuery(value);
    if (value.trim()) setOpenId(null);
  }

  return (
    <div className="qh-kb">
      {sideOpen ? <button className="qh-mask" type="button" aria-label="关闭分类" onClick={() => setSideOpen(false)} /> : null}
      <aside className={`qh-side qh-kb-side ${sideOpen ? "open" : ""}`}>
        <div className="qh-side-head">
          <Ico name="book.svg" />
          <div>
            <strong>知识库</strong>
            <span>专业 · 权威 · 实用</span>
          </div>
        </div>
        <label className="qh-kb-search">
          <SearchIcon />
          <input
            value={query}
            placeholder="搜索疾病、症状、用药、检查……"
            onChange={(event) => search(event.target.value)}
          />
        </label>
        <ul className="qh-kb-cats">
          <li>
            <button type="button" className={category === "all" ? "on" : ""} onClick={() => pickCategory("all")}>
              <Ico name="book.svg" />全部分类<Ico name="arrow_right.svg" />
            </button>
          </li>
          {sideCategories.map((item) => (
            <li key={item.id}>
              <button type="button" className={category === item.id ? "on" : ""} onClick={() => pickCategory(item.id)}>
                <Ico name={ICONS[item.id] || "book.svg"} />
                {item.label}
                <Ico name="arrow_right.svg" />
              </button>
            </li>
          ))}
        </ul>
        <p className="qh-side-foot">关爱健康 · 从问诊开始</p>
      </aside>
      <div className="qh-kb-main">
        <button type="button" className="qh-menu" aria-label="打开分类" onClick={() => setSideOpen(true)}>
          <Ico name="book.svg" />
        </button>
        {article && entry ? (
          <article className="qh-kb-read">
            <button type="button" onClick={() => setOpenId(null)}><Ico name="arrow_right.svg" />返回知识库</button>
            <p className="qh-kb-tag">{entry.categoryLabel}</p>
            <h2>{article.title}</h2>
            <p className="qh-kb-meta">{entry.sourceName} · 约 {entry.minutes} 分钟</p>
            <h3>知识说明</h3>
            <p>{article.body}</p>
            <h3>何时需要就医</h3>
            <p>出现加重、高热、呼吸困难、意识改变或说明书提示的危险情况时，停止自行处理并去医院。这里的资料是教学参考，不能代替面诊。</p>
            {article.sourceName ? <p className="qh-kb-src">来源：{article.sourceName}{article.sourceUrl ? ` · ${article.sourceUrl}` : ""}</p> : null}
          </article>
        ) : (
          <>
            <section className="qh-kb-hero">
              <div>
                <p className="qh-kb-tag"><Ico name="book.svg" />知识库</p>
                <h2>科学健康知识 · 专业医疗参考</h2>
                <p>汇聚权威医学知识，帮助您更好地了解疾病、用药和健康管理。</p>
                <div className="qh-kb-trust">
                  <span><Ico name="shield.svg" />权威可靠</span>
                  <span><Ico name="check_circle.svg" />专业审核</span>
                  <span><Ico name="sparkles.svg" />持续更新</span>
                </div>
                <label className="qh-kb-hero-search">
                  <input
                    value={query}
                    placeholder="搜索疾病、症状、用药、检查……"
                    onChange={(event) => search(event.target.value)}
                  />
                  <button type="button" aria-label="搜索" onClick={() => document.getElementById("kb-all")?.scrollIntoView({ behavior: "smooth" })}>
                    <SearchIcon light />
                  </button>
                </label>
              </div>
              <img src="/brand/ai_robot.png" alt="" />
            </section>
            <section className="qh-kb-shortcuts">
              {shortcuts.map((item) => (
                <button key={item.id} type="button" onClick={() => pickCategory(item.id)}>
                  <span className={`qh-kb-ico tone-${item.id}`}><Ico name={ICONS[item.id] || "book.svg"} /></span>
                  <strong>{item.label}</strong>
                  <em>{item.blurb}</em>
                  <Ico name="arrow_right.svg" />
                </button>
              ))}
            </section>
            <section className="qh-kb-block">
              <header>
                <h3>热门知识推荐</h3>
                <button type="button" onClick={() => document.getElementById("kb-all")?.scrollIntoView({ behavior: "smooth" })}>
                  查看更多 <Ico name="arrow_right.svg" />
                </button>
              </header>
              <div className="qh-kb-hot">
                {hot.map((item) => (
                  <button key={item.id ?? item.title} type="button" onClick={() => setOpenId(item.id)}>
                    <span className={`qh-kb-cover tone-${item.category}`}>
                      <img src={coverOf(item.category)} alt="" />
                    </span>
                    <em className={`tone-${item.category}`}>{item.categoryLabel}</em>
                    <strong>{item.title}</strong>
                    <p>{item.summary}</p>
                    <small>{readsLabel(item.id)} · {item.minutes} 分钟</small>
                  </button>
                ))}
              </div>
            </section>
            <section className="qh-kb-block" id="kb-all">
              <header>
                <h3>全部知识</h3>
              </header>
              <div className="qh-kb-all">
                <aside>
                  {categories.map((item) => (
                    <button key={item.id} type="button" className={category === item.id ? "on" : ""} onClick={() => pickCategory(item.id)}>
                      <Ico name={ICONS[item.id] || "book.svg"} />
                      <span>{item.id === "all" ? "全部知识" : item.label}</span>
                      <small>{item.count}</small>
                    </button>
                  ))}
                </aside>
                <div>
                  <div className="qh-kb-bar">
                    {SORTS.map((item) => (
                      <button key={item} type="button" className={sort === item ? "on" : ""} onClick={() => setSort(item)}>{item}</button>
                    ))}
                    <label>
                      <SearchIcon />
                      <input value={query} placeholder="搜索知识……" onChange={(event) => search(event.target.value)} />
                    </label>
                  </div>
                  <ul>
                    {visible.map((item) => (
                      <li key={item.id ?? item.title}>
                        <button type="button" onClick={() => setOpenId(item.id)}>
                          <span className={`qh-kb-ico tone-${item.category}`}><Ico name={ICONS[item.category] || "book.svg"} /></span>
                          <span>
                            <strong>{item.title}</strong>
                            <p>{item.summary}</p>
                            <small>
                              <em className={`tone-${item.category}`}>{item.categoryLabel}</em>
                              {readsLabel(item.id)} · {item.minutes} 分钟
                            </small>
                          </span>
                          <Ico name="arrow_right.svg" />
                        </button>
                      </li>
                    ))}
                  </ul>
                  {visible.length === 0 ? <p className="qh-kb-empty">这一类还没有对上的资料。</p> : null}
                </div>
              </div>
            </section>
            <section className="qh-kb-tip">
              <span><Ico name="shield.svg" /></span>
              <div>
                <strong>知识库小贴士</strong>
                <p>如需更准确的个性化建议，建议结合您的就诊卡信息进行问诊。</p>
              </div>
              <button type="button" onClick={() => onOpenProfile?.()}>去添加就诊卡 <Ico name="arrow_right.svg" /></button>
            </section>
            <p className="qh-kb-end">青禾门诊 · 让医疗更有温度</p>
          </>
        )}
        {message ? <p className="qh-vc-toast">{message}</p> : null}
      </div>
    </div>
  );
}

const CATEGORY_META: { id: string; label: string; blurb: string }[] = [
  { id: "all", label: "全部分类", blurb: "按疾病、症状、用药和检查浏览" },
  { id: "disease", label: "常见疾病", blurb: "了解常见疾病的症状与护理" },
  { id: "symptom", label: "症状表现", blurb: "各类症状的可能原因与应对建议" },
  { id: "drug", label: "用药指南", blurb: "药品使用方法与注意事项" },
  { id: "exam", label: "检查检验", blurb: "常见检查项目解读与准备事项" },
  { id: "health", label: "健康科普", blurb: "日常健康知识与生活方式" },
  { id: "emergency", label: "急救知识", blurb: "紧急情况处理与就医提醒" },
  { id: "tcm", label: "中医调理", blurb: "中医养生与调理、体质改善建议" }
];

function decorate(entries: Entry[]) {
  const next = entries.map((item) => {
    const category = categoryOfTitle(item.title);
    const meta = CATEGORY_META.find((row) => row.id === category);
    return { ...item, category, categoryLabel: meta?.label || item.categoryLabel, hot: false };
  });
  const hotIds = new Set<number>();
  for (const id of ["disease", "drug", "exam", "health"]) {
    const found = next.find((item) => item.category === id);
    if (found) hotIds.add(found.id);
  }
  return next.map((item) => ({ ...item, hot: hotIds.has(item.id) }));
}

function withCounts(entries: Entry[]): Category[] {
  return CATEGORY_META.map((item) => ({
    ...item,
    count: item.id === "all" ? entries.length : entries.filter((entry) => entry.category === item.id).length
  }));
}

function categoryOfTitle(title: string) {
  if (has(title, "中医", "经络", "体质", "调理")) return "tcm";
  if (has(title, "检查", "化验", "血常规", "检验")) return "exam";
  if (has(title, "中毒", "急救")) return "emergency";
  if (has(title, "用药", "退热药", "布洛芬", "抗生素的")) return "drug";
  if (has(title, "湿疹", "便秘", "口腔", "龋", "牙痛", "饮食", "免疫", "偏头痛")) return "health";
  if (has(title, "区别", "不要混", "咳嗽", "鼻炎", "风团")) return "symptom";
  return "disease";
}

function has(text: string, ...words: string[]) {
  return words.some((word) => text.includes(word));
}

function coverOf(category: string) {
  const known = ["disease", "symptom", "drug", "exam", "health", "emergency", "tcm"];
  return `/brand/kb-cover-${known.includes(category) ? category : "disease"}.svg`;
}

function readsLabel(id: number) {
  const count = 680 + (Math.abs(id || 1) % 19) * 90;
  return `${count} 次阅读`;
}

function SearchIcon({ light }: { light?: boolean }) {
  return (
    <svg className={light ? "qh-search light" : "qh-search"} viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="11" cy="11" r="6.5" fill="none" stroke="currentColor" strokeWidth="2.2" />
      <path d="M16 16.5 20 20.5" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
    </svg>
  );
}

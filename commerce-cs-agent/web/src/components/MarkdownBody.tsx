import ReactMarkdown from "react-markdown";
import { useState } from "react";

export type Evidence = {
  title: string;
  snippet?: string;
  sourceUrl?: string;
  channel?: string;
};

/** 把助手回复按 Markdown 渲染；纯文本会尽量拆成短段落。 */
export function MarkdownBody({ text, className }: { text: string; className?: string }) {
  const source = normalizeMarkdown(text);
  if (!source) {
    return null;
  }
  return (
    <div className={className ? `prose prose-cyan max-w-none ${className}` : "prose prose-cyan max-w-none"}>
      <ReactMarkdown
        components={{
          a: ({ href, children }) => (
            <a href={href} target="_blank" rel="noreferrer">
              {children}
            </a>
          )
        }}
      >
        {source}
      </ReactMarkdown>
    </div>
  );
}

/** DeepSeek 风格的小号来源角标：点开查看来源标题与链接。 */
export function SourceCites({ evidences }: { evidences?: Evidence[] | null }) {
  const items = (evidences ?? []).filter((item) => item.title || item.sourceUrl).slice(0, 3);
  const [open, setOpen] = useState<number | null>(null);
  if (items.length === 0) {
    return null;
  }
  const current = open == null ? null : items[open];
  return (
    <div className="not-prose mt-2">
      <div className="flex flex-wrap items-center gap-1.5">
        {items.map((item, index) => (
          <button
            key={`${item.title}-${index}`}
            type="button"
            title={item.title || "查看来源"}
            aria-label={`来源 ${index + 1}${item.title ? `：${item.title}` : ""}`}
            className={`inline-grid h-4 w-4 cursor-pointer place-items-center rounded-full text-[10px] font-medium leading-none text-white transition duration-150 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-tide ${
              open === index ? "bg-tide" : "bg-slate-400 hover:bg-slate-500"
            }`}
            onClick={() => setOpen((prev) => (prev === index ? null : index))}
          >
            {index + 1}
          </button>
        ))}
      </div>
      {current ? (
        <div className="mt-2 rounded-xl border border-cyan-100 bg-white px-3 py-2 text-sm text-ink shadow-sm">
          <p className="m-0 font-medium leading-6">{current.title || "参考来源"}</p>
          {current.snippet ? <p className="mt-1 m-0 text-xs leading-5 text-cyan-900/70">{current.snippet}</p> : null}
          {current.sourceUrl ? (
            <a
              href={current.sourceUrl}
              target="_blank"
              rel="noreferrer"
              className="mt-2 inline-flex text-xs text-tide underline-offset-2 hover:underline"
            >
              打开原文
            </a>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

export function normalizeMarkdown(text: string | null | undefined): string {
  if (!text) {
    return "";
  }
  const trimmed = text.trim();
  if (!trimmed) {
    return "";
  }
  if (/^#{1,3}\s|^\*\*|^\- |\n\n|> /.test(trimmed) || trimmed.includes("\n")) {
    return trimmed;
  }
  return trimmed.replace(/([。！？；])\s*/g, "$1\n\n").replace(/\n{3,}/g, "\n\n").trim();
}

export function cardToMarkdown(card: {
  redFlag?: boolean;
  degraded?: boolean;
  modeNote?: string;
  disclaimer?: string;
  plan?: string;
  diagnoses?: { name: string; summary: string }[];
  drugs?: { name: string; usage: string; caution: string }[];
  evidences?: Evidence[];
  memory?: string;
  chronicCaution?: string;
}): string {
  const parts: string[] = [];
  if (card.degraded) {
    parts.push("*模型未接入，这次按知识库规则整理。*");
  }
  for (const item of card.diagnoses ?? []) {
    parts.push(`### 参考方向：${item.name}\n\n${item.summary || ""}`);
  }
  if (card.plan && !card.redFlag) {
    parts.push(`### 处理建议\n\n${card.plan}`);
  }
  if (card.drugs && card.drugs.length > 0) {
    parts.push("### 可参考药品");
    for (const drug of card.drugs) {
      parts.push(`- **${drug.name}**：${drug.usage}  \n  注意：${drug.caution}`);
    }
  }
  if (card.chronicCaution) {
    parts.push(`> **长期病对照**  \n> ${card.chronicCaution}`);
  }
  if (card.disclaimer) {
    parts.push(`*${card.disclaimer}*`);
  }
  return parts.filter(Boolean).join("\n\n");
}

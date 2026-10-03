# -*- coding: utf-8 -*-
"""递归父子切片。参数按现有科普正文篇幅设定，不是默认套数。"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CHROMA_PATH = Path(__file__).resolve().parent / "chroma-data"
COLLECTION = "med_parent_child"

# 段落中位约 42 字、七成不超过 82 字，最长一段 363 字。
# 子块 200 字大约拼起 3 到 5 个短段，又不会把整节吞成一条。
# 父块按要求是子块的 4 倍。重叠都取块长的 20%，避免句子卡在切点上。
CHILD_SIZE = 200
CHILD_OVERLAP = 40
PARENT_SIZE = CHILD_SIZE * 4
PARENT_OVERLAP = CHILD_OVERLAP * 4

MODEL_NAME = "BAAI/bge-small-zh-v1.5"
QUERY_PREFIX = "为这个句子生成表示以用于检索相关文章："
SEPARATORS = ("\n## ", "\n\n", "\n", "。", "；", "，")

# 只有底账里已有的病种才写编码，避免向量把新文章误判成可开药的诊断。
DISEASE_CODE = {
    "01": "URI",
    "02": "URI",
    "03": "FLU",
    "04": "GASTRO",
    "05": "MIGRAINE",
    "06": "CHEST",
    "07": "URTICARIA",
    "08": "RHINITIS",
    "09": "HFMD",
    "10": "CONJUNCTIVITIS",
    "11": "FOOD",
    "12": "COUGH",
    "13": "ECZEMA",
    "14": "CONSTIPATION",
    "15": "APHTHOUS",
    "16": "CARIES",
}


def article_files():
    files = []
    for path in sorted(ROOT.glob("[0-9][0-9]-*.md")):
        if path.parent != ROOT or path.name.startswith("00-"):
            continue
        files.append(path)
    return files


def parse_article(path: Path):
    raw = path.read_text(encoding="utf-8")
    title = ""
    for line in raw.splitlines():
        if line.startswith("# "):
            title = line[2:].strip()
            break
    source_name = _field(raw, "来源机构")
    source_url = _field(raw, "原文链接")
    body = raw.split("\n---\n", 1)[-1].strip() if "\n---\n" in raw else raw
    body = re.sub(r"^# .+\n+", "", body).strip()
    if title:
        body = re.sub(rf"^{re.escape(title)}[？?]?\s*", "", body, count=1).strip()
        text = (title + "\n\n" + body).strip()
    else:
        text = body
    code = DISEASE_CODE.get(path.name[:2], "")
    return {
        "file": path.name,
        "title": title or path.stem,
        "source_name": source_name,
        "source_url": source_url,
        "disease_code": code,
        "text": text,
        "chars": len(re.sub(r"\s", "", text)),
    }


def _field(raw: str, name: str) -> str:
    match = re.search(rf"^- {name}：(.+)$", raw, re.M)
    return match.group(1).strip() if match else ""


def recursive_split(text: str, size: int, overlap: int, separators=SEPARATORS):
    text = text.strip()
    if not text:
        return []
    if len(text) <= size:
        return [text]
    for index, sep in enumerate(separators):
        if sep not in text:
            continue
        pieces = [piece.strip() for piece in _split_keep(text, sep) if piece.strip()]
        if len(pieces) <= 1:
            continue
        return _merge(pieces, size, overlap, separators[index + 1 :])
    return _hard_cut(text, size, overlap)


def _split_keep(text: str, sep: str):
    parts = text.split(sep)
    if sep in ("。", "；", "，"):
        out = []
        for index, part in enumerate(parts):
            out.append(part + sep if index < len(parts) - 1 else part)
        return out
    if sep == "\n## ":
        out = [parts[0]]
        out.extend("## " + part for part in parts[1:])
        return out
    out = [parts[0]]
    out.extend(sep + part for part in parts[1:])
    return out


def _merge(pieces, size, overlap, next_separators):
    chunks = []
    buf = ""
    for piece in pieces:
        if len(piece) > size:
            if buf:
                chunks.append(buf)
                buf = ""
            chunks.extend(recursive_split(piece, size, overlap, next_separators or SEPARATORS))
            continue
        candidate = piece if not buf else buf + piece
        if len(candidate) <= size:
            buf = candidate
            continue
        if buf:
            chunks.append(buf)
            tail = buf[-overlap:] if overlap else ""
            buf = tail + piece
            if len(buf) > size:
                buf = piece
        else:
            buf = piece
    if buf:
        chunks.append(buf)
    return [chunk.strip() for chunk in chunks if chunk.strip()]


def _hard_cut(text: str, size: int, overlap: int):
    step = max(1, size - overlap)
    chunks = []
    start = 0
    while start < len(text):
        piece = text[start : start + size].strip()
        if piece:
            chunks.append(piece)
        if start + size >= len(text):
            break
        start += step
    return chunks


def build_records():
    records = []
    articles = []
    for path in article_files():
        article = parse_article(path)
        articles.append(article)
        parents = recursive_split(article["text"], PARENT_SIZE, PARENT_OVERLAP)
        for parent_index, parent in enumerate(parents):
            parent_id = f"{path.stem}-p{parent_index}"
            children = absorb_tiny(recursive_split(parent, CHILD_SIZE, CHILD_OVERLAP), 30)
            for child_index, child in enumerate(children):
                records.append({
                    "id": f"{parent_id}-c{child_index}",
                    "document": child,
                    "metadata": {
                        "parent_id": parent_id,
                        "parent_text": parent,
                        "title": article["title"],
                        "source_name": article["source_name"],
                        "source_url": article["source_url"],
                        "disease_code": article["disease_code"],
                        "file": article["file"],
                    },
                })
    return articles, records


def absorb_tiny(chunks, limit):
    """过短的标题行并进后一块，避免单独拿来匹配。"""
    kept = []
    carry = ""
    for chunk in chunks:
        if len(chunk) < limit:
            carry = (carry + "\n" + chunk).strip() if carry else chunk
            continue
        if carry:
            chunk = carry + "\n" + chunk
            carry = ""
        kept.append(chunk)
    if carry:
        if kept:
            kept[-1] = kept[-1] + "\n" + carry
        else:
            kept.append(carry)
    return kept


def load_model():
    from fastembed import TextEmbedding
    return TextEmbedding(MODEL_NAME)


def embed_passages(model, texts):
    return [vector.tolist() for vector in model.embed(texts)]


def embed_query(model, text):
    vector = next(iter(model.embed([QUERY_PREFIX + text])))
    return vector.tolist()

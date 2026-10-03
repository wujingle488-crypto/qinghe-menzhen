# -*- coding: utf-8 -*-
"""按线上三路检索 + RRF 计算 Recall、MRR、NDCG、HitRate。"""
import json
import math
import urllib.request
from pathlib import Path

from neo4j import GraphDatabase

OUT = Path(__file__).resolve().parent / "eval_rag_result.json"
CHROMA = "http://127.0.0.1:8000"
EMBED = "http://127.0.0.1:8001"
COLLECTION = "med_parent_child"
RRF_K = 60
KS = (1, 3, 5)

# 与 KeywordRanker / MedicalSeed 一致。病名整句命中 +3，症状词 +2。
LEDGER = [
    ("URI", "普通感冒样上呼吸道感染", "喉咙痛,咽痛,低烧,流涕,鼻塞,咳嗽,打喷嚏"),
    ("FLU", "流感样症状", "高烧,寒战,全身酸痛,流感,乏力明显"),
    ("URTICARIA", "荨麻疹样过敏", "风团,荨麻疹,很痒,皮疹,过敏"),
    ("GASTRO", "急性胃肠炎样", "拉肚子,腹泻,呕吐,水样便,胃肠炎,肚子疼"),
    ("MIGRAINE", "偏头痛样头痛", "偏头痛,一侧头痛,怕光,头痛"),
]

GRAPH_SEARCH = """
MATCH (s:Symptom)
WHERE $q CONTAINS s.name AND size(s.name) >= 2
MATCH (d:Disease)-[:HAS_SYMPTOM]->(s)
WITH d, count(DISTINCT s) AS hits
RETURN d.code AS code
ORDER BY hits DESC, d.code ASC
LIMIT 8
"""

# rel: 2 主目标，1 症状确实相关但不是最贴切的资料。0 不写入。
CASES = [
    {
        "id": "cold",
        "name": "普通感冒",
        "relevant": {"URI": 2},
        "queries": ["喉咙痛，低烧两天", "嗓子疼，有点低热，还流鼻涕", "鼻塞咽痛，烧得不高"],
    },
    {
        "id": "flu",
        "name": "流感",
        "relevant": {"FLU": 2, "URI": 1},
        "queries": ["高烧三十九度，全身酸痛，特别没力气", "发高烧还寒战，骨头疼", "像流感，全身症状很重"],
    },
    {
        "id": "rhinitis",
        "name": "过敏性鼻炎",
        "relevant": {"RHINITIS": 2, "URI": 1},
        "queries": ["春天花粉一来就连续打喷嚏，流清水鼻涕，不发烧", "鼻子痒，一低头就流清水，没有发热", "过敏性鼻炎，清水样鼻涕，不是感冒那种发烧"],
    },
    {
        "id": "urticaria",
        "name": "荨麻疹",
        "relevant": {"URTICARIA": 2},
        "queries": ["身上起了一片风团，很痒", "皮肤突然出一片痒包，过几小时又消了", "起了风疙瘩，又痒又来去很快"],
    },
    {
        "id": "gastro",
        "name": "病毒性胃肠炎",
        "relevant": {"GASTRO": 2, "FOOD": 1},
        "queries": ["上吐下泻，拉水样便", "拉肚子还吐，担心脱水", "像诺如那样又吐又拉"],
    },
    {
        "id": "food",
        "name": "食物中毒",
        "relevant": {"FOOD": 2, "GASTRO": 1},
        "queries": ["吃了剩菜两小时后开始吐和拉肚子，同桌的人也这样", "怀疑食物中毒，一起吃饭的人都难受", "剩饭没热透，吃完腹痛腹泻"],
    },
    {
        "id": "migraine",
        "name": "偏头痛",
        "relevant": {"MIGRAINE": 2},
        "queries": ["一边太阳穴跳着疼，怕光，还想吐", "偏头痛，怕光怕吵", "一侧头痛一跳一跳的，头疼"],
    },
    {
        "id": "hfmd",
        "name": "手足口病",
        "relevant": {"HFMD": 2},
        "queries": ["小朋友手上和口腔里有疱疹", "幼儿园有人手足口，孩子手心和嘴里起疱", "孩子手上有疱疹，还发烧"],
    },
    {
        "id": "eye",
        "name": "红眼病",
        "relevant": {"CONJUNCTIVITIS": 2},
        "queries": ["眼睛又红又肿，同学也得了红眼病", "红眼病，眼红还流泪", "结膜炎，眼睛红"],
    },
    {
        "id": "cough",
        "name": "婴幼儿咳嗽",
        "relevant": {"COUGH": 2, "URI": 1},
        "queries": ["宝宝咳嗽四五天了，没有发烧，精神还好", "婴幼儿咳嗽，不发热", "小孩咳了好几天，精神不错"],
    },
    {
        "id": "chest",
        "name": "胸痛警示",
        "relevant": {"CHEST": 2},
        "queries": ["突然胸口疼，出冷汗", "胸痛还冒冷汗", "胸口像心脏病那样疼"],
    },
]


def post_json(url, payload):
    data = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def get_json(url):
    with urllib.request.urlopen(url, timeout=15) as response:
        return json.load(response)


def keyword_rank(query):
    text = query.replace(" ", "")
    scores = {}
    for code, name, aliases in LEDGER:
        score = 3 if name and name in text else 0
        scores[code] = score
        for token in aliases.split(","):
            token = token.strip()
            if len(token) >= 2 and token in text:
                scores[code] = scores.get(code, 0) + 2
    ranked = [(code, score) for code, score in scores.items() if score > 0]
    ranked.sort(key=lambda item: (-item[1], item[0]))
    return [code for code, _score in ranked[:5]]


def vector_rank(query, collection_id):
    embedded = post_json(EMBED + "/embed", {"text": query.strip()})
    url = (
        f"{CHROMA}/api/v2/tenants/default_tenant/databases/default_database"
        f"/collections/{collection_id}/query"
    )
    result = post_json(url, {
        "query_embeddings": [embedded["vector"]],
        "n_results": 8,
        "include": ["metadatas", "distances"],
    })
    metadatas = (result.get("metadatas") or [[]])[0]
    seen_parents = set()
    codes = []
    seen_codes = set()
    for meta in metadatas:
        if not isinstance(meta, dict):
            continue
        parent_id = meta.get("parent_id") or ""
        if parent_id and parent_id in seen_parents:
            continue
        if parent_id:
            seen_parents.add(parent_id)
        code = (meta.get("disease_code") or "").strip()
        if not code or code in seen_codes:
            continue
        seen_codes.add(code)
        codes.append(code)
        if len(codes) >= 8:
            break
    return codes


def graph_rank(session, query):
    text = query.replace(" ", "").replace("\n", "")
    rows = session.run(GRAPH_SEARCH, q=text)
    return [row["code"] for row in rows]


def rrf(rankings):
    scores = {}
    for ranking in rankings:
        seen = set()
        rank = 1
        for key in ranking:
            if not key or key in seen:
                continue
            seen.add(key)
            scores[key] = scores.get(key, 0.0) + 1.0 / (RRF_K + rank)
            rank += 1
    ordered = sorted(scores.items(), key=lambda item: (-item[1], item[0]))
    return [key for key, _score in ordered]


def recall_at(ranking, relevant, k):
    if not relevant:
        return 0.0
    hit = sum(1 for code in ranking[:k] if code in relevant)
    return hit / len(relevant)


def hit_at(ranking, relevant, k):
    return 1.0 if any(code in relevant for code in ranking[:k]) else 0.0


def mrr(ranking, relevant):
    for index, code in enumerate(ranking, start=1):
        if code in relevant:
            return 1.0 / index
    return 0.0


def ndcg_at(ranking, relevant, k):
    gains = [relevant.get(code, 0) for code in ranking[:k]]
    dcg = sum((2 ** rel - 1) / math.log2(index + 2) for index, rel in enumerate(gains))
    ideal = sorted(relevant.values(), reverse=True)[:k]
    idcg = sum((2 ** rel - 1) / math.log2(index + 2) for index, rel in enumerate(ideal))
    if idcg == 0:
        return 0.0
    return dcg / idcg


def mean(values):
    return sum(values) / len(values) if values else 0.0


def main():
    info = get_json(
        f"{CHROMA}/api/v2/tenants/default_tenant/databases/default_database/collections/{COLLECTION}"
    )
    collection_id = info["id"]
    driver = GraphDatabase.driver("bolt://127.0.0.1:7687", auth=None)
    rows = []
    with driver.session() as session:
        for case in CASES:
            for query in case["queries"]:
                channels = {
                    "关键词": keyword_rank(query),
                    "向量": vector_rank(query, collection_id),
                    "图谱": graph_rank(session, query),
                }
                fused = rrf(channels.values())
                relevant = case["relevant"]
                record = {
                    "intent": case["id"],
                    "name": case["name"],
                    "query": query,
                    "relevant": relevant,
                    "channels": channels,
                    "fused": fused[:8],
                    "mrr": mrr(fused, relevant),
                }
                for k in KS:
                    record[f"recall@{k}"] = recall_at(fused, relevant, k)
                    record[f"hit@{k}"] = hit_at(fused, relevant, k)
                    record[f"ndcg@{k}"] = ndcg_at(fused, relevant, k)
                rows.append(record)
    driver.close()

    summary = {"queries": len(rows), "intents": len(CASES), "k": list(KS), "rrf_k": RRF_K}
    for metric in ("recall", "hit", "ndcg"):
        for k in KS:
            summary[f"{metric}@{k}"] = mean([row[f"{metric}@{k}"] for row in rows])
    summary["mrr"] = mean([row["mrr"] for row in rows])

    by_intent = []
    for case in CASES:
        group = [row for row in rows if row["intent"] == case["id"]]
        item = {"id": case["id"], "name": case["name"], "queries": len(group)}
        item["mrr"] = mean([row["mrr"] for row in group])
        for metric in ("recall", "hit", "ndcg"):
            for k in KS:
                item[f"{metric}@{k}"] = mean([row[f"{metric}@{k}"] for row in group])
        misses = [
            {"query": row["query"], "fused": row["fused"][:5], "channels": row["channels"]}
            for row in group
            if row["hit@3"] < 1 or row["recall@3"] < 1 or row["mrr"] < 1
        ]
        item["weak"] = misses
        by_intent.append(item)

    payload = {"summary": summary, "intents": by_intent, "rows": rows}
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

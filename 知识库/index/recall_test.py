# -*- coding: utf-8 -*-
"""按线上路径测父块召回：子块匹配，parent_id 去重，取 Top3。"""
import json
import urllib.error
import urllib.request
from pathlib import Path

OUT = Path(__file__).resolve().parent / "recall_result.json"
CHROMA = "http://127.0.0.1:8000"
EMBED = "http://127.0.0.1:8001"
COLLECTION = "med_parent_child"

CASES = [
    {
        "id": "flu-systemic",
        "intent": "流感样全身症状",
        "primary": "03",
        "acceptable": ["01", "02", "03"],
        "queries": [
            "高烧到三十九度，浑身酸痛，特别没力气",
            "烧得很厉害，骨头缝都疼，一点劲都没有",
            "体温上去了，全身又酸又软，起不来床",
        ],
    },
    {
        "id": "cold-mild",
        "intent": "普通感冒样上呼吸道不适",
        "primary": "01",
        "acceptable": ["01", "02"],
        "queries": [
            "嗓子疼，流鼻涕，鼻塞，人倒还行",
            "喉咙不舒服，鼻子堵着，有鼻涕，没有浑身难受",
            "就觉得咽部疼、鼻子不通，精神还好",
        ],
    },
    {
        "id": "flu-vs-cold",
        "intent": "区分流感和普通感冒",
        "primary": "01",
        "acceptable": ["01", "02", "03"],
        "queries": [
            "怎么判断是流感还是只是着凉了",
            "家里人说我这是流感，可我分不清和感冒有啥不一样",
            "想知道重感冒和流感在表现上差在哪",
        ],
    },
    {
        "id": "spring-virus",
        "intent": "换季呼吸道病毒感染",
        "primary": "02",
        "acceptable": ["01", "02", "12"],
        "queries": [
            "开春以后孩子老咳嗽，不知道是哪种呼吸道的问题",
            "天气刚暖，小朋友反复咳，是不是换季容易中招",
            "春天大人小孩轮流嗓子不舒服，会不会好几种病毒一起流行",
        ],
    },
    {
        "id": "norovirus",
        "intent": "集体呕吐腹泻",
        "primary": "04",
        "acceptable": ["04"],
        "queries": [
            "幼儿园好几个孩子一起吐，拉得很稀",
            "班里一下子好几个人上吐下泻",
            "托班小朋友集体闹肚子，吐得厉害",
        ],
    },
    {
        "id": "food-poison",
        "intent": "饭后急性胃肠不适",
        "primary": "11",
        "acceptable": ["11", "04"],
        "queries": [
            "吃了隔夜菜，过了两个小时开始吐，一起吃饭的人也拉肚子",
            "晚饭后不久就恶心吐了，同桌的人也肚子不舒服",
            "剩饭热了一下还是吃坏了，腹痛还拉肚子",
        ],
    },
    {
        "id": "migraine",
        "intent": "一侧搏动性头痛伴畏光恶心",
        "primary": "05",
        "acceptable": ["05"],
        "queries": [
            "一边太阳穴一跳一跳地疼，见光就难受，还想吐",
            "半边头痛得厉害，吵和亮都受不了，胃里翻",
            "眼睛后面抽着疼，活动一下更重，还恶心",
        ],
    },
    {
        "id": "chest-pain",
        "intent": "突发胸痛伴冷汗",
        "primary": "06",
        "acceptable": ["06"],
        "queries": [
            "突然胸口闷着疼，直冒冷汗",
            "心口一阵疼，汗一下子出来了，脸色发白",
            "左边胸口不舒服，还喘不上气",
        ],
    },
    {
        "id": "urticaria",
        "intent": "突发瘙痒性风团样皮疹",
        "primary": "07",
        "acceptable": ["07"],
        "queries": [
            "身上突然起了一片很痒的包，过几小时又消了",
            "皮肤冒出一块一块的，痒得受不了，不一会儿又平了",
            "满身风疙瘩，又痒又消",
        ],
    },
    {
        "id": "rhinitis",
        "intent": "季节性喷嚏清涕且不发烧",
        "primary": "08",
        "acceptable": ["08"],
        "queries": [
            "一到春天就连着打喷嚏，流清水鼻涕，不发烧",
            "花粉季鼻子痒，喷嚏停不下来，人倒不烧",
            "每年换季鼻子就像水龙头，连续打好几个喷嚏",
        ],
    },
    {
        "id": "hfmd",
        "intent": "儿童手口腔疱疹伴发热",
        "primary": "09",
        "acceptable": ["09"],
        "queries": [
            "小孩手上和嘴里起了小水疱，还发热",
            "宝宝手心脚心有疹子，嘴里也破了，烧了一下",
            "幼儿园的孩子手上长疱，口腔里也有",
        ],
    },
    {
        "id": "hfmd-severe",
        "intent": "疱疹后精神差、手脚凉",
        "primary": "09",
        "acceptable": ["09", "06"],
        "queries": [
            "孩子手上长疱之后变得没精神，手脚凉，还出冷汗",
            "疹子出来以后孩子蔫了，手脚发凉，一直出汗",
            "口腔和手上有疱的小朋友突然萎靡，末梢发凉",
        ],
    },
    {
        "id": "pinkeye",
        "intent": "传染性眼红流泪",
        "primary": "10",
        "acceptable": ["10"],
        "queries": [
            "眼睛又红又怕光，还流泪，同学也得了",
            "一只眼红肿，觉得里面有沙子，班里好几个人一样",
            "游泳之后眼睛红、流泪，传染得很快",
        ],
    },
    {
        "id": "infant-cough",
        "intent": "婴幼儿轻咳不发烧",
        "primary": "12",
        "acceptable": ["12", "02"],
        "queries": [
            "宝宝咳嗽四五天了，不发烧，吃喝都正常",
            "小孩就干咳，精神好，要不要先在家看看",
            "孩子晚上咳，白天还行，没有发热",
        ],
    },
]


def post(url, payload):
    data = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.loads(response.read().decode("utf-8"))


def get(url):
    with urllib.request.urlopen(url, timeout=10) as response:
        return json.loads(response.read().decode("utf-8"))


def service_up():
    try:
        get(CHROMA + "/api/v2/heartbeat")
        get(EMBED + "/health")
        return True
    except Exception:
        return False


def embed_http(text):
    body = post(EMBED + "/embed", {"text": text})
    return body["vector"]


def collection_id():
    body = get(
        CHROMA
        + "/api/v2/tenants/default_tenant/databases/default_database/collections/"
        + COLLECTION
    )
    return body["id"]


def query_http(vector, collection):
    url = (
        CHROMA
        + "/api/v2/tenants/default_tenant/databases/default_database/collections/"
        + collection
        + "/query"
    )
    return post(url, {
        "query_embeddings": [vector],
        "n_results": 8,
        "include": ["metadatas", "documents", "distances"],
    })


def local_search():
    import chromadb
    from kb_common import CHROMA_PATH, COLLECTION as NAME, embed_query, load_model

    client = chromadb.PersistentClient(path=str(CHROMA_PATH))
    collection = client.get_collection(NAME)
    model = load_model()

    def search(text):
        vector = embed_query(model, text)
        return collection.query(query_embeddings=[vector], n_results=8, include=["metadatas", "documents", "distances"])

    return search


def parents_of(response):
    metadatas = response["metadatas"][0]
    distances = response["distances"][0]
    seen = []
    found = set()
    for meta, distance in zip(metadatas, distances):
        parent_id = meta.get("parent_id") or ""
        if parent_id in found:
            continue
        found.add(parent_id)
        filename = meta.get("file") or ""
        seen.append({
            "file": filename[:2],
            "name": filename,
            "title": meta.get("title") or "",
            "parent_id": parent_id,
            "distance": round(float(distance), 3),
        })
        if len(seen) == 3:
            break
    return seen


def main():
    online = service_up()
    mode = "http"
    search = None
    collection = None
    if online:
        collection = collection_id()
    else:
        mode = "local"
        search = local_search()

    rows = []
    for case in CASES:
        for index, query in enumerate(case["queries"], start=1):
            if online:
                response = query_http(embed_http(query), collection)
            else:
                response = search(query)
            hits = parents_of(response)
            files = [item["file"] for item in hits]
            primary = case["primary"]
            acceptable = set(case["acceptable"])
            rows.append({
                "id": case["id"],
                "intent": case["intent"],
                "primary": primary,
                "acceptable": case["acceptable"],
                "variant": index,
                "query": query,
                "hits": hits,
                "hit1": bool(files) and files[0] == primary,
                "hit3": primary in files,
                "accept3": any(item in acceptable for item in files),
            })

    intents = []
    for case in CASES:
        group = [row for row in rows if row["id"] == case["id"]]
        n = len(group)
        intents.append({
            "id": case["id"],
            "intent": case["intent"],
            "primary": case["primary"],
            "acceptable": case["acceptable"],
            "n": n,
            "hit1": round(sum(row["hit1"] for row in group) / n, 4),
            "hit3": round(sum(row["hit3"] for row in group) / n, 4),
            "accept3": round(sum(row["accept3"] for row in group) / n, 4),
            "covered": any(row["hit3"] for row in group),
            "accept_covered": any(row["accept3"] for row in group),
        })

    summary = {
        "mode": mode,
        "intents": len(intents),
        "queries": len(rows),
        "macro_hit1": round(sum(item["hit1"] for item in intents) / len(intents), 4),
        "macro_hit3": round(sum(item["hit3"] for item in intents) / len(intents), 4),
        "macro_accept3": round(sum(item["accept3"] for item in intents) / len(intents), 4),
        "micro_hit1": round(sum(row["hit1"] for row in rows) / len(rows), 4),
        "micro_hit3": round(sum(row["hit3"] for row in rows) / len(rows), 4),
        "micro_accept3": round(sum(row["accept3"] for row in rows) / len(rows), 4),
        "intent_coverage": round(sum(item["covered"] for item in intents) / len(intents), 4),
        "accept_intent_coverage": round(sum(item["accept_covered"] for item in intents) / len(intents), 4),
    }
    OUT.write_text(json.dumps({"summary": summary, "intents": intents, "rows": rows}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    main()

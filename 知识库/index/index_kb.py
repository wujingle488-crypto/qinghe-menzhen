# -*- coding: utf-8 -*-
"""把知识文档切成父子块，用 bge-small-zh-v1.5 写入本地 Chroma。"""
import json
from pathlib import Path

import chromadb

from kb_common import (
    CHILD_OVERLAP,
    CHILD_SIZE,
    CHROMA_PATH,
    COLLECTION,
    MODEL_NAME,
    PARENT_OVERLAP,
    PARENT_SIZE,
    build_records,
    embed_passages,
    load_model,
)

OUT = Path(__file__).resolve().parent / "chunks.json"


def main():
    articles, records = build_records()
    payload = {
        "model": MODEL_NAME,
        "child_size": CHILD_SIZE,
        "child_overlap": CHILD_OVERLAP,
        "parent_size": PARENT_SIZE,
        "parent_overlap": PARENT_OVERLAP,
        "article_count": len(articles),
        "article_chars": sum(item["chars"] for item in articles),
        "parent_count": len({item["metadata"]["parent_id"] for item in records}),
        "child_count": len(records),
        "articles": [
            {"file": item["file"], "chars": item["chars"], "title": item["title"]}
            for item in articles
        ],
        "chunks": [
            {
                "id": item["id"],
                "parent_id": item["metadata"]["parent_id"],
                "chars": len(item["document"]),
                "parent_chars": len(item["metadata"]["parent_text"]),
                "text": item["document"],
            }
            for item in records
        ],
    }
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(
        f"articles={payload['article_count']} chars={payload['article_chars']} "
        f"parents={payload['parent_count']} children={payload['child_count']}"
    )

    print("loading", MODEL_NAME)
    model = load_model()
    vectors = embed_passages(model, [item["document"] for item in records])
    dim = len(vectors[0]) if vectors else 0
    print("dim", dim)

    CHROMA_PATH.mkdir(parents=True, exist_ok=True)
    client = chromadb.PersistentClient(path=str(CHROMA_PATH))
    try:
        client.delete_collection(COLLECTION)
    except Exception:
        pass
    collection = client.get_or_create_collection(
        name=COLLECTION,
        metadata={"hnsw:space": "cosine"},
    )
    collection.add(
        ids=[item["id"] for item in records],
        documents=[item["document"] for item in records],
        metadatas=[item["metadata"] for item in records],
        embeddings=vectors,
    )
    print("chroma", collection.count(), "at", CHROMA_PATH)


if __name__ == "__main__":
    main()

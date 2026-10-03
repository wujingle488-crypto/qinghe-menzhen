package com.commerce.cs.server.medical;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
public class RagProperties {
    private boolean esEnabled = false;
    private String esUrl = "http://127.0.0.1:9200";
    private boolean qdrantEnabled = false;
    private String qdrantUrl = "http://127.0.0.1:6333";
    private boolean chromaEnabled = true;
    private String chromaUrl = "http://127.0.0.1:8000";
    private String embedUrl = "http://127.0.0.1:8001";
    private String chromaCollection = "med_parent_child";
    private boolean neo4jEnabled = true;
    private String neo4jUrl = "bolt://127.0.0.1:7687";
    private int rrfK = 60;

    public boolean isEsEnabled() { return esEnabled; }
    public void setEsEnabled(boolean esEnabled) { this.esEnabled = esEnabled; }
    public String getEsUrl() { return esUrl; }
    public void setEsUrl(String esUrl) { this.esUrl = esUrl; }
    public boolean isQdrantEnabled() { return qdrantEnabled; }
    public void setQdrantEnabled(boolean qdrantEnabled) { this.qdrantEnabled = qdrantEnabled; }
    public String getQdrantUrl() { return qdrantUrl; }
    public void setQdrantUrl(String qdrantUrl) { this.qdrantUrl = qdrantUrl; }
    public boolean isChromaEnabled() { return chromaEnabled; }
    public void setChromaEnabled(boolean chromaEnabled) { this.chromaEnabled = chromaEnabled; }
    public String getChromaUrl() { return chromaUrl; }
    public void setChromaUrl(String chromaUrl) { this.chromaUrl = chromaUrl; }
    public String getEmbedUrl() { return embedUrl; }
    public void setEmbedUrl(String embedUrl) { this.embedUrl = embedUrl; }
    public String getChromaCollection() { return chromaCollection; }
    public void setChromaCollection(String chromaCollection) { this.chromaCollection = chromaCollection; }
    public boolean isNeo4jEnabled() { return neo4jEnabled; }
    public void setNeo4jEnabled(boolean neo4jEnabled) { this.neo4jEnabled = neo4jEnabled; }
    public String getNeo4jUrl() { return neo4jUrl; }
    public void setNeo4jUrl(String neo4jUrl) { this.neo4jUrl = neo4jUrl; }
    public int getRrfK() { return rrfK; }
    public void setRrfK(int rrfK) { this.rrfK = rrfK; }
}

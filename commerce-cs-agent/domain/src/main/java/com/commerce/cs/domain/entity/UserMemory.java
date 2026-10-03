package com.commerce.cs.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "cs_user_memory")
public class UserMemory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "disease_code", length = 32)
    private String diseaseCode;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "base_importance", nullable = false)
    private double baseImportance;

    @Column(nullable = false)
    private double importance;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    @Column(columnDefinition = "LONGTEXT")
    private String embedding;

    /** 逗号分隔的来源会话号。空表示旧数据，删除会话时不连带清除。 */
    @Column(name = "source_session_ids", length = 200)
    private String sourceSessionIds;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getDiseaseCode() { return diseaseCode; }
    public void setDiseaseCode(String diseaseCode) { this.diseaseCode = diseaseCode; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public double getBaseImportance() { return baseImportance; }
    public void setBaseImportance(double baseImportance) { this.baseImportance = baseImportance; }
    public double getImportance() { return importance; }
    public void setImportance(double importance) { this.importance = importance; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getLastAccessedAt() { return lastAccessedAt; }
    public void setLastAccessedAt(LocalDateTime lastAccessedAt) { this.lastAccessedAt = lastAccessedAt; }
    public String getEmbedding() { return embedding; }
    public void setEmbedding(String embedding) { this.embedding = embedding; }
    public String getSourceSessionIds() { return sourceSessionIds; }
    public void setSourceSessionIds(String sourceSessionIds) { this.sourceSessionIds = sourceSessionIds; }
}

package com.commerce.cs.domain.entity;

import com.commerce.cs.domain.ConsultTaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "cs_consult_task")
public class ConsultTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "buyer_id")
    private Long buyerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ConsultTaskStatus status = ConsultTaskStatus.RUNNING;

    /** 意图锚点：本轮用户主诉摘要，模型不能单独改掉。 */
    @Column(name = "core_intent", length = 240)
    private String coreIntent = "";

    @Column(name = "last_content", length = 900)
    private String lastContent = "";

    @Column(name = "current_node", length = 64)
    private String currentNode = "";

    @Column(name = "completed_nodes", columnDefinition = "LONGTEXT")
    private String completedNodes = "[]";

    @Column(name = "applied_keys", columnDefinition = "LONGTEXT")
    private String appliedKeys = "[]";

    @Column(name = "resume_node", length = 64)
    private String resumeNode = "";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }
    public ConsultTaskStatus getStatus() { return status; }
    public void setStatus(ConsultTaskStatus status) { this.status = status; }
    public String getCoreIntent() { return coreIntent; }
    public void setCoreIntent(String coreIntent) { this.coreIntent = coreIntent; }
    public String getLastContent() { return lastContent; }
    public void setLastContent(String lastContent) { this.lastContent = lastContent; }
    public String getCurrentNode() { return currentNode; }
    public void setCurrentNode(String currentNode) { this.currentNode = currentNode; }
    public String getCompletedNodes() { return completedNodes; }
    public void setCompletedNodes(String completedNodes) { this.completedNodes = completedNodes; }
    public String getAppliedKeys() { return appliedKeys; }
    public void setAppliedKeys(String appliedKeys) { this.appliedKeys = appliedKeys; }
    public String getResumeNode() { return resumeNode; }
    public void setResumeNode(String resumeNode) { this.resumeNode = resumeNode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

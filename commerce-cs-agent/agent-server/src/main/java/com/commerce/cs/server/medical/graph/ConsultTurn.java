package com.commerce.cs.server.medical.graph;

import com.commerce.cs.domain.entity.ChatSession;
import com.commerce.cs.domain.entity.MedDisease;
import com.commerce.cs.domain.entity.MedDrug;
import com.commerce.cs.domain.service.ConsultIntake;
import com.commerce.cs.domain.service.SafetyGate;
import com.commerce.cs.domain.service.SessionMemory;
import com.commerce.cs.server.medical.ConsultOrchestrator.Step;
import com.commerce.cs.server.medical.RagFacade;
import com.commerce.cs.server.medical.UserMemoryService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 单次问诊图状态。节点间通过这个可变上下文传递，门禁与检索路由由固定边决定。 */
public final class ConsultTurn {
    public static final String ROUTE_BLOCKED = "blocked";
    public static final String ROUTE_ASK = "ask";
    public static final String ROUTE_READY = "ready";
    public static final String ROUTE_DONE = "done";

    public Long sessionId;
    public ChatSession session;
    public Long taskId;
    /** 恢复模式：不再重复写入用户消息。 */
    public boolean resumeMode;
    public String content = "";
    public String history = "";
    public List<Step> steps = new ArrayList<>();
    public SessionMemory memory;
    public boolean memoryFresh;
    public UserMemoryService.View longTerm;
    public SafetyGate.Hit redFlag;
    public ConsultIntake.Slots slots;
    public String route = ROUTE_DONE;
    public String reply = "";
    public Map<String, Object> card;
    public RagFacade.Bundle bundle;
    public List<RagFacade.Hit> webHits = List.of();
    public boolean webSearch;
    /** 模型判断这次可以直接回答，不按症状槽位追问，也不据此下诊断。 */
    public boolean directAnswer;
    /** 这一轮的追问或收口是模型根据原话推断的，不再用词表缺口给回答加「还没说到」。 */
    public boolean intakeByModel;
    /** 用户在问自己或就诊卡上的资料。 */
    public boolean aboutSelf;
    /** 本条消息是否带着已关联的就诊卡；null 表示沿用会话里记下的状态（例如断点恢复）。 */
    public Boolean useProfile;
    public MedDisease disease;
    public List<MedDrug> diseaseDrugs = List.of();
    public List<String> proposed = List.of();
    public List<MedDrug> kept = List.of();
    public boolean llmUsed;
    public boolean thin;
    public String uncoveredGeneral = "";
    public boolean uncovered;
}

package com.commerce.cs.server.medical.graph;

import com.commerce.cs.domain.entity.ChatMessage;
import com.commerce.cs.domain.entity.ChatSession;
import com.commerce.cs.domain.entity.MedDisease;
import com.commerce.cs.domain.entity.MedDrug;
import com.commerce.cs.domain.entity.TraceStep;
import com.commerce.cs.domain.repo.ChatMessageRepository;
import com.commerce.cs.domain.repo.ChatSessionRepository;
import com.commerce.cs.domain.repo.TraceRepository;
import com.commerce.cs.domain.service.ConsultIntake;
import com.commerce.cs.domain.service.SessionMemory;
import com.commerce.cs.server.llm.DeepSeekClient;
import com.commerce.cs.server.medical.ConsultMemoryStore;
import com.commerce.cs.server.medical.ConsultOrchestrator.Step;
import com.commerce.cs.server.medical.ConsultTaskStore;
import com.commerce.cs.server.medical.HealthProfileService;
import com.commerce.cs.server.medical.KnowledgeLedger;
import com.commerce.cs.server.medical.RagFacade;
import com.commerce.cs.server.medical.UserMemoryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 图节点共用的记忆/草稿/trace 能力；药白名单与红旗仍走 SafetyGate，不由模型决定是否执行。 */
@Component
public class ConsultSupport {
    static final String DISCLAIMER = "以上为教学参考，不是确诊，也不是处方。用药请以说明书和医师意见为准。";
    private static final List<String> BANNED = List.of("奥司他韦", "阿莫西林", "头孢", "地塞米松", "泼尼松", "左氧氟沙星");

    public static String asMarkdownCaution(String caution) {
        if (caution == null || caution.isBlank()) {
            return "";
        }
        return "> **长期病对照**  \n> " + caution.trim().replace("\n", "  \n> ") + "\n\n";
    }

    public static String ensureMarkdown(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.contains("\n") || trimmed.contains("## ") || trimmed.contains("- ") || trimmed.contains("> ")) {
            return trimmed;
        }
        return trimmed.replace("。", "。\n\n").replaceAll("\n{3,}", "\n\n").trim();
    }

    private final KnowledgeLedger ledger;
    private final ConsultMemoryStore memoryStore;
    private final ConsultTaskStore taskStore;
    private final UserMemoryService userMemories;
    private final HealthProfileService healthProfiles;
    private final DeepSeekClient llm;
    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final TraceRepository traces;
    private final ObjectMapper objectMapper;

    public ConsultSupport(KnowledgeLedger ledger, ConsultMemoryStore memoryStore, ConsultTaskStore taskStore,
                          UserMemoryService userMemories, HealthProfileService healthProfiles, DeepSeekClient llm,
                          ChatSessionRepository sessions, ChatMessageRepository messages, TraceRepository traces,
                          ObjectMapper objectMapper) {
        this.ledger = ledger;
        this.memoryStore = memoryStore;
        this.taskStore = taskStore;
        this.userMemories = userMemories;
        this.healthProfiles = healthProfiles;
        this.llm = llm;
        this.sessions = sessions;
        this.messages = messages;
        this.traces = traces;
        this.objectMapper = objectMapper;
    }

    public KnowledgeLedger ledger() {
        return ledger;
    }

    public DeepSeekClient llm() {
        return llm;
    }

    public UserMemoryService userMemories() {
        return userMemories;
    }

    public HealthProfileService healthProfiles() {
        return healthProfiles;
    }

    public ConsultTaskStore tasks() {
        return taskStore;
    }

    public void checkpoint(ConsultTurn turn, String node) {
        taskStore.checkpoint(turn.taskId, node);
    }

    public boolean tryApply(ConsultTurn turn, String action) {
        return taskStore.tryApply(turn.taskId, turn.taskId + ":" + action);
    }

    public void openMemory(ConsultTurn turn) {
        ConsultMemoryStore.Held held = memoryStore.open(turn.sessionId);
        turn.memory = held.memory();
        turn.memoryFresh = held.fresh();
        if (held.fresh()) {
            turn.memory.askCount = countAsks(turn.sessionId);
        }
        if (!turn.resumeMode) {
            turn.memory.supplement(turn.content);
            turn.memory.pushTurn("用户", turn.content);
            if (Boolean.FALSE.equals(turn.useProfile)) {
                turn.memory.profileLinked = false;
                turn.memory.profileId = null;
            }
        }
    }

    public Long linkedProfileId(Long sessionId) {
        SessionMemory memory = memoryStore.open(sessionId).memory();
        return memory.profileLinked ? memory.profileId : null;
    }

    public void linkProfile(Long sessionId, Long profileId) {
        ConsultMemoryStore.Held held = memoryStore.open(sessionId);
        SessionMemory memory = held.memory();
        if (held.fresh()) {
            memory.askCount = countAsks(sessionId);
        }
        memory.profileId = profileId;
        memory.profileLinked = profileId != null;
        memoryStore.save(sessionId, memory);
    }

    /** 关联了就诊卡才给模型看摘要；摘要读失败或为空时按普通问诊走。 */
    public String profileContext(SessionMemory memory, Long buyerId) {
        if (memory == null || !memory.profileLinked || memory.profileId == null || buyerId == null) {
            return "";
        }
        try {
            String context = healthProfiles.consultContext(buyerId, memory.profileId);
            return context == null ? "" : context;
        } catch (Exception ex) {
            return "";
        }
    }

    /**
     * 信息不齐时的追问。模型可用就让它按用户原话问缺的那几项，不分析、不给药；
     * 模型不可用、超时或越界时用规则问题。
     */
    public String followUp(String history, ConsultIntake.Slots slots, String memoryContext, String fallback) {
        if (!llm.available() || slots == null || slots.missingText().isBlank()) {
            return fallback;
        }
        String asked = llm.chat("""
                你是教学用门诊助手，正在问诊。用户的情况还没说全，现在只做一件事：把缺的信息问清楚。
                结合用户原话，用一两句口语化的中文追问，可以把缺的几项合在一句里问，也可以顺带问一个和这个症状相关、能帮忙判断轻重的问题。
                不要分析病因，不要给建议，不要写任何药品名，不要说「信息未齐」「参考方向」之类的话，不要用标题和列表。
                如果材料里有就诊卡摘要且和用户这次说的不一致，可以顺带问一句确认。
                只追问最后一句还缺的症状，不要把更早的闲聊再答一遍。
                只输出追问本身。
                """, scopedUser(history)
                + "\n还缺：" + slots.missingText()
                + (memoryContext == null || memoryContext.isBlank() ? "" : "\n" + memoryContext));
        if (asked == null) {
            return fallback;
        }
        String text = asked.trim();
        if (text.isBlank() || text.length() > 220 || outsideWhitelist(text, List.of())
                || text.contains("信息未齐") || text.contains("##")) {
            return fallback;
        }
        return text;
    }

    public void remember(ConsultTurn turn) {
        if (!tryApply(turn, "session_memory")) {
            return;
        }
        memoryStore.save(turn.sessionId, turn.memory);
        if (tryApply(turn, "trace:memory")) {
            trace(turn.sessionId, "记忆", "更新会话记忆", turn.content, turn.memory.summary(), true);
        }
    }

    public String brief(SessionMemory memory, UserMemoryService.View longTerm) {
        return brief(memory, longTerm, null);
    }

    public String brief(SessionMemory memory, UserMemoryService.View longTerm, Long buyerId) {
        StringBuilder text = new StringBuilder();
        if (!memory.shortTermText().isBlank()) {
            text.append("近期对话：\n").append(memory.shortTermText()).append("\n");
        }
        if (!memory.promptContext().isBlank()) {
            text.append(memory.promptContext()).append("\n");
        }
        if (longTerm != null && longTerm.context() != null && !longTerm.context().isBlank()) {
            text.append(longTerm.context()).append("\n");
        }
        if (memory.openFlags != null && !memory.openFlags.isEmpty()) {
            text.append("未关闭的紧急情况：");
            for (SessionMemory.OpenFlag flag : memory.openFlags) {
                text.append(flag.phrase == null ? "" : flag.phrase);
                if (flag.message != null && !flag.message.isBlank()) {
                    text.append("（").append(flag.message).append("）");
                }
                text.append("；");
            }
            text.append("\n");
        }
        String profile = profileContext(memory, buyerId);
        if (!profile.isBlank()) {
            text.append(profile).append("\n");
        }
        return text.toString().trim();
    }

    public String memoryLine(SessionMemory memory, UserMemoryService.View longTerm) {
        String profile = longTerm == null || longTerm.profileLine() == null ? "" : longTerm.profileLine();
        return memory.summary() + profile;
    }

    public String withUncertainty(String reply, ConsultIntake.Slots slots) {
        if (slots.complete() || slots.missingText().isBlank()) {
            return reply;
        }
        return "> 先按你现在说的情况给方向。还没说到" + slots.missingText() + "，补上后判断会更准。\n\n" + reply;
    }

    public boolean lexicalSupport(RagFacade.Hit hit) {
        String channel = hit.channel() == null ? "" : hit.channel();
        return channel.contains("关键词") || channel.contains("图谱");
    }

    /** 关联了就诊卡时，用户如果在问自己是谁或卡片上的资料，就直接按卡片回答。 */
    public boolean askingAboutSelf(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String compact = text.replace(" ", "").replace("\n", "");
        if (compact.contains("我是谁") || compact.contains("我叫什么")) {
            return true;
        }
        if (!llm.available()) {
            return false;
        }
        String decision = llm.chat("""
                用户是否在问自己的身份，或自己就诊卡上的资料（是谁、年龄、性别、身高、体重、过敏、用药、慢病）。
                如果在区分「我是谁」和「你是谁」，要的是自己，输出 YES。只有单独问助手是谁才输出 NO。
                只输出 YES 或 NO。
                """, text.trim());
        if (decision == null || decision.isBlank()) {
            return false;
        }
        String token = decision.trim().toUpperCase();
        if (token.startsWith("NO") || token.startsWith("否")) {
            return false;
        }
        return token.startsWith("YES") || token.startsWith("是");
    }

    /**
     * 槽位没齐时，由模型判断这次是该直接回答，还是继续追问症状。
     * 模型不可用时返回 false，沿用规则追问。
     */
    public boolean answerNow(String history, ConsultIntake.Slots slots, String memoryContext) {
        if (!llm.available() || slots == null || slots.complete() || history == null || history.isBlank()) {
            return false;
        }
        String decision = llm.chat("""
                你在决定这一轮要不要直接回答用户。
                用户如果是在问一个可以靠资料或你自己的知识回答的问题（比如某种药怎么用、能不能用、有什么作用），输出 YES。
                用户如果是在描述自己的不适，而这些情况还没说清，输出 NO，后面会再追问。
                只输出 YES 或 NO。
                """, "用户原话：\n" + history
                + "\n还没提到：" + slots.missingText()
                + (memoryContext == null || memoryContext.isBlank() ? "" : "\n" + memoryContext));
        if (decision == null || decision.isBlank()) {
            return false;
        }
        String token = decision.trim().toUpperCase();
        if (token.startsWith("NO") || token.startsWith("否")) {
            return false;
        }
        return token.startsWith("YES") || token.startsWith("是");
    }

    /**
     * 直接回答这一问。资料够就用知识库和联网结果，不够就用模型自己的知识。
     * 不规定段落提纲，也不在这里写死任何药品的服法。
     */
    public String directAnswer(String history, String knowledge, String web, String memoryContext) {
        if (!llm.available()) {
            return "";
        }
        String material = "";
        if (knowledge != null && !knowledge.isBlank()) {
            material += "\n知识库片段：\n" + knowledge;
        }
        if (web != null && !web.isBlank()) {
            material += "\n联网资料：\n" + web;
        }
        String drafted = llm.chat("""
                你是教学用门诊助手，不是医生。请直接回答用户这次的问题。
                先用下面的知识库片段和联网资料；不够的部分用你自己的知识补上，把问题答完。
                不要改去追问症状，也不要用「去看说明书」代替回答。
                材料里如果有就诊卡，用户问自己是谁或自己的资料时，按卡片回答，不要说不知道。
                不要提及知识库有没有覆盖，不要说这是你自己的常识。
                不要推荐抗生素或处方抗病毒药，不要说已经确诊，不要开处方。
                用 Markdown，结构按这个问题本身来，不要套固定提纲。
                """, scopedUser(history) + material
                + (memoryContext == null || memoryContext.isBlank() ? "" : "\n" + memoryContext));
        if (drafted == null || drafted.isBlank()) {
            return "";
        }
        drafted = stripCoverageNotice(drafted);
        if (!drafted.contains("教学参考")) {
            drafted = drafted.trim() + "\n\n*" + DISCLAIMER + "*";
        }
        return ensureMarkdown(drafted);
    }

    /** 整段历史里只把最后一句当这次要回答的话。症状和红旗可以接着用，闲聊不要再答一遍。 */
    static String scopedUser(String history) {
        String raw = history == null ? "" : history;
        String extra = "";
        int webAt = raw.indexOf("\n联网资料：");
        if (webAt >= 0) {
            extra = raw.substring(webAt);
            raw = raw.substring(0, webAt);
        }
        if (raw.contains("只按就诊卡回答")) {
            return "用户原话：\n" + raw.trim() + extra;
        }
        String trimmed = raw.trim();
        int split = trimmed.lastIndexOf('\n');
        String latest = split < 0 ? trimmed : trimmed.substring(split + 1).trim();
        String earlier = split < 0 ? "" : trimmed.substring(0, split).trim();
        if (earlier.isBlank()) {
            return "用户这次说：\n" + latest + extra;
        }
        return "只回答最后一句。更早的闲聊不要再写进这次回答，例如你是谁、打招呼、确认身份。\n"
                + "更早的症状可以和最后一句合在一起，判断这次可能是什么情况。只有材料里确实有未关闭的紧急情况时，才在分析后再问是否还在。\n\n"
                + "最后一句：\n" + latest + "\n\n"
                + "更早的用户原话（不要逐条再答）：\n" + earlier
                + extra;
    }

    /** 去掉「不在知识库 / 这是常识」这类过程说明，用户只看回答本身。 */
    static String stripCoverageNotice(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String cleaned = text;
        cleaned = cleaned.replaceAll("(?m)^#{1,3}[^\\n]*(不在知识库|知识库未覆盖|知识库没有对上|模型自身知识|模型自己的常识)[^\\n]*\\n*", "");
        cleaned = cleaned.replaceAll("(?m)^[^\\n]*(这条不在知识库|下面是我自己的常识|下面是模型自己的常识|知识库没有对上|改用模型自身知识)[^\\n]*\\n*", "");
        return cleaned.trim();
    }

    public String generalKnowledge(String history, String memoryContext) {
        if (!llm.available()) {
            return "";
        }
        String drafted = llm.chat("""
                你是教学用门诊助手，不是医生。请直接回答用户的问题。
                用 Markdown 输出：二级标题、短段落和列表，不要写成一整段。
                说清可能的情况、在家可以怎么观察、什么时候该去医院。
                知识库资料不够时，用联网资料和你自己的知识继续答，可以写出常见对症药品的名称和大致用法。
                写明这只是教学参考，不是确诊，也不是处方。
                不要写「不能推荐药品」「我不能推荐具体药品名称」「也不开处方」这类推脱。
                不要提及知识库有没有覆盖，不要说这是你自己的常识或模型常识。
                抗生素和处方抗病毒药不要写成自己在家常规服用的建议；需要时说明去医院由医生决定。
                若材料里有未关闭的紧急情况，先分析当前这个不适，再另起一段用你自己的话问现在是否还在；还在就说明仍需马上就医。不要用「另外确认一下」「你之前提到的」这类套话。
                只回答最后一句。更早的闲聊不要再写进这次回答。症状和紧急情况可以接着用。
                """, scopedUser(history) + (memoryContext == null || memoryContext.isBlank() ? "" : "\n" + memoryContext));
        if (drafted.isBlank()) {
            return "";
        }
        drafted = stripCoverageNotice(drafted);
        if (!drafted.contains("教学参考")) {
            drafted = drafted + "\n\n*" + DISCLAIMER + "*";
        }
        return ensureMarkdown(drafted);
    }

    public String draft(String history, RagFacade.Bundle bundle, MedDisease disease, List<MedDrug> diseaseDrugs,
                        boolean incomplete, String memoryContext) {
        String evidence = bundle.hits().stream()
                .map(hit -> hit.title() + "：" + hit.text())
                .collect(Collectors.joining("\n"));
        String names = diseaseDrugs.stream().map(MedDrug::getName).distinct().collect(Collectors.joining("、"));
        String system = """
                你是教学用门诊助手，不是医生。只根据给定资料写参考意见。
                只输出 JSON，不要包 markdown 代码块。格式：{"reply":"...","drugNames":["药名"]}
                reply 字段必须是 Markdown 正文：用 ## / ###、短段落和 - 列表，不要挤成一整段。
                drugNames 只能从白名单里选，可以是空数组。不得输出白名单以外的药，不得说已确诊或已开处方。
                处理要点只用来核对边界，reply 必须根据用户原话自己分析，不要照抄处理要点或检索片段。
                若材料里有未关闭的紧急情况：先把当前主诉分析完，再另起一段询问这些情况现在是否还在。用你自己的话，结合这次症状一起判断，不要写「另外确认一下」或「你之前提到的某某现在还在吗」。若还在，说明仍需马上就医或联系急救，且不要把当前主诉的药品用到那条紧急情况上。
                只回答最后一句。更早的闲聊（例如你是谁、打招呼）不要再写进这次回答。更早的症状可以和最后一句合在一起判断这次是什么情况。
                """ + (incomplete
                ? "用户信息还没齐，把方向写成参考，不要说已经确定。这次可以写出常见对症药品的名称和大致用法，白名单限制放宽，并说明只是参考、不是处方。不要写「不能推荐药品」。"
                : "");
        String user = scopedUser(history)
                + (memoryContext == null || memoryContext.isBlank() ? "" : "\n" + memoryContext)
                + "\n可能方向：" + disease.getName()
                + "\n处理要点（不要照抄）：" + disease.getAdvice()
                + "\n检索片段：\n" + evidence
                + "\n白名单：" + names;
        return llm.chat(system, user);
    }

    public Parsed parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String json = raw.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("(?s)^```[a-zA-Z]*\\s*", "").replaceAll("(?s)```\\s*$", "").trim();
        }
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                JsonNode node = objectMapper.readTree(json.substring(start, end + 1));
                String reply = ensureMarkdown(node.path("reply").asText("").trim());
                if (!reply.isBlank()) {
                    List<String> names = new ArrayList<>();
                    node.path("drugNames").forEach(item -> names.add(item.asText("").trim()));
                    return new Parsed(reply, names.stream().filter(name -> !name.isBlank()).toList());
                }
            } catch (Exception ignored) {
                // 模型没按 JSON 输出时，下面把正文本身当作分析
            }
        }
        if (json.startsWith("{") || json.isBlank()) {
            return null;
        }
        return new Parsed(ensureMarkdown(json), List.of());
    }

    public boolean outsideWhitelist(String reply, List<MedDrug> allowed) {
        if (reply == null) {
            return true;
        }
        for (String banned : BANNED) {
            if (reply.contains(banned) && allowed.stream().noneMatch(drug -> drug.getName().equals(banned))) {
                return true;
            }
        }
        for (MedDrug drug : ledger.drugs()) {
            boolean allowedHere = allowed.stream().anyMatch(item -> item.getName().equals(drug.getName()));
            if (!allowedHere && reply.contains(drug.getName())) {
                return true;
            }
        }
        return false;
    }

    public String ruleReply(MedDisease disease, List<MedDrug> drugs) {
        if (disease == null) {
            return "## 暂未对上\n\n暂时对不上知识库里的情况。\n\n*" + DISCLAIMER + "*";
        }
        StringBuilder text = new StringBuilder();
        text.append("## 参考方向\n\n");
        text.append("你描述的情况更接近 **").append(disease.getName()).append("**，这只是参考，不是确诊。\n\n");
        text.append(disease.getSummary()).append("\n\n");
        text.append("### 处理建议\n\n").append(disease.getAdvice()).append("\n\n");
        if (drugs != null && !drugs.isEmpty()) {
            text.append("### 可参考药品\n\n");
            for (MedDrug drug : drugs) {
                text.append("- **").append(drug.getName()).append("**：")
                        .append(drug.getUsageText())
                        .append("  \n  注意：").append(drug.getCaution()).append("\n");
            }
            text.append("\n");
        }
        text.append("资料出处：").append(disease.getSourceName()).append("。\n\n");
        text.append("*").append(DISCLAIMER).append("*");
        return text.toString();
    }

    public Map<String, Object> baseCard(boolean redFlag, boolean degraded, String modeNote) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("redFlag", redFlag);
        card.put("degraded", degraded);
        card.put("modeNote", modeNote);
        card.put("disclaimer", DISCLAIMER);
        card.put("diagnoses", List.of());
        card.put("drugs", List.of());
        card.put("evidences", List.of());
        card.put("plan", "");
        return card;
    }

    public Map<String, Object> drugView(MedDrug drug) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("name", drug.getName());
        view.put("usage", drug.getUsageText());
        view.put("caution", drug.getCaution());
        view.put("source", drug.getSourceName());
        return view;
    }

    public Map<String, Object> hitView(RagFacade.Hit hit) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("title", hit.title());
        view.put("snippet", clip(hit.text(), 160));
        view.put("sourceUrl", hit.sourceUrl());
        view.put("channel", hit.channel());
        return view;
    }

    /** 链路页展示真实命中，不再写死 RRF/Chroma 说明。 */
    public String retrieveTrace(RagFacade.Bundle bundle, List<RagFacade.Hit> webHits) {
        StringBuilder text = new StringBuilder();
        List<RagFacade.Hit> hits = bundle == null || bundle.hits() == null ? List.of() : bundle.hits();
        if (hits.isEmpty()) {
            text.append("知识库未命中明确资料。");
        } else {
            text.append("知识库命中 ").append(Math.min(hits.size(), 5)).append(" 条：\n");
            int i = 1;
            for (RagFacade.Hit hit : hits) {
                if (i > 5) {
                    break;
                }
                text.append(i++).append(". 《").append(clip(hit.title(), 60)).append("》");
                if (hit.text() != null && !hit.text().isBlank()) {
                    text.append(" — ").append(clip(hit.text(), 80));
                }
                text.append("\n");
            }
        }
        if (webHits != null && !webHits.isEmpty()) {
            text.append("联网补充 ").append(webHits.size()).append(" 条：\n");
            int i = 1;
            for (RagFacade.Hit hit : webHits) {
                text.append(i++).append(". 《").append(clip(hit.title(), 60)).append("》");
                if (hit.sourceUrl() != null && !hit.sourceUrl().isBlank()) {
                    text.append(" ").append(hit.sourceUrl());
                }
                text.append("\n");
            }
        }
        return text.toString().trim();
    }

    /**
     * 检索回来的资料先由模型核对是不是在讲同一件事。
     * 只沾到同一个部位、病却不一样的，不留给回答，也不当引用。
     * 模型不可用或没给出明确编号时，保持原结果。
     */
    public void keepRelevant(ConsultTurn turn) {
        if (!llm.available() || turn == null) {
            return;
        }
        List<RagFacade.Hit> kb = turn.bundle == null || turn.bundle.hits() == null ? List.of() : turn.bundle.hits();
        List<RagFacade.Hit> web = turn.webHits == null ? List.of() : turn.webHits;
        if (kb.isEmpty() && web.isEmpty()) {
            return;
        }
        StringBuilder catalog = new StringBuilder();
        int n = 1;
        for (RagFacade.Hit hit : kb) {
            catalog.append(n++).append(". ").append(clip(hit.title(), 40)).append("：").append(clip(hit.text(), 80)).append("\n");
        }
        int webFrom = n;
        for (RagFacade.Hit hit : web) {
            catalog.append(n++).append(". ").append(clip(hit.title(), 40)).append("：").append(clip(hit.text(), 80)).append("\n");
        }
        String decision = llm.chat("""
                你在校验这些资料能不能当作这次问题的引用。
                只保留真正在讲同一件事的。只是提到了同一个身体部位、但讲的是另一种情况，不算相关。
                只输出要保留的编号，用英文逗号分隔。一条都不相关就只输出 NONE。
                """, "用户问题：\n" + turn.content + "\n资料：\n" + catalog);
        if (decision == null || decision.isBlank()) {
            return;
        }
        String upper = decision.trim().toUpperCase();
        Matcher matcher = Pattern.compile("\\d+").matcher(decision);
        List<Integer> ids = new ArrayList<>();
        while (matcher.find()) {
            int id = Integer.parseInt(matcher.group());
            if (id >= 1 && id < n && !ids.contains(id)) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            if (upper.contains("NONE")) {
                replaceHits(turn, kb, web, List.of(), List.of());
            }
            return;
        }
        List<RagFacade.Hit> keptKb = new ArrayList<>();
        List<RagFacade.Hit> keptWeb = new ArrayList<>();
        for (int id : ids) {
            if (id < webFrom) {
                keptKb.add(kb.get(id - 1));
            } else {
                keptWeb.add(web.get(id - webFrom));
            }
        }
        replaceHits(turn, kb, web, keptKb, keptWeb);
    }

    private void replaceHits(ConsultTurn turn, List<RagFacade.Hit> kb, List<RagFacade.Hit> web,
                             List<RagFacade.Hit> keptKb, List<RagFacade.Hit> keptWeb) {
        if (turn.bundle != null && keptKb.size() != kb.size()) {
            turn.bundle = new RagFacade.Bundle(keptKb, turn.bundle.keywordDegraded(), turn.bundle.vectorDegraded(),
                    turn.bundle.graphDegraded(), turn.bundle.note());
        }
        if (keptWeb.size() != web.size()) {
            turn.webHits = keptWeb;
        }
    }

    public List<Map<String, Object>> evidenceViews(ConsultTurn turn) {
        List<Map<String, Object>> views = new ArrayList<>();
        if (turn.bundle != null && turn.bundle.hits() != null) {
            for (RagFacade.Hit hit : turn.bundle.hits()) {
                if (views.size() >= 3) {
                    break;
                }
                views.add(hitView(hit));
            }
        }
        if (turn.webHits != null) {
            for (RagFacade.Hit hit : turn.webHits) {
                if (views.size() >= 5) {
                    break;
                }
                views.add(hitView(hit));
            }
        }
        return views;
    }

    public void finish(ConsultTurn turn, String agent, String action, String output, Boolean passed) {
        if (tryApply(turn, "finish:" + agent + ":" + action)) {
            saveMessage(turn.sessionId, "assistant", clip(turn.reply, 1800));
            turn.session.setLastMessage(clip(turn.reply, 180));
            turn.session.setUpdatedAt(LocalDateTime.now());
            sessions.save(turn.session);
            trace(turn.sessionId, agent, action, turn.content, output, passed);
        }
        if (turn.card != null) {
            turn.steps.add(new Step("progress", "安全校验完成"));
        }
    }

    public String userHistory(Long sessionId) {
        return messages.findBySessionIdOrderByIdAsc(sessionId).stream()
                .filter(message -> "user".equals(message.getRole()))
                .map(ChatMessage::getContent)
                .collect(Collectors.joining("\n"));
    }

    public void saveMessage(Long sessionId, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(clip(content, 1800));
        message.setCreatedAt(LocalDateTime.now());
        messages.save(message);
    }

    public void trace(Long sessionId, String agent, String action, String input, String output, Boolean passed) {
        TraceStep step = new TraceStep();
        step.setSessionId(sessionId);
        step.setSeq(traces.countBySessionId(sessionId) + 1);
        step.setAgentName(agent);
        step.setAction(clip(action, 64));
        step.setInputText(clip(input, 900));
        step.setOutputText(clip(output, 1800));
        step.setElapsedMs(0);
        step.setPolicyPassed(passed);
        traces.save(step);
    }

    public ChatSession requireSession(Long sessionId) {
        return sessions.findById(sessionId).orElseThrow();
    }

    private int countAsks(Long sessionId) {
        return (int) traces.findBySessionIdOrderBySeqAsc(sessionId).stream()
                .filter(step -> "追问".equals(step.getAction()))
                .count();
    }

    static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    public record Parsed(String reply, List<String> drugNames) {
    }
}

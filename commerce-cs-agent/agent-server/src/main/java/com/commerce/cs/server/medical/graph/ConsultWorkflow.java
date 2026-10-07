package com.commerce.cs.server.medical.graph;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.commerce.cs.domain.entity.MedDisease;
import com.commerce.cs.domain.entity.MedDrug;
import com.commerce.cs.domain.service.ConsultIntake;
import com.commerce.cs.domain.service.KeywordRanker;
import com.commerce.cs.domain.service.RedFlagFollowUp;
import com.commerce.cs.domain.service.SafetyGate;
import com.commerce.cs.server.medical.ConsultOrchestrator.Step;
import com.commerce.cs.server.medical.McpWebSearchTool;
import com.commerce.cs.server.medical.RagFacade;
import com.commerce.cs.server.medical.UserMemoryService;
import com.commerce.cs.server.medical.WebKnowledgeIngest;
import com.commerce.cs.server.medical.WebSearchProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 固定 Workflow：红旗 / 记忆 / 检索 / SafetyGate / 写记忆不可跳过；
 * 问诊为受约束 Agent 节点（只决定追问或收口）；意见生成为白名单约束节点。
 */
@Configuration
public class ConsultWorkflow {
    public static final String KEY_TURN = "turn";
    public static final String NODE_RED_FLAG = "red_flag";
    public static final String NODE_LOAD_MEMORY = "load_memory";
    public static final String NODE_INTAKE_AGENT = "intake_agent";
    public static final String NODE_RETRIEVE = "retrieve";
    public static final String NODE_ADVICE = "advice";
    public static final String NODE_SAFETY_GATE = "safety_gate";
    public static final String NODE_WRITE_MEMORY = "write_memory";

    @Bean
    public CompiledGraph consultGraph(ConsultSupport support, RagFacade rag, McpWebSearchTool webSearch,
                                      WebSearchProperties webSearchProperties, WebKnowledgeIngest webIngest)
            throws GraphStateException {
        StateGraph graph = new StateGraph("consult-workflow", () -> {
            HashMap<String, com.alibaba.cloud.ai.graph.KeyStrategy> strategies = new HashMap<>();
            strategies.put(KEY_TURN, new ReplaceStrategy());
            return strategies;
        });

        graph.addNode(NODE_RED_FLAG, node_async(state -> redFlag(state, support)));
        graph.addNode(NODE_LOAD_MEMORY, node_async(state -> loadMemory(state, support)));
        graph.addNode(NODE_INTAKE_AGENT, node_async(state -> intakeAgent(state, support)));
        graph.addNode(NODE_RETRIEVE, node_async(state -> retrieve(state, support, rag, webSearch, webSearchProperties, webIngest)));
        graph.addNode(NODE_ADVICE, node_async(state -> advice(state, support)));
        graph.addNode(NODE_SAFETY_GATE, node_async(state -> safetyGate(state, support)));
        graph.addNode(NODE_WRITE_MEMORY, node_async(state -> writeMemory(state, support)));

        graph.addEdge(START, NODE_RED_FLAG);
        graph.addConditionalEdges(NODE_RED_FLAG, edge_async(state -> routeOf(state)), Map.of(
                ConsultTurn.ROUTE_BLOCKED, NODE_WRITE_MEMORY,
                ConsultTurn.ROUTE_READY, NODE_LOAD_MEMORY));
        graph.addEdge(NODE_LOAD_MEMORY, NODE_INTAKE_AGENT);
        graph.addConditionalEdges(NODE_INTAKE_AGENT, edge_async(state -> routeOf(state)), Map.of(
                ConsultTurn.ROUTE_ASK, NODE_WRITE_MEMORY,
                ConsultTurn.ROUTE_READY, NODE_RETRIEVE));
        graph.addEdge(NODE_RETRIEVE, NODE_ADVICE);
        graph.addEdge(NODE_ADVICE, NODE_SAFETY_GATE);
        graph.addEdge(NODE_SAFETY_GATE, NODE_WRITE_MEMORY);
        graph.addEdge(NODE_WRITE_MEMORY, END);

        return graph.compile();
    }

    private static String routeOf(OverAllState state) {
        ConsultTurn turn = turn(state);
        return turn.route == null ? ConsultTurn.ROUTE_DONE : turn.route;
    }

    private static ConsultTurn turn(OverAllState state) {
        return state.value(KEY_TURN, ConsultTurn.class)
                .orElseThrow(() -> new IllegalStateException("问诊图缺少 turn 状态"));
    }

    private static Map<String, Object> redFlag(OverAllState state, ConsultSupport support) {
        ConsultTurn turn = turn(state);
        support.openMemory(turn);
        RedFlagFollowUp.restore(turn.memory, turn.history, turn.content, support.ledger().redFlags());
        RedFlagFollowUp.resolve(turn.memory, turn.content);
        turn.redFlag = RedFlagFollowUp.scanCurrent(turn.content, support.ledger().redFlags());
        if (turn.redFlag == null) {
            turn.redFlag = RedFlagFollowUp.confirms(turn.memory, turn.content);
        }
        if (turn.redFlag != null) {
            turn.steps.add(new Step("progress", "正在检查有没有需要马上就医的情况"));
            turn.slots = ConsultIntake.read(turn.content, support.ledger().diseases(), support.ledger().symptoms());
            turn.memory.sync(turn.slots, turn.memory.candidateCode, turn.memory.candidateName);
            turn.memory.flag(turn.redFlag.phrase());
            turn.memory.rememberFlag(turn.redFlag.phrase(), turn.redFlag.message());
            turn.reply = "## 需要马上就医\n\n" + turn.redFlag.message()
                    + "请立即联系急诊或拨打 **120**。这里不能诊断，也不会推荐药品。";
            turn.memory.pushTurn("助手", turn.reply);
            turn.card = support.baseCard(true, false, "门禁已拦截，未检索用药方案");
            turn.card.put("plan", turn.reply);
            turn.route = ConsultTurn.ROUTE_BLOCKED;
        } else {
            turn.route = ConsultTurn.ROUTE_READY;
        }
        support.checkpoint(turn, NODE_RED_FLAG);
        return Map.of(KEY_TURN, turn);
    }

    private static Map<String, Object> loadMemory(OverAllState state, ConsultSupport support) {
        ConsultTurn turn = turn(state);
        turn.longTerm = support.userMemories().recall(turn.session.getBuyerId());
        turn.steps.add(new Step("progress", "正在对照会话记忆"));
        turn.route = ConsultTurn.ROUTE_READY;
        support.checkpoint(turn, NODE_LOAD_MEMORY);
        return Map.of(KEY_TURN, turn);
    }

    /**
     * 受约束 Agent 节点：可读会话记忆，只决定追问、闲聊引导或提前收口；不能跳过红旗/检索/门禁。
     */
    private static Map<String, Object> intakeAgent(OverAllState state, ConsultSupport support) {
        ConsultTurn turn = turn(state);
        turn.slots = ConsultIntake.read(turn.history, support.ledger().diseases(), support.ledger().symptoms());
        int asked = turn.memory.askCount;
        if (turn.memory.profileLinked && support.askingAboutSelf(turn.content, turn.memory, turn.session.getBuyerId())) {
            turn.directAnswer = true;
            turn.aboutSelf = true;
            turn.route = ConsultTurn.ROUTE_READY;
            turn.steps.add(new Step("progress", "按关联的就诊卡回答"));
            support.checkpoint(turn, NODE_INTAKE_AGENT);
            return Map.of(KEY_TURN, turn);
        }
        // 统一原则：还没采到任何症状槽位时，整轮都走 LLM 直接答，绝不进规则追问。
        // 只有已经进入症状采集（槽位有信号但未齐）时，才由模型决定继续追问还是直接答。
        boolean inSymptomIntake = turn.slots.site() || turn.slots.duration() || turn.slots.fever();
        if (!ConsultIntake.stopRequested(turn.content) && asked < ConsultIntake.MAX_ASKS && !turn.slots.complete()
                && (!inSymptomIntake || support.answerNow(turn.history, turn.slots,
                support.profileContext(turn.memory, turn.session.getBuyerId())))) {
            turn.directAnswer = true;
            turn.route = ConsultTurn.ROUTE_READY;
            turn.steps.add(new Step("progress",
                    turn.webSearch ? "由模型回答（可联网）" : "由模型直接回答"));
            support.checkpoint(turn, NODE_INTAKE_AGENT);
            return Map.of(KEY_TURN, turn);
        }
        if (!ConsultIntake.stopRequested(turn.content) && asked < ConsultIntake.MAX_ASKS && !turn.slots.complete()) {
            String candidate = KeywordRanker.rank(turn.history, support.ledger().diseases(), support.ledger().symptoms())
                    .stream()
                    .findFirst()
                    .map(KeywordRanker.Ranked::diseaseCode)
                    .orElse("");
            MedDisease hinted = candidate.isBlank() ? null : support.ledger().disease(candidate);
            turn.memory.sync(turn.slots, candidate, hinted == null ? "" : hinted.getName());
            String question = support.followUp(turn.history, turn.slots,
                    support.profileContext(turn.memory, turn.session.getBuyerId()),
                    ConsultIntake.question(turn.slots, asked, candidate));
            turn.memory.ask(question);
            turn.memory.pushTurn("助手", question);
            turn.reply = question;
            turn.card = null;
            turn.steps.add(new Step("progress", "先补一个问题"));
            turn.route = ConsultTurn.ROUTE_ASK;
        } else {
            turn.route = ConsultTurn.ROUTE_READY;
        }
        support.checkpoint(turn, NODE_INTAKE_AGENT);
        return Map.of(KEY_TURN, turn);
    }

    /**
     * 默认只走 RAG。打开联网后与知识库并行：病种锁定永远以 RAG 词法支持为准；
     * 联网结果作补充依据，并异步总结入库。不用「谁先回来用谁」裁决用药。
     */
    private static Map<String, Object> retrieve(OverAllState state, ConsultSupport support, RagFacade rag,
                                                McpWebSearchTool webSearch, WebSearchProperties webProps,
                                                WebKnowledgeIngest webIngest) {
        ConsultTurn turn = turn(state);
        boolean wantWeb = turn.webSearch && webProps.isEnabled();
        turn.steps.add(new Step("retrieving", wantWeb ? "正在并行检索知识库与联网资料" : "正在检索知识库"));

        String searchQ = support.webQuery(turn);
        boolean skipKb = turn.directAnswer;
        CompletableFuture<RagFacade.Bundle> ragFuture = skipKb
                ? CompletableFuture.completedFuture(new RagFacade.Bundle(List.of(), false, false, false, "非问诊不检索医学知识库"))
                : CompletableFuture.supplyAsync(() -> rag.retrieve(turn.history));
        CompletableFuture<List<RagFacade.Hit>> webFuture = wantWeb
                ? CompletableFuture.supplyAsync(() -> webSearch.search(searchQ))
                : CompletableFuture.completedFuture(List.of());

        turn.bundle = ragFuture.join();
        List<RagFacade.Hit> webHits = List.of();
        if (wantWeb) {
            try {
                webHits = webFuture.get(Math.max(webProps.getTimeoutMs(), 3000L), TimeUnit.MILLISECONDS);
            } catch (Exception ex) {
                webHits = List.of();
            }
        }
        turn.webHits = webHits == null ? List.of() : webHits;
        if (!turn.directAnswer) {
            support.keepRelevant(turn);
        }

        String code = turn.bundle.hits().stream()
                .filter(hit -> hit.diseaseCode() != null && !hit.diseaseCode().isBlank())
                .filter(hit -> support.ledger().disease(hit.diseaseCode()) != null)
                .filter(support::lexicalSupport)
                .map(RagFacade.Hit::diseaseCode)
                .findFirst()
                .orElse(null);
        if (code == null) {
            turn.uncovered = true;
            turn.disease = null;
            turn.diseaseDrugs = List.of();
        } else {
            turn.uncovered = false;
            turn.disease = support.ledger().disease(code);
            turn.diseaseDrugs = turn.disease == null ? List.of() : support.ledger().drugsFor(turn.disease.getCode());
        }
        if (support.tryApply(turn, "trace:retrieve")) {
            String action = wantWeb ? (turn.webHits.isEmpty() ? "知识库优先（联网未赶上）" : "知识库+联网补充") : "知识库检索";
            support.trace(turn.sessionId, "检索", action, turn.content,
                    support.retrieveTrace(turn.bundle, turn.webHits), null);
        }
        if (wantWeb && !turn.directAnswer) {
            List<RagFacade.Hit> forIngest = new ArrayList<>(turn.webHits);
            webFuture.whenComplete((late, err) -> {
                List<RagFacade.Hit> batch = forIngest;
                if ((batch == null || batch.isEmpty()) && late != null && !late.isEmpty()) {
                    batch = late;
                }
                if (batch != null && !batch.isEmpty()) {
                    webIngest.ingestLater(turn.content, batch, code);
                }
            });
        }
        support.checkpoint(turn, NODE_RETRIEVE);
        return Map.of(KEY_TURN, turn);
    }

    /** 受约束意见节点：只能使用该病白名单药；LLM 不可用或越权时回退规则方案。 */
    private static Map<String, Object> advice(OverAllState state, ConsultSupport support) {
        ConsultTurn turn = turn(state);
        turn.thin = turn.slots != null && turn.slots.thin();
        if (turn.longTerm == null) {
            turn.longTerm = support.userMemories().recall(turn.session.getBuyerId());
        }
        if (turn.directAnswer) {
            String kb = "";
            String web = turn.webHits == null ? "" : turn.webHits.stream()
                    .map(hit -> "- [" + (hit.channel() == null ? "联网" : hit.channel()) + "] "
                            + hit.title() + "：" + hit.text())
                    .collect(Collectors.joining("\n"));
            String focus = turn.aboutSelf
                    ? turn.history + "\n（这一问只按上方 [就诊卡] 回答卡上姓名对应的人的资料；有登记的身高体重等必须直接报出，不要说材料里没有。）"
                    : turn.history;
            String answer = support.directAnswer(focus, kb, web,
                    support.brief(turn.memory, turn.longTerm, turn.session.getBuyerId()));
            boolean fromModel = !answer.isBlank();
            if (!fromModel) {
                answer = "这次没有整理出回答，你可以换个说法再问一次。\n\n*" + ConsultSupport.DISCLAIMER + "*";
            }
            turn.uncovered = true;
            turn.disease = null;
            turn.diseaseDrugs = List.of();
            turn.uncoveredGeneral = answer;
            turn.reply = answer;
            turn.proposed = List.of();
            turn.llmUsed = fromModel;
            turn.steps.add(new Step("generating", "由模型组织回答"));
            support.checkpoint(turn, NODE_ADVICE);
            return Map.of(KEY_TURN, turn);
        }
        if (turn.uncovered) {
            String webExtra = "";
            if (turn.webHits != null && !turn.webHits.isEmpty()) {
                webExtra = "\n联网资料：\n" + turn.webHits.stream()
                        .map(hit -> "- " + hit.title() + "：" + hit.text())
                        .collect(Collectors.joining("\n"));
            }
            String general = support.generalKnowledge(turn.history + webExtra,
                    support.brief(turn.memory, turn.longTerm, turn.session.getBuyerId()));
            turn.uncoveredGeneral = general;
            turn.reply = general.isBlank()
                    ? "这次没有整理出回答。\n\n*" + ConsultSupport.DISCLAIMER + "*"
                    : general;
            turn.reply = support.withUncertainty(turn.reply, turn.slots);
            turn.proposed = List.of();
            turn.llmUsed = !general.isBlank();
            support.checkpoint(turn, NODE_ADVICE);
            return Map.of(KEY_TURN, turn);
        }

        turn.steps.add(new Step("generating", "正在整理参考意见"));
        turn.proposed = turn.thin ? List.of() : turn.diseaseDrugs.stream().map(MedDrug::getName).distinct().toList();
        turn.reply = support.ruleReply(turn.disease, turn.thin ? List.of() : turn.diseaseDrugs);
        turn.llmUsed = false;
        if (support.llm().available() && turn.disease != null) {
            List<MedDrug> allowed = turn.thin ? List.of() : turn.diseaseDrugs;
            String drafted = support.draft(turn.history, turn.bundle, turn.disease, allowed,
                    turn.thin || !turn.slots.complete(), support.brief(turn.memory, turn.longTerm, turn.session.getBuyerId()));
            ConsultSupport.Parsed parsed = support.parse(drafted);
            boolean referenceOnly = turn.thin || !turn.slots.complete();
            if (parsed != null && (referenceOnly || !support.outsideWhitelist(parsed.reply(), allowed))) {
                turn.reply = parsed.reply();
                turn.llmUsed = true;
                if (!turn.thin && !parsed.drugNames().isEmpty()) {
                    turn.proposed = parsed.drugNames();
                }
            }
        }
        turn.reply = support.withUncertainty(turn.reply, turn.slots);
        if (!turn.reply.contains("教学参考")) {
            turn.reply = turn.reply + "\n\n*" + ConsultSupport.DISCLAIMER + "*";
        }
        support.checkpoint(turn, NODE_ADVICE);
        return Map.of(KEY_TURN, turn);
    }

    /** 固定 SafetyGate：药白名单永远执行，不交给模型决定是否过滤。 */
    private static Map<String, Object> safetyGate(OverAllState state, ConsultSupport support) {
        ConsultTurn turn = turn(state);
        if (turn.uncovered) {
            turn.kept = List.of();
            support.checkpoint(turn, NODE_SAFETY_GATE);
            return Map.of(KEY_TURN, turn);
        }
        turn.kept = turn.thin ? List.of() : SafetyGate.allow(turn.proposed, turn.diseaseDrugs);
        if (!turn.thin && turn.kept.isEmpty()) {
            turn.kept = turn.diseaseDrugs;
        }
        support.checkpoint(turn, NODE_SAFETY_GATE);
        return Map.of(KEY_TURN, turn);
    }

    private static Map<String, Object> writeMemory(OverAllState state, ConsultSupport support) {
        ConsultTurn turn = turn(state);
        if (ConsultTurn.ROUTE_BLOCKED.equals(turn.route)) {
            if (turn.longTerm == null) {
                turn.longTerm = support.userMemories().recall(turn.session.getBuyerId());
            }
            turn.card.put("memory", support.memoryLine(turn.memory, turn.longTerm));
            support.remember(turn);
            support.finish(turn, "门禁", "红旗拦截", turn.redFlag.phrase(), true);
            support.checkpoint(turn, NODE_WRITE_MEMORY);
            support.tasks().markDone(turn.taskId);
            turn.route = ConsultTurn.ROUTE_DONE;
            return Map.of(KEY_TURN, turn);
        }
        if (ConsultTurn.ROUTE_ASK.equals(turn.route)) {
            support.remember(turn);
            support.finish(turn, "问诊", "追问", turn.reply, null);
            support.checkpoint(turn, NODE_WRITE_MEMORY);
            support.tasks().markWaitingUser(turn.taskId);
            turn.route = ConsultTurn.ROUTE_DONE;
            return Map.of(KEY_TURN, turn);
        }

        if (turn.uncovered) {
            if (support.tryApply(turn, "ltm")) {
                support.userMemories().rememberFromConsult(turn.session.getBuyerId(), turn.history, "", turn.sessionId);
            }
            UserMemoryService.View saved = support.userMemories().recall(turn.session.getBuyerId());
            turn.memory.sync(turn.slots, turn.memory.candidateCode, turn.memory.candidateName);
            turn.memory.conclude("知识库未覆盖", "", "没有关键词或图谱支持的病种");
            turn.memory.pushTurn("助手", turn.reply);
            support.remember(turn);
            String note = turn.directAnswer
                    ? "由模型直接回答，不据此诊断"
                    : (turn.slots.complete() ? "" : "信息未齐。")
                    + (turn.uncoveredGeneral.isBlank() ? "知识库未覆盖，未推荐药品" : "知识库未覆盖，以下由模型自身知识整理");
            turn.card = support.baseCard(false, false, note);
            turn.card.put("plan", "");
            turn.card.put("evidences", support.evidenceViews(turn));
            turn.card.put("memory", support.memoryLine(turn.memory, saved));
            support.finish(turn, "兜底", "模型自身知识已过门禁", "未推荐药品", true);
            if (turn.memory.profileLinked && support.tryApply(turn, "profile:update")) {
                try {
                    support.healthProfiles().updateAfterConsult(turn.session.getBuyerId(), turn.memory.profileId,
                            turn.history + "\n" + turn.reply);
                } catch (Exception ignored) {
                    // 档案回写失败不挡问诊
                }
            }
            support.checkpoint(turn, NODE_WRITE_MEMORY);
            support.tasks().markDone(turn.taskId);
            turn.route = ConsultTurn.ROUTE_DONE;
            return Map.of(KEY_TURN, turn);
        }

        if (support.tryApply(turn, "ltm")) {
            support.userMemories().rememberFromConsult(turn.session.getBuyerId(), turn.history,
                    turn.disease == null ? "" : turn.disease.getCode(), turn.sessionId);
        }
        UserMemoryService.View saved = support.userMemories().recall(turn.session.getBuyerId());
        String caution = saved.caution(turn.disease == null ? "" : turn.disease.getCode());
        if (!caution.isBlank() && !turn.reply.contains("你之前说过") && !turn.reply.contains("你之前提到过")) {
            turn.reply = ConsultSupport.asMarkdownCaution(caution) + turn.reply;
        }
        String drugNames = turn.kept.stream().map(MedDrug::getName).collect(Collectors.joining("、"));
        turn.memory.sync(turn.slots, turn.disease == null ? "" : turn.disease.getCode(),
                turn.disease == null ? "" : turn.disease.getName());
        turn.memory.conclude(turn.disease == null ? "未命名" : turn.disease.getName(), drugNames,
                turn.thin ? "信息未齐，未推荐药品" : "按知识库给出参考");
        turn.memory.pushTurn("助手", turn.reply);
        support.remember(turn);
        boolean degraded = !turn.llmUsed;
        String note = turn.slots.complete() ? turn.bundle.note() : "信息未齐。" + turn.bundle.note();
        turn.card = support.baseCard(false, degraded, note);
        turn.card.put("diagnoses", List.of(Map.of(
                "name", turn.disease == null ? "未命名" : turn.disease.getName(),
                "summary", turn.llmUsed || turn.disease == null ? "" : turn.disease.getSummary())));
        turn.card.put("plan", turn.llmUsed || turn.disease == null ? "" : turn.disease.getAdvice());
        turn.card.put("drugs", turn.kept.stream().map(support::drugView).toList());
        turn.card.put("evidences", support.evidenceViews(turn));
        turn.card.put("keywordDegraded", turn.bundle.keywordDegraded());
        turn.card.put("vectorDegraded", turn.bundle.vectorDegraded());
        turn.card.put("graphDegraded", turn.bundle.graphDegraded());
        turn.card.put("memory", support.memoryLine(turn.memory, saved));
        if (!caution.isBlank()) {
            turn.card.put("chronicCaution", caution);
        }
        support.finish(turn, "门禁", turn.llmUsed ? "模型草稿已过白名单" : "规则方案已过白名单", drugNames, true);
        if (turn.memory.profileLinked && support.tryApply(turn, "profile:update")) {
            try {
                support.healthProfiles().updateAfterConsult(turn.session.getBuyerId(), turn.memory.profileId,
                        turn.history + "\n" + turn.reply);
                support.trace(turn.sessionId, "档案", "问诊回写", turn.content, "已尝试更新电子就诊卡", true);
            } catch (Exception ex) {
                support.trace(turn.sessionId, "档案", "问诊回写", turn.content, "回写跳过：" + ex.getClass().getSimpleName(), false);
            }
        }
        support.checkpoint(turn, NODE_WRITE_MEMORY);
        support.tasks().markDone(turn.taskId);
        turn.route = ConsultTurn.ROUTE_DONE;
        return Map.of(KEY_TURN, turn);
    }
}

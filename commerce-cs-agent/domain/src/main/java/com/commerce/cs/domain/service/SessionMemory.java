package com.commerce.cs.domain.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/** 一次问诊的三层记忆：工作记忆、观察、已经给过的方向。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionMemory {
    public boolean site;
    public boolean duration;
    public boolean fever;
    public int askCount;
    public String candidateCode = "";
    public String candidateName = "";
    public String missing = "";
    public List<Observation> observations = new ArrayList<>();
    public List<Episode> episodes = new ArrayList<>();
    public List<String> recentTurns = new ArrayList<>();
    /** 本会话里尚未确认缓解的红旗。后续别的主诉先正常处理，收口后再追问一次。 */
    public List<OpenFlag> openFlags = new ArrayList<>();
    public boolean flagReminded;
    /** 本会话主动关联的就诊卡。只作用于这一次会话，新会话默认不关联。 */
    public boolean profileLinked;
    public Long profileId;

    public void sync(ConsultIntake.Slots slots, String code, String name) {
        site = slots != null && slots.site();
        duration = slots != null && slots.duration();
        fever = slots != null && slots.fever();
        missing = slots == null ? "" : slots.missingText();
        candidateCode = code == null ? "" : code;
        candidateName = name == null ? "" : name;
    }

    public void pushTurn(String role, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (recentTurns == null) {
            recentTurns = new ArrayList<>();
        }
        recentTurns.add((role == null || role.isBlank() ? "用户" : role) + "：" + clip(text.trim(), 240));
        while (recentTurns.size() > 6) {
            recentTurns.remove(0);
        }
    }

    public String shortTermText() {
        if (recentTurns == null || recentTurns.isEmpty()) {
            return "";
        }
        return String.join("\n", recentTurns);
    }

    public void supplement(String userText) {
        if (episodes.isEmpty() || userText == null || userText.isBlank()) {
            return;
        }
        String extra = userText.trim();
        if (ConsultIntake.stopRequested(extra) && extra.length() <= 12) {
            return;
        }
        Episode last = episodes.get(episodes.size() - 1);
        last.supplement = clip(last.supplement == null || last.supplement.isBlank()
                ? extra : last.supplement + "；" + extra, 180);
    }

    public void ask(String question) {
        askCount++;
        observe("追问", missing.isBlank() ? "信息还不够" : "还缺" + missing, "询问：" + nullToEmpty(question));
    }

    public void conclude(String direction, String drugs, String result) {
        observe("收口", nullToEmpty(result), nullToEmpty(direction));
        episodes.add(new Episode(nullToEmpty(direction), nullToEmpty(drugs), ""));
        while (episodes.size() > 4) {
            episodes.remove(0);
        }
    }

    public void flag(String phrase) {
        observe("红旗", nullToEmpty(phrase), "停止检索和给药");
    }

    public void rememberFlag(String phrase, String message) {
        if (phrase == null || phrase.isBlank()) {
            return;
        }
        if (openFlags == null) {
            openFlags = new ArrayList<>();
        }
        for (OpenFlag item : openFlags) {
            if (phrase.equals(item.phrase)) {
                return;
            }
        }
        openFlags.add(new OpenFlag(phrase.trim(), message == null ? "" : message));
        flagReminded = false;
    }

    public void dropFlag(String phrase) {
        if (openFlags == null || phrase == null) {
            return;
        }
        openFlags.removeIf(item -> phrase.equals(item.phrase));
    }

    public void clearFlags() {
        if (openFlags != null) {
            openFlags.clear();
        }
        flagReminded = false;
    }

    public void observe(String step, String result, String next) {
        if (observations == null) {
            observations = new ArrayList<>();
        }
        observations.add(new Observation(nullToEmpty(step), clip(nullToEmpty(result), 160), clip(nullToEmpty(next), 160)));
        while (observations.size() > 8) {
            observations.remove(0);
        }
    }

    public String promptContext() {
        if (episodes == null || episodes.isEmpty()) {
            return "";
        }
        Episode last = episodes.get(episodes.size() - 1);
        String drugs = last.drugs == null || last.drugs.isBlank() ? "未推荐药品" : "上次药品是" + last.drugs;
        String extra = last.supplement == null || last.supplement.isBlank() ? "" : "用户后来补充：" + last.supplement + "。";
        return "会话记忆：上次参考方向是" + last.direction + "，" + drugs + "。" + extra;
    }

    public String summary() {
        String known = site && duration && fever
                ? "部位、时长、发烧都已了解"
                : (filledText().isBlank() ? "还没有记下部位、时长和发烧" : "已了解" + filledText())
                + (missing == null || missing.isBlank() ? "" : "，还缺" + missing);
        String candidate = candidateName == null || candidateName.isBlank() ? "" : "；当前候选" + candidateName;
        String observed = observations == null || observations.isEmpty()
                ? ""
                : "。最近观察：" + observations.get(observations.size() - 1).result;
        String episode;
        if (episodes == null || episodes.isEmpty()) {
            episode = "。还没有给过参考方向";
        } else {
            Episode last = episodes.get(episodes.size() - 1);
            String drugs = last.drugs == null || last.drugs.isBlank() ? "未推荐药品" : "药品" + last.drugs;
            String extra = last.supplement == null || last.supplement.isBlank() ? "" : "；用户补充过" + last.supplement;
            episode = "。上次参考方向是" + last.direction + "，" + drugs + extra;
        }
        return "短期记忆保留最近 " + (recentTurns == null ? 0 : recentTurns.size()) + " 句。工作记忆："
                + known + "；已追问 " + askCount + " 次" + candidate + observed + episode + "。";
    }

    private String filledText() {
        StringBuilder text = new StringBuilder();
        if (site) {
            text.append("部位");
        }
        if (duration) {
            if (!text.isEmpty()) {
                text.append("、");
            }
            text.append("时长");
        }
        if (fever) {
            if (!text.isEmpty()) {
                text.append("、");
            }
            text.append("发烧");
        }
        return text.toString();
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Observation {
        public String step = "";
        public String result = "";
        public String next = "";

        public Observation() {
        }

        public Observation(String step, String result, String next) {
            this.step = step;
            this.result = result;
            this.next = next;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Episode {
        public String direction = "";
        public String drugs = "";
        public String supplement = "";

        public Episode() {
        }

        public Episode(String direction, String drugs, String supplement) {
            this.direction = direction;
            this.drugs = drugs;
            this.supplement = supplement;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenFlag {
        public String phrase = "";
        public String message = "";

        public OpenFlag() {
        }

        public OpenFlag(String phrase, String message) {
            this.phrase = phrase;
            this.message = message;
        }
    }
}

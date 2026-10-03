package com.commerce.cs.server.medical;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;

/** 病-症-资料图谱。Neo4j 没启动时由检索门面降级，不在这里伪造结果。 */
@Component
public class Neo4jGraphClient implements AutoCloseable {
    private static final String UPSERT_DISEASE = """
            MERGE (d:Disease {code: $code})
            SET d.name = $name, d.summary = $summary, d.sourceName = $sourceName, d.sourceUrl = $sourceUrl
            WITH d
            UNWIND $symptoms AS symptom
            MERGE (s:Symptom {name: symptom})
            MERGE (d)-[:HAS_SYMPTOM]->(s)
            """;
    private static final String UPSERT_ARTICLE = """
            MERGE (a:Article {file: $file})
            SET a.title = $title, a.diseaseCode = $code, a.sourceName = $sourceName, a.sourceUrl = $sourceUrl
            WITH a
            MATCH (d:Disease {code: $code})
            MERGE (a)-[:ABOUT]->(d)
            """;
    private static final String SEARCH = """
            MATCH (s:Symptom)
            WHERE $q CONTAINS s.name AND size(s.name) >= 2
            MATCH (d:Disease)-[:HAS_SYMPTOM]->(s)
            WITH d, count(DISTINCT s) AS hits, collect(DISTINCT s.name) AS names
            OPTIONAL MATCH (a:Article)-[:ABOUT]->(d)
            RETURN d.code AS diseaseCode, d.name AS title, d.summary AS summary,
                   d.sourceName AS sourceName, d.sourceUrl AS sourceUrl,
                   hits, names, collect(DISTINCT a.title) AS titles
            ORDER BY hits DESC, d.code ASC
            LIMIT 8
            """;
    private static final String DROP_SHORT_ALIAS = """
            MATCH (d:Disease {code:'URTICARIA'})-[r:HAS_SYMPTOM]->(s:Symptom {name:'过敏'})
            DELETE r
            """;
    private static final String LINK_DIFFERENTIAL = """
            MATCH (a:Disease {code: $from}), (b:Disease {code: $to})
            MERGE (a)-[:鉴别]->(b)
            MERGE (b)-[:鉴别]->(a)
            """;
    private static final String DIFFERENTIAL = """
            MATCH (d:Disease)-[:鉴别]-(o:Disease)
            WHERE d.code IN $codes
            RETURN d.code AS fromCode, o.code AS diseaseCode, o.name AS title, o.summary AS summary,
                   o.sourceName AS sourceName, o.sourceUrl AS sourceUrl
            """;

    private final RagProperties properties;
    private volatile Driver driver;
    private volatile boolean seeded;

    public Neo4jGraphClient(RagProperties properties) {
        this.properties = properties;
    }

    public List<RagFacade.Hit> search(String query) {
        String text = query == null ? "" : query.replace(" ", "").replace("\n", "");
        if (text.isEmpty()) {
            return List.of();
        }
        Driver current = driver();
        ensureSeed(current);
        try (Session session = current.session()) {
            List<Record> records = session.executeRead(tx -> tx.run(SEARCH, Map.of("q", text)).list());
            List<RagFacade.Hit> hits = new ArrayList<>();
            java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
            for (Record record : records) {
                String code = record.get("diseaseCode").asString("");
                if (!seen.add(code)) {
                    continue;
                }
                hits.add(directHit(record));
            }
            if (!seen.isEmpty() && hits.size() < 8) {
                List<String> codes = List.copyOf(seen);
                List<Record> related = session.executeRead(tx -> tx.run(DIFFERENTIAL, Map.of("codes", codes)).list());
                java.util.Map<String, Integer> order = new java.util.HashMap<>();
                for (int i = 0; i < codes.size(); i++) {
                    order.put(codes.get(i), i);
                }
                related.sort(java.util.Comparator.comparingInt(record ->
                        order.getOrDefault(record.get("fromCode").asString(""), Integer.MAX_VALUE)));
                for (Record record : related) {
                    String code = record.get("diseaseCode").asString("");
                    if (code.isBlank() || !seen.add(code)) {
                        continue;
                    }
                    hits.add(differentialHit(record));
                    if (hits.size() >= 8) {
                        break;
                    }
                }
            }
            return hits;
        }
    }

    private static RagFacade.Hit directHit(Record record) {
        String names = String.join("、", record.get("names").asList(org.neo4j.driver.Value::asString));
        String titles = String.join("、", record.get("titles").asList(org.neo4j.driver.Value::asString));
        String body = record.get("summary").asString("")
                + (names.isBlank() ? "" : " 图谱命中症状：" + names + "。")
                + (titles.isBlank() ? "" : " 相关资料：" + titles + "。");
        return new RagFacade.Hit("图谱", record.get("title").asString(""), body,
                record.get("sourceName").asString(""), record.get("sourceUrl").asString(""),
                record.get("diseaseCode").asString(""));
    }

    private static RagFacade.Hit differentialHit(Record record) {
        String body = "鉴别对照。" + record.get("summary").asString("");
        return new RagFacade.Hit("图谱", record.get("title").asString(""), body,
                record.get("sourceName").asString(""), record.get("sourceUrl").asString(""),
                record.get("diseaseCode").asString(""));
    }

    private Driver driver() {
        Driver current = driver;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (driver == null) {
                driver = GraphDatabase.driver(properties.getNeo4jUrl(), AuthTokens.none(),
                        org.neo4j.driver.Config.builder()
                                .withConnectionTimeout(800, TimeUnit.MILLISECONDS)
                                .build());
            }
            return driver;
        }
    }

    private void ensureSeed(Driver current) {
        if (seeded) {
            return;
        }
        synchronized (this) {
            if (seeded) {
                return;
            }
            current.verifyConnectivity();
            try (Session session = current.session()) {
                session.executeWrite(tx -> {
                    for (DiseaseSeed disease : diseases()) {
                        tx.run(UPSERT_DISEASE, Map.of(
                                "code", disease.code(),
                                "name", disease.name(),
                                "summary", disease.summary(),
                                "sourceName", disease.sourceName(),
                                "sourceUrl", disease.sourceUrl(),
                                "symptoms", disease.symptoms())).consume();
                    }
                    for (ArticleSeed article : articles()) {
                        tx.run(UPSERT_ARTICLE, Map.of(
                                "file", article.file(),
                                "title", article.title(),
                                "code", article.code(),
                                "sourceName", article.sourceName(),
                                "sourceUrl", article.sourceUrl())).consume();
                    }
                    tx.run(DROP_SHORT_ALIAS).consume();
                    for (String[] pair : new String[][]{
                            {"RHINITIS", "URI"},
                            {"GASTRO", "FOOD"},
                            {"URTICARIA", "ECZEMA"},
                            {"APHTHOUS", "HFMD"}
                    }) {
                        tx.run(LINK_DIFFERENTIAL, Map.of("from", pair[0], "to", pair[1])).consume();
                    }
                    return null;
                });
            }
            seeded = true;
        }
    }

    @Override
    @PreDestroy
    public void close() {
        Driver current = driver;
        if (current != null) {
            current.close();
        }
    }

    private record DiseaseSeed(String code, String name, String summary, String sourceName, String sourceUrl, List<String> symptoms) {
    }

    private record ArticleSeed(String file, String title, String code, String sourceName, String sourceUrl) {
    }

    private static List<DiseaseSeed> diseases() {
        return List.of(
                disease("URI", "普通感冒样上呼吸道感染",
                        "以咽痛、流涕、鼻塞、咳嗽为主，可以有低热，全身症状通常较轻。",
                        "中国疾病预防控制中心",
                        "https://www.chinacdc.cn/jkyj/mygh02/ymkyfjb/lg/202606/t20260622_1837279.html",
                        "喉咙痛", "咽痛", "低烧", "流涕", "鼻塞", "咳嗽", "打喷嚏", "普通感冒", "嗓子疼", "低热", "流鼻涕"),
                disease("FLU", "流感样症状",
                        "除呼吸道症状外，常有高热、寒战、头痛、全身酸痛和明显乏力。只靠症状不能确诊。",
                        "中国国家流感中心",
                        "https://ivdc.chinacdc.cn/cnic/lgwd/ptlg/201912/t20191225_209365.htm",
                        "高烧", "寒战", "全身酸痛", "流感", "发高烧", "高热"),
                disease("URTICARIA", "荨麻疹样过敏",
                        "皮肤突然起风团、明显瘙痒，可在数小时内消退。呼吸困难时要按急症处理。",
                        "中华医学会科学普及部",
                        "https://www.cma.org.cn/art/2023/4/3/art_4584_50197.html",
                        "风团", "荨麻疹", "很痒", "风疙瘩", "瘙痒"),
                disease("GASTRO", "急性胃肠炎样",
                        "腹泻、呕吐为主，诺如病毒胃肠炎传染性强，首先防止脱水。",
                        "中国疾控中心病毒病预防控制所",
                        "https://ivdc.chinacdc.cn/jkzt/kpzs/202407/t20240729_287173.htm",
                        "拉肚子", "腹泻", "呕吐", "水样便", "胃肠炎", "诺如", "上吐下泻"),
                disease("MIGRAINE", "偏头痛样头痛",
                        "一侧搏动性头痛，可怕光、怕声，可伴恶心。突然爆发的剧烈头痛不属于这里。",
                        "世界卫生组织",
                        "https://www.who.int/zh/news-room/fact-sheets/detail/headache-disorders",
                        "偏头痛", "怕光", "一侧头痛", "太阳穴"),
                disease("RHINITIS", "过敏性鼻炎",
                        "鼻子痒、连续打喷嚏、大量清水样鼻涕，通常不发烧。花粉季节容易反复。",
                        "中华医学会科学普及部",
                        "https://www.cma.org.cn/art/2022/12/5/art_4584_48543.html",
                        "过敏性鼻炎", "清水样鼻涕", "鼻子痒", "花粉", "连续打喷嚏"),
                disease("HFMD", "手足口病",
                        "儿童手、足、口腔出现疱疹或皮疹，可发热。精神很差、出冷汗、四肢发凉要尽快就医。",
                        "中国疾病预防控制中心",
                        "https://www.chinacdc.cn/jkkp/crb/bcr/202411/t20241114_302648.html",
                        "手足口", "口腔疱疹", "手上疱疹", "手足口病", "疱疹"),
                disease("CONJUNCTIVITIS", "急性出血性结膜炎",
                        "俗称红眼病，眼红、流泪、畏光，传染性强，学校和泳池容易集中出现。",
                        "中国疾病预防控制中心",
                        "https://www.chinacdc.cn/jkkp/crb/bcr/202511/t20251117_313511.html",
                        "红眼", "结膜炎", "红眼病", "眼红"),
                disease("FOOD", "食物中毒",
                        "进食不洁食物后短时间内恶心、呕吐、腹痛、腹泻，同餐的人可一起发病。",
                        "中国疾病预防控制中心",
                        "https://www.chinacdc.cn/jkyj/tfggws/jswj1_14714/202603/t20260303_315260.html",
                        "食物中毒", "剩菜", "同餐"),
                disease("COUGH", "婴幼儿咳嗽",
                        "宝宝咳嗽不一定马上用药。没有发烧、精神好时可以观察；发热并精神差要去医院。",
                        "中华医学会科学普及部",
                        "https://www.cma.org.cn/art/2022/10/9/art_4584_47761.html",
                        "宝宝咳嗽", "婴幼儿咳嗽", "咳嗽很久", "小孩咳", "咳了", "宝宝咳"),
                disease("ECZEMA", "湿疹样皮炎",
                        "皮肤红斑、丘疹、瘙痒，可有干燥或少量渗出。婴幼儿和屈侧皮肤较常见。",
                        "科普中国网 / 福棠儿童用药咨询中心",
                        "https://www.kepuchina.cn/article/articleinfo?ar_id=477505&business_type=100&classify=0",
                        "湿疹", "皮肤痒", "红斑丘疹", "宝宝湿疹", "皮肤干燥痒"),
                disease("CONSTIPATION", "便秘",
                        "排便次数少、粪便干硬、排便费力或有不尽感。久坐和膳食纤维不足常见。",
                        "北京市卫生健康委员会 / 北京友谊医院",
                        "https://wjw.beijing.gov.cn/bmfw_20143/jkzs/jksh/202503/t20250331_4051000.html",
                        "便秘", "排便困难", "大便干硬", "好几天没大便", "通便"),
                disease("APHTHOUS", "口腔溃疡",
                        "口腔黏膜圆形或椭圆形溃疡，周围红晕，表面可有黄白假膜，疼痛明显。多数可自限。",
                        "央视网 / 北京大学口腔医院",
                        "https://news.cctv.cn/2025/07/10/ARTIcGNH0uwV9DZ0pHxOkY7K250710.shtml",
                        "口腔溃疡", "嘴破了", "口疮", "舌头溃疡", "嘴里溃疡"),
                disease("CARIES", "龋病样牙痛",
                        "蛀牙引起的牙痛、遇冷热敏感，或牙齿出现黑点、小洞。止痛不能代替看牙。",
                        "中国疾病预防控制中心",
                        "https://www.chinacdc.cn/jkkp/mxfcrb/kqjk/202408/t20240823_295142.html",
                        "牙痛", "蛀牙", "龋齿", "牙齿疼", "遇冷热疼", "虫牙"),
                disease("CHEST", "胸痛警示",
                        "突然胸痛、出冷汗要马上急诊。这里只保留警示，不提供用药。",
                        "世界卫生组织",
                        "https://www.who.int/zh/news-room/fact-sheets/detail/cardiovascular-diseases-(cvds)",
                        "胸口疼", "胸痛", "出冷汗", "心脏病")
        );
    }

    private static List<ArticleSeed> articles() {
        return List.of(
                article("01-流感与普通感冒的区别.md", "流感和普通感冒有什么区别", "URI",
                        "中国疾病预防控制中心", "https://www.chinacdc.cn/jkyj/mygh02/ymkyfjb/lg/202606/t20260622_1837279.html"),
                article("02-春季呼吸道病毒感染.md", "春夏之交的呼吸道病毒感染", "URI",
                        "中国疾病预防控制中心", "https://www.chinacdc.cn/jkkp/crb/qtcr/202605/t20260511_1835710.html"),
                article("03-流感一般有什么症状.md", "得了流感一般有什么症状", "FLU",
                        "中国国家流感中心", "https://ivdc.chinacdc.cn/cnic/lgwd/ptlg/201912/t20191225_209365.htm"),
                article("04-诺如病毒引起的急性胃肠炎.md", "诺如病毒引起的急性胃肠炎", "GASTRO",
                        "中国疾控中心病毒病预防控制所", "https://ivdc.chinacdc.cn/jkzt/kpzs/202407/t20240729_287173.htm"),
                article("05-头痛疾患.md", "头痛疾患", "MIGRAINE",
                        "世界卫生组织", "https://www.who.int/zh/news-room/fact-sheets/detail/headache-disorders"),
                article("06-心血管疾病与胸痛警示.md", "心血管疾病", "CHEST",
                        "世界卫生组织", "https://www.who.int/zh/news-room/fact-sheets/detail/cardiovascular-diseases-(cvds)"),
                article("07-荨麻疹.md", "疙瘩来无影去无踪，还伴有瘙痒，会是怎么回事？", "URTICARIA",
                        "中华医学会科学普及部", "https://www.cma.org.cn/art/2023/4/3/art_4584_50197.html"),
                article("08-过敏性鼻炎还是感冒.md", "过敏性鼻炎还是感冒？主要看这几点！", "RHINITIS",
                        "中华医学会科学普及部", "https://www.cma.org.cn/art/2022/12/5/art_4584_48543.html"),
                article("09-手足口病.md", "秋冬季到来，做好防护远离手足口病", "HFMD",
                        "中国疾病预防控制中心", "https://www.chinacdc.cn/jkkp/crb/bcr/202411/t20241114_302648.html"),
                article("10-急性出血性结膜炎.md", "急性出血性结膜炎（红眼病）", "CONJUNCTIVITIS",
                        "中国疾病预防控制中心", "https://www.chinacdc.cn/jkkp/crb/bcr/202511/t20251117_313511.html"),
                article("11-食物中毒.md", "春节饮食安全指南", "FOOD",
                        "中国疾病预防控制中心", "https://www.chinacdc.cn/jkyj/tfggws/jswj1_14714/202603/t20260303_315260.html"),
                article("12-婴幼儿咳嗽.md", "婴幼儿咳嗽家长应该如何面对呢？", "COUGH",
                        "中华医学会科学普及部", "https://www.cma.org.cn/art/2022/10/9/art_4584_47761.html"),
                article("13-湿疹用药常识.md", "药师教您如何轻松应对湿疹", "ECZEMA",
                        "科普中国网 / 福棠儿童用药咨询中心",
                        "https://www.kepuchina.cn/article/articleinfo?ar_id=477505&business_type=100&classify=0"),
                article("14-便秘日常指南.md", "世界便秘日｜肠道通畅，生活轻松：便秘患者的日常指南", "CONSTIPATION",
                        "北京市卫生健康委员会 / 北京友谊医院",
                        "https://wjw.beijing.gov.cn/bmfw_20143/jkzs/jksh/202503/t20250331_4051000.html"),
                article("15-口腔溃疡就医提醒.md", "口腔溃疡短期反复需警惕！这些情况需就医", "APHTHOUS",
                        "央视网 / 北京大学口腔医院",
                        "https://news.cctv.cn/2025/07/10/ARTIcGNH0uwV9DZ0pHxOkY7K250710.shtml"),
                article("16-龋病与牙痛提醒.md", "龋病、牙龈炎……青少年常见口腔问题如何防治", "CARIES",
                        "中国疾病预防控制中心",
                        "https://www.chinacdc.cn/jkkp/mxfcrb/kqjk/202408/t20240823_295142.html")
        );
    }

    private static DiseaseSeed disease(String code, String name, String summary, String sourceName, String sourceUrl, String... symptoms) {
        return new DiseaseSeed(code, name, summary, sourceName, sourceUrl, List.of(symptoms));
    }

    private static ArticleSeed article(String file, String title, String code, String sourceName, String sourceUrl) {
        return new ArticleSeed(file, title, code, sourceName, sourceUrl);
    }
}

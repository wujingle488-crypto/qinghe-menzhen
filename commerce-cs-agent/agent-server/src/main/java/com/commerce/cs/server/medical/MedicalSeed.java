package com.commerce.cs.server.medical;

import com.commerce.cs.domain.entity.MedArticle;
import com.commerce.cs.domain.entity.MedDisease;
import com.commerce.cs.domain.entity.MedDrug;
import com.commerce.cs.domain.entity.MedRedFlag;
import com.commerce.cs.domain.entity.MedSymptom;
import java.util.List;

/** 首批病种。正文是依据公开科普改写的教学摘要，不转载指南全文。 */
final class MedicalSeed {
    static final String CDC_FLU = "https://www.chinacdc.cn/jkyj/mygh02/ymkyfjb/lg/";
    static final String NHC = "https://www.nhc.gov.cn/";
    static final String NMPA = "https://www.nmpa.gov.cn/";

    private MedicalSeed() {
    }

    static List<MedDisease> diseases() {
        return List.of(
                disease("URI", "普通感冒样上呼吸道感染",
                        "以咽痛、流涕、鼻塞、咳嗽为主，可以有低热，全身症状通常较轻，多数可以自限。",
                        "休息、多喝水，按症状处理。不要自行使用抗生素。高热不退、呼吸费力或症状明显加重时去医院。",
                        "中国疾控中心流感专题中对普通感冒的公开比较（教学摘编）", CDC_FLU),
                disease("FLU", "流感样症状",
                        "流感由流感病毒引起。除呼吸道症状外，常有高热、寒战、头痛、全身酸痛和明显乏力。只靠症状不能确诊。",
                        "疑似流感不要自行使用抗病毒药。这里只给出库内对症退热建议，并提示尽早就医评估。",
                        "中国疾控中心流感专题（教学摘编，非诊疗方案全文）", CDC_FLU),
                disease("URTICARIA", "荨麻疹样过敏",
                        "皮肤突然起风团、明显瘙痒，可伴红斑。需要同时排除呼吸困难和喉头水肿。",
                        "避开可疑诱因。库内抗过敏药只用于轻度瘙痒。呼吸困难、声音嘶哑或头晕时立即急诊。",
                        "门诊健康教育要点（教学摘编）", NHC),
                disease("GASTRO", "急性胃肠炎样",
                        "腹泻、呕吐、腹部不适，可有低热。首先防止脱水。血便、尿少或精神很差时不要在家硬扛。",
                        "少量多次补液。止泻药不能代替补液，也不能用于疑似梗阻。",
                        "门诊健康教育要点（教学摘编）", NHC),
                disease("MIGRAINE", "偏头痛样头痛",
                        "一侧搏动性头痛，可怕光、怕声，恶心。突然爆发的剧烈头痛不属于这里。",
                        "先到安静暗处休息。库内止痛药有胃和妊娠禁忌。头痛伴颈强、视物不清或越来越重应就医。",
                        "门诊健康教育要点（教学摘编）", NHC),
                disease("RHINITIS", "过敏性鼻炎",
                        "鼻子痒、连续打喷嚏、大量清水样鼻涕，通常不发烧。花粉季节容易反复。",
                        "避开花粉和尘螨，不要按感冒自行使用抗生素。轻度鼻痒流涕可参考库内抗过敏药。喘憋或症状持续时去医院。",
                        "中华医学会科学普及部", "https://www.cma.org.cn/art/2022/12/5/art_4584_48543.html"),
                disease("HFMD", "手足口病",
                        "儿童手、足、口腔出现疱疹或皮疹，可发热。精神很差、出冷汗、四肢发凉要尽快就医。",
                        "注意隔离和洗手。这里不推荐处方抗病毒药或抗生素。出现精神差、出冷汗或四肢发凉时马上就医。",
                        "中国疾病预防控制中心", "https://www.chinacdc.cn/jkkp/crb/bcr/202411/t20241114_302648.html"),
                disease("CONJUNCTIVITIS", "急性出血性结膜炎",
                        "俗称红眼病，眼红、流泪，传染性强。不要和普通结膜炎或过敏混为一谈。",
                        "不要共用毛巾，不要自行使用抗生素眼药水。干涩不适可参考库内人工泪液。视力下降或疼痛明显时去医院。",
                        "中国疾病预防控制中心", "https://www.chinacdc.cn/jkkp/crb/bcr/202511/t20251117_313511.html"),
                disease("FOOD", "食物中毒",
                        "进食不洁食物后短时间内恶心、呕吐、腹痛、腹泻，同餐的人可以一起发病。",
                        "停止食用可疑食物并留样。能喝就先补液。血便、高热或尿少时去医院。不要只靠止泻药处理。",
                        "中国疾病预防控制中心", "https://www.chinacdc.cn/jkyj/tfggws/jswj1_14714/202603/t20260303_315260.html"),
                disease("COUGH", "婴幼儿咳嗽",
                        "宝宝咳嗽不一定马上用药。没有发烧、精神好时可以观察。",
                        "不要自行强力止咳或滥用抗生素。发热、喘息或精神差时去医院。",
                        "中华医学会科学普及部", "https://www.cma.org.cn/art/2022/10/9/art_4584_47761.html"),
                disease("ECZEMA", "湿疹样皮炎",
                        "皮肤红斑、丘疹、瘙痒，可有干燥或少量渗出。婴幼儿和屈侧皮肤较常见。",
                        "先保湿。轻度局部瘙痒可参考库内外用药。大面积破溃、渗液多或发热时去医院，不要长期大面积用强效激素。",
                        "科普中国网 / 福棠儿童用药咨询中心",
                        "https://www.kepuchina.cn/article/articleinfo?ar_id=477505&business_type=100&classify=0"),
                disease("CONSTIPATION", "便秘",
                        "排便次数少、粪便干硬、排便费力或有不尽感。久坐和膳食纤维不足常见。",
                        "先增加膳食纤维、饮水和定时排便。轻度可参考库内温和通便药。便血、剧烈腹痛或长期依赖泻药时去医院。",
                        "北京市卫生健康委员会 / 北京友谊医院",
                        "https://wjw.beijing.gov.cn/bmfw_20143/jkzs/jksh/202503/t20250331_4051000.html"),
                disease("APHTHOUS", "口腔溃疡",
                        "口腔黏膜圆形或椭圆形溃疡，周围红晕，表面可有黄白假膜，疼痛明显。多数可自限。",
                        "少吃刺激食物。轻度疼痛可参考库内局部或营养补充建议。溃疡超过一个月、形状不规则或伴全身症状时去医院。",
                        "央视网 / 北京大学口腔医院",
                        "https://news.cctv.cn/2025/07/10/ARTIcGNH0uwV9DZ0pHxOkY7K250710.shtml"),
                disease("CARIES", "龋病样牙痛",
                        "蛀牙引起的牙痛、遇冷热敏感，或牙齿出现黑点、小洞。止痛不能代替看牙。",
                        "成人轻度牙痛可短期参考库内退热镇痛药缓解。面部肿胀、高热或剧痛难忍时去口腔科，不要自行使用抗生素。",
                        "中国疾病预防控制中心",
                        "https://www.chinacdc.cn/jkkp/mxfcrb/kqjk/202408/t20240823_295142.html")
        );
    }

    static List<MedSymptom> symptoms() {
        return List.of(
                symptom("URI", "喉咙痛,咽痛,低烧,流涕,鼻塞,咳嗽,打喷嚏,嗓子疼,低热,流鼻涕"),
                symptom("FLU", "高烧,寒战,全身酸痛,流感,乏力明显"),
                symptom("URTICARIA", "风团,荨麻疹,很痒,皮疹"),
                symptom("GASTRO", "拉肚子,腹泻,呕吐,水样便,胃肠炎,肚子疼"),
                symptom("MIGRAINE", "偏头痛,一侧头痛,怕光,头痛"),
                symptom("RHINITIS", "过敏性鼻炎,清水样鼻涕,鼻子痒,花粉,连续打喷嚏"),
                symptom("HFMD", "手足口,口腔疱疹,手上疱疹,手足口病,疱疹"),
                symptom("CONJUNCTIVITIS", "红眼,红眼病,结膜炎,眼红,急性出血性结膜炎"),
                symptom("FOOD", "食物中毒,剩菜,同餐,吃坏了"),
                symptom("COUGH", "宝宝咳嗽,婴幼儿咳嗽,小孩咳,咳了"),
                symptom("ECZEMA", "湿疹,皮肤痒,红斑丘疹,宝宝湿疹,皮肤干燥痒"),
                symptom("CONSTIPATION", "便秘,排便困难,大便干硬,好几天没大便,通便"),
                symptom("APHTHOUS", "口腔溃疡,嘴破了,口疮,舌头溃疡,嘴里溃疡"),
                symptom("CARIES", "牙痛,蛀牙,龋齿,牙齿疼,遇冷热疼,虫牙")
        );
    }

    static List<MedDrug> drugs() {
        return List.of(
                drug("对乙酰氨基酚", "URI", "成人按说明书剂量退热镇痛，两次用药留出间隔。",
                        "肝病、饮酒过多时不要自行加量；不要和含同一成分的复方感冒药叠用。", NMPA),
                drug("对乙酰氨基酚", "FLU", "仅用于退热和缓解酸痛，按说明书剂量。",
                        "不能代替对流感的就医评估；肝病患者不要自行加量。", NMPA),
                drug("氯雷他定", "URTICARIA", "成人轻度瘙痒可按说明书口服。",
                        "可能嗜睡，驾车注意；肝功能异常者慎用。呼吸困难时不要靠它代替急救。", NMPA),
                drug("口服补液盐", "GASTRO", "按说明书冲开，少量多次喝。",
                        "不能代替静脉补液。尿少、血便或喝了就吐时应就医。", NMPA),
                drug("蒙脱石散", "GASTRO", "腹泻时按说明书冲服。",
                        "与其他药间隔约两小时。怀疑肠梗阻时不要用。", NMPA),
                drug("布洛芬", "MIGRAINE", "无禁忌时按说明书用于疼痛。",
                        "胃溃疡、消化道出血史和孕晚期不宜；哮喘患者慎用。", NMPA),
                drug("氯雷他定", "RHINITIS", "成人轻度鼻痒、喷嚏、清水样鼻涕可按说明书口服。",
                        "不能代替避开过敏原；嗜睡者慎用。喘憋时不要只靠抗过敏药。", NMPA),
                drug("人工泪液", "CONJUNCTIVITIS", "眼干、异物感时可按说明书点用，润滑结膜。",
                        "不能代替抗感染治疗。视力下降、眼痛加重或分泌物明显增多时去医院。", NMPA),
                drug("口服补液盐", "FOOD", "进食不洁食物后腹泻呕吐、还能喝水时，按说明书少量多次补液。",
                        "不能代替就医。血便、高热、尿少或精神差时马上去医院。", NMPA),
                drug("炉甘石洗剂", "ECZEMA", "无大量渗出的轻度瘙痒皮损，按说明书外涂。",
                        "皮肤破溃渗液多时不宜。大面积或反复发作应就医，不要长期当唯一治疗。", NMPA),
                drug("氢化可的松乳膏", "ECZEMA", "轻度局限性皮损可按说明书薄涂，疗程宜短。",
                        "面部、褶皱处慎用；不要大面积或长期连用。儿童用药需特别谨慎。", NMPA),
                drug("乳果糖", "CONSTIPATION", "成人轻度便秘可按说明书口服，作用相对温和。",
                        "腹痛、呕吐或怀疑肠梗阻时不要用。不要长期依赖泻药代替就医。", NMPA),
                drug("维生素B2", "APHTHOUS", "轻度口腔溃疡不适时可按说明书口服补充。",
                        "不能根治溃疡。溃疡超过一个月、形状不规则或伴发热时去医院。", NMPA),
                drug("对乙酰氨基酚", "CARIES", "成人轻度牙痛可短期按说明书缓解不适。",
                        "止痛不能代替看牙。面部肿胀、高热或剧痛时去口腔科，不要自行使用抗生素。", NMPA)
        );
    }

    static List<MedRedFlag> redFlags() {
        return List.of(
                flag("胸口疼", "突然胸痛或胸口疼，需要马上就医。"),
                flag("胸痛", "胸痛需要马上就医，排除危及生命的情况。"),
                flag("大出血", "大量出血不能在这里处理。"),
                flag("吐血", "呕血需要急诊。"),
                flag("意识不清", "意识不清需要立即急救。"),
                flag("叫不醒", "叫不醒需要立即急救。"),
                flag("昏迷", "昏迷需要立即急救。"),
                flag("呼吸困难", "呼吸困难需要立即就医。"),
                flag("喘不上气", "喘不上气需要立即就医。"),
                flag("喉头水肿", "喉头水肿可能危及呼吸，请立即急诊。")
        );
    }

    static List<MedArticle> articles() {
        return List.of(
                article("URI", "普通感冒和流感不要混为一谈",
                        "普通感冒多为鼻病毒等引起，以咽痛、流涕、鼻塞、咳嗽为主，全身症状轻。没有特效抗病毒药，重点是休息和对症，避免滥用抗生素。",
                        "感冒,咽痛,流涕,抗生素", "中国疾控中心公开科普教学摘编", CDC_FLU),
                article("FLU", "流感更常有全身症状",
                        "流感除呼吸道症状外，常伴高热、寒战、头痛、全身肌肉关节酸痛和乏力，重症可以出现肺炎等并发症。症状不能代替实验室确诊。",
                        "流感,高烧,全身酸痛", "中国疾控中心公开科普教学摘编", CDC_FLU),
                article("URTICARIA", "风团瘙痒先看呼吸",
                        "急性风团和瘙痒多数可以先对症。一旦合并呼吸困难、声音嘶哑、腹痛剧烈或头晕，按急症处理，不要只吃抗过敏药在家观察。",
                        "风团,瘙痒,呼吸困难", "教学健康教育摘要", NHC),
                article("GASTRO", "腹泻先补液",
                        "急性腹泻和呕吐容易脱水。能喝的时候用口服补液，少量多次。出现血便、高热、尿少或精神差，应去医院而不是只吃止泻药。",
                        "腹泻,呕吐,补液", "教学健康教育摘要", NHC),
                article("MIGRAINE", "偏头痛样疼痛的边界",
                        "一侧头痛、怕光，休息后可能减轻。突然出现的生平最严重头痛、伴肢体无力、言语不清或颈项强直，不属于普通偏头痛处理范围。",
                        "偏头痛,怕光,头痛", "教学健康教育摘要", NHC),
                article("RHINITIS", "过敏性鼻炎和感冒的区别",
                        "鼻子痒、连续打喷嚏、大量清水样鼻涕，通常不发烧。不要按感冒自行使用抗生素。",
                        "过敏性鼻炎,清水样鼻涕,花粉", "中华医学会科学普及部", "https://www.cma.org.cn/art/2022/12/5/art_4584_48543.html"),
                article("HFMD", "手足口病的居家注意",
                        "儿童手、足、口腔出现疱疹时注意隔离和洗手。精神差、出冷汗或四肢发凉要马上就医。",
                        "手足口,口腔疱疹", "中国疾病预防控制中心", "https://www.chinacdc.cn/jkkp/crb/bcr/202411/t20241114_302648.html"),
                article("CONJUNCTIVITIS", "红眼病不要自行点抗生素",
                        "急性出血性结膜炎传染性强，不要共用毛巾。这里不推荐自行使用抗生素眼药水。",
                        "红眼,结膜炎", "中国疾病预防控制中心", "https://www.chinacdc.cn/jkkp/crb/bcr/202511/t20251117_313511.html"),
                article("FOOD", "食物中毒先停食并留样",
                        "进食不洁食物后短时间出现呕吐腹泻，同餐者可一起发病。停止食用可疑食物并补液，血便或尿少时去医院。",
                        "食物中毒,同餐", "中国疾病预防控制中心", "https://www.chinacdc.cn/jkyj/tfggws/jswj1_14714/202603/t20260303_315260.html"),
                article("COUGH", "婴幼儿咳嗽不要自行强力止咳",
                        "宝宝咳嗽、没有发烧且精神好时可以观察。发热、喘息或精神差时去医院。",
                        "宝宝咳嗽,婴幼儿咳嗽", "中华医学会科学普及部", "https://www.cma.org.cn/art/2022/10/9/art_4584_47761.html"),
                article("ECZEMA", "湿疹先保湿再外用",
                        "湿疹常见红斑丘疹和瘙痒。先足量润肤。轻度无渗出可用炉甘石；局限皮损可短程弱效激素乳膏。大面积破溃要就医。",
                        "湿疹,皮肤痒,氢化可的松", "科普中国网 / 福棠儿童用药咨询中心",
                        "https://www.kepuchina.cn/article/articleinfo?ar_id=477505&business_type=100&classify=0"),
                article("CONSTIPATION", "便秘先吃纤维再考虑温和通便",
                        "便秘常见大便干硬和排便费力。先增加膳食纤维和饮水。轻度可参考乳果糖。便血或剧烈腹痛要就医，避免长期刺激性泻药。",
                        "便秘,排便困难,乳果糖", "北京市卫生健康委员会 / 北京友谊医院",
                        "https://wjw.beijing.gov.cn/bmfw_20143/jkzs/jksh/202503/t20250331_4051000.html"),
                article("APHTHOUS", "口腔溃疡多数可自愈",
                        "口腔溃疡疼痛明显但多数一两周自愈。少吃刺激食物，轻度可补充维生素B2。超过一个月或不规则溃疡要就医。",
                        "口腔溃疡,口疮,维生素B2", "央视网 / 北京大学口腔医院",
                        "https://news.cctv.cn/2025/07/10/ARTIcGNH0uwV9DZ0pHxOkY7K250710.shtml"),
                article("CARIES", "牙痛只能临时止痛",
                        "龋病引起的牙痛、遇冷热敏感要尽快看牙。成人轻度不适可短期用对乙酰氨基酚，不要自行使用抗生素。",
                        "牙痛,蛀牙,龋齿", "中国疾病预防控制中心",
                        "https://www.chinacdc.cn/jkkp/mxfcrb/kqjk/202408/t20240823_295142.html"),
                article("EXAM", "血常规检查解读",
                        "血常规用于了解白细胞、红细胞和血小板的大致情况。单项轻度异常不能自行下结论，发热、出血或结果明显异常时由医生结合症状判断。",
                        "血常规,检查,化验", "教学健康教育摘要", NHC),
                article("DRUG", "常见退热药的使用注意",
                        "成人咽痛或发热时，可按说明书选择对乙酰氨基酚或布洛芬中的一种，不要叠加。儿童剂量、肝病、胃病和过敏情况要先看说明书。",
                        "退热药,布洛芬,对乙酰氨基酚,用药", "常见非处方药说明书要点（教学摘编）", NMPA),
                article("TCM", "日常起居与饮食调理",
                        "起居规律、饮食清淡、适度活动和保持睡眠，是日常调理的基础。明显不适、高热或症状加重时，不以食疗或调理代替就医。",
                        "中医,调理,饮食", "教学健康教育摘要", NHC)
        );
    }

    private static MedDisease disease(String code, String name, String summary, String advice, String sourceName, String sourceUrl) {
        MedDisease disease = new MedDisease();
        disease.setCode(code);
        disease.setName(name);
        disease.setSummary(summary);
        disease.setAdvice(advice);
        disease.setSourceName(sourceName);
        disease.setSourceUrl(sourceUrl);
        return disease;
    }

    private static MedSymptom symptom(String code, String aliases) {
        MedSymptom symptom = new MedSymptom();
        symptom.setDiseaseCode(code);
        symptom.setAliases(aliases);
        return symptom;
    }

    private static MedDrug drug(String name, String code, String usage, String caution, String sourceUrl) {
        MedDrug drug = new MedDrug();
        drug.setName(name);
        drug.setDiseaseCode(code);
        drug.setUsageText(usage);
        drug.setCaution(caution);
        drug.setSourceName("常见非处方药说明书要点（教学摘编）");
        drug.setSourceUrl(sourceUrl);
        return drug;
    }

    private static MedRedFlag flag(String phrase, String message) {
        MedRedFlag flag = new MedRedFlag();
        flag.setPhrase(phrase);
        flag.setMessage(message);
        return flag;
    }

    private static MedArticle article(String code, String title, String body, String keywords, String sourceName, String sourceUrl) {
        MedArticle article = new MedArticle();
        article.setDiseaseCode(code);
        article.setTitle(title);
        article.setBody(body);
        article.setKeywords(keywords);
        article.setSourceName(sourceName);
        article.setSourceUrl(sourceUrl);
        return article;
    }
}

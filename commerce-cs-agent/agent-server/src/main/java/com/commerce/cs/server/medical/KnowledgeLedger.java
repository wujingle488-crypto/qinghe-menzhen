package com.commerce.cs.server.medical;

import com.commerce.cs.domain.entity.MedArticle;
import com.commerce.cs.domain.entity.MedDisease;
import com.commerce.cs.domain.entity.MedDrug;
import com.commerce.cs.domain.entity.MedRedFlag;
import com.commerce.cs.domain.entity.MedSymptom;
import com.commerce.cs.domain.repo.MedArticleRepository;
import com.commerce.cs.domain.repo.MedDiseaseRepository;
import com.commerce.cs.domain.repo.MedDrugRepository;
import com.commerce.cs.domain.repo.MedRedFlagRepository;
import com.commerce.cs.domain.repo.MedSymptomRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeLedger {
    private final MedDiseaseRepository diseases;
    private final MedSymptomRepository symptoms;
    private final MedDrugRepository drugs;
    private final MedRedFlagRepository redFlags;
    private final MedArticleRepository articles;

    public KnowledgeLedger(MedDiseaseRepository diseases, MedSymptomRepository symptoms, MedDrugRepository drugs,
                           MedRedFlagRepository redFlags, MedArticleRepository articles) {
        this.diseases = diseases;
        this.symptoms = symptoms;
        this.drugs = drugs;
        this.redFlags = redFlags;
        this.articles = articles;
    }

    @Transactional
    public void seedIfEmpty() {
        if (diseases.count() == 0) {
            diseases.saveAll(MedicalSeed.diseases());
            symptoms.saveAll(MedicalSeed.symptoms());
            drugs.saveAll(MedicalSeed.drugs());
            redFlags.saveAll(MedicalSeed.redFlags());
            articles.saveAll(MedicalSeed.articles());
            return;
        }
        for (MedDisease disease : MedicalSeed.diseases()) {
            diseases.findByCode(disease.getCode()).ifPresentOrElse(existing -> {
                existing.setName(disease.getName());
                existing.setSummary(disease.getSummary());
                existing.setAdvice(disease.getAdvice());
                existing.setSourceName(disease.getSourceName());
                existing.setSourceUrl(disease.getSourceUrl());
                diseases.save(existing);
            }, () -> diseases.save(disease));
        }
        for (MedSymptom symptom : MedicalSeed.symptoms()) {
            List<MedSymptom> existing = symptoms.findByDiseaseCode(symptom.getDiseaseCode());
            if (existing.isEmpty()) {
                symptoms.save(symptom);
            } else {
                MedSymptom row = existing.get(0);
                row.setAliases(symptom.getAliases());
                symptoms.save(row);
                for (int i = 1; i < existing.size(); i++) {
                    symptoms.delete(existing.get(i));
                }
            }
        }
        for (MedDrug drug : MedicalSeed.drugs()) {
            List<MedDrug> existing = drugs.findByDiseaseCode(drug.getDiseaseCode()).stream()
                    .filter(item -> drug.getName().equals(item.getName()))
                    .toList();
            if (existing.isEmpty()) {
                drugs.save(drug);
            } else {
                MedDrug row = existing.get(0);
                row.setUsageText(drug.getUsageText());
                row.setCaution(drug.getCaution());
                row.setSourceName(drug.getSourceName());
                row.setSourceUrl(drug.getSourceUrl());
                drugs.save(row);
                for (int i = 1; i < existing.size(); i++) {
                    drugs.delete(existing.get(i));
                }
            }
        }
        for (MedArticle article : MedicalSeed.articles()) {
            boolean present = articles.findAll().stream().anyMatch(item -> article.getTitle().equals(item.getTitle()));
            if (!present) {
                articles.save(article);
            }
        }
    }

    public List<MedDisease> diseases() { return diseases.findAll(); }
    public List<MedSymptom> symptoms() { return symptoms.findAll(); }
    public List<MedDrug> drugs() { return drugs.findAll(); }
    public List<MedDrug> drugsFor(String diseaseCode) { return drugs.findByDiseaseCode(diseaseCode); }
    public List<MedRedFlag> redFlags() { return redFlags.findAll(); }
    public List<MedArticle> articles() { return articles.findAll(); }

    public MedDisease disease(String code) {
        return diseases.findByCode(code).orElse(null);
    }

    @Transactional
    public MedArticle addArticle(String title, String body, String keywords, String sourceUrl, String diseaseCode) {
        return addArticle(title, body, keywords, sourceUrl, diseaseCode, "运营补充");
    }

    @Transactional
    public MedArticle addArticle(String title, String body, String keywords, String sourceUrl, String diseaseCode,
                                 String sourceName) {
        MedArticle article = new MedArticle();
        article.setTitle(title);
        article.setBody(body);
        article.setKeywords(keywords == null || keywords.isBlank() ? title : keywords);
        article.setSourceName(sourceName == null || sourceName.isBlank() ? "运营补充" : sourceName);
        article.setSourceUrl(sourceUrl == null || sourceUrl.isBlank() ? MedicalSeed.NHC : sourceUrl);
        article.setDiseaseCode(diseaseCode);
        return articles.save(article);
    }
}

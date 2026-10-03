package com.commerce.cs.domain.repo;

import com.commerce.cs.domain.entity.MedArticle;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedArticleRepository extends JpaRepository<MedArticle, Long> {
    List<MedArticle> findByDiseaseCode(String diseaseCode);
}

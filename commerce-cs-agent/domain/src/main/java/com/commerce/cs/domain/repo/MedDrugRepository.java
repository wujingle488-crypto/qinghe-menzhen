package com.commerce.cs.domain.repo;

import com.commerce.cs.domain.entity.MedDrug;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedDrugRepository extends JpaRepository<MedDrug, Long> {
    List<MedDrug> findByDiseaseCode(String diseaseCode);
}

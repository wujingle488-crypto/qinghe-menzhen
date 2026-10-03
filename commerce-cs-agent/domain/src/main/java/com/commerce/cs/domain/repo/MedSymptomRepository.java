package com.commerce.cs.domain.repo;

import com.commerce.cs.domain.entity.MedSymptom;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedSymptomRepository extends JpaRepository<MedSymptom, Long> {
    List<MedSymptom> findByDiseaseCode(String diseaseCode);
}

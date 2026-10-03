package com.commerce.cs.domain.repo;

import com.commerce.cs.domain.entity.MedDisease;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedDiseaseRepository extends JpaRepository<MedDisease, Long> {
    Optional<MedDisease> findByCode(String code);
}

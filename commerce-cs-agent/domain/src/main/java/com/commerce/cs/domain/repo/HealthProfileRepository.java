package com.commerce.cs.domain.repo;

import com.commerce.cs.domain.entity.HealthProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthProfileRepository extends JpaRepository<HealthProfile, Long> {
    List<HealthProfile> findByUserIdOrderByUpdatedAtDescIdDesc(Long userId);

    Optional<HealthProfile> findByIdAndUserId(Long id, Long userId);
}

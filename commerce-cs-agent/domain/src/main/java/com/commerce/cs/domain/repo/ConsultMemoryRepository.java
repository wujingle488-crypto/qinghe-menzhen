package com.commerce.cs.domain.repo;

import com.commerce.cs.domain.entity.ConsultMemory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultMemoryRepository extends JpaRepository<ConsultMemory, Long> {
    Optional<ConsultMemory> findBySessionId(Long sessionId);

    void deleteBySessionId(Long sessionId);
}

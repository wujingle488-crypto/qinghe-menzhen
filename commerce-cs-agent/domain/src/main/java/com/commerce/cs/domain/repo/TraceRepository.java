package com.commerce.cs.domain.repo;

import com.commerce.cs.domain.entity.TraceStep;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TraceRepository extends JpaRepository<TraceStep, Long> {
    List<TraceStep> findBySessionIdOrderBySeqAsc(Long sessionId);

    int countBySessionId(Long sessionId);

    void deleteBySessionId(Long sessionId);
}

package com.commerce.cs.domain.repo;

import com.commerce.cs.domain.ConsultTaskStatus;
import com.commerce.cs.domain.entity.ConsultTask;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultTaskRepository extends JpaRepository<ConsultTask, Long> {
    Optional<ConsultTask> findFirstBySessionIdAndStatusOrderByIdDesc(Long sessionId, ConsultTaskStatus status);

    List<ConsultTask> findBySessionIdOrderByIdDesc(Long sessionId);

    Optional<ConsultTask> findFirstBySessionIdAndStatusInOrderByIdDesc(Long sessionId, List<ConsultTaskStatus> statuses);

    void deleteBySessionId(Long sessionId);
}

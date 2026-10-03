package com.commerce.cs.domain.repo;

import com.commerce.cs.domain.entity.UserMemory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {
    List<UserMemory> findByUserId(Long userId);

    @Transactional
    void deleteByUserId(Long userId);
}

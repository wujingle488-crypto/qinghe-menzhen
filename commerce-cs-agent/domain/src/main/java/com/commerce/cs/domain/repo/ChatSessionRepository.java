package com.commerce.cs.domain.repo;

import com.commerce.cs.domain.entity.ChatSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    List<ChatSession> findByBuyerIdOrderByUpdatedAtDescIdDesc(Long buyerId);

    List<ChatSession> findByShopIdOrderByUpdatedAtDescIdDesc(Long shopId);

    Optional<ChatSession> findFirstByBuyerIdAndShopIdAndOrderNoOrderByIdDesc(Long buyerId, Long shopId, String orderNo);

    Optional<ChatSession> findFirstByBuyerIdAndShopIdAndProductIdAndOrderNoIsNullOrderByIdDesc(
            Long buyerId, Long shopId, Long productId);
}

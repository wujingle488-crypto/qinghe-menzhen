package com.commerce.cs.domain.repo;

import com.commerce.cs.domain.entity.ClinicUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicUserRepository extends JpaRepository<ClinicUser, Long> {
    Optional<ClinicUser> findByUsername(String username);
}

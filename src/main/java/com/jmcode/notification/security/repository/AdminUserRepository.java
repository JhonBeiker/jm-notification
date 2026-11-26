package com.jmcode.notification.security.repository;

import com.jmcode.notification.security.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

import com.jmcode.notification.security.entity.AdminUser;
import java.util.List;
import com.jmcode.notification.security.entity.AdminUser;
import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {
    Optional<AdminUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<AdminUser> findAllByCompanyId(Long companyId);
    long countByCompanyId(Long companyId);
}

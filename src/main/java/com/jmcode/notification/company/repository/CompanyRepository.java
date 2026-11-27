package com.jmcode.notification.company.repository;

import com.jmcode.notification.company.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}

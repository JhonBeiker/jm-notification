package com.jmcode.notification.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WhatsAppDeviceRepository extends JpaRepository<WhatsAppDevice, Long> {

    Optional<WhatsAppDevice> findByClientCodeIgnoreCaseAndActiveTrue(String clientCode);

    Optional<WhatsAppDevice> findByIsDefaultTrueAndActiveTrue();

    Optional<WhatsAppDevice> findFirstByActiveTrueOrderByIdAsc();

    /** Mismo orden de resolución, pero dentro de la empresa del llamante acotado. */
    Optional<WhatsAppDevice> findByCompanyIdAndIsDefaultTrueAndActiveTrue(Long companyId);

    Optional<WhatsAppDevice> findFirstByCompanyIdAndActiveTrueOrderByIdAsc(Long companyId);

    boolean existsByClientCodeIgnoreCase(String clientCode);

    Optional<WhatsAppDevice> findByDeviceIdIgnoreCase(String deviceId);

    List<WhatsAppDevice> findAllByCompanyId(Long companyId);

    long countByCompanyId(Long companyId);

    long countByActiveTrue();
}

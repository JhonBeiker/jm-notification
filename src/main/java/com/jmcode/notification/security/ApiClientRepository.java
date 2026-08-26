package com.jmcode.notification.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface ApiClientRepository extends JpaRepository<ApiClient, Long> {

    Optional<ApiClient> findByApiKeyHash(String apiKeyHash);

    boolean existsByNameIgnoreCase(String name);

    /**
     * Marca de último uso en un solo UPDATE. La versión anterior cargaba la entidad y la
     * volvía a guardar (dos consultas más un flush) en <em>cada</em> petición autenticada.
     */
    @Modifying(clearAutomatically = true)
    @Query("update ApiClient c set c.lastUsedAt = :now where c.id = :id")
    void touchLastUsed(@Param("id") Long id, @Param("now") Instant now);
}

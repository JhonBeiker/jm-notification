package com.jmcode.notification.security.config;

import com.jmcode.notification.security.entity.AdminUser;
import com.jmcode.notification.security.service.AdminUserService;
import org.slf4j.Logger;
import com.jmcode.notification.security.entity.AdminUser;
import com.jmcode.notification.security.service.AdminUserService;
import org.slf4j.LoggerFactory;
import com.jmcode.notification.security.entity.AdminUser;
import com.jmcode.notification.security.service.AdminUserService;
import org.springframework.boot.CommandLineRunner;
import com.jmcode.notification.security.entity.AdminUser;
import com.jmcode.notification.security.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import com.jmcode.notification.security.entity.AdminUser;
import com.jmcode.notification.security.service.AdminUserService;
import org.springframework.stereotype.Component;
import com.jmcode.notification.security.entity.AdminUser;
import com.jmcode.notification.security.service.AdminUserService;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AdminUserSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final AdminUserService adminUserService;
    private final AuthProperties authProperties;

    @Override
    @Transactional
    public void run(String... args) {
        try {
            AdminUser seeded = adminUserService.seedIfEmpty(authProperties);
            if (seeded != null) {
                log.info("Seeded initial SUPER_ADMIN user: {}", seeded.getEmail());
            }
        } catch (IllegalStateException ex) {
            log.error("Admin user seed failed: {}", ex.getMessage());
        }
    }
}

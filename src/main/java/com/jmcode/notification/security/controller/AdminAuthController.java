package com.jmcode.notification.security.controller;

import com.jmcode.notification.security.config.AuthProperties;
import com.jmcode.notification.security.entity.AdminUser;
import com.jmcode.notification.security.service.AdminUserService;
import com.jmcode.notification.security.service.JwtService;
import com.jmcode.notification.security.dto.LoginRequestDto;
import com.jmcode.notification.security.config.AuthProperties;
import com.jmcode.notification.security.entity.AdminUser;
import com.jmcode.notification.security.service.AdminUserService;
import com.jmcode.notification.security.service.JwtService;
import com.jmcode.notification.security.dto.LoginResponseDto;
import com.jmcode.notification.security.config.AuthProperties;
import com.jmcode.notification.security.entity.AdminUser;
import com.jmcode.notification.security.service.AdminUserService;
import com.jmcode.notification.security.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import com.jmcode.notification.security.config.AuthProperties;
import com.jmcode.notification.security.entity.AdminUser;
import com.jmcode.notification.security.service.AdminUserService;
import com.jmcode.notification.security.service.JwtService;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.jmcode.notification.security.config.AuthProperties;
import com.jmcode.notification.security.entity.AdminUser;
import com.jmcode.notification.security.service.AdminUserService;
import com.jmcode.notification.security.service.JwtService;
import lombok.RequiredArgsConstructor;
import com.jmcode.notification.security.config.AuthProperties;
import com.jmcode.notification.security.entity.AdminUser;
import com.jmcode.notification.security.service.AdminUserService;
import com.jmcode.notification.security.service.JwtService;
import jakarta.validation.Valid;
import com.jmcode.notification.security.config.AuthProperties;
import com.jmcode.notification.security.entity.AdminUser;
import com.jmcode.notification.security.service.AdminUserService;
import com.jmcode.notification.security.service.JwtService;
import org.springframework.http.HttpStatus;
import com.jmcode.notification.security.config.AuthProperties;
import com.jmcode.notification.security.entity.AdminUser;
import com.jmcode.notification.security.service.AdminUserService;
import com.jmcode.notification.security.service.JwtService;
import org.springframework.web.bind.annotation.PostMapping;
import com.jmcode.notification.security.config.AuthProperties;
import com.jmcode.notification.security.entity.AdminUser;
import com.jmcode.notification.security.service.AdminUserService;
import com.jmcode.notification.security.service.JwtService;
import org.springframework.web.bind.annotation.RequestBody;
import com.jmcode.notification.security.config.AuthProperties;
import com.jmcode.notification.security.entity.AdminUser;
import com.jmcode.notification.security.service.AdminUserService;
import com.jmcode.notification.security.service.JwtService;
import org.springframework.web.bind.annotation.RequestMapping;
import com.jmcode.notification.security.config.AuthProperties;
import com.jmcode.notification.security.entity.AdminUser;
import com.jmcode.notification.security.service.AdminUserService;
import com.jmcode.notification.security.service.JwtService;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.jmcode.notification.security.config.AuthProperties;
import com.jmcode.notification.security.entity.AdminUser;
import com.jmcode.notification.security.service.AdminUserService;
import com.jmcode.notification.security.service.JwtService;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/auth")
@Tag(name = "Admin Auth", description = "Login para usuarios administradores")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminUserService adminUserService;
    private final JwtService jwtService;
    private final AuthProperties authProperties;

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Login con email + password, devuelve JWT")
    public LoginResponseDto login(@Valid @RequestBody LoginRequestDto dto) {
        AdminUser user = adminUserService.authenticate(dto.email(), dto.password());
        adminUserService.markLoggedIn(user.getId());
        Long companyId = user.getCompany() == null ? null : user.getCompany().getId();
        String companyCode = user.getCompany() == null ? null : user.getCompany().getCode();
        String token = jwtService.issue(user.getId(), user.getEmail(), user.getRole(), companyId);
        return new LoginResponseDto(token, authProperties.jwtTtl().toSeconds(), user.getRole().name(),
                companyId, companyCode);
    }
}

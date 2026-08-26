package com.jmcode.notification.security;

import com.jmcode.notification.security.dto.LoginRequestDto;
import com.jmcode.notification.security.dto.LoginResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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
        String token = jwtService.issue(user.getId(), user.getEmail(), user.getRole());
        return new LoginResponseDto(token, authProperties.jwtTtl().toSeconds(), user.getRole().name());
    }
}

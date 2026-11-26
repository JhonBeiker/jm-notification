package com.jmcode.notification.security.controller;

import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.AdminUserService;
import com.jmcode.notification.company.entity.Company;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.AdminUserService;
import com.jmcode.notification.company.service.CompanyService;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.AdminUserService;
import com.jmcode.notification.security.dto.AdminUserResponseDto;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.AdminUserService;
import com.jmcode.notification.security.dto.CreateAdminUserRequestDto;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.AdminUserService;
import com.jmcode.notification.security.dto.ResetAdminPasswordRequestDto;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.AdminUserService;
import com.jmcode.notification.security.dto.UpdateAdminUserRequestDto;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.AdminUserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.AdminUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.AdminUserService;
import jakarta.validation.Valid;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.AdminUserService;
import org.springframework.http.HttpStatus;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.AdminUserService;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.AdminUserService;
import org.springframework.web.bind.annotation.GetMapping;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.AdminUserService;
import org.springframework.web.bind.annotation.PathVariable;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.AdminUserService;
import org.springframework.web.bind.annotation.PostMapping;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.AdminUserService;
import org.springframework.web.bind.annotation.PutMapping;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.AdminUserService;
import org.springframework.web.bind.annotation.RequestBody;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.AdminUserService;
import org.springframework.web.bind.annotation.RequestMapping;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.AdminUserService;
import org.springframework.web.bind.annotation.RequestParam;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.AdminUserService;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.AdminUserService;
import org.springframework.web.bind.annotation.RestController;

import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.AdminUserService;
import java.util.List;

/**
 * Alta de usuarios administradores. Sólo SUPER_ADMIN: es quien crea el ADMIN de cada
 * empresa, que a partir de ahí carga las cuentas de correo, los bots y las plantillas.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin Users", description = "Usuarios administradores y su empresa (sólo SUPER_ADMIN)")
@SecurityRequirement(name = "adminJwt")
@RequiredArgsConstructor
public class AdminUserAdminController {

    private final AdminUserService adminUserService;
    private final CompanyService companyService;

    @GetMapping
    @Operation(summary = "Listar administradores", description = "Filtra por empresa con ?companyId=")
    public List<AdminUserResponseDto> list(@RequestParam(required = false) Long companyId) {
        return adminUserService.list(companyId).stream().map(AdminUserResponseDto::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener administrador por ID")
    public AdminUserResponseDto getById(@PathVariable Long id) {
        return AdminUserResponseDto.from(adminUserService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear administrador", description = "ADMIN exige companyId; SUPER_ADMIN no admite ninguno.")
    public AdminUserResponseDto create(@Valid @RequestBody CreateAdminUserRequestDto dto) {
        return AdminUserResponseDto.from(
                adminUserService.create(dto.email(), dto.password(), dto.role(), company(dto.companyId())));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar rol, empresa o estado de un administrador")
    public AdminUserResponseDto update(@PathVariable Long id, @Valid @RequestBody UpdateAdminUserRequestDto dto) {
        return AdminUserResponseDto.from(
                adminUserService.update(id, dto.role(), company(dto.companyId()), dto.active()));
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Restablecer la contraseña de un administrador")
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetAdminPasswordRequestDto dto) {
        adminUserService.resetPassword(id, dto.password());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar administrador")
    public void delete(@PathVariable Long id) {
        adminUserService.delete(id);
    }

    private Company company(Long companyId) {
        return companyId == null ? null : companyService.getById(companyId);
    }
}

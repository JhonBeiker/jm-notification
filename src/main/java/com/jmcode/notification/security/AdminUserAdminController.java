package com.jmcode.notification.security;

import com.jmcode.notification.company.Company;
import com.jmcode.notification.company.CompanyService;
import com.jmcode.notification.security.dto.AdminUserResponseDto;
import com.jmcode.notification.security.dto.CreateAdminUserRequestDto;
import com.jmcode.notification.security.dto.ResetAdminPasswordRequestDto;
import com.jmcode.notification.security.dto.UpdateAdminUserRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

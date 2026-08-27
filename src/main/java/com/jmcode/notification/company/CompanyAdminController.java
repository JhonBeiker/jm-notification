package com.jmcode.notification.company;

import com.jmcode.notification.company.dto.CompanyRequestDto;
import com.jmcode.notification.company.dto.CompanyResponseDto;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Alta y mantenimiento de empresas. Sólo SUPER_ADMIN: el administrador de una empresa
 * gestiona los datos de la suya, pero no crea empresas nuevas (ver {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/v1/admin/companies")
@Tag(name = "Companies", description = "Alta de empresas clientes (sólo SUPER_ADMIN)")
@SecurityRequirement(name = "adminJwt")
@RequiredArgsConstructor
public class CompanyAdminController {

    private final CompanyService companyService;

    @GetMapping
    @Operation(summary = "Listar empresas")
    public List<CompanyResponseDto> list() {
        return companyService.listAll().stream().map(CompanyResponseDto::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener empresa por ID")
    public CompanyResponseDto getById(@PathVariable Long id) {
        return CompanyResponseDto.from(companyService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear empresa")
    public CompanyResponseDto create(@Valid @RequestBody CompanyRequestDto dto) {
        return CompanyResponseDto.from(companyService.save(dto.applyTo(new Company())));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar empresa")
    public CompanyResponseDto update(@PathVariable Long id, @Valid @RequestBody CompanyRequestDto dto) {
        return CompanyResponseDto.from(companyService.save(dto.applyTo(companyService.getById(id))));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar empresa", description = "Falla con 409 si aún tiene usuarios, cuentas o plantillas.")
    public void delete(@PathVariable Long id) {
        companyService.delete(id);
    }
}

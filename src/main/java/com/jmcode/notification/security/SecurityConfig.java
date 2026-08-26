package com.jmcode.notification.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
            "/error"
    };

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtAuthFilter jwtAuthFilter(JwtService jwtService) {
        return new JwtAuthFilter(jwtService);
    }

    @Bean
    ApiKeyAuthFilter apiKeyAuthFilter(ApiClientRepository repository, ApiKeyGenerator generator, ApiClientService service) {
        return new ApiKeyAuthFilter(repository, generator, service);
    }

    /**
     * Los filtros se registran sólo dentro de la cadena de Spring Security: sin esto el
     * contenedor de servlets los aplicaría además a todas las peticiones.
     */
    @Bean
    FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter filter) {
        return disabledRegistration(filter);
    }

    @Bean
    FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilterRegistration(ApiKeyAuthFilter filter) {
        return disabledRegistration(filter);
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthFilter jwtAuthFilter,
            ApiKeyAuthFilter apiKeyAuthFilter,
            ObjectMapper objectMapper
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // Telegram entrega los updates sin credenciales: la autenticación
                        // real es el secret token que valida el propio controlador.
                        .requestMatchers(HttpMethod.POST, "/api/v1/telegram/webhook").permitAll()
                        .requestMatchers("/api/v1/admin/auth/login").permitAll()
                        .requestMatchers("/api/v1/admin/email-accounts/**").hasRole(Role.SUPER_ADMIN.name())
                        .requestMatchers("/api/v1/admin/**").hasAnyRole(Role.SUPER_ADMIN.name(), Role.ADMIN.name())
                        .requestMatchers("/api/v1/notifications/**").hasRole("API_CLIENT")
                        .requestMatchers("/api/v1/telegram/**")
                            .hasAnyRole(Role.SUPER_ADMIN.name(), Role.ADMIN.name())
                        // Antes esto era permitAll(): cualquier endpoint nuevo nacía público.
                        .anyRequest().authenticated()
                )
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((req, res, ex) ->
                                writeJson(objectMapper, res, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                                        "Authentication required", req.getRequestURI()))
                        .accessDeniedHandler((req, res, ex) ->
                                writeJson(objectMapper, res, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                                        "Access denied", req.getRequestURI()))
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * La consola H2 necesita frames y su propia regla; se activa sólo si está habilitada,
     * en vez de dejarla abierta permanentemente como antes.
     */
    @Bean
    @Order(1)
    @ConditionalOnProperty(name = "spring.h2.console.enabled", havingValue = "true")
    SecurityFilterChain h2ConsoleFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/h2-console/**")
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    private static <T extends jakarta.servlet.Filter> FilterRegistrationBean<T> disabledRegistration(T filter) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    private static void writeJson(ObjectMapper objectMapper, HttpServletResponse response,
                                  int status, String error, String message, String path) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "timestamp", Instant.now().toString(),
                "status", status,
                "error", error,
                "message", message,
                "path", path));
    }
}

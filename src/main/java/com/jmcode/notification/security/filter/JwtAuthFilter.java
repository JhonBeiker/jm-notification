package com.jmcode.notification.security.filter;

import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.JwtService;
import jakarta.servlet.FilterChain;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.JwtService;
import jakarta.servlet.ServletException;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.JwtService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.JwtService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.JwtService;
import org.springframework.security.core.context.SecurityContextHolder;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.JwtService;
import org.springframework.web.filter.OncePerRequestFilter;

import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.JwtService;
import java.io.IOException;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.security.service.JwtService;
import java.util.List;

public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "Authorization";
    public static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length());
            jwtService.parse(token).ifPresent(principal -> {
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        chain.doFilter(request, response);
    }
}

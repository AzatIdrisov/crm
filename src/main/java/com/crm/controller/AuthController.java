package com.crm.controller;

import com.crm.dto.auth.AuthRequest;
import com.crm.dto.auth.AuthResponse;
import com.crm.security.CrmUserDetails;
import com.crm.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequest request) {
        // AuthenticationManager использует UserDetailsService + PasswordEncoder.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        // principal содержит UserDetails, из него извлекаем доменного пользователя.
        CrmUserDetails principal = (CrmUserDetails) authentication.getPrincipal();

        String token = jwtService.generateToken(principal.getUser());
        AuthResponse response = new AuthResponse();
        response.setAccessToken(token);
        response.setTokenType("Bearer");
        response.setExpiresInSeconds(jwtService.getExpirationSeconds());
        response.setRole(principal.getUser().getRole());
        return response;
    }

    // TODO(4.7): /auth/register с проверкой роли и сохранением в БД.
}

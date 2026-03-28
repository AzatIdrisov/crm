package com.crm.service;

import com.crm.model.User;
import com.crm.model.enums.UserRole;
import com.crm.model.value.Email;
import com.crm.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Инициализация дефолтных пользователей при старте.
     *
     * Нюанс: @PostConstruct вызывается после инициализации бина, но до первого запроса.
     * Используем existsByEmailValue чтобы не дублировать пользователей при рестарте.
     * В продакшене такой подход неприемлем — используют Liquibase data-changesets.
     */
    @PostConstruct
    @Transactional
    public void initDefaults() {
        registerIfAbsent("admin@crm.local",   "admin123",   UserRole.ADMIN);
        registerIfAbsent("manager@crm.local", "manager123", UserRole.MANAGER);
        registerIfAbsent("viewer@crm.local",  "viewer123",  UserRole.VIEWER);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmailValue(normalizeEmail(email));
    }

    @Transactional
    public User register(String email, String rawPassword, UserRole role) {
        String normalized = normalizeEmail(email);
        User user = User.builder()
                .email(new Email(normalized))
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .build();
        return userRepository.save(user);
    }

    private void registerIfAbsent(String email, String rawPassword, UserRole role) {
        String normalized = normalizeEmail(email);
        if (!userRepository.existsByEmailValue(normalized)) {
            register(normalized, rawPassword, role);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}

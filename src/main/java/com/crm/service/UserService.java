package com.crm.service;

import com.crm.model.User;
import com.crm.model.enums.UserRole;
import com.crm.model.value.Email;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class UserService {

    // In-memory хранилище — заменится на репозиторий в Фазе 5.
    private final Map<String, User> usersByEmail = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);
    private final PasswordEncoder passwordEncoder;

    public UserService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void initDefaults() {
        // Для демо: дефолтные пользователи. В проде так делать нельзя.
        register("admin@crm.local", "admin123", UserRole.ADMIN);
        register("manager@crm.local", "manager123", UserRole.MANAGER);
        register("viewer@crm.local", "viewer123", UserRole.VIEWER);
    }

    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(usersByEmail.get(normalizeEmail(email)));
    }

    public User register(String email, String rawPassword, UserRole role) {
        String normalized = normalizeEmail(email);
        // TODO(4.7): добавить проверку уникальности email и правила сложности пароля.
        User user = User.builder()
                .id(idSequence.getAndIncrement())
                .email(new Email(normalized))
                // Храним только хэш пароля.
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .build();
        usersByEmail.put(normalized, user);
        return user;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}

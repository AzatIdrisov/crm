package com.crm.service;

import com.crm.model.User;
import com.crm.model.enums.UserRole;
import com.crm.model.value.Email;
import com.crm.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты UserService.
 *
 * Ключевые концепции:
 *  - @PostConstruct initDefaults() НЕ вызывается при @InjectMocks — Spring жизненный
 *    цикл бина отсутствует в Mockito-тестах. Это хорошо: тест изолирован от
 *    побочных эффектов инициализации.
 *
 *  - PasswordEncoder — зависимость, которую нужно мокировать: не хотим тестировать
 *    BCrypt (медленный), хотим убедиться что encode() вызывается и его результат
 *    сохраняется в User.password.
 *
 *  - Email нормализация: normalizeEmail() приватный, но мы тестируем его поведение
 *    косвенно — проверяем что репозиторий вызывается с нормализованным email.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    UserService userService;

    // =========================================================================
    // findByEmail
    // =========================================================================

    @Test
    @DisplayName("findByEmail: нормализует email перед поиском (trim + toLowerCase)")
    void findByEmail_normalizesEmail() {
        // Входной email с пробелами и заглавными буквами
        String rawEmail = "  ALICE@TEST.COM  ";
        String normalized = "alice@test.com";

        when(userRepository.findByEmailValue(normalized)).thenReturn(Optional.empty());

        userService.findByEmail(rawEmail);

        // Репозиторий должен быть вызван с нормализованным email, а не с raw
        verify(userRepository).findByEmailValue(normalized);
        verify(userRepository, never()).findByEmailValue(rawEmail);
    }

    @Test
    @DisplayName("findByEmail: возвращает Optional.of(user) если пользователь найден")
    void findByEmail_whenFound_returnsUser() {
        User user = User.builder()
                .id(1L)
                .email(new Email("admin@crm.local"))
                .role(UserRole.ADMIN)
                .firstName("Admin")
                .lastName("User")
                .build();

        when(userRepository.findByEmailValue("admin@crm.local"))
                .thenReturn(Optional.of(user));

        Optional<User> result = userService.findByEmail("admin@crm.local");

        assertThat(result).isPresent().contains(user);
    }

    @Test
    @DisplayName("findByEmail: возвращает Optional.empty() если пользователь не найден")
    void findByEmail_whenNotFound_returnsEmpty() {
        when(userRepository.findByEmailValue(anyString())).thenReturn(Optional.empty());

        Optional<User> result = userService.findByEmail("unknown@test.com");

        assertThat(result).isEmpty();
    }

    // =========================================================================
    // register
    // =========================================================================

    @Test
    @DisplayName("register: кодирует пароль через PasswordEncoder перед сохранением")
    void register_encodesPassword() {
        String rawPassword = "secret123";
        String encodedPassword = "$2a$10$hashedpassword";
        User savedUser = User.builder()
                .id(1L)
                .email(new Email("new@test.com"))
                .password(encodedPassword)
                .role(UserRole.MANAGER)
                .firstName("New")
                .lastName("User")
                .build();

        when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        User result = userService.register("new@test.com", rawPassword, UserRole.MANAGER, "New", "User");

        assertThat(result).isEqualTo(savedUser);
        // encode() должен быть вызван с исходным паролем
        verify(passwordEncoder).encode(rawPassword);
    }

    @Test
    @DisplayName("register: сохраняет пользователя с закодированным паролем и нужными полями")
    void register_savesUserWithCorrectFields() {
        String encodedPassword = "$2a$10$hashedpassword";
        when(passwordEncoder.encode("pass123")).thenReturn(encodedPassword);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.register("John@Test.COM", "pass123", UserRole.VIEWER, "John", "Doe");

        // ArgumentCaptor для захвата объекта User, переданного в save()
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User saved = userCaptor.getValue();
        // Email должен быть нормализован: john@test.com
        assertThat(saved.getEmail().value()).isEqualTo("john@test.com");
        // Пароль — закодированный, не оригинальный
        assertThat(saved.getPassword()).isEqualTo(encodedPassword);
        assertThat(saved.getRole()).isEqualTo(UserRole.VIEWER);
        assertThat(saved.getFirstName()).isEqualTo("John");
        assertThat(saved.getLastName()).isEqualTo("Doe");
    }

    @Test
    @DisplayName("register: нормализует email (trim + toLowerCase) перед сохранением")
    void register_normalizesEmail() {
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.register("  ADMIN@CRM.LOCAL  ", "pass", UserRole.ADMIN, "Admin", "User");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        // Email должен быть обрезан и приведён к нижнему регистру
        assertThat(captor.getValue().getEmail().value()).isEqualTo("admin@crm.local");
    }
}

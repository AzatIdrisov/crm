package com.crm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Конфигурация JPA/Hibernate.
 *
 * Ключевые концепции:
 *  - @EnableJpaAuditing: активирует AuditingEntityListener, который заполняет
 *    поля @CreatedDate и @LastModifiedDate в BaseEntity.
 *    Без этой аннотации поля будут null, несмотря на @EntityListeners.
 *
 *  - @EnableTransactionManagement: включает поддержку декларативных транзакций
 *    через @Transactional. Spring Boot включает это автоматически, но явное
 *    объявление делает конфигурацию понятной.
 *
 *  - @EnableJpaRepositories: Spring Boot тоже делает это автоматически при
 *    наличии spring-boot-starter-data-jpa. Здесь для явности.
 */
@Configuration
@EnableJpaAuditing
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.crm.repository")
public class JpaConfig {
    // Точка расширения: можно добавить кастомный AuditorAware<String>
    // для записи "кто изменил" (createdBy, modifiedBy).
    // Пример:
    //   @Bean
    //   public AuditorAware<String> auditorProvider() {
    //       return () -> Optional.ofNullable(SecurityContextHolder.getContext())
    //           .map(ctx -> ctx.getAuthentication())
    //           .map(auth -> auth.getName());
    //   }
}

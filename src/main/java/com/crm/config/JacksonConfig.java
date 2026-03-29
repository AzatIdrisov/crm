package com.crm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Конфигурация Jackson ObjectMapper.
 *
 * Ключевые концепции:
 *
 *  - Hibernate6Module: решает проблему сериализации Hibernate lazy-proxy в Redis.
 *    Когда @Cacheable перехватывает результат метода и сохраняет его в Redis,
 *    сессия Hibernate может быть уже закрыта. Без модуля Jackson попытается
 *    обойти PersistentBag/HibernateProxy и получит LazyInitializationException.
 *
 *    FORCE_LAZY_LOADING = false: НЕ запускать ленивую загрузку при сериализации.
 *    Неинициализированные прокси будут записаны как null.
 *
 *    SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS = true: неинициализированный
 *    @ManyToOne прокси сериализуется как {"id": 5} вместо null.
 *    Это позволяет коду делать deal.getCustomer().getId() после десериализации.
 *
 *  - JavaTimeModule: поддержка LocalDateTime, LocalDate, Duration и т.д.
 *    Без него Jackson сериализует LocalDateTime как массив [2024, 3, 15, 10, 30].
 *    WRITE_DATES_AS_TIMESTAMPS = false → ISO-8601: "2024-03-15T10:30:00"
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Поддержка Java 8+ Date/Time API
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Hibernate lazy-proxy: не грузить при сериализации, id сохранять
        Hibernate6Module hibernateModule = new Hibernate6Module();
        hibernateModule.configure(Hibernate6Module.Feature.FORCE_LAZY_LOADING, false);
        hibernateModule.configure(
                Hibernate6Module.Feature.SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS, true);
        mapper.registerModule(hibernateModule);

        return mapper;
    }
}

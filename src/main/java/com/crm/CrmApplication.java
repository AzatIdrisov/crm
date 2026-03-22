package com.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// Включает автоконфигурацию, сканирование компонентов и конфигурацию Spring Boot
@SpringBootApplication
// Разрешает выполнение методов с @Async в отдельных потоках (уведомления, события)
@EnableAsync
// Разрешает выполнение методов с @Scheduled по расписанию
@EnableScheduling
// Автоматически регистрирует все классы с @ConfigurationProperties как Spring-бины
@ConfigurationPropertiesScan
public class CrmApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrmApplication.class, args);
    }
}

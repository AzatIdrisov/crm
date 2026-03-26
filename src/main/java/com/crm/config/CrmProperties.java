package com.crm.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "crm")
public class CrmProperties {

    @NotBlank
    private String instanceName;
    @Valid
    private final Notification notification = new Notification();
    @Valid
    private final Queue queue = new Queue();

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public Notification getNotification() {
        return notification;
    }

    public Queue getQueue() {
        return queue;
    }

    public static class Notification {
        private boolean enabled = true;
        @NotNull
        private Duration reminderDelay = Duration.ofSeconds(60);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getReminderDelay() {
            return reminderDelay;
        }

        public void setReminderDelay(Duration reminderDelay) {
            this.reminderDelay = reminderDelay;
        }
    }

    public static class Queue {
        @Min(1)
        private int capacity = 100;

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }
    }

}

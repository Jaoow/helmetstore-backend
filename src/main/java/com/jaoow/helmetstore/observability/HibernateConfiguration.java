package com.jaoow.helmetstore.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração do Hibernate para registrar o QueryPerformanceInspector
 */
@Configuration
public class HibernateConfiguration {

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer(
            QueryPerformanceInspector queryPerformanceInspector) {
        return hibernateProperties ->
            hibernateProperties.put(AvailableSettings.STATEMENT_INSPECTOR, queryPerformanceInspector);
    }
}

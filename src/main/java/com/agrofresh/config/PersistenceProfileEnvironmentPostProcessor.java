package com.agrofresh.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/** Keeps the base web application independent of persistence until a JDBC URL is configured. */
public class PersistenceProfileEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String DATABASE_AUTOCONFIGURATION_EXCLUSION =
            "spring.autoconfigure.exclude";
    private static final String DATASOURCE_URL = "spring.datasource.url";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean dataSourceConfigured = environment.getProperty(DATASOURCE_URL) != null
                && !environment.getProperty(DATASOURCE_URL).isBlank();
        if (!dataSourceConfigured) {
            environment.getPropertySources().addFirst(new MapPropertySource(
                    "agrofreshDefaultPersistenceExclusion",
                    Map.of(DATABASE_AUTOCONFIGURATION_EXCLUSION,
                            DataSourceAutoConfiguration.class.getName())));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

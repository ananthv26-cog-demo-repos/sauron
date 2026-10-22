package com.freenow.sauron.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * Loads the springdoc defaults even when SPRING_CONFIG_LOCATION replaces the default config locations (as the Docker setup does).
 */
@Configuration
@PropertySource("classpath:springdoc.properties")
public class OpenApiConfig
{
}

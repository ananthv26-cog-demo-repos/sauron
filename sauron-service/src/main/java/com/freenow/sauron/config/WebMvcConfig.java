package com.freenow.sauron.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Keeps {@code /api/v1/plugins/} answering like {@code /api/v1/plugins}, as it did up to Spring Boot 2.7. Spring 6 turned trailing slash matching off.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer
{
    @Override
    @SuppressWarnings("deprecation")
    public void configurePathMatch(PathMatchConfigurer configurer)
    {
        configurer.setUseTrailingSlashMatch(true);
    }
}

package com.freenow.sauron.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring 6 stopped matching "/api/v1/build/" to "/api/v1/build". Clients of the Boot 2 service relied on it.
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

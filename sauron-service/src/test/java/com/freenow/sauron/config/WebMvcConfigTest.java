package com.freenow.sauron.config;

import org.junit.Test;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

public class WebMvcConfigTest
{
    @Test
    @SuppressWarnings("deprecation")
    public void trailingSlashStillMatches()
    {
        PathMatchConfigurer configurer = new PathMatchConfigurer();

        new WebMvcConfig().configurePathMatch(configurer);

        assertThat(configurer.isUseTrailingSlashMatch()).isTrue();
    }
}

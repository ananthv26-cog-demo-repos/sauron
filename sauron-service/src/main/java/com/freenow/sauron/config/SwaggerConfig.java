package com.freenow.sauron.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig
{
    static final String CONTROLLER_PACKAGE = "com.freenow.sauron.controller";


    @Bean
    public GroupedOpenApi api()
    {
        return GroupedOpenApi.builder()
            .group("default")
            .packagesToScan(CONTROLLER_PACKAGE)
            .pathsToMatch("/**")
            .build();
    }


    @Bean
    public OpenAPI openApi()
    {
        return new OpenAPI().info(new Info().title("Api Documentation").version("1.0"));
    }
}

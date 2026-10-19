package com.freenow.sauron.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SwaggerConfigTest
{
    @Test
    public void openApiKeepsSpringfoxDefaultTitleAndVersion()
    {
        OpenAPI openApi = new SwaggerConfig().openApi();

        assertThat(openApi.getInfo().getTitle()).isEqualTo("Api Documentation");
        assertThat(openApi.getInfo().getVersion()).isEqualTo("1.0");
    }
}

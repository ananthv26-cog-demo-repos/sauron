package com.freenow.sauron.config;

import org.junit.Test;
import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleBeansConfigTest
{
    @Test
    public void restTemplateUsesApacheHttpClientWithFiveSecondTimeouts()
    {
        RestTemplate restTemplate = new RestTemplateConfig().restTemplate();

        assertThat(restTemplate.getRequestFactory()).isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
        assertThat(ReflectionTestUtils.getField(restTemplate.getRequestFactory(), "connectTimeout")).isEqualTo(5000L);
        assertThat(ReflectionTestUtils.getField(restTemplate.getRequestFactory(), "connectionRequestTimeout")).isEqualTo(5000L);
        assertThat(ReflectionTestUtils.getField(restTemplate.getRequestFactory(), "readTimeout")).isEqualTo(5000L);
    }


    @Test
    public void httpExchangeRepositoryIsInMemory()
    {
        assertThat(new BeansConfig().httpExchangeRepository()).isInstanceOf(InMemoryHttpExchangeRepository.class);
    }
}

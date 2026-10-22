package com.freenow.sauron.config;

import org.junit.Test;
import org.springframework.boot.actuate.web.exchanges.HttpExchange;
import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class InfrastructureBeansConfigTest
{
    @Test
    public void restTemplateUsesApacheHttpClientWithFiveSecondTimeouts()
    {
        RestTemplate restTemplate = new RestTemplateConfig().restTemplate();

        assertThat(restTemplate.getRequestFactory()).isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
        // TEST TIED TO INTERNALS (Spring 6 / HttpClient 5): the factory keeps the timeouts in its own fields instead of an HttpClient 4 RequestConfig; same 5 second values.
        assertThat(ReflectionTestUtils.getField(restTemplate.getRequestFactory(), "connectTimeout")).isEqualTo(5000L);
        assertThat(ReflectionTestUtils.getField(restTemplate.getRequestFactory(), "connectionRequestTimeout")).isEqualTo(5000L);
        assertThat(ReflectionTestUtils.getField(restTemplate.getRequestFactory(), "readTimeout")).isEqualTo(5000L);
    }


    @Test
    public void httpTraceRepositoryIsInMemoryAndKeepsTheLatestHundred()
    {
        // TEST TIED TO INTERNALS (Spring Boot 3): actuator HttpTrace* was renamed HttpExchange*; same in-memory store capped at 100.
        HttpExchangeRepository repository = new BeansConfig().httpExchangeRepository();

        assertThat(repository).isExactlyInstanceOf(InMemoryHttpExchangeRepository.class);
        for (int i = 0; i < 101; i++)
        {
            repository.add(mock(HttpExchange.class));
        }
        assertThat(repository.findAll()).hasSize(100);
    }
}

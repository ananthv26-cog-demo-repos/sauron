package com.freenow.sauron.config;

import org.apache.http.client.config.RequestConfig;
import org.junit.Test;
import org.springframework.boot.actuate.trace.http.HttpTrace;
import org.springframework.boot.actuate.trace.http.HttpTraceRepository;
import org.springframework.boot.actuate.trace.http.InMemoryHttpTraceRepository;
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
        RequestConfig config = (RequestConfig) ReflectionTestUtils.getField(restTemplate.getRequestFactory(), "requestConfig");
        assertThat(config.getConnectTimeout()).isEqualTo(5000);
        assertThat(config.getConnectionRequestTimeout()).isEqualTo(5000);
        assertThat(config.getSocketTimeout()).isEqualTo(5000);
    }


    @Test
    public void httpTraceRepositoryIsInMemoryAndKeepsTheLatestHundred()
    {
        HttpTraceRepository repository = new BeansConfig().httpTraceRepository();

        assertThat(repository).isExactlyInstanceOf(InMemoryHttpTraceRepository.class);
        for (int i = 0; i < 101; i++)
        {
            repository.add(mock(HttpTrace.class));
        }
        assertThat(repository.findAll()).hasSize(100);
    }
}

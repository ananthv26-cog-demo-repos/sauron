package com.freenow.sauron.config;

import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeansConfig
{
    @Bean
    public HttpExchangeRepository httpExchangeRepository()
    {
        return new InMemoryHttpExchangeRepository();
    }
}

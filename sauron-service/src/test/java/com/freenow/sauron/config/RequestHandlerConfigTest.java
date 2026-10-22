package com.freenow.sauron.config;

import com.freenow.sauron.handler.RequestHandler;
import com.freenow.sauron.handler.impl.RabbitmqRequestHandler;
import com.freenow.sauron.handler.impl.SpringEventsRequestHandler;
import org.junit.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class RequestHandlerConfigTest
{
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(RabbitInfrastructure.class, RequestHandlerConfig.class);


    @Test
    public void springEventsHandlerWhenRabbitFlagIsMissing()
    {
        runner.run(context -> assertThat(context).getBean(RequestHandler.class).isExactlyInstanceOf(SpringEventsRequestHandler.class));
    }


    @Test
    public void springEventsHandlerWhenRabbitFlagIsFalse()
    {
        runner.withPropertyValues("spring.rabbitmq.enabled=false")
            .run(context -> assertThat(context).getBean(RequestHandler.class).isExactlyInstanceOf(SpringEventsRequestHandler.class));
    }


    @Test
    public void rabbitHandlerWhenRabbitFlagIsTrue()
    {
        runner.withPropertyValues("spring.rabbitmq.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(RequestHandler.class);
            assertThat(context).getBean(RequestHandler.class).isExactlyInstanceOf(RabbitmqRequestHandler.class);
            assertThat(context).getBean("rabbitmqRequestHandler").isNotNull();
        });
    }


    @Test
    public void flagIsCaseInsensitive()
    {
        runner.withPropertyValues("spring.rabbitmq.enabled=TRUE")
            .run(context -> assertThat(context).getBean(RequestHandler.class).isExactlyInstanceOf(RabbitmqRequestHandler.class));
    }


    @Test
    public void unrecognisedFlagValueLeavesNoRequestHandler_currentBehavior()
    {
        // CHARACTERIZATION: any value other than true/false (e.g. "yes", "") matches neither condition, so no RequestHandler exists and PipelineService cannot start.
        runner.withPropertyValues("spring.rabbitmq.enabled=yes")
            .run(context -> assertThat(context).doesNotHaveBean(RequestHandler.class));
        runner.withPropertyValues("spring.rabbitmq.enabled=")
            .run(context -> assertThat(context).doesNotHaveBean(RequestHandler.class));
    }


    @Configuration
    static class RabbitInfrastructure
    {
        @Bean
        RabbitTemplate rabbitTemplate()
        {
            return new RabbitTemplate(mock(ConnectionFactory.class));
        }


        @Bean
        RabbitListenerEndpointRegistry rabbitListenerEndpointRegistry()
        {
            return new RabbitListenerEndpointRegistry();
        }
    }
}

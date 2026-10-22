package com.freenow.sauron.config;

import org.junit.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class RabbitmqConfigTest
{
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
        .withUserConfiguration(RabbitmqConfig.class);


    @Test
    public void noRabbitBeansUnlessEnabled()
    {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(Queue.class);
            assertThat(context).doesNotHaveBean(RabbitTemplate.class);
            assertThat(context).doesNotHaveBean(SimpleRabbitListenerContainerFactory.class);
            assertThat(context).doesNotHaveBean(Jackson2JsonMessageConverter.class);
        });
        runner.withPropertyValues("spring.rabbitmq.enabled=false").run(context -> assertThat(context).doesNotHaveBean(Queue.class));
    }


    @Test
    public void declaresADurableSauronQueue()
    {
        runner.withPropertyValues("spring.rabbitmq.enabled=true").run(context -> {
            Queue queue = context.getBean(Queue.class);
            assertThat(queue.getName()).isEqualTo("asyncSauronPipelineRequest");
            assertThat(queue.isDurable()).isTrue();
            assertThat(queue.isExclusive()).isFalse();
            assertThat(queue.isAutoDelete()).isFalse();
            assertThat(queue.getArguments()).isEmpty();
        });
    }


    @Test
    public void listenerFactoryUsesPrefetchOneAndTheJsonConverter()
    {
        runner.withPropertyValues("spring.rabbitmq.enabled=true").run(context -> {
            SimpleRabbitListenerContainerFactory factory = context.getBean("rabbitListenerContainerFactory", SimpleRabbitListenerContainerFactory.class);
            Jackson2JsonMessageConverter converter = context.getBean(Jackson2JsonMessageConverter.class);
            assertThat(ReflectionTestUtils.getField(factory, "prefetchCount")).isEqualTo(1);
            assertThat(ReflectionTestUtils.getField(factory, "messageConverter")).isSameAs(converter);
            assertThat(ReflectionTestUtils.getField(factory, "connectionFactory")).isSameAs(context.getBean(ConnectionFactory.class));
        });
    }


    @Test
    public void templateUsesTheJsonConverterAndTheConnectionFactory()
    {
        runner.withPropertyValues("spring.rabbitmq.enabled=true").run(context -> {
            RabbitTemplate template = context.getBean(RabbitTemplate.class);
            assertThat(template.getMessageConverter()).isSameAs(context.getBean(Jackson2JsonMessageConverter.class));
            assertThat(template.getConnectionFactory()).isSameAs(context.getBean(ConnectionFactory.class));
            assertThat(context).getBean("producerJackson2MessageConverter").isNotNull();
        });
    }
}

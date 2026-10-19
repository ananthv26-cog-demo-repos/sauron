package com.freenow.sauron.handler.impl;

import com.freenow.sauron.datatransferobject.BuildRequest;
import com.freenow.sauron.plugins.PluginsLoadedEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.SimpleResourceHolder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class RabbitmqRequestHandlerTest
{
    private final CachingConnectionFactory connectionFactory = new CachingConnectionFactory("localhost");

    private final CapturingRabbitTemplate template = new CapturingRabbitTemplate(connectionFactory);

    private final MessageListenerContainer container = mock(MessageListenerContainer.class);

    private final List<String> requestedListenerIds = new ArrayList<>();

    private final RabbitListenerEndpointRegistry registry = new RabbitListenerEndpointRegistry()
    {
        @Override
        public MessageListenerContainer getListenerContainer(String id)
        {
            requestedListenerIds.add(id);
            return container;
        }
    };

    private final RabbitmqRequestHandler handler = new RabbitmqRequestHandler(template, registry);


    @After
    public void tearDown()
    {
        while (SimpleResourceHolder.has(connectionFactory))
        {
            SimpleResourceHolder.unbind(connectionFactory);
        }
        connectionFactory.destroy();
    }


    private static PluginsLoadedEvent event(boolean success)
    {
        PluginsLoadedEvent event = new PluginsLoadedEvent();
        event.setSuccess(success);
        return event;
    }


    @Test
    public void handleSendsTheRequestToTheSauronQueueWithTheListenerFactoryBound()
    {
        BuildRequest request = BuildRequest.builder().serviceName("svc").build();

        handler.handle(request);

        assertThat(template.routingKeys).containsExactly("asyncSauronPipelineRequest");
        assertThat(template.payloads).containsExactly(request);
        assertThat(template.boundLookupKeys).containsExactly("rabbitListenerContainerFactory");
        assertThat(SimpleResourceHolder.has(connectionFactory)).isFalse();
    }


    @Test
    public void handleOverwritesAndRemovesAnExistingBinding_currentBehavior()
    {
        // CHARACTERIZATION: an existing binding on the connection factory is overwritten for the send and then removed, so the caller loses it.
        SimpleResourceHolder.bind(connectionFactory, "caller-binding");

        handler.handle(new BuildRequest());

        assertThat(template.boundLookupKeys).containsExactly("rabbitListenerContainerFactory");
        assertThat(SimpleResourceHolder.has(connectionFactory)).isFalse();
    }


    @Test
    public void consumeForwardsToTheConsumer()
    {
        List<BuildRequest> consumed = new ArrayList<>();
        handler.setConsumer(consumed::add);
        BuildRequest request = new BuildRequest();

        handler.consume(request);

        assertThat(consumed).containsExactly(request);
    }


    @Test
    public void consumeWithoutConsumerDiscardsTheRequest()
    {
        handler.consume(new BuildRequest());

        assertThat(template.payloads).isEmpty();
    }


    @Test
    public void successfulPluginLoadStartsTheListener()
    {
        handler.onPluginLoadedEvent(event(true));

        assertThat(requestedListenerIds).containsExactly("sauron-consumer");
        verify(container).start();
        verify(container, never()).stop();
    }


    @Test
    public void failedPluginLoadStopsTheListener()
    {
        handler.onPluginLoadedEvent(event(false));

        assertThat(requestedListenerIds).containsExactly("sauron-consumer");
        verify(container).stop();
        verify(container, never()).start();
    }


    @Test
    public void listenerIsDeclaredOnTheSauronQueueWithManualStartup() throws Exception
    {
        RabbitListener listener = RabbitmqRequestHandler.class.getMethod("consume", BuildRequest.class).getAnnotation(RabbitListener.class);

        assertThat(listener.id()).isEqualTo("sauron-consumer");
        assertThat(listener.containerFactory()).isEqualTo("rabbitListenerContainerFactory");
        assertThat(listener.queues()).containsExactly("asyncSauronPipelineRequest");
        assertThat(listener.autoStartup()).isEqualTo("false");
    }


    private static class CapturingRabbitTemplate extends RabbitTemplate
    {
        private final List<String> routingKeys = new ArrayList<>();

        private final List<Object> payloads = new ArrayList<>();

        private final List<Object> boundLookupKeys = new ArrayList<>();


        CapturingRabbitTemplate(ConnectionFactory connectionFactory)
        {
            super(connectionFactory);
        }


        @Override
        public void convertAndSend(String routingKey, Object object)
        {
            routingKeys.add(routingKey);
            payloads.add(object);
            boundLookupKeys.add(SimpleResourceHolder.get(getConnectionFactory()));
        }
    }
}

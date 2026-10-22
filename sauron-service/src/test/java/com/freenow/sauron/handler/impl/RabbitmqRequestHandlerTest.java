package com.freenow.sauron.handler.impl;

import com.freenow.sauron.datatransferobject.BuildRequest;
import com.freenow.sauron.plugins.PluginsLoadedEvent;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.SimpleResourceHolder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RabbitmqRequestHandlerTest
{
    private ConnectionFactory connectionFactory;

    private Channel channel;

    private RabbitListenerEndpointRegistry registry;

    private MessageListenerContainer container;

    private RabbitmqRequestHandler handler;

    private final List<Object> boundDuringPublish = new ArrayList<>();


    @Before
    public void setUp() throws Exception
    {
        connectionFactory = mock(ConnectionFactory.class);
        Connection connection = mock(Connection.class);
        channel = mock(Channel.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        when(connection.createChannel(anyBoolean())).thenReturn(channel);
        when(channel.isOpen()).thenReturn(true);
        doAnswer(inv -> boundDuringPublish.add(SimpleResourceHolder.get(connectionFactory)))
            .when(channel).basicPublish(anyString(), anyString(), anyBoolean(), any(AMQP.BasicProperties.class), any(byte[].class));

        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        registry = mock(RabbitListenerEndpointRegistry.class);
        container = mock(MessageListenerContainer.class);
        when(registry.getListenerContainer("sauron-consumer")).thenReturn(container);
        handler = new RabbitmqRequestHandler(template, registry);
    }


    @After
    public void tearDown()
    {
        if (SimpleResourceHolder.has(connectionFactory))
        {
            SimpleResourceHolder.unbind(connectionFactory);
        }
    }


    @Test
    public void handlePublishesJsonToTheSauronQueueViaTheDefaultExchange() throws Exception
    {
        BuildRequest request = BuildRequest.builder().serviceName("svc").commitId("c1").buildId("b1").eventTime(new Date(0)).returnCode(0).build();

        handler.handle(request);

        ArgumentCaptor<AMQP.BasicProperties> props = ArgumentCaptor.forClass(AMQP.BasicProperties.class);
        ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
        verify(channel).basicPublish(eq(""), eq("asyncSauronPipelineRequest"), eq(false), props.capture(), body.capture());
        assertThat(props.getValue().getContentType()).isEqualTo("application/json");
        assertThat(props.getValue().getContentEncoding()).isEqualTo("UTF-8");
        assertThat(props.getValue().getDeliveryMode()).isEqualTo(2);
        assertThat(props.getValue().getHeaders()).containsEntry("__TypeId__", "com.freenow.sauron.datatransferobject.BuildRequest");
        assertThat(new String(body.getValue(), StandardCharsets.UTF_8)).isEqualTo(
            "{\"serviceName\":\"svc\",\"repositoryUrl\":null,\"commitId\":\"c1\",\"buildId\":\"b1\",\"owner\":null,\"eventTime\":0,"
                + "\"environment\":null,\"release\":null,\"dockerImage\":null,\"returnCode\":0,\"rollback\":null,\"user\":\"none\","
                + "\"platform\":\"ECS\",\"deploymentStrategy\":\"BLUE_GREEN\",\"documentId\":null,\"indexName\":null,\"plugin\":null,"
                + "\"customizedRepositoryUrl\":null,\"deploymentState\":null,\"tookSeconds\":null,\"canaryWeight\":null}");
    }


    @Test
    public void handleBindsTheContainerFactoryKeyOnlyWhilePublishing()
    {
        handler.handle(BuildRequest.builder().serviceName("svc").build());

        assertThat(boundDuringPublish).containsExactly("rabbitListenerContainerFactory");
        assertThat(SimpleResourceHolder.has(connectionFactory)).isFalse();
    }


    @Test
    public void handleDropsAPreExistingBinding_currentBehavior()
    {
        // CHARACTERIZATION: if the connection factory is already bound, bind() fails after overwriting, and the unbind afterwards drops the caller's original binding.
        SimpleResourceHolder.bind(connectionFactory, "caller-binding");

        handler.handle(BuildRequest.builder().serviceName("svc").build());

        assertThat(boundDuringPublish).containsExactly("rabbitListenerContainerFactory");
        assertThat(SimpleResourceHolder.has(connectionFactory)).isFalse();
    }


    @Test
    public void handlePropagatesPublishFailures() throws Exception
    {
        doAnswer(inv -> {
            throw new java.io.IOException("broker gone");
        }).when(channel).basicPublish(anyString(), anyString(), anyBoolean(), any(AMQP.BasicProperties.class), any(byte[].class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> handler.handle(BuildRequest.builder().serviceName("svc").build()))
            .isInstanceOf(org.springframework.amqp.AmqpException.class);
    }


    @Test
    public void consumePassesTheRequestToTheConsumer()
    {
        List<BuildRequest> consumed = new ArrayList<>();
        handler.setConsumer(consumed::add);
        BuildRequest request = BuildRequest.builder().serviceName("svc").build();

        handler.consume(request);

        assertThat(consumed).containsExactly(request);
    }


    @Test
    public void consumeWithoutConsumerDiscardsTheMessage()
    {
        assertThatCode(() -> handler.consume(BuildRequest.builder().serviceName("svc").build())).doesNotThrowAnyException();
    }


    @Test
    public void successfulPluginLoadStartsTheListenerAndFailureStopsIt()
    {
        handler.onPluginLoadedEvent(event(true));
        verify(container).start();
        verify(container, never()).stop();

        handler.onPluginLoadedEvent(event(false));
        org.mockito.InOrder order = inOrder(container);
        order.verify(container).start();
        order.verify(container).stop();
    }


    private static PluginsLoadedEvent event(boolean success)
    {
        PluginsLoadedEvent event = new PluginsLoadedEvent();
        event.setSuccess(success);
        return event;
    }
}

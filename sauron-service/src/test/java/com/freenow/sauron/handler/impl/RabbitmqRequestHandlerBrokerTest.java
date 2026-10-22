package com.freenow.sauron.handler.impl;

import com.freenow.sauron.config.RabbitmqConfig;
import com.freenow.sauron.config.RequestHandlerConfig;
import com.freenow.sauron.datatransferobject.BuildRequest;
import com.freenow.sauron.handler.RequestHandler;
import com.freenow.sauron.plugins.PluginsLoadedEvent;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Real RabbitMQ (Testcontainers) with the production Rabbit wiring. Skipped, not failed, when Docker is unavailable.
 */
public class RabbitmqRequestHandlerBrokerTest
{
    private static final String QUEUE = "asyncSauronPipelineRequest";

    private static RabbitMQContainer rabbit;


    @BeforeClass
    public static void startBroker()
    {
        Assume.assumeTrue("Docker is required for the RabbitMQ broker test", DockerClientFactory.instance().isDockerAvailable());
        rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-alpine"));
        rabbit.start();
    }


    @AfterClass
    public static void stopBroker()
    {
        if (rabbit != null)
        {
            rabbit.stop();
        }
    }


    @Test
    public void requestsWaitInADurableQueueUntilPluginsLoadThenReachTheConsumerIntact()
    {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
            .withUserConfiguration(RabbitmqConfig.class, RequestHandlerConfig.class)
            .withPropertyValues(
                "spring.rabbitmq.enabled=true",
                "spring.rabbitmq.host=" + rabbit.getHost(),
                "spring.rabbitmq.port=" + rabbit.getAmqpPort(),
                "spring.rabbitmq.username=" + rabbit.getAdminUsername(),
                "spring.rabbitmq.password=" + rabbit.getAdminPassword())
            .run(context -> {
                RequestHandler handler = context.getBean(RequestHandler.class);
                assertThat(handler).isInstanceOf(RabbitmqRequestHandler.class);
                List<BuildRequest> received = new CopyOnWriteArrayList<>();
                handler.setConsumer(received::add);
                RabbitTemplate template = context.getBean(RabbitTemplate.class);
                RabbitAdmin admin = context.getBean(RabbitAdmin.class);
                RabbitListenerEndpointRegistry registry = context.getBean(RabbitListenerEndpointRegistry.class);

                BuildRequest first = request("first");
                handler.handle(first);
                Message raw = template.receive(QUEUE, 10000);
                assertThat(raw).isNotNull();
                assertThat(raw.getMessageProperties().getContentType()).isEqualTo("application/json");
                assertThat(raw.getMessageProperties().getHeaders()).containsEntry("__TypeId__", "com.freenow.sauron.datatransferobject.BuildRequest");
                assertThat(new String(raw.getBody(), StandardCharsets.UTF_8)).startsWith("{\"serviceName\":\"first\",").contains("\"eventTime\":1000,");
                assertThat(admin.getQueueProperties(QUEUE).get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).isEqualTo(0);

                BuildRequest second = request("second");
                handler.handle(second);
                assertThat(registry.getListenerContainer("sauron-consumer").isRunning()).isFalse();
                await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(admin.getQueueProperties(QUEUE).get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).isEqualTo(1));
                assertThat(received).isEmpty();

                context.publishEvent(event(true));
                await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(received).containsExactly(second));
                assertThat(registry.getListenerContainer("sauron-consumer").isRunning()).isTrue();
                assertThat(received.get(0).getEventTime()).isEqualTo(new Date(1000L));

                context.publishEvent(event(false));
                assertThat(registry.getListenerContainer("sauron-consumer").isRunning()).isFalse();
                assertThat(Collections.unmodifiableList(received)).hasSize(1);
            });
    }


    private static BuildRequest request(String serviceName)
    {
        return BuildRequest.builder().serviceName(serviceName).repositoryUrl("git@x:svc.git").commitId("c1").buildId("b1").owner("team")
            .eventTime(new Date(1000L)).environment("prod").release("r1").dockerImage("img:1").returnCode(0).rollback(false)
            .platform(BuildRequest.Platform.K8S).tookSeconds(7).build();
    }


    private static PluginsLoadedEvent event(boolean success)
    {
        PluginsLoadedEvent event = new PluginsLoadedEvent();
        event.setSuccess(success);
        return event;
    }
}

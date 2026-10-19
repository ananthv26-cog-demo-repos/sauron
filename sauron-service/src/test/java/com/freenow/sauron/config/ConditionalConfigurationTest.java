package com.freenow.sauron.config;

import com.freenow.sauron.handler.RequestHandler;
import com.freenow.sauron.handler.impl.RabbitmqRequestHandler;
import com.freenow.sauron.handler.impl.SpringEventsRequestHandler;
import com.freenow.sauron.plugins.artifactory.ArtifactoryDownloader;
import com.freenow.sauron.plugins.artifactory.ArtifactoryRepository;
import com.freenow.sauron.utils.Constants;
import java.util.List;
import org.jfrog.artifactory.client.Artifactory;
import org.junit.Test;
import org.pf4j.JarPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.update.DefaultUpdateRepository;
import org.pf4j.update.UpdateManager;
import org.pf4j.update.UpdateRepository;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import springfox.documentation.spring.web.plugins.Docket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class ConditionalConfigurationTest
{
    private final ApplicationContextRunner handlers = new ApplicationContextRunner()
        .withUserConfiguration(RequestHandlerConfig.class)
        .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
        .withBean(RabbitListenerEndpointRegistry.class, RabbitListenerEndpointRegistry::new);


    @Test
    public void springEventsHandlerIsTheDefault()
    {
        handlers.run(context -> assertThat(context).getBean(RequestHandler.class).isInstanceOf(SpringEventsRequestHandler.class));
    }


    @Test
    public void springEventsHandlerWhenRabbitDisabled()
    {
        handlers.withPropertyValues("spring.rabbitmq.enabled=false")
            .run(context -> assertThat(context).getBean(RequestHandler.class).isInstanceOf(SpringEventsRequestHandler.class));
    }


    @Test
    public void rabbitHandlerWhenRabbitEnabled()
    {
        handlers.withPropertyValues("spring.rabbitmq.enabled=true")
            .run(context ->
            {
                assertThat(context).hasSingleBean(RequestHandler.class);
                assertThat(context).getBean(RequestHandler.class).isInstanceOf(RabbitmqRequestHandler.class);
            });
    }


    @Test
    public void anyOtherRabbitEnabledValueLeavesNoRequestHandler_currentBehavior()
    {
        // CHARACTERIZATION: spring.rabbitmq.enabled values other than true/false (e.g. "yes") register no RequestHandler, so PipelineService cannot be created.
        handlers.withPropertyValues("spring.rabbitmq.enabled=yes")
            .run(context -> assertThat(context).doesNotHaveBean(RequestHandler.class));
    }


    @Test
    public void rabbitBeansOnlyWhenEnabled()
    {
        ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RabbitmqConfig.class)
            .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class));

        runner.run(context -> assertThat(context).doesNotHaveBean(Queue.class).doesNotHaveBean(RabbitTemplate.class));

        runner.withPropertyValues("spring.rabbitmq.enabled=true").run(context ->
        {
            Queue queue = context.getBean(Queue.class);
            assertThat(queue.getName()).isEqualTo(Constants.SAURON_QUEUE_NAME);
            assertThat(queue.isDurable()).isTrue();
            assertThat(queue.isExclusive()).isFalse();
            assertThat(queue.isAutoDelete()).isFalse();

            ConnectionFactory connectionFactory = context.getBean(ConnectionFactory.class);
            Jackson2JsonMessageConverter converter = context.getBean(Jackson2JsonMessageConverter.class);
            SimpleRabbitListenerContainerFactory factory = context.getBean("rabbitListenerContainerFactory", SimpleRabbitListenerContainerFactory.class);
            assertThat(ReflectionTestUtils.getField(factory, "prefetchCount")).isEqualTo(1);
            assertThat(ReflectionTestUtils.getField(factory, "messageConverter")).isSameAs(converter);
            assertThat(ReflectionTestUtils.getField(factory, "connectionFactory")).isSameAs(connectionFactory);

            RabbitTemplate template = context.getBean(RabbitTemplate.class);
            assertThat(template.getMessageConverter()).isSameAs(converter);
            assertThat(template.getConnectionFactory()).isSameAs(connectionFactory);
        });
    }


    @Test
    public void artifactoryBeansOnlyWhenEnabled()
    {
        ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(ArtifactoryConfig.class);

        runner.run(context -> assertThat(context).doesNotHaveBean(Artifactory.class).doesNotHaveBean(UpdateRepository.class));

        runner.withPropertyValues(
                "sauron.artifactory.enabled=true",
                "sauron.artifactory.url=https://artifactory.example.com/artifactory",
                "sauron.artifactory.access-token=token",
                "sauron.artifactory.repository=libs",
                "sauron.artifactory.group-id=com/x")
            .run(context ->
            {
                assertThat(context.getBean(Artifactory.class).getUri()).isEqualTo("https://artifactory.example.com");
                assertThat(context.getBean(Artifactory.class).getContextName()).isEqualTo("artifactory");
                ArtifactoryDownloader downloader = context.getBean(ArtifactoryDownloader.class);
                assertThat(downloader).isNotNull();
                assertThat(context).getBean(UpdateRepository.class).isInstanceOf(ArtifactoryRepository.class);
                assertThat(((ArtifactoryRepository) context.getBean(UpdateRepository.class)).getFileDownloader()).isSameAs(downloader);
            });
    }


    @Test
    public void pluginUpdateUsesALocalRepositoryByDefault()
    {
        new ApplicationContextRunner().withUserConfiguration(PluginUpdateConfig.class).run(context ->
        {
            PluginManager pluginManager = context.getBean(PluginManager.class);
            assertThat(pluginManager).isInstanceOf(JarPluginManager.class);
            UpdateRepository repository = context.getBean(UpdateRepository.class);
            assertThat(repository).isInstanceOf(DefaultUpdateRepository.class);
            assertThat(repository.getId()).isEqualTo(DefaultUpdateRepository.class.getName());
            assertThat(repository.getUrl()).isEqualTo(pluginManager.getPluginsRoot().toUri().toURL());
            assertThat(context).hasSingleBean(UpdateManager.class);
        });
    }


    @Test
    public void pluginUpdateUsesArtifactoryWhenEnabled()
    {
        new ApplicationContextRunner()
            .withUserConfiguration(ArtifactoryConfig.class, PluginUpdateConfig.class)
            .withPropertyValues("sauron.artifactory.enabled=true", "sauron.artifactory.url=https://artifactory.example.com/artifactory")
            .run(context ->
            {
                assertThat(context).hasSingleBean(UpdateRepository.class);
                @SuppressWarnings("unchecked")
                List<UpdateRepository> repositories = (List<UpdateRepository>) ReflectionTestUtils.getField(context.getBean(UpdateManager.class), "repositories");
                assertThat(repositories).containsExactly(context.getBean(UpdateRepository.class));
                assertThat(repositories.get(0)).isInstanceOf(ArtifactoryRepository.class);
            });
    }


    @Test
    public void swaggerDocumentsOnlyControllersWithoutDefaultResponses()
    {
        Docket docket = new SwaggerConfig().api();

        assertThat(ReflectionTestUtils.getField(docket, "applyDefaultResponseMessages")).isEqualTo(false);
        assertThat(docket.getDocumentationType().getName()).isEqualTo("swagger");
        assertThat(docket.getDocumentationType().getVersion()).isEqualTo("2.0");
    }
}

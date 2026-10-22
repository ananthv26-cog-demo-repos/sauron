package com.freenow.sauron.config;

import com.freenow.sauron.plugins.artifactory.ArtifactoryDownloader;
import com.freenow.sauron.plugins.artifactory.ArtifactoryRepository;
import com.freenow.sauron.properties.ArtifactoryConfigurationProperties;
import org.jfrog.artifactory.client.Artifactory;
import org.junit.Test;
import org.pf4j.JarPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.update.DefaultUpdateRepository;
import org.pf4j.update.UpdateManager;
import org.pf4j.update.UpdateRepository;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class ArtifactoryConfigTest
{
    // Same order as component scanning (alphabetical): ArtifactoryConfig is registered before PluginUpdateConfig.
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(ArtifactoryConfig.class, PluginUpdateConfig.class);


    @Test
    public void withoutArtifactoryTheLocalPluginsFolderIsTheOnlyRepository()
    {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(Artifactory.class);
            assertThat(context).doesNotHaveBean(ArtifactoryConfigurationProperties.class);
            PluginManager pluginManager = context.getBean(PluginManager.class);
            assertThat(pluginManager).isExactlyInstanceOf(JarPluginManager.class);
            UpdateRepository repository = context.getBean(UpdateRepository.class);
            assertThat(repository).isExactlyInstanceOf(DefaultUpdateRepository.class);
            assertThat(repository.getId()).isEqualTo("org.pf4j.update.DefaultUpdateRepository");
            assertThat(repository.getUrl()).isEqualTo(pluginManager.getPluginsRoot().toUri().toURL());
            UpdateManager updateManager = context.getBean(UpdateManager.class);
            assertThat(updateManager.getRepositories()).containsExactly(repository);
        });
    }


    @Test
    public void artifactoryFlagMustBeExactlyTrue()
    {
        runner.withPropertyValues("sauron.artifactory.enabled=false", "sauron.artifactory.url=http://artifactory.local")
            .run(context -> assertThat(context).doesNotHaveBean(Artifactory.class));
    }


    @Test
    public void artifactoryEnabledReplacesTheLocalRepository()
    {
        runner.withPropertyValues(
            "sauron.artifactory.enabled=true",
            "sauron.artifactory.url=http://artifactory.local/artifactory",
            "sauron.artifactory.access-token=t0ken",
            "sauron.artifactory.repository=internal",
            "sauron.artifactory.group-id=com/freenow/sauron/plugins"
        ).run(context -> {
            ArtifactoryConfigurationProperties properties = context.getBean(ArtifactoryConfigurationProperties.class);
            assertThat(properties.getUrl()).isEqualTo("http://artifactory.local/artifactory");
            assertThat(properties.getAccessToken()).isEqualTo("t0ken");
            assertThat(properties.getRepository()).isEqualTo("internal");
            assertThat(properties.getGroupId()).isEqualTo("com/freenow/sauron/plugins");
            assertThat(properties.getEnabled()).isTrue();

            // CHARACTERIZATION: the Artifactory client keeps only scheme and host in getUri(); the "/artifactory" context path is dropped.
            assertThat(context.getBean(Artifactory.class).getUri()).isEqualTo("http://artifactory.local");
            assertThat(context).hasSingleBean(ArtifactoryDownloader.class);
            assertThat(context).hasSingleBean(UpdateRepository.class);
            UpdateRepository repository = context.getBean(UpdateRepository.class);
            assertThat(repository).isExactlyInstanceOf(ArtifactoryRepository.class);
            assertThat(repository.getFileDownloader()).isSameAs(context.getBean(ArtifactoryDownloader.class));
            assertThat(context.getBean(UpdateManager.class).getRepositories()).containsExactly(repository);
        });
    }


    @Test
    public void propertiesToStringExposesTheAccessToken_currentBehavior()
    {
        // CHARACTERIZATION: Lombok @Data toString prints the Artifactory access token in clear text.
        ArtifactoryConfigurationProperties properties = new ArtifactoryConfigurationProperties();
        properties.setUrl("http://a");
        properties.setRepository("r");
        properties.setAccessToken("secret-token");
        properties.setGroupId("g");

        assertThat(properties.toString())
            .isEqualTo("ArtifactoryConfigurationProperties(url=http://a, repository=r, accessToken=secret-token, groupId=g, enabled=null)");
    }
}

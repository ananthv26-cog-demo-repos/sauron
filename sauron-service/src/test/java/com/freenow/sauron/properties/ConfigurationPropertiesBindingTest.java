package com.freenow.sauron.properties;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigurationPropertiesBindingTest
{
    private static Binder binder(Map<String, String> properties)
    {
        return new Binder(new MapConfigurationPropertySource(properties));
    }


    private static PipelineConfigurationProperties bindPipeline(Map<String, String> properties)
    {
        return binder(properties).bindOrCreate("sauron", Bindable.of(PipelineConfigurationProperties.class));
    }


    @Test
    public void pipelineDefaultsWhenNothingIsConfigured()
    {
        PipelineConfigurationProperties properties = new PipelineConfigurationProperties();

        assertThat(properties.getPipelines()).isEmpty();
        assertThat(properties.getDefaultPipeline()).isEmpty();
        assertThat(properties.getPipeline("missing")).isEmpty();
        assertThat(properties.getMandatoryOutputPlugin()).isEqualTo("elasticsearch-output");
    }


    @Test
    public void bindsIndexedPipelineLists()
    {
        Map<String, String> source = new HashMap<>();
        source.put("sauron.pipelines.default[0]", "git-checkout");
        source.put("sauron.pipelines.default[1]", "maven-report");
        source.put("sauron.pipelines.other[0]", "console-output");

        PipelineConfigurationProperties properties = bindPipeline(source);

        assertThat(properties.getDefaultPipeline()).containsExactly("git-checkout", "maven-report");
        assertThat(properties.getPipeline("other")).containsExactly("console-output");
        assertThat(properties.getMandatoryOutputPlugin()).isEqualTo("elasticsearch-output");
    }


    @Test
    public void bindsCommaSeparatedPipelineAndOverridesMandatoryOutput()
    {
        Map<String, String> source = new HashMap<>();
        source.put("sauron.pipelines.default", "a,b,c");
        source.put("sauron.mandatory-output-plugin", "console-output");

        PipelineConfigurationProperties properties = bindPipeline(source);

        assertThat(properties.getDefaultPipeline()).containsExactly("a", "b", "c");
        assertThat(properties.getMandatoryOutputPlugin()).isEqualTo("console-output");
    }


    @Test
    public void emptyMandatoryOutputPluginBindsAsEmptyString()
    {
        Map<String, String> source = new HashMap<>();
        source.put("sauron.mandatory-output-plugin", "");

        assertThat(bindPipeline(source).getMandatoryOutputPlugin()).isEmpty();
    }


    @Test
    public void bindsArtifactoryProperties()
    {
        Map<String, String> source = new HashMap<>();
        source.put("sauron.artifactory.url", "https://artifactory.example/artifactory");
        source.put("sauron.artifactory.repository", "libs-release");
        source.put("sauron.artifactory.access-token", "secret");
        source.put("sauron.artifactory.group-id", "com/free-now/sauron/plugins");
        source.put("sauron.artifactory.enabled", "true");

        ArtifactoryConfigurationProperties properties = binder(source)
            .bind("sauron.artifactory", Bindable.of(ArtifactoryConfigurationProperties.class)).get();

        assertThat(properties.getUrl()).isEqualTo("https://artifactory.example/artifactory");
        assertThat(properties.getRepository()).isEqualTo("libs-release");
        assertThat(properties.getAccessToken()).isEqualTo("secret");
        assertThat(properties.getGroupId()).isEqualTo("com/free-now/sauron/plugins");
        assertThat(properties.getEnabled()).isTrue();
    }


    @Test
    public void artifactoryPropertiesToStringIncludesAccessToken_currentBehavior()
    {
        // CHARACTERIZATION: Lombok @Data toString prints the access token, so logging this bean leaks the secret.
        ArtifactoryConfigurationProperties properties = new ArtifactoryConfigurationProperties();
        properties.setAccessToken("secret");

        assertThat(properties.toString()).contains("accessToken=secret");
    }
}

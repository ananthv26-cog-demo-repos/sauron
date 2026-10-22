package com.freenow.sauron.config;

import com.freenow.sauron.properties.PipelineConfigurationProperties;
import org.junit.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

public class PipelineConfigurationPropertiesBindingTest
{
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Enable.class);


    @Test
    public void defaultsWhenNothingIsConfigured()
    {
        runner.run(context -> {
            PipelineConfigurationProperties properties = context.getBean(PipelineConfigurationProperties.class);
            assertThat(properties.getPipelines()).isEmpty();
            assertThat(properties.getDefaultPipeline()).isEmpty();
            assertThat(properties.getMandatoryOutputPlugin()).isEqualTo("elasticsearch-output");
        });
    }


    @Test
    public void bindsIndexedPipelinesInOrderIncludingDuplicates()
    {
        runner.withPropertyValues(
            "sauron.pipelines.default[0]=console-output",
            "sauron.pipelines.default[1]=git-checkout",
            "sauron.pipelines.default[2]=console-output",
            "sauron.pipelines.reprocess[0]=maven-report",
            "sauron.mandatory-output-plugin=custom-output"
        ).run(context -> {
            PipelineConfigurationProperties properties = context.getBean(PipelineConfigurationProperties.class);
            assertThat(properties.getDefaultPipeline()).containsExactly("console-output", "git-checkout", "console-output");
            assertThat(properties.getPipeline("reprocess")).containsExactly("maven-report");
            assertThat(properties.getPipeline("unknown")).isEmpty();
            assertThat(properties.getPipelines()).containsOnlyKeys("default", "reprocess");
            assertThat(properties.getMandatoryOutputPlugin()).isEqualTo("custom-output");
        });
    }


    @Test
    public void bindsCommaSeparatedPipelineAndCamelCaseOutputKey()
    {
        runner.withPropertyValues("sauron.pipelines.default=a, b ,c", "sauron.mandatoryOutputPlugin=x")
            .run(context -> {
                PipelineConfigurationProperties properties = context.getBean(PipelineConfigurationProperties.class);
                assertThat(properties.getDefaultPipeline()).containsExactly("a", "b", "c");
                assertThat(properties.getMandatoryOutputPlugin()).isEqualTo("x");
            });
    }


    @Test
    public void emptyMandatoryOutputPluginBindsAsEmptyString()
    {
        runner.withPropertyValues("sauron.mandatory-output-plugin=")
            .run(context -> assertThat(context.getBean(PipelineConfigurationProperties.class).getMandatoryOutputPlugin()).isEmpty());
    }


    @Test
    public void pipelineNamesKeepTheirCase()
    {
        runner.withPropertyValues("sauron.pipelines.Default[0]=a")
            .run(context -> {
                PipelineConfigurationProperties properties = context.getBean(PipelineConfigurationProperties.class);
                assertThat(properties.getPipelines()).containsOnlyKeys("Default");
                assertThat(properties.getDefaultPipeline()).isEmpty();
            });
    }


    @Configuration
    @EnableConfigurationProperties(PipelineConfigurationProperties.class)
    static class Enable
    {
    }
}

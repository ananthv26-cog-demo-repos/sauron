package com.freenow.sauron.service;

import com.freenow.sauron.datatransferobject.BuildRequest;
import com.freenow.sauron.handler.RequestHandler;
import com.freenow.sauron.model.DataSet;
import com.freenow.sauron.plugins.SauronExtension;
import com.freenow.sauron.properties.PipelineConfigurationProperties;
import com.freenow.sauron.properties.PluginsConfigurationProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pf4j.PluginManager;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PipelineServiceCharacterizationTest
{
    private final List<String> executed = new ArrayList<>();

    private final List<DataSet> seenDataSets = new ArrayList<>();

    private final List<Map<String, String>> seenMdc = new ArrayList<>();

    private final Map<String, List<SauronExtension>> extensions = new HashMap<>();

    private PluginManager pluginManager;

    private PipelineConfigurationProperties pipelineProperties;

    private PluginsConfigurationProperties pluginsProperties;

    private SimpleMeterRegistry meterRegistry;

    private CapturingHandler handler;

    private PipelineService service;


    @Before
    public void setUp()
    {
        MDC.clear();
        pluginManager = mock(PluginManager.class);
        when(pluginManager.getExtensions(eq(SauronExtension.class), anyString()))
            .thenAnswer(inv -> extensions.getOrDefault(inv.<String>getArgument(1), Collections.emptyList()));
        pipelineProperties = new PipelineConfigurationProperties();
        pluginsProperties = new PluginsConfigurationProperties();
        meterRegistry = new SimpleMeterRegistry();
        handler = new CapturingHandler();
        service = new PipelineService(pluginManager, pipelineProperties, pluginsProperties, handler, meterRegistry);
    }


    @After
    public void tearDown()
    {
        MDC.clear();
    }


    @Test
    public void constructorRegistersProcessAsTheHandlerConsumer()
    {
        register("a");
        defaultPipeline("a");

        handler.consumer.accept(request(null));

        assertThat(executed).containsExactly("a");
    }


    @Test
    public void publishHandsTheSameRequestToTheHandler()
    {
        BuildRequest request = request(null);

        service.publish(request);

        assertThat(handler.handled).containsExactly(request);
        assertThat(executed).isEmpty();
    }


    @Test
    public void publishSwallowsHandlerExceptions()
    {
        handler.failOnHandle = true;

        assertThatCode(() -> service.publish(request(null))).doesNotThrowAnyException();
        assertThat(handler.handled).hasSize(1);
    }


    @Test
    public void defaultPipelineRunsEveryPluginInOrderOnOneSharedDataSet()
    {
        register("a", "b", "c");
        defaultPipeline("a", "b", "a", "c");

        service.process(request(null));

        assertThat(executed).containsExactly("a", "b", "a", "c");
        assertThat(seenDataSets).hasSize(4);
        assertThat(seenDataSets).allSatisfy(ds -> assertThat(ds).isSameAs(seenDataSets.get(0)));
        DataSet dataSet = seenDataSets.get(0);
        assertThat(dataSet.getServiceName()).isEqualTo("svc");
        assertThat(dataSet.getCommitId()).isEqualTo("c1");
        assertThat(dataSet.getStringAdditionalInformation("environment")).contains("prod");
        assertThat(successCount("a")).isEqualTo(2.0);
        assertThat(successCount("b")).isEqualTo(1.0);
        assertThat(timer("a").count()).isEqualTo(2L);
    }


    @Test
    public void emptyDefaultPipelineRunsNothing()
    {
        register("elasticsearch-output");

        service.process(request(null));

        assertThat(executed).isEmpty();
        assertThat(meterRegistry.getMeters()).isEmpty();
    }


    @Test
    public void pluginsMutateTheDataSetInPlaceForLaterPlugins()
    {
        extensions.put("writer", Collections.singletonList((props, ds) -> {
            executed.add("writer");
            ds.setAdditionalInformation("written", "yes");
            return ds;
        }));
        extensions.put("reader", Collections.singletonList((props, ds) -> {
            executed.add("reader:" + ds.getStringAdditionalInformation("written").orElse("missing"));
            return ds;
        }));
        defaultPipeline("writer", "reader");

        service.process(request(null));

        assertThat(executed).containsExactly("writer", "reader:yes");
    }


    @Test
    public void pluginReturnValueIsIgnored_currentBehavior()
    {
        // CHARACTERIZATION: SauronExtension.apply returns a DataSet, but the pipeline discards it; a plugin that returns a new DataSet instead of mutating the input loses its output.
        extensions.put("replacer", Collections.singletonList((props, ds) -> {
            executed.add("replacer");
            DataSet replacement = new DataSet();
            replacement.setAdditionalInformation("replaced", true);
            return replacement;
        }));
        extensions.put("reader", Collections.singletonList((props, ds) -> {
            executed.add("reader:" + ds.getBooleanAdditionalInformation("replaced").isPresent());
            return ds;
        }));
        defaultPipeline("replacer", "reader");

        service.process(request(null));

        assertThat(executed).containsExactly("replacer", "reader:false");
    }


    @Test
    public void pluginsReceiveTheSharedPluginsConfigurationProperties()
    {
        List<PluginsConfigurationProperties> seen = new ArrayList<>();
        extensions.put("a", Collections.singletonList((props, ds) -> {
            seen.add(props);
            return ds;
        }));
        defaultPipeline("a");

        service.process(request(null));

        assertThat(seen).containsExactly(pluginsProperties);
    }


    @Test
    public void failingPluginIsCountedAsFailureAndThePipelineContinues()
    {
        register("a", "c");
        extensions.put("b", Collections.singletonList((props, ds) -> {
            executed.add("b");
            throw new IllegalStateException("boom");
        }));
        defaultPipeline("a", "b", "c");

        service.process(request(null));

        assertThat(executed).containsExactly("a", "b", "c");
        assertThat(failureCount("b")).isEqualTo(1.0);
        assertThat(meterRegistry.find("sauron.plugin.executions.total").tags("plugin", "b", "result", "success").counter()).isNull();
        assertThat(successCount("c")).isEqualTo(1.0);
    }


    @Test
    public void everyExtensionOfAPluginRuns()
    {
        extensions.put("multi", Arrays.asList(recording("multi-1"), recording("multi-2")));
        defaultPipeline("multi");

        service.process(request(null));

        assertThat(executed).containsExactly("multi-1", "multi-2");
        assertThat(successCount("multi")).isEqualTo(2.0);
    }


    @Test
    public void unknownPluginRunsNothingAndRecordsNoMetrics()
    {
        defaultPipeline("missing");

        service.process(request(null));

        assertThat(executed).isEmpty();
        assertThat(meterRegistry.getMeters()).isEmpty();
    }


    @Test
    public void mdcCarriesPluginAndBuildIdsDuringExecutionAndIsClearedAfterwards()
    {
        extensions.put("a", Collections.singletonList((props, ds) -> {
            seenMdc.add(MDC.getCopyOfContextMap());
            return ds;
        }));
        defaultPipeline("a");

        service.process(request(null));

        Map<String, String> expected = new HashMap<>();
        expected.put("sauron.pluginId", "a");
        expected.put("sauron.serviceName", "svc");
        expected.put("sauron.commitId", "c1");
        expected.put("sauron.buildId", "b1");
        assertThat(seenMdc).containsExactly(expected);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }


    @Test
    public void mdcIsClearedEvenWhenThePluginFails()
    {
        extensions.put("a", Collections.singletonList((props, ds) -> {
            throw new IllegalStateException("boom");
        }));
        defaultPipeline("a");
        MDC.put("unrelated", "kept");

        service.process(request(null));

        assertThat(MDC.getCopyOfContextMap()).containsOnlyKeys("unrelated");
    }


    @Test
    public void preExistingMdcValuesForSauronKeysAreRemoved_currentBehavior()
    {
        // CHARACTERIZATION: runPlugin removes the sauron.* MDC keys instead of restoring the caller's values.
        register("a");
        defaultPipeline("a");
        MDC.put("sauron.serviceName", "outer");

        service.process(request(null));

        assertThat(MDC.get("sauron.serviceName")).isNull();
    }


    @Test
    public void userPluginNotInDefaultPipelineRunsAloneThenMandatoryOutput()
    {
        register("a", "b", "user-plugin", "elasticsearch-output");
        defaultPipeline("a", "b");

        service.process(request("user-plugin"));

        assertThat(executed).containsExactly("user-plugin", "elasticsearch-output");
    }


    @Test
    public void userPluginInDefaultPipelineRunsItsPredecessorsFirstButNotItsSuccessors()
    {
        register("a", "b", "user-plugin", "after", "elasticsearch-output");
        defaultPipeline("a", "b", "user-plugin", "after");

        service.process(request("user-plugin"));

        assertThat(executed).containsExactly("a", "b", "user-plugin", "elasticsearch-output");
    }


    @Test
    public void userPluginFirstInDefaultPipelineRunsNoDependencies()
    {
        register("user-plugin", "b", "elasticsearch-output");
        defaultPipeline("user-plugin", "b");

        service.process(request("user-plugin"));

        assertThat(executed).containsExactly("user-plugin", "elasticsearch-output");
    }


    @Test
    public void userPluginIsLowerCasedBeforeLookup()
    {
        register("user-plugin", "elasticsearch-output");

        service.process(request("User-Plugin"));

        assertThat(executed).containsExactly("user-plugin", "elasticsearch-output");
        assertThat(successCount("user-plugin")).isEqualTo(1.0);
    }


    @Test
    public void mixedCasePipelineEntriesAreNotMatchedAfterLowerCasing_currentBehavior()
    {
        // CHARACTERIZATION: the requested plugin is lower-cased but pipeline entries and PF4J ids are case-sensitive, so a mixed-case id skips its dependencies and never runs.
        register("dep", "My-Plugin", "elasticsearch-output");
        defaultPipeline("dep", "My-Plugin");

        service.process(request("My-Plugin"));

        assertThat(executed).containsExactly("elasticsearch-output");
    }


    @Test
    public void blankUserPluginRunsTheDefaultPipeline()
    {
        register("a", "elasticsearch-output");
        defaultPipeline("a");

        service.process(request("   "));

        assertThat(executed).containsExactly("a");
    }


    @Test
    public void mandatoryOutputPluginIsConfigurable()
    {
        register("user-plugin", "custom-output", "elasticsearch-output");
        pipelineProperties.setMandatoryOutputPlugin("custom-output");

        service.process(request("user-plugin"));

        assertThat(executed).containsExactly("user-plugin", "custom-output");
    }


    @Test
    public void blankMandatoryOutputPluginIsSkipped()
    {
        register("user-plugin", "elasticsearch-output");
        pipelineProperties.setMandatoryOutputPlugin(" ");

        service.process(request("user-plugin"));

        assertThat(executed).containsExactly("user-plugin");
    }


    @Test
    public void requestingTheMandatoryOutputPluginRunsItTwice_currentBehavior()
    {
        // CHARACTERIZATION: when the requested plugin is the mandatory output plugin it runs twice (writes the document twice).
        register("elasticsearch-output");

        service.process(request("elasticsearch-output"));

        assertThat(executed).containsExactly("elasticsearch-output", "elasticsearch-output");
    }


    @Test
    public void processSwallowsExceptionsFromThePluginManager()
    {
        when(pluginManager.getExtensions(eq(SauronExtension.class), eq("a"))).thenThrow(new IllegalStateException("pf4j"));
        register("b");
        defaultPipeline("a", "b");

        assertThatCode(() -> service.process(request(null))).doesNotThrowAnyException();
        assertThat(executed).isEmpty();
    }


    @Test
    public void timerBuilderUsesTheGivenName()
    {
        Timer timer = service.getTimerBuilder("some.name").register(meterRegistry);

        assertThat(timer.getId().getName()).isEqualTo("some.name");
    }


    private void register(String... ids)
    {
        for (String id : ids)
        {
            extensions.put(id, Collections.singletonList(recording(id)));
        }
    }


    private SauronExtension recording(String name)
    {
        return (props, ds) -> {
            executed.add(name);
            seenDataSets.add(ds);
            return ds;
        };
    }


    private void defaultPipeline(String... ids)
    {
        Map<String, List<String>> pipelines = new HashMap<>();
        pipelines.put("default", Arrays.asList(ids));
        pipelineProperties.setPipelines(pipelines);
    }


    private double successCount(String plugin)
    {
        Counter counter = meterRegistry.find("sauron.plugin.executions.total").tags("plugin", plugin, "result", "success").counter();
        return counter == null ? 0 : counter.count();
    }


    private double failureCount(String plugin)
    {
        return meterRegistry.get("sauron.plugin.executions.total").tags("plugin", plugin, "result", "failure").counter().count();
    }


    private Timer timer(String plugin)
    {
        return meterRegistry.get("sauron.plugin.execution.time").tags("plugin", plugin).timer();
    }


    private static BuildRequest request(String plugin)
    {
        return BuildRequest.builder()
            .serviceName("svc").commitId("c1").buildId("b1").repositoryUrl("git@x:svc.git").owner("team")
            .eventTime(new Date(0)).environment("prod").plugin(plugin).build();
    }


    private static class CapturingHandler implements RequestHandler
    {
        private final List<BuildRequest> handled = new ArrayList<>();

        private Consumer<BuildRequest> consumer;

        private boolean failOnHandle;


        @Override
        public void handle(BuildRequest request)
        {
            handled.add(request);
            if (failOnHandle)
            {
                throw new IllegalStateException("broker down");
            }
        }


        @Override
        public void setConsumer(Consumer<BuildRequest> consumer)
        {
            this.consumer = consumer;
        }


        @Override
        public void consume(BuildRequest buildRequest)
        {
            consumer.accept(buildRequest);
        }
    }
}

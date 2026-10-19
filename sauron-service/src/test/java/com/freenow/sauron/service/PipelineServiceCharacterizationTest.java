package com.freenow.sauron.service;

import com.freenow.sauron.datatransferobject.BuildRequest;
import com.freenow.sauron.handler.impl.SpringEventsRequestHandler;
import com.freenow.sauron.model.DataSet;
import com.freenow.sauron.plugins.SauronExtension;
import com.freenow.sauron.properties.PipelineConfigurationProperties;
import com.freenow.sauron.properties.PluginsConfigurationProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pf4j.PluginManager;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PipelineServiceCharacterizationTest
{
    private final List<String> executed = new ArrayList<>();

    private final List<Map<String, String>> mdcDuringApply = new ArrayList<>();

    private final Map<String, List<SauronExtension>> extensions = new HashMap<>();

    private final List<Object> publishedEvents = new ArrayList<>();

    private PluginManager pluginManager;

    private PipelineConfigurationProperties pipelineProperties;

    private PluginsConfigurationProperties pluginsProperties;

    private SimpleMeterRegistry meterRegistry;

    private SpringEventsRequestHandler handler;

    private PipelineService service;


    @Before
    public void setUp()
    {
        MDC.clear();
        pluginManager = mock(PluginManager.class);
        when(pluginManager.getExtensions(eq(SauronExtension.class), anyString()))
            .thenAnswer(invocation -> extensions.getOrDefault(invocation.<String>getArgument(1), Collections.emptyList()));

        pipelineProperties = new PipelineConfigurationProperties();
        pluginsProperties = new PluginsConfigurationProperties();
        meterRegistry = new SimpleMeterRegistry();
        handler = new SpringEventsRequestHandler(publishedEvents::add);
        service = new PipelineService(pluginManager, pipelineProperties, pluginsProperties, handler, meterRegistry);
    }


    @After
    public void clearMdc()
    {
        MDC.clear();
    }


    private void defaultPipeline(String... plugins)
    {
        Map<String, List<String>> pipelines = new HashMap<>();
        pipelines.put("default", Arrays.asList(plugins));
        pipelineProperties.setPipelines(pipelines);
    }


    private void recordingPlugin(String id)
    {
        extensions.put(id, Collections.singletonList((properties, dataSet) ->
        {
            executed.add(id);
            mdcDuringApply.add(MDC.getCopyOfContextMap());
            return dataSet;
        }));
    }


    private static BuildRequest request(String plugin)
    {
        BuildRequest request = new BuildRequest();
        request.setServiceName("svc");
        request.setCommitId("c1");
        request.setBuildId("b1");
        request.setPlugin(plugin);
        return request;
    }


    private double counter(String plugin, String result)
    {
        return meterRegistry.counter("sauron.plugin.executions.total", "plugin", plugin, "result", result).count();
    }


    @Test
    public void constructorRegistersProcessAsTheHandlerConsumer()
    {
        defaultPipeline("a");
        recordingPlugin("a");

        handler.consume(request(null));

        assertThat(executed).containsExactly("a");
    }


    @Test
    public void publishHandsTheRequestToTheHandler()
    {
        BuildRequest request = request(null);

        service.publish(request);

        assertThat(publishedEvents).containsExactly(request);
        assertThat(executed).isEmpty();
    }


    @Test
    public void publishSwallowsHandlerExceptions()
    {
        SpringEventsRequestHandler failing = new SpringEventsRequestHandler(event ->
        {
            throw new IllegalStateException("broker down");
        });
        PipelineService failingService = new PipelineService(pluginManager, pipelineProperties, pluginsProperties, failing, meterRegistry);

        failingService.publish(request(null));

        assertThat(executed).isEmpty();
    }


    @Test
    public void defaultPipelineRunsEveryPluginInOrderWithTheSameDataSet()
    {
        defaultPipeline("a", "b", "c");
        List<DataSet> seen = new ArrayList<>();
        for (String id : Arrays.asList("a", "b", "c"))
        {
            extensions.put(id, Collections.singletonList((properties, dataSet) ->
            {
                executed.add(id);
                seen.add(dataSet);
                dataSet.setAdditionalInformation(id, true);
                return dataSet;
            }));
        }

        service.process(request(null));

        assertThat(executed).containsExactly("a", "b", "c");
        assertThat(seen).hasSize(3).allSatisfy(dataSet -> assertThat(dataSet).isSameAs(seen.get(0)));
        assertThat(seen.get(0).getServiceName()).isEqualTo("svc");
        assertThat(seen.get(0).copyAdditionalInformation()).containsKeys("a", "b", "c", "user", "platform");
    }


    @Test
    public void defaultPipelineDoesNotRunTheMandatoryOutputPlugin()
    {
        defaultPipeline("a");
        recordingPlugin("a");
        recordingPlugin("elasticsearch-output");

        service.process(request(null));

        assertThat(executed).containsExactly("a");
    }


    @Test
    public void blankPluginRunsTheDefaultPipeline()
    {
        defaultPipeline("a");
        recordingPlugin("a");
        recordingPlugin(" ");

        service.process(request("  "));

        assertThat(executed).containsExactly("a");
    }


    @Test
    public void pluginsReceiveTheConfiguredPluginProperties()
    {
        defaultPipeline("a");
        List<PluginsConfigurationProperties> received = new ArrayList<>();
        extensions.put("a", Collections.singletonList((properties, dataSet) ->
        {
            received.add(properties);
            return dataSet;
        }));

        service.process(request(null));

        assertThat(received).containsExactly(pluginsProperties);
    }


    @Test
    public void dataSetReturnedByAPluginIsDiscarded_currentBehavior()
    {
        // CHARACTERIZATION: the return value of SauronExtension.apply is ignored; only in-place changes reach later plugins.
        defaultPipeline("replacer", "reader");
        List<DataSet> seenByReader = new ArrayList<>();
        extensions.put("replacer", Collections.singletonList((properties, dataSet) -> DataSet.builder().serviceName("replaced").build()));
        extensions.put("reader", Collections.singletonList((properties, dataSet) ->
        {
            seenByReader.add(dataSet);
            return dataSet;
        }));

        service.process(request(null));

        assertThat(seenByReader).extracting(DataSet::getServiceName).containsExactly("svc");
    }


    @Test
    public void userPluginIsLowerCasedAndFollowedByMandatoryOutput()
    {
        defaultPipeline("other");
        recordingPlugin("other");
        recordingPlugin("my-plugin");
        recordingPlugin("elasticsearch-output");

        service.process(request("My-Plugin"));

        assertThat(executed).containsExactly("my-plugin", "elasticsearch-output");
    }


    @Test
    public void userPluginInDefaultPipelineRunsItsPredecessorsFirstAndSkipsLaterOnes()
    {
        defaultPipeline("dep-1", "dep-2", "target", "after");
        recordingPlugin("dep-1");
        recordingPlugin("dep-2");
        recordingPlugin("target");
        recordingPlugin("after");
        recordingPlugin("elasticsearch-output");

        service.process(request("TARGET"));

        assertThat(executed).containsExactly("dep-1", "dep-2", "target", "elasticsearch-output");
    }


    @Test
    public void userPluginIsMatchedCaseSensitivelyAgainstTheDefaultPipeline_currentBehavior()
    {
        // CHARACTERIZATION: the requested plugin is lower-cased but default-pipeline ids are not, so "Target" never matches and its dependencies are skipped.
        defaultPipeline("dep-1", "Target");
        recordingPlugin("dep-1");
        recordingPlugin("target");
        recordingPlugin("elasticsearch-output");

        service.process(request("Target"));

        assertThat(executed).containsExactly("target", "elasticsearch-output");
    }


    @Test
    public void mandatoryOutputPluginRunsTwiceWhenItIsTheRequestedPlugin_currentBehavior()
    {
        // CHARACTERIZATION: requesting the mandatory output plugin itself executes it twice.
        recordingPlugin("elasticsearch-output");

        service.process(request("elasticsearch-output"));

        assertThat(executed).containsExactly("elasticsearch-output", "elasticsearch-output");
    }


    @Test
    public void blankMandatoryOutputPluginIsSkipped()
    {
        pipelineProperties.setMandatoryOutputPlugin(" ");
        recordingPlugin("p");
        recordingPlugin(" ");

        service.process(request("p"));

        assertThat(executed).containsExactly("p");
    }


    @Test
    public void customMandatoryOutputPluginIsUsed()
    {
        pipelineProperties.setMandatoryOutputPlugin("console-output");
        recordingPlugin("p");
        recordingPlugin("console-output");
        recordingPlugin("elasticsearch-output");

        service.process(request("p"));

        assertThat(executed).containsExactly("p", "console-output");
    }


    @Test
    public void everyExtensionOfAPluginIsApplied()
    {
        defaultPipeline("multi");
        extensions.put("multi", Arrays.asList(
            (properties, dataSet) ->
            {
                executed.add("first");
                return dataSet;
            },
            (properties, dataSet) ->
            {
                executed.add("second");
                return dataSet;
            }));

        service.process(request(null));

        assertThat(executed).containsExactly("first", "second");
        assertThat(counter("multi", "success")).isEqualTo(2.0);
    }


    @Test
    public void successfulPluginRecordsTimerAndSuccessCounter()
    {
        defaultPipeline("a");
        recordingPlugin("a");

        service.process(request(null));

        assertThat(meterRegistry.get("sauron.plugin.execution.time").tag("plugin", "a").timer().count()).isEqualTo(1);
        assertThat(counter("a", "success")).isEqualTo(1.0);
        assertThat(meterRegistry.find("sauron.plugin.executions.total").tag("result", "failure").counter()).isNull();
    }


    @Test
    public void failingPluginRecordsFailureAndPipelineContinues()
    {
        defaultPipeline("broken", "ok");
        extensions.put("broken", Collections.singletonList((properties, dataSet) ->
        {
            throw new IllegalStateException("boom");
        }));
        recordingPlugin("ok");

        service.process(request(null));

        assertThat(executed).containsExactly("ok");
        assertThat(counter("broken", "failure")).isEqualTo(1.0);
        assertThat(meterRegistry.find("sauron.plugin.executions.total").tags("plugin", "broken", "result", "success").counter()).isNull();
        assertThat(meterRegistry.get("sauron.plugin.execution.time").tag("plugin", "broken").timer().count()).isEqualTo(1);
        assertThat(counter("ok", "success")).isEqualTo(1.0);
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
    public void pluginLookupFailureAbortsTheRestOfThePipeline_currentBehavior()
    {
        // CHARACTERIZATION: an exception from PluginManager.getExtensions is caught at request level, so later plugins never run and no failure metric is recorded.
        defaultPipeline("a", "lookup-fails", "c");
        recordingPlugin("a");
        recordingPlugin("c");
        when(pluginManager.getExtensions(SauronExtension.class, "lookup-fails")).thenThrow(new IllegalStateException("plugin unloaded"));

        service.process(request(null));

        assertThat(executed).containsExactly("a");
        assertThat(meterRegistry.find("sauron.plugin.executions.total").tag("result", "failure").counter()).isNull();
    }


    @Test
    public void mdcCarriesPluginAndBuildContextDuringApplyAndIsClearedAfter()
    {
        defaultPipeline("a");
        recordingPlugin("a");
        MDC.put("unrelated", "kept");

        service.process(request(null));

        assertThat(mdcDuringApply).hasSize(1);
        assertThat(mdcDuringApply.get(0))
            .containsEntry("sauron.pluginId", "a")
            .containsEntry("sauron.serviceName", "svc")
            .containsEntry("sauron.commitId", "c1")
            .containsEntry("sauron.buildId", "b1");
        assertThat(MDC.getCopyOfContextMap()).containsOnlyKeys("unrelated");
    }


    @Test
    public void mdcIsClearedEvenWhenThePluginFails()
    {
        defaultPipeline("broken");
        extensions.put("broken", Collections.singletonList((properties, dataSet) ->
        {
            throw new IllegalStateException("boom");
        }));

        service.process(request(null));

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }


    @Test
    public void timerBuilderUsesTheGivenName()
    {
        assertThat(service.getTimerBuilder("x").register(meterRegistry).getId().getName()).isEqualTo("x");
    }
}

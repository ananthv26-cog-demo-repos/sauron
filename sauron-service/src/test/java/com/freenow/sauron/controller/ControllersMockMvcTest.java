package com.freenow.sauron.controller;

import com.freenow.sauron.datatransferobject.BuildRequest;
import com.freenow.sauron.exception.handler.ControllerExceptionHandler;
import com.freenow.sauron.plugins.AutomaticPluginUpdater;
import com.freenow.sauron.service.PipelineService;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isEmptyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ControllersMockMvcTest
{
    private static final String REST = "\"owner\":\"team\",\"eventTime\":\"2020-01-01T00:00:00.000+0000\",\"environment\":\"prod\",\"release\":\"1.0\","
        + "\"dockerImage\":\"img\",\"returnCode\":0,\"rollback\":false";

    private static final String VALID_BODY = "{\"serviceName\":\"svc\",\"commitId\":\"c1\",\"buildId\":\"b1\",\"repositoryUrl\":\"git@x\",\"plugin\":\"p\"," + REST + "}";

    private PipelineService pipelineService;

    private AutomaticPluginUpdater updater;

    private PluginManager pluginManager;

    private MockMvc mvc;


    @Before
    public void setUp()
    {
        pipelineService = mock(PipelineService.class);
        updater = mock(AutomaticPluginUpdater.class);
        pluginManager = mock(PluginManager.class);
        mvc = MockMvcBuilders
            .standaloneSetup(new PipelineController(pipelineService), new PluginsController(updater, pluginManager))
            .setControllerAdvice(new ControllerExceptionHandler())
            .build();
    }


    private static PluginWrapper wrapper(String id, String version)
    {
        PluginWrapper wrapper = mock(PluginWrapper.class);
        when(wrapper.getDescriptor()).thenReturn(new DefaultPluginDescriptor(id, "desc " + id, "Main", version, "", "freenow", "MIT"));
        return wrapper;
    }


    @Test
    public void buildAcceptsAValidRequestAndPublishesIt() throws Exception
    {
        mvc.perform(post("/api/v1/build").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
            .andExpect(status().isOk())
            .andExpect(content().string(isEmptyString()));

        ArgumentCaptor<BuildRequest> captor = ArgumentCaptor.forClass(BuildRequest.class);
        verify(pipelineService).publish(captor.capture());
        assertThat(captor.getValue().getServiceName()).isEqualTo("svc");
        assertThat(captor.getValue().getPlugin()).isEqualTo("p");
        assertThat(captor.getValue().getUser()).isEqualTo("none");
    }


    @Test
    public void buildRejectsMissingMandatoryFieldsWithTheCustomErrorBody() throws Exception
    {
        mvc.perform(post("/api/v1/build").contentType(MediaType.APPLICATION_JSON).content("{\"serviceName\":\"svc\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value(""))
            .andExpect(jsonPath("$.path").value("/api/v1/build"))
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.*", hasSize(5)));

        verify(pipelineService, never()).publish(any());
    }


    @Test
    public void buildAcceptsWhitespaceOnlyMandatoryFields_currentBehavior() throws Exception
    {
        // CHARACTERIZATION: @NotEmpty lets whitespace-only string fields (serviceName, commitId, owner, environment, ...) through to the pipeline.
        mvc.perform(post("/api/v1/build").contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceName\":\" \",\"commitId\":\" \",\"buildId\":\" \",\"repositoryUrl\":\" \",\"owner\":\" \",\"eventTime\":\"2020-01-01T00:00:00.000+0000\","
                    + "\"environment\":\" \",\"release\":\" \",\"dockerImage\":\" \",\"returnCode\":0,\"rollback\":false}"))
            .andExpect(status().isOk());

        verify(pipelineService).publish(any());
    }


    @Test
    public void malformedJsonIsRejectedWithoutTheCustomErrorBody() throws Exception
    {
        mvc.perform(post("/api/v1/build").contentType(MediaType.APPLICATION_JSON).content("{not json"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(isEmptyString()));

        verifyNoInteractions(pipelineService);
    }


    @Test
    public void listReturnsTheDescriptorsOfLoadedPlugins() throws Exception
    {
        List<PluginWrapper> plugins = Arrays.asList(wrapper("a", "1.0"), wrapper("b", "2.0"));
        when(pluginManager.getPlugins()).thenReturn(plugins);

        mvc.perform(get("/api/v1/plugins"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].pluginId").value("a"))
            .andExpect(jsonPath("$[0].version").value("1.0"))
            .andExpect(jsonPath("$[0].provider").value("freenow"))
            .andExpect(jsonPath("$[1].pluginId").value("b"));
    }


    @Test
    public void idsReturnsOnlyPluginIdsInLoadOrder() throws Exception
    {
        List<PluginWrapper> plugins = Arrays.asList(wrapper("b", "1.0"), wrapper("a", "1.0"));
        when(pluginManager.getPlugins()).thenReturn(plugins);

        mvc.perform(get("/api/v1/plugins/ids"))
            .andExpect(status().isOk())
            .andExpect(content().json("[\"b\",\"a\"]", true));
    }


    @Test
    public void emptyPluginListIsAnEmptyArray() throws Exception
    {
        when(pluginManager.getPlugins()).thenReturn(Collections.emptyList());

        mvc.perform(get("/api/v1/plugins")).andExpect(status().isOk()).andExpect(content().json("[]", true));
    }


    @Test
    public void reloadRunsAnUpdateCycleSynchronously() throws Exception
    {
        mvc.perform(put("/api/v1/plugins/reload")).andExpect(status().isOk()).andExpect(content().string(isEmptyString()));

        verify(updater).update();
    }


    @Test
    public void forceReloadPassesThePathVariable() throws Exception
    {
        mvc.perform(put("/api/v1/plugins/forcereload/my-plugin")).andExpect(status().isOk());

        verify(updater).forceReload("my-plugin");
        verify(updater, never()).update();
    }


    @Test
    public void reloadIsOnlyMappedToPut() throws Exception
    {
        mvc.perform(get("/api/v1/plugins/reload")).andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(updater);
    }
}

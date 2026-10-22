package com.freenow.sauron.controller;

import com.freenow.sauron.datatransferobject.BuildRequest;
import com.freenow.sauron.service.PipelineService;
import java.util.Date;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract of POST /api/v1/build with Spring Boot's auto-configured Jackson and validation.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(controllers = PipelineController.class, properties = "spring.cloud.config.enabled=false")
public class PipelineControllerWebTest
{
    private static final String VALID = "{\"serviceName\":\"svc\",\"repositoryUrl\":\"git@x:svc.git\",\"commitId\":\"c1\",\"buildId\":\"b1\","
        + "\"owner\":\"team\",\"eventTime\":0,\"environment\":\"prod\",\"release\":\"r1\",\"dockerImage\":\"img:1\",\"returnCode\":0,\"rollback\":false}";

    private static final String TIMESTAMP = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\+00:00";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PipelineService pipelineService;


    @Test
    public void validBuildReturns200WithEmptyBodyAndPublishesTheBoundRequest() throws Exception
    {
        MvcResult result = mvc.perform(post("/api/v1/build").contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEmpty();
        BuildRequest published = captured();
        assertThat(published.getServiceName()).isEqualTo("svc");
        assertThat(published.getRepositoryUrl()).isEqualTo("git@x:svc.git");
        assertThat(published.getCommitId()).isEqualTo("c1");
        assertThat(published.getBuildId()).isEqualTo("b1");
        assertThat(published.getOwner()).isEqualTo("team");
        assertThat(published.getEventTime()).isEqualTo(new Date(0));
        assertThat(published.getEnvironment()).isEqualTo("prod");
        assertThat(published.getRelease()).isEqualTo("r1");
        assertThat(published.getDockerImage()).isEqualTo("img:1");
        assertThat(published.getReturnCode()).isZero();
        assertThat(published.getRollback()).isFalse();
        assertThat(published.getUser()).isEqualTo("none");
        assertThat(published.getPlatform()).isEqualTo(BuildRequest.Platform.ECS);
        assertThat(published.getDeploymentStrategy()).isEqualTo(BuildRequest.DeploymentStrategy.BLUE_GREEN);
        assertThat(published.getPlugin()).isNull();
    }


    @Test
    public void optionalFieldsEnumsAndIsoDatesBind() throws Exception
    {
        String body = VALID.replace("\"eventTime\":0", "\"eventTime\":\"2020-01-02T03:04:05Z\"")
            .replace("}", ",\"user\":\"alice\",\"platform\":\"K8S\",\"deploymentStrategy\":\"AUTO_CANARY\",\"plugin\":\"Some-Plugin\","
                + "\"documentId\":\"d\",\"indexName\":\"i\",\"customizedRepositoryUrl\":\"u\",\"deploymentState\":1,\"tookSeconds\":2,\"canaryWeight\":3}");

        mvc.perform(post("/api/v1/build").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());

        BuildRequest published = captured();
        assertThat(published.getEventTime()).isEqualTo(Date.from(java.time.Instant.parse("2020-01-02T03:04:05Z")));
        assertThat(published.getUser()).isEqualTo("alice");
        assertThat(published.getPlatform()).isEqualTo(BuildRequest.Platform.K8S);
        assertThat(published.getDeploymentStrategy()).isEqualTo(BuildRequest.DeploymentStrategy.AUTO_CANARY);
        assertThat(published.getPlugin()).isEqualTo("Some-Plugin");
        assertThat(published.getDocumentId()).isEqualTo("d");
        assertThat(published.getIndexName()).isEqualTo("i");
        assertThat(published.getCustomizedRepositoryUrl()).isEqualTo("u");
        assertThat(published.getDeploymentState()).isEqualTo(1);
        assertThat(published.getTookSeconds()).isEqualTo(2);
        assertThat(published.getCanaryWeight()).isEqualTo(3);
    }


    @Test
    public void emptyUserBecomesNone() throws Exception
    {
        mvc.perform(post("/api/v1/build").contentType(MediaType.APPLICATION_JSON).content(VALID.replace("}", ",\"user\":\"\"}")))
            .andExpect(status().isOk());

        assertThat(captured().getUser()).isEqualTo("none");
    }


    @Test
    public void unknownPropertiesAreIgnored() throws Exception
    {
        mvc.perform(post("/api/v1/build").contentType(MediaType.APPLICATION_JSON).content(VALID.replace("}", ",\"unexpected\":true}")))
            .andExpect(status().isOk());

        assertThat(captured().getServiceName()).isEqualTo("svc");
    }


    @Test
    public void whitespaceOnlyRequiredFieldPassesValidation_currentBehavior() throws Exception
    {
        // CHARACTERIZATION: @NotEmpty accepts "   ", so a blank serviceName is accepted and published.
        mvc.perform(post("/api/v1/build").contentType(MediaType.APPLICATION_JSON).content(VALID.replace("\"svc\"", "\"   \"")))
            .andExpect(status().isOk());

        assertThat(captured().getServiceName()).isEqualTo("   ");
    }


    @Test
    public void missingRequiredFieldReturnsTheCustom400Body() throws Exception
    {
        mvc.perform(post("/api/v1/build").contentType(MediaType.APPLICATION_JSON).content(VALID.replace("\"serviceName\":\"svc\",", "")))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.timestamp").value(org.hamcrest.Matchers.matchesPattern(TIMESTAMP)))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value(""))
            .andExpect(jsonPath("$.path").value("/api/v1/build"));

        verify(pipelineService, never()).publish(any());
    }


    @Test
    public void errorBodyKeysAreInAFixedOrderWithNoFieldDetails() throws Exception
    {
        String body = mvc.perform(post("/api/v1/build").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andReturn().getResponse().getContentAsString();

        assertThat(body).matches("\\{\"timestamp\":\"" + TIMESTAMP + "\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"\",\"path\":\"/api/v1/build\"}");
    }


    @Test
    public void nullEnumAndEmptyStringAreRejected() throws Exception
    {
        mvc.perform(post("/api/v1/build").contentType(MediaType.APPLICATION_JSON).content(VALID.replace("}", ",\"platform\":null}")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(""));
        mvc.perform(post("/api/v1/build").contentType(MediaType.APPLICATION_JSON).content(VALID.replace("\"c1\"", "\"\"")))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/build").contentType(MediaType.APPLICATION_JSON).content(VALID.replace(",\"rollback\":false", "")))
            .andExpect(status().isBadRequest());
    }


    @Test
    public void unreadableBodiesAre400WithoutTheCustomBody() throws Exception
    {
        assertThat(mvc.perform(post("/api/v1/build").contentType(MediaType.APPLICATION_JSON).content("{not json"))
            .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString()).isEmpty();
        assertThat(mvc.perform(post("/api/v1/build").contentType(MediaType.APPLICATION_JSON).content(VALID.replace("}", ",\"platform\":\"k8s\"}")))
            .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString()).isEmpty();
        verify(pipelineService, never()).publish(any());
    }


    @Test
    public void wrongMethodAndMediaType() throws Exception
    {
        mvc.perform(get("/api/v1/build")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/v1/build").contentType(MediaType.TEXT_PLAIN).content(VALID)).andExpect(status().isUnsupportedMediaType());
    }


    private BuildRequest captured()
    {
        ArgumentCaptor<BuildRequest> captor = ArgumentCaptor.forClass(BuildRequest.class);
        verify(pipelineService).publish(captor.capture());
        return captor.getValue();
    }
}

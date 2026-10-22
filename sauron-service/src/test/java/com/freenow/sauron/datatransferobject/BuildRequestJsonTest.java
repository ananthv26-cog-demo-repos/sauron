package com.freenow.sauron.datatransferobject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.time.Instant;
import java.util.Date;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JSON shape of BuildRequest with the ObjectMapper Spring Boot auto-configures for the REST API.
 */
public class BuildRequestJsonTest
{
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class));


    @Test
    public void serializesEveryFieldIncludingNullsWithIsoDates()
    {
        runner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            BuildRequest request = BuildRequest.builder().serviceName("svc").eventTime(new Date(1000L)).returnCode(1).rollback(true)
                .platform(BuildRequest.Platform.K8S).canaryWeight(5).build();

            assertThat(mapper.writeValueAsString(request)).isEqualTo(
                "{\"serviceName\":\"svc\",\"repositoryUrl\":null,\"commitId\":null,\"buildId\":null,\"owner\":null,\"eventTime\":\"1970-01-01T00:00:01.000+00:00\","
                    + "\"environment\":null,\"release\":null,\"dockerImage\":null,\"returnCode\":1,\"rollback\":true,\"user\":\"none\","
                    + "\"platform\":\"K8S\",\"deploymentStrategy\":\"BLUE_GREEN\",\"documentId\":null,\"indexName\":null,\"plugin\":null,"
                    + "\"customizedRepositoryUrl\":null,\"deploymentState\":null,\"tookSeconds\":null,\"canaryWeight\":5}");
        });
    }


    @Test
    public void deserializesIsoAndDateOnlyStringsAndIgnoresUnknownFields()
    {
        runner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            BuildRequest iso = mapper.readValue("{\"eventTime\":\"2020-01-02T03:04:05.678+02:00\",\"extra\":1}", BuildRequest.class);
            assertThat(iso.getEventTime()).isEqualTo(Date.from(Instant.parse("2020-01-02T01:04:05.678Z")));

            BuildRequest dateOnly = mapper.readValue("{\"eventTime\":\"2020-01-02\"}", BuildRequest.class);
            assertThat(dateOnly.getEventTime()).isEqualTo(Date.from(Instant.parse("2020-01-02T00:00:00Z")));
        });
    }


    @Test
    public void omittedFieldsKeepTheirDefaultsAndExplicitNullsOverrideThem()
    {
        runner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            BuildRequest omitted = mapper.readValue("{}", BuildRequest.class);
            assertThat(omitted.getUser()).isEqualTo("none");
            assertThat(omitted.getPlatform()).isEqualTo(BuildRequest.Platform.ECS);
            assertThat(omitted.getDeploymentStrategy()).isEqualTo(BuildRequest.DeploymentStrategy.BLUE_GREEN);

            BuildRequest nulls = mapper.readValue("{\"user\":null,\"platform\":null}", BuildRequest.class);
            assertThat(nulls.getUser()).isEqualTo("none");
            assertThat(nulls.getPlatform()).isNull();
        });
    }


    @Test
    public void enumValuesAreCaseSensitive()
    {
        runner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            assertThatThrownBy(() -> mapper.readValue("{\"deploymentStrategy\":\"canary\"}", BuildRequest.class))
                .isInstanceOf(InvalidFormatException.class);
        });
    }


    @Test
    public void setUserIsFluentAndMapsEmptyToNone()
    {
        BuildRequest request = new BuildRequest();

        assertThat(request.setUser("alice")).isSameAs(request);
        assertThat(request.getUser()).isEqualTo("alice");
        assertThat(request.setUser(null).getUser()).isEqualTo("none");
        assertThat(request.setUser("").getUser()).isEqualTo("none");
        assertThat(request.setUser(" ").getUser()).isEqualTo(" ");
    }
}

package com.freenow.sauron.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freenow.sauron.datatransferobject.BuildRequest;
import com.freenow.sauron.model.DataSet;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Date;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

public class BuildMapperCharacterizationTest
{
    private static BuildRequest fullRequest()
    {
        return BuildRequest.builder()
            .serviceName("svc")
            .repositoryUrl("git://r")
            .commitId("c1")
            .buildId("b1")
            .owner("o")
            .eventTime(new Date(0))
            .environment("prod")
            .release("r1")
            .dockerImage("img")
            .returnCode(0)
            .rollback(false)
            .user("u")
            .platform(BuildRequest.Platform.K8S)
            .deploymentStrategy(BuildRequest.DeploymentStrategy.CANARY)
            .documentId("d")
            .indexName("i")
            .plugin("P")
            .customizedRepositoryUrl("cu")
            .deploymentState(3)
            .tookSeconds(4)
            .canaryWeight(5)
            .build();
    }


    @Test
    public void mapsCoreFieldsOntoDataSet()
    {
        DataSet dataSet = BuildMapper.makeDataSet(fullRequest());

        assertThat(dataSet.getServiceName()).isEqualTo("svc");
        assertThat(dataSet.getRepositoryUrl()).isEqualTo("git://r");
        assertThat(dataSet.getCommitId()).isEqualTo("c1");
        assertThat(dataSet.getBuildId()).isEqualTo("b1");
        assertThat(dataSet.getOwner()).isEqualTo("o");
        assertThat(dataSet.getEventTime()).isEqualTo(new Date(0));
    }


    @Test
    public void copiesEveryOptionalFieldIntoAdditionalInformationWithItsJavaType()
    {
        Map<String, Object> info = BuildMapper.makeDataSet(fullRequest()).copyAdditionalInformation();

        assertThat(info).containsOnly(
            entry("environment", "prod"),
            entry("release", "r1"),
            entry("dockerImage", "img"),
            entry("returnCode", 0),
            entry("rollback", false),
            entry("user", "u"),
            entry("platform", "K8S"),
            entry("deploymentStrategy", "CANARY"),
            entry("documentId", "d"),
            entry("indexName", "i"),
            entry("customizedRepositoryUrl", "cu"),
            entry("deploymentState", 3),
            entry("tookSeconds", 4),
            entry("canaryWeight", 5));
        assertThat(info.get("returnCode")).isInstanceOf(Integer.class);
        assertThat(info.get("rollback")).isInstanceOf(Boolean.class);
        assertThat(info.get("platform")).isInstanceOf(String.class);
        assertThat(info.get("deploymentStrategy")).isInstanceOf(String.class);
    }


    @Test
    public void pluginFieldIsNotCopiedIntoTheDataSet()
    {
        Map<String, Object> info = BuildMapper.makeDataSet(fullRequest()).copyAdditionalInformation();

        assertThat(info).doesNotContainKey("plugin");
    }


    @Test
    public void emptyRequestOnlyCarriesDefaultedFields()
    {
        DataSet dataSet = BuildMapper.makeDataSet(new BuildRequest());

        assertThat(dataSet.getServiceName()).isNull();
        assertThat(dataSet.getEventTime()).isNull();
        assertThat(dataSet.copyAdditionalInformation()).containsOnly(
            entry("user", "none"),
            entry("platform", "ECS"),
            entry("deploymentStrategy", "BLUE_GREEN"));
    }


    @Test
    public void nullUserFromBuilderIsLeftOut()
    {
        DataSet dataSet = BuildMapper.makeDataSet(BuildRequest.builder().user(null).build());

        assertThat(dataSet.copyAdditionalInformation()).doesNotContainKey("user");
    }


    @Test
    public void eachOptionalFieldIsCopiedIndependently()
    {
        BuildRequest request = BuildRequest.builder().tookSeconds(9).build();

        assertThat(BuildMapper.makeDataSet(request).copyAdditionalInformation())
            .containsOnlyKeys("user", "platform", "deploymentStrategy", "tookSeconds")
            .containsEntry("tookSeconds", 9);
    }


    @Test
    public void dataSetJsonFlattensAdditionalInformationAndUsesIsoDates() throws Exception
    {
        JsonNode json = new ObjectMapper().readTree(BuildMapper.makeDataSet(fullRequest()).toJson());

        assertThat(json.get("serviceName").asText()).isEqualTo("svc");
        assertThat(json.get("eventTime").asText()).isEqualTo("1970-01-01T00:00:00.000+00:00");
        assertThat(json.get("platform").asText()).isEqualTo("K8S");
        assertThat(json.get("returnCode").isInt()).isTrue();
        assertThat(json.get("rollback").isBoolean()).isTrue();
        assertThat(json.has("additionalInformation")).isFalse();
        assertThat(json.size()).isEqualTo(20);
    }


    @Test
    public void cannotBeInstantiated() throws Exception
    {
        Constructor<BuildMapper> constructor = BuildMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
            .isInstanceOf(InvocationTargetException.class)
            .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}

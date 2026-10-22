package com.freenow.sauron.mapper;

import com.freenow.sauron.datatransferobject.BuildRequest;
import com.freenow.sauron.model.DataSet;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BuildMapperCharacterizationTest
{
    @Test
    public void makeDataSet_copiesCoreFieldsAndEveryOptionalFieldIntoAdditionalInformation()
    {
        BuildRequest request = BuildRequest.builder()
            .serviceName("svc").repositoryUrl("git@x:svc.git").commitId("c1").buildId("b1").owner("team")
            .eventTime(new Date(1000L)).environment("prod").release("r1").dockerImage("img:1")
            .returnCode(3).rollback(true).user("alice").platform(BuildRequest.Platform.K8S)
            .deploymentStrategy(BuildRequest.DeploymentStrategy.CANARY).documentId("doc").indexName("idx")
            .plugin("plugin-x").customizedRepositoryUrl("git@y:svc.git").deploymentState(2).tookSeconds(42).canaryWeight(10)
            .build();

        DataSet dataSet = BuildMapper.makeDataSet(request);

        assertThat(dataSet.getServiceName()).isEqualTo("svc");
        assertThat(dataSet.getRepositoryUrl()).isEqualTo("git@x:svc.git");
        assertThat(dataSet.getCommitId()).isEqualTo("c1");
        assertThat(dataSet.getBuildId()).isEqualTo("b1");
        assertThat(dataSet.getOwner()).isEqualTo("team");
        assertThat(dataSet.getEventTime()).isEqualTo(new Date(1000L));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("environment", "prod");
        expected.put("release", "r1");
        expected.put("dockerImage", "img:1");
        expected.put("returnCode", 3);
        expected.put("rollback", true);
        expected.put("user", "alice");
        expected.put("platform", "K8S");
        expected.put("deploymentStrategy", "CANARY");
        expected.put("documentId", "doc");
        expected.put("indexName", "idx");
        expected.put("customizedRepositoryUrl", "git@y:svc.git");
        expected.put("deploymentState", 2);
        expected.put("tookSeconds", 42);
        expected.put("canaryWeight", 10);
        assertThat(dataSet.copyAdditionalInformation()).containsExactlyInAnyOrderEntriesOf(expected);
        assertThat(dataSet.copyAdditionalInformation()).doesNotContainKey("plugin");
    }


    @Test
    public void makeDataSet_enumsAreStoredAsStringsAndIntegersStayIntegers()
    {
        DataSet dataSet = BuildMapper.makeDataSet(BuildRequest.builder().returnCode(0).tookSeconds(5).build());

        assertThat(dataSet.getObjectAdditionalInformation("platform")).containsInstanceOf(String.class);
        assertThat(dataSet.getObjectAdditionalInformation("deploymentStrategy")).contains("BLUE_GREEN");
        assertThat(dataSet.getObjectAdditionalInformation("returnCode")).containsInstanceOf(Integer.class);
        assertThat(dataSet.getObjectAdditionalInformation("tookSeconds")).contains(5);
    }


    @Test
    public void makeDataSet_emptyRequest_currentBehavior()
    {
        // CHARACTERIZATION: user/platform/deploymentStrategy defaults are always written, so an empty request still carries "none"/ECS/BLUE_GREEN.
        DataSet dataSet = BuildMapper.makeDataSet(new BuildRequest());

        assertThat(dataSet.getServiceName()).isNull();
        assertThat(dataSet.getEventTime()).isNull();
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("user", "none");
        expected.put("platform", "ECS");
        expected.put("deploymentStrategy", "BLUE_GREEN");
        assertThat(dataSet.copyAdditionalInformation()).containsExactlyInAnyOrderEntriesOf(expected);
    }


    @Test
    public void makeDataSet_nullEnumsAndUserAreSkipped()
    {
        BuildRequest request = new BuildRequest();
        request.setPlatform(null);
        request.setDeploymentStrategy(null);
        request.setUser(null);

        DataSet dataSet = BuildMapper.makeDataSet(request);

        assertThat(dataSet.copyAdditionalInformation()).containsOnlyKeys("user");
        assertThat(dataSet.getStringAdditionalInformation("user")).contains("none");
    }


    @Test
    public void makeDataSet_customizedRepositoryUrlDoesNotReplaceRepositoryUrl()
    {
        BuildRequest request = new BuildRequest();
        request.setCustomizedRepositoryUrl("git@custom:svc.git");

        DataSet dataSet = BuildMapper.makeDataSet(request);

        assertThat(dataSet.getRepositoryUrl()).isNull();
        assertThat(dataSet.getStringAdditionalInformation("customizedRepositoryUrl")).contains("git@custom:svc.git");
    }


    @Test
    public void constructorIsNotCallable() throws Exception
    {
        Constructor<BuildMapper> constructor = BuildMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
            .isInstanceOf(InvocationTargetException.class)
            .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}

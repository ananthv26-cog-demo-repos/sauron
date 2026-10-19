package com.freenow.sauron.datatransferobject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BuildRequestTest
{
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();


    static BuildRequest validRequest()
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
            .build();
    }


    @Test
    public void noArgConstructorAppliesBuilderDefaults()
    {
        BuildRequest request = new BuildRequest();

        assertThat(request.getUser()).isEqualTo("none");
        assertThat(request.getPlatform()).isEqualTo(BuildRequest.Platform.ECS);
        assertThat(request.getDeploymentStrategy()).isEqualTo(BuildRequest.DeploymentStrategy.BLUE_GREEN);
        assertThat(request.getPlugin()).isNull();
    }


    @Test
    public void builderAppliesDefaults()
    {
        BuildRequest request = BuildRequest.builder().build();

        assertThat(request.getUser()).isEqualTo("none");
        assertThat(request.getPlatform()).isEqualTo(BuildRequest.Platform.ECS);
        assertThat(request.getDeploymentStrategy()).isEqualTo(BuildRequest.DeploymentStrategy.BLUE_GREEN);
    }


    @Test
    public void setUserNormalisesNullAndEmptyToNone()
    {
        BuildRequest request = new BuildRequest();

        assertThat(request.setUser(null).getUser()).isEqualTo("none");
        assertThat(request.setUser("").getUser()).isEqualTo("none");
        assertThat(request.setUser("alice").getUser()).isEqualTo("alice");
    }


    @Test
    public void setUserReturnsSameInstanceForChaining()
    {
        BuildRequest request = new BuildRequest();

        assertThat(request.setUser("bob")).isSameAs(request);
    }


    @Test
    public void setUserKeepsBlankUser_currentBehavior()
    {
        // CHARACTERIZATION: whitespace-only user is kept as-is (StringUtils.isEmpty, not isBlank).
        assertThat(new BuildRequest().setUser(" ").getUser()).isEqualTo(" ");
    }


    @Test
    public void builderWithNullUserBypassesNormalisation_currentBehavior()
    {
        // CHARACTERIZATION: the builder skips setUser, so user(null) stays null instead of "none".
        assertThat(BuildRequest.builder().user(null).build().getUser()).isNull();
    }


    @Test
    public void toStringListsAllFieldsInDeclarationOrder()
    {
        assertThat(BuildRequest.builder().build().toString()).isEqualTo(
            "BuildRequest(serviceName=null, repositoryUrl=null, commitId=null, buildId=null, owner=null, eventTime=null, environment=null, "
                + "release=null, dockerImage=null, returnCode=null, rollback=null, user=none, platform=ECS, deploymentStrategy=BLUE_GREEN, "
                + "documentId=null, indexName=null, plugin=null, customizedRepositoryUrl=null, deploymentState=null, tookSeconds=null, canaryWeight=null)");
    }


    @Test
    public void equalsAndHashCodeUseAllFields()
    {
        assertThat(validRequest()).isEqualTo(validRequest());
        assertThat(validRequest().hashCode()).isEqualTo(validRequest().hashCode());

        BuildRequest other = validRequest();
        other.setPlugin("x");
        assertThat(other).isNotEqualTo(validRequest());
    }


    @Test
    public void enumsKeepTheirDeclaredConstants()
    {
        assertThat(BuildRequest.Platform.values()).containsExactly(BuildRequest.Platform.ECS, BuildRequest.Platform.K8S);
        assertThat(BuildRequest.DeploymentStrategy.values()).extracting(Enum::name)
            .containsExactly("AUTO_CANARY", "BLUE_GREEN", "CANARY", "CRON", "ROLLING", "OTHER");
    }


    @Test
    public void jsonSerialisationUsesFieldNamesEnumNamesAndEpochMillis() throws Exception
    {
        BuildRequest request = validRequest();
        request.setPlatform(BuildRequest.Platform.K8S);
        request.setDeploymentStrategy(BuildRequest.DeploymentStrategy.CANARY);
        request.setPlugin("P");
        request.setTookSeconds(4);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(request));

        assertThat(json.get("serviceName").asText()).isEqualTo("svc");
        assertThat(json.get("eventTime").isNumber()).isTrue();
        assertThat(json.get("eventTime").asLong()).isZero();
        assertThat(json.get("platform").asText()).isEqualTo("K8S");
        assertThat(json.get("deploymentStrategy").asText()).isEqualTo("CANARY");
        assertThat(json.get("user").asText()).isEqualTo("none");
        assertThat(json.get("tookSeconds").asInt()).isEqualTo(4);
        assertThat(json.get("documentId").isNull()).isTrue();
        assertThat(json.size()).isEqualTo(21);
    }


    @Test
    public void jsonRoundTripPreservesTheRequest() throws Exception
    {
        BuildRequest request = validRequest();
        request.setCanaryWeight(5);

        assertThat(objectMapper.readValue(objectMapper.writeValueAsString(request), BuildRequest.class)).isEqualTo(request);
    }


    @Test
    public void jsonDeserialisationAppliesDefaultsAndNormalisesEmptyUser() throws Exception
    {
        BuildRequest request = objectMapper.readValue("{\"serviceName\":\"s\",\"user\":\"\"}", BuildRequest.class);

        assertThat(request.getServiceName()).isEqualTo("s");
        assertThat(request.getUser()).isEqualTo("none");
        assertThat(request.getPlatform()).isEqualTo(BuildRequest.Platform.ECS);
        assertThat(request.getDeploymentStrategy()).isEqualTo(BuildRequest.DeploymentStrategy.BLUE_GREEN);
    }


    @Test
    public void jsonDeserialisationIgnoresUnknownProperties() throws Exception
    {
        assertThat(objectMapper.readValue("{\"serviceName\":\"s\",\"unknown\":1}", BuildRequest.class).getServiceName()).isEqualTo("s");
    }


    @Test
    public void jsonDeserialisationOfEnumsIsCaseSensitive()
    {
        assertThatThrownBy(() -> objectMapper.readValue("{\"platform\":\"k8s\"}", BuildRequest.class))
            .isInstanceOf(InvalidFormatException.class);
    }


    @Test
    public void validRequestHasNoViolations()
    {
        assertThat(VALIDATOR.validate(validRequest())).isEmpty();
    }


    @Test
    public void emptyRequestViolatesExactlyTheMandatoryFields()
    {
        assertThat(violatedProperties(new BuildRequest())).containsExactlyInAnyOrder(
            "serviceName", "repositoryUrl", "commitId", "buildId", "owner", "eventTime",
            "environment", "release", "dockerImage", "returnCode", "rollback");
    }


    @Test
    public void emptyStringsViolateNotEmpty()
    {
        BuildRequest request = validRequest();
        request.setServiceName("");
        request.setBuildId("");

        assertThat(VALIDATOR.validate(request)).extracting(v -> v.getPropertyPath() + ":" + v.getMessage())
            .containsExactlyInAnyOrder("serviceName:must not be empty", "buildId:must not be empty");
    }


    @Test
    public void blankStringsPassValidation_currentBehavior()
    {
        // CHARACTERIZATION: @NotEmpty accepts whitespace-only values such as serviceName=" ".
        BuildRequest request = validRequest();
        request.setServiceName(" ");

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }


    @Test
    public void nullPlatformAndStrategyViolateNotNull()
    {
        BuildRequest request = validRequest();
        request.setPlatform(null);
        request.setDeploymentStrategy(null);

        assertThat(violatedProperties(request)).containsExactlyInAnyOrder("platform", "deploymentStrategy");
    }


    @Test
    public void optionalFieldsAreNotValidated()
    {
        BuildRequest request = validRequest();
        request.setUser(null);
        request.setDocumentId(null);
        request.setIndexName(null);
        request.setPlugin(null);

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }


    private static Set<String> violatedProperties(BuildRequest request)
    {
        return VALIDATOR.validate(request).stream()
            .map(ConstraintViolation::getPropertyPath)
            .map(Object::toString)
            .collect(Collectors.toSet());
    }
}

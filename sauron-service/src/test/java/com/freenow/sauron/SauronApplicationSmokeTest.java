package com.freenow.sauron;

import com.freenow.sauron.handler.RequestHandler;
import com.freenow.sauron.handler.impl.SpringEventsRequestHandler;
import com.freenow.sauron.support.LocalPluginRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.pf4j.AbstractPluginManager;
import org.pf4j.JarPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.update.DefaultUpdateRepository;
import org.pf4j.update.UpdateRepository;
import org.springframework.amqp.core.Queue;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Boots the whole service on a random port with the default (Spring events) request handler and a local plugin repository
 * containing the console-output plugin, then checks the HTTP surface the migration will touch.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.cloud.config.enabled=false",
    "sauron.pipelines.default[0]=console-output"
})
public class SauronApplicationSmokeTest
{
    private static final String TIMESTAMP = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\+00:00";

    private static final String VALID = "{\"serviceName\":\"svc\",\"repositoryUrl\":\"git@x:svc.git\",\"commitId\":\"c1\",\"buildId\":\"b1\","
        + "\"owner\":\"team\",\"eventTime\":0,\"environment\":\"prod\",\"release\":\"r1\",\"dockerImage\":\"img:1\",\"returnCode\":0,\"rollback\":false}";

    @ClassRule
    public static final TemporaryFolder TEMP = new TemporaryFolder();

    private static String previousPluginsDir;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MeterRegistry meterRegistry;


    @BeforeClass
    public static void pluginRepository() throws Exception
    {
        Path pluginsRoot = TEMP.newFolder("plugins-root").toPath();
        LocalPluginRepository.write(pluginsRoot, TEMP.newFolder("jars").toPath(), "0.0.7");
        previousPluginsDir = System.getProperty(AbstractPluginManager.PLUGINS_DIR_PROPERTY_NAME);
        System.setProperty(AbstractPluginManager.PLUGINS_DIR_PROPERTY_NAME, pluginsRoot.toString());
    }


    @AfterClass
    public static void restorePluginsDir()
    {
        if (previousPluginsDir == null)
        {
            System.clearProperty(AbstractPluginManager.PLUGINS_DIR_PROPERTY_NAME);
        }
        else
        {
            System.setProperty(AbstractPluginManager.PLUGINS_DIR_PROPERTY_NAME, previousPluginsDir);
        }
    }


    @Before
    public void waitForTheScheduledPluginInstall()
    {
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
            assertThat(rest.getForObject("/api/v1/plugins/ids", String.class)).isEqualTo("[\"console-output\"]"));
    }


    @Test
    public void defaultWiringWithoutRabbitOrArtifactory()
    {
        RequestHandler handler = context.getBean(RequestHandler.class);
        assertThat(AopUtils.isJdkDynamicProxy(handler)).isTrue();
        assertThat(AopUtils.getTargetClass(handler)).isEqualTo(SpringEventsRequestHandler.class);
        assertThat(context.getBeansOfType(Queue.class)).isEmpty();
        assertThat(context.getBean(PluginManager.class)).isExactlyInstanceOf(JarPluginManager.class);
        assertThat(context.getBean(UpdateRepository.class)).isExactlyInstanceOf(DefaultUpdateRepository.class);
    }


    @Test
    public void buildRequestRunsThePipelineAsynchronouslyThroughTheRealPlugin()
    {
        ResponseEntity<String> response = rest.postForEntity("/api/v1/build", json(VALID), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNull();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Counter counter = meterRegistry.find("sauron.plugin.executions.total").tags("plugin", "console-output", "result", "success").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isGreaterThanOrEqualTo(1.0);
        });
        assertThat(meterRegistry.find("sauron.plugin.execution.time").tags("plugin", "console-output").timer()).isNotNull();
    }


    @Test
    public void validationErrorUsesTheCustomBody()
    {
        ResponseEntity<String> response = rest.postForEntity("/api/v1/build", json("{}"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).matches("\\{\"timestamp\":\"" + TIMESTAMP + "\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"\",\"path\":\"/api/v1/build\"}");
    }


    @Test
    public void malformedJsonUsesBootsDefaultErrorBodyWithABlankMessage()
    {
        ResponseEntity<String> response = rest.postForEntity("/api/v1/build", json("{not json"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).matches("\\{\"timestamp\":\"" + TIMESTAMP + "\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"\",\"path\":\"/api/v1/build\"}");
    }


    @Test
    public void unknownPathIsAJson404()
    {
        ResponseEntity<String> response = rest.getForEntity("/nope", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).matches("\\{\"timestamp\":\"" + TIMESTAMP + "\",\"status\":404,\"error\":\"Not Found\",\"message\":\"\",\"path\":\"/nope\"}");
    }


    @Test
    public void trailingSlashMatchesTheSameEndpoint()
    {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/plugins/ids/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("[\"console-output\"]");
    }


    @Test
    public void pluginDescriptorsAreServedAsJson()
    {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/plugins", String.class);

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isEqualTo("[{\"pluginId\":\"console-output\",\"pluginDescription\":\"\",\"pluginClass\":\"com.freenow.sauron.plugins.SauronPlugin\","
            + "\"version\":\"0.0.7\",\"requires\":\"*\",\"provider\":\"freenow\",\"dependencies\":[],\"license\":null}]");
    }


    @Test
    public void healthIsDownBecauseTheRabbitCheckRunsEvenWhenRabbitIsDisabled_currentBehavior()
    {
        // CHARACTERIZATION: spring.rabbitmq.enabled only switches Sauron's handler; Boot's Rabbit health check still probes localhost:5672, so without a broker the service reports DOWN (503) even though plugins are loaded.
        ResponseEntity<String> health = rest.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(health.getBody()).isEqualTo("{\"status\":\"DOWN\"}");

        HealthContributorRegistry registry = context.getBean(HealthContributorRegistry.class);
        assertThat(registry.stream().map(c -> c.getName()).sorted().collect(java.util.stream.Collectors.joining(","))).isEqualTo("clientConfigServer,discoveryComposite,diskSpace,ping,pluginsLoadedIndicator,rabbit,refreshScope");
        // INTENDED CHANGE (Spring Cloud 2020): the config client now always registers a clientConfigServer health check (UNKNOWN while spring.cloud.config.enabled=false; Hoxton called it configServer and only added it when the config client was on).
        assertThat(((HealthIndicator) registry.getContributor("pluginsLoadedIndicator")).health().getStatus()).isEqualTo(Status.UP);
        assertThat(((HealthIndicator) registry.getContributor("rabbit")).health().getStatus()).isEqualTo(Status.DOWN);

        // Component drill-down is hidden while details are hidden.
        assertThat(rest.getForEntity("/actuator/health/pluginsLoadedIndicator", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }


    @Test
    public void onlyHealthAndInfoActuatorEndpointsAreExposed()
    {
        ResponseEntity<String> info = rest.getForEntity("/actuator/info", String.class);
        assertThat(info.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // INTENDED CHANGE (Spring Boot 2.5): /actuator/info is no longer exposed over HTTP by default (was 200 {}). Needs approval; restore with management.endpoints.web.exposure.include=health,info.
        assertThat(rest.getForEntity("/actuator/prometheus", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/actuator/httptrace", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/actuator/env", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }


    @Test
    @SuppressWarnings("unchecked")
    public void swaggerDocumentListsOnlyTheSauronControllers()
    {
        ResponseEntity<Map> response = rest.getForEntity("/v2/api-docs", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body.get("swagger")).isEqualTo("2.0");
        Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) body.get("paths");
        assertThat(paths).containsOnlyKeys("/api/v1/build", "/api/v1/plugins", "/api/v1/plugins/ids", "/api/v1/plugins/reload", "/api/v1/plugins/forcereload/{pluginId}");
        Map<String, Object> build = (Map<String, Object>) paths.get("/api/v1/build").get("post");
        assertThat(build.get("summary")).isEqualTo("Start a new asynchronous build in Sauron.");
        assertThat((Map<String, Object>) build.get("responses")).containsOnlyKeys("200", "400", "500");
        assertThat(paths.get("/api/v1/plugins/reload")).containsOnlyKeys("put");
    }


    private static HttpEntity<String> json(String body)
    {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}

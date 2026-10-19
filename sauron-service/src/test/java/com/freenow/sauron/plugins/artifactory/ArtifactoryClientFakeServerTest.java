package com.freenow.sauron.plugins.artifactory;

import com.freenow.sauron.properties.ArtifactoryConfigurationProperties;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.http.client.HttpResponseException;
import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.ArtifactoryClientBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pf4j.update.PluginInfo;
import org.pf4j.update.verifier.BasicVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ArtifactoryClientFakeServerTest
{
    private MockWebServer server;

    private Artifactory artifactory;

    private String baseUri;

    private ArtifactoryConfigurationProperties properties;


    @Before
    public void setUp() throws IOException
    {
        server = new MockWebServer();
        server.start();
        baseUri = "http://localhost:" + server.getPort();
        artifactory = ArtifactoryClientBuilder.create().setUrl(baseUri + "/artifactory").setAccessToken("token").build();
        properties = new ArtifactoryConfigurationProperties();
        properties.setRepository("libs");
        properties.setGroupId("com/x");
    }


    @After
    public void tearDown() throws IOException
    {
        artifactory.close();
        server.shutdown();
    }


    private void enqueueJson(String body)
    {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(body));
    }


    private static String folder(String path, String children)
    {
        return "{\"repo\":\"libs\",\"path\":\"" + path + "\",\"children\":[" + children + "]}";
    }


    private static String child(String name, boolean folder)
    {
        return "{\"uri\":\"/" + name + "\",\"folder\":" + folder + "}";
    }


    private String searchResult(String... itemPaths)
    {
        StringBuilder results = new StringBuilder();
        for (String itemPath : itemPaths)
        {
            results.append(results.length() == 0 ? "" : ",").append("{\"uri\":\"").append(baseUri).append("/artifactory/api/storage/libs/").append(itemPath).append("\"}");
        }
        return "{\"results\":[" + results + "]}";
    }


    private RecordedRequest take() throws InterruptedException
    {
        return server.takeRequest(5, TimeUnit.SECONDS);
    }


    @Test
    public void downloadFileFetchesTheArtifactWithBearerTokenAndSavesItUnderItsFileName() throws Exception
    {
        server.enqueue(new MockResponse().setBody("JARBYTES"));

        Path path = new ArtifactoryDownloader(artifactory).downloadFile(new URL(baseUri + "/libs/com/x/plugin-a/1.0/plugin-a-1.0.jar"));

        RecordedRequest request = take();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).isEqualTo("/artifactory/libs/com/x/plugin-a/1.0/plugin-a-1.0.jar");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer token");
        assertThat(path.getFileName().toString()).isEqualTo("plugin-a-1.0.jar");
        assertThat(path.getParent().getFileName().toString()).startsWith("plugins");
        assertThat(new String(Files.readAllBytes(path))).isEqualTo("JARBYTES");
        Files.delete(path);
    }


    @Test
    public void downloadFileTreatsTheFirstPathSegmentAfterTheClientUriAsTheRepository_currentBehavior() throws Exception
    {
        // CHARACTERIZATION: a browsable URL that includes the "/artifactory" context makes "artifactory" the repository name, so the context is requested twice.
        server.enqueue(new MockResponse().setBody("X"));

        Path path = new ArtifactoryDownloader(artifactory).downloadFile(new URL(baseUri + "/artifactory/libs/a.jar"));

        assertThat(take().getPath()).isEqualTo("/artifactory/artifactory/libs/a.jar");
        Files.delete(path);
    }


    @Test
    public void downloadFileReturnsNullWhenTheServerFails() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThat(new ArtifactoryDownloader(artifactory).downloadFile(new URL(baseUri + "/libs/a.jar"))).isNull();
    }


    @Test
    public void refreshListsPluginsAndResolvesTheReleaseJarUrl() throws Exception
    {
        enqueueJson(folder("/com/x", child("plugin-a", true)));
        enqueueJson(folder("/com/x/plugin-a", child("1.0", true) + "," + child("maven-metadata.xml", false)));
        enqueueJson(searchResult("com/x/plugin-a/1.0/plugin-a-1.0.pom", "com/x/plugin-a/1.0/plugin-a-1.0-sources.jar", "com/x/plugin-a/1.0/plugin-a-1.0.jar"));
        ArtifactoryRepository repository = new ArtifactoryRepository(artifactory, new ArtifactoryDownloader(artifactory), properties);
        Date before = new Date();

        Map<String, PluginInfo> plugins = repository.getPlugins();

        assertThat(plugins).containsOnlyKeys("plugin-a");
        PluginInfo plugin = plugins.get("plugin-a");
        assertThat(plugin.id).isEqualTo("plugin-a");
        assertThat(plugin.name).isEqualTo("plugin-a");
        assertThat(plugin.releases).hasSize(1);
        PluginInfo.PluginRelease release = plugin.releases.get(0);
        assertThat(release.version).isEqualTo("1.0");
        assertThat(release.url).isEqualTo(baseUri + "/libs/com/x/plugin-a/1.0/plugin-a-1.0.jar");
        assertThat(release.date).isBetween(before, new Date(), true, true);
        assertThat(take().getPath()).isEqualTo("/artifactory/api/storage/libs/com/x");
        assertThat(take().getPath()).isEqualTo("/artifactory/api/storage/libs/com/x/plugin-a");
        RecordedRequest search = take();
        assertThat(search.getPath()).isEqualTo("/artifactory/api/search/gavc?g=com%2Fx&a=plugin-a&v=1.0&repos=libs");
        assertThat(search.getHeader("Authorization")).isEqualTo("Bearer token");
        assertThat(repository.getPlugin("plugin-a")).isSameAs(plugin);
        assertThat(repository.getPlugin("missing")).isNull();
    }


    @Test
    public void releaseUrlRoundTripsThroughTheDownloader() throws Exception
    {
        enqueueJson(folder("/com/x", child("plugin-a", true)));
        enqueueJson(folder("/com/x/plugin-a", child("1.0", true)));
        enqueueJson(searchResult("com/x/plugin-a/1.0/plugin-a-1.0.jar"));
        server.enqueue(new MockResponse().setBody("JAR"));
        ArtifactoryRepository repository = new ArtifactoryRepository(artifactory, new ArtifactoryDownloader(artifactory), properties);

        String url = repository.getPlugins().get("plugin-a").releases.get(0).url;
        Path path = repository.getFileDownloader().downloadFile(new URL(url));

        take();
        take();
        take();
        assertThat(take().getPath()).isEqualTo("/artifactory/libs/com/x/plugin-a/1.0/plugin-a-1.0.jar");
        assertThat(new String(Files.readAllBytes(path))).isEqualTo("JAR");
        Files.delete(path);
    }


    @Test
    public void releaseWithoutJarGetsANullPathInItsUrl_currentBehavior() throws Exception
    {
        // CHARACTERIZATION: when the GAVC search finds no jar the release URL ends in the literal "null".
        enqueueJson(folder("/com/x", child("plugin-a", true)));
        enqueueJson(folder("/com/x/plugin-a", child("1.0", true)));
        enqueueJson(searchResult("com/x/plugin-a/1.0/plugin-a-1.0.pom"));

        PluginInfo plugin = new ArtifactoryRepository(artifactory, new ArtifactoryDownloader(artifactory), properties).getPlugins().get("plugin-a");

        assertThat(plugin.releases.get(0).url).isEqualTo(baseUri + "/libs/null");
    }


    @Test
    public void pluginWithOnlyFilesIsListedWithoutReleases_currentBehavior() throws Exception
    {
        // CHARACTERIZATION: a plugin folder holding only files (no version folders) is still published, with an empty release list.
        enqueueJson(folder("/com/x", child("plugin-a", true) + "," + child("plugin-b", true)));
        enqueueJson(folder("/com/x/plugin-a", child("maven-metadata.xml", false)));
        enqueueJson(folder("/com/x/plugin-b", ""));

        Map<String, PluginInfo> plugins = new ArtifactoryRepository(artifactory, new ArtifactoryDownloader(artifactory), properties).getPlugins();

        assertThat(plugins).containsOnlyKeys("plugin-a");
        assertThat(plugins.get("plugin-a").releases).isEmpty();
    }


    @Test
    public void pluginsAreCachedUntilRefresh() throws Exception
    {
        enqueueJson(folder("/com/x", ""));
        enqueueJson(folder("/com/x", child("plugin-a", true)));
        enqueueJson(folder("/com/x/plugin-a", ""));
        ArtifactoryRepository repository = new ArtifactoryRepository(artifactory, new ArtifactoryDownloader(artifactory), properties);

        assertThat(repository.getPlugins()).isEmpty();
        assertThat(repository.getPlugins()).isEmpty();
        assertThat(server.getRequestCount()).isEqualTo(1);

        repository.refresh();
        assertThat(server.getRequestCount()).isEqualTo(3);
    }


    @Test
    public void serverErrorEscapesGetPluginsAndLeavesAnEmptyCache_currentBehavior()
    {
        // CHARACTERIZATION: an HTTP error escapes getPlugins() as an undeclared checked HttpResponseException, and the half-built empty map is cached, so the next call returns no plugins without retrying.
        server.enqueue(new MockResponse().setResponseCode(500));
        ArtifactoryRepository repository = new ArtifactoryRepository(artifactory, new ArtifactoryDownloader(artifactory), properties);

        assertThatThrownBy(repository::getPlugins).isInstanceOf(HttpResponseException.class).hasMessageContaining("status code: 500");
        assertThat(repository.getPlugins()).isEmpty();
        assertThat(server.getRequestCount()).isEqualTo(1);
    }


    @Test
    public void repositoryMetadata()
    {
        ArtifactoryDownloader downloader = new ArtifactoryDownloader(artifactory);
        ArtifactoryRepository repository = new ArtifactoryRepository(artifactory, downloader, properties);

        assertThat(repository.getId()).isEqualTo("com.freenow.sauron.plugins.artifactory.ArtifactoryRepository");
        assertThat(repository.getUrl()).isNull();
        assertThat(repository.getFileDownloader()).isSameAs(downloader);
        assertThat(repository.getFileVerifier()).isInstanceOf(BasicVerifier.class);
    }
}

package com.freenow.sauron.plugins.artifactory;

import com.freenow.sauron.properties.ArtifactoryConfigurationProperties;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.ArtifactoryClientBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pf4j.update.PluginInfo;
import org.pf4j.update.verifier.BasicVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real Artifactory client against a fake Artifactory REST API (storage + GAVC search endpoints).
 */
public class ArtifactoryRepositoryTest
{
    private static final String GROUP = "com/free-now/plugins";

    private final Map<String, MockResponse> routes = new ConcurrentHashMap<>();

    private MockWebServer server;

    private Artifactory artifactory;

    private ArtifactoryDownloader downloader;

    private ArtifactoryRepository repository;

    private String baseUrl;


    @Before
    public void setUp() throws IOException
    {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher()
        {
            @Override
            public MockResponse dispatch(RecordedRequest request)
            {
                return routes.getOrDefault(request.getPath(), new MockResponse().setResponseCode(404));
            }
        });
        server.start();
        baseUrl = "http://localhost:" + server.getPort();
        artifactory = ArtifactoryClientBuilder.create().setUrl(baseUrl).setAccessToken("t0ken").build();
        downloader = new ArtifactoryDownloader(artifactory);
        ArtifactoryConfigurationProperties properties = new ArtifactoryConfigurationProperties();
        properties.setRepository("repo");
        properties.setGroupId(GROUP);
        repository = new ArtifactoryRepository(artifactory, downloader, properties);
    }


    @After
    public void tearDown() throws IOException
    {
        server.shutdown();
    }


    @Test
    public void staticMetadata()
    {
        assertThat(repository.getId()).isEqualTo("com.freenow.sauron.plugins.artifactory.ArtifactoryRepository");
        assertThat(repository.getUrl()).isNull();
        assertThat(repository.getFileDownloader()).isSameAs(downloader);
        assertThat(repository.getFileVerifier()).isInstanceOf(BasicVerifier.class);
    }


    @Test
    public void listsPluginsWithOneReleasePerVersionFolderAndSendsTheBearerToken() throws Exception
    {
        folder(GROUP, child("console-output", true), child("empty-plugin", true));
        folder(GROUP + "/console-output", child("0.0.7", true), child("0.0.8", true), child("maven-metadata.xml", false));
        folder(GROUP + "/empty-plugin");
        search("console-output", "0.0.7", "console-output-0.0.7.pom", "console-output-0.0.7.jar", "console-output-0.0.7-sources.jar");
        search("console-output", "0.0.8", "console-output-0.0.8.jar");
        Date before = new Date();

        Map<String, PluginInfo> plugins = repository.getPlugins();

        assertThat(plugins).containsOnlyKeys("console-output");
        PluginInfo plugin = plugins.get("console-output");
        assertThat(plugin.id).isEqualTo("console-output");
        assertThat(plugin.name).isEqualTo("console-output");
        assertThat(plugin.description).isNull();
        assertThat(plugin.releases).extracting(r -> r.version).containsExactly("0.0.7", "0.0.8");
        assertThat(plugin.releases.get(0).url).isEqualTo(baseUrl + "/repo/" + GROUP + "/console-output/0.0.7/console-output-0.0.7.jar");
        assertThat(plugin.releases.get(1).url).isEqualTo(baseUrl + "/repo/" + GROUP + "/console-output/0.0.8/console-output-0.0.8.jar");
        assertThat(plugin.releases.get(0).date).isBetween(before, new Date(), true, true);
        assertThat(plugin.releases.get(0).requires).isNull();
        assertThat(plugin.releases.get(0).sha512sum).isNull();

        RecordedRequest first = server.takeRequest();
        assertThat(first.getMethod()).isEqualTo("GET");
        assertThat(first.getPath()).isEqualTo("/api/storage/repo/" + GROUP);
        assertThat(first.getHeader("Authorization")).isEqualTo("Bearer t0ken");
    }


    @Test
    public void gavcSearchSendsTheSlashSeparatedGroupId_currentBehavior() throws Exception
    {
        // CHARACTERIZATION: sauron.artifactory.group-id is used both as a folder path and as the GAVC groupId (g=com/free-now/plugins); Artifactory's GAVC search expects dotted group ids.
        folder(GROUP, child("console-output", true));
        folder(GROUP + "/console-output", child("0.0.7", true));
        search("console-output", "0.0.7", "console-output-0.0.7.jar");

        repository.getPlugins();

        server.takeRequest();
        server.takeRequest();
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/search/gavc?g=com%2Ffree-now%2Fplugins&a=console-output&v=0.0.7&repos=repo");
    }


    @Test
    public void releaseUrlEndsInNullWhenNoJarIsFound_currentBehavior()
    {
        // CHARACTERIZATION: a version folder without a matching jar still becomes a release, with the literal "null" as its path.
        folder(GROUP, child("console-output", true));
        folder(GROUP + "/console-output", child("0.0.7", true));
        search("console-output", "0.0.7", "console-output-0.0.7.pom");

        PluginInfo plugin = repository.getPlugin("console-output");

        assertThat(plugin.releases).hasSize(1);
        assertThat(plugin.releases.get(0).url).isEqualTo(baseUrl + "/repo/null");
    }


    @Test
    public void jarPatternDotIsUnescaped_currentBehavior()
    {
        // CHARACTERIZATION: the filter regex ".*.jar" has an unescaped dot, so any path ending in "jar" (e.g. "notes-jar") counts as a jar; the lexically largest path wins.
        folder(GROUP, child("p", true));
        folder(GROUP + "/p", child("1", true));
        search("p", "1", "p-1.jar", "p-1-notes-jar");

        assertThat(repository.getPlugin("p").releases.get(0).url).isEqualTo(baseUrl + "/repo/" + GROUP + "/p/1/p-1.jar");

        search("p", "1", "p-1.jar", "p-1.zip-jar");
        repository.refresh();

        assertThat(repository.getPlugin("p").releases.get(0).url).isEqualTo(baseUrl + "/repo/" + GROUP + "/p/1/p-1.zip-jar");
    }


    @Test
    public void fileEntryUnderTheGroupFolderBreaksTheWholeRefresh_currentBehavior()
    {
        // CHARACTERIZATION: a file directly under the group folder (e.g. maven-metadata.xml) is read as a Folder with null children; isFolder() is true, so getChildren().isEmpty() throws and every plugin listed after it is lost.
        folder(GROUP, child("a", true), child("maven-metadata.xml", false), child("b", true));
        folder(GROUP + "/a", child("1", true));
        search("a", "1", "a-1.jar");
        folder(GROUP + "/b", child("1", true));
        search("b", "1", "b-1.jar");
        routes.put("/api/storage/repo/" + GROUP + "/maven-metadata.xml", new MockResponse().setHeader("Content-Type", "application/json")
            .setBody("{\"repo\":\"repo\",\"path\":\"/" + GROUP + "/maven-metadata.xml\",\"size\":\"10\",\"mimeType\":\"application/xml\"}"));

        assertThatThrownBy(() -> repository.getPlugins()).isInstanceOf(NullPointerException.class);
        assertThat(repository.getPlugins()).containsOnlyKeys("a");
    }


    @Test
    public void pluginsAreCachedUntilRefresh()
    {
        folder(GROUP, child("a", true));
        folder(GROUP + "/a", child("1", true));
        search("a", "1", "a-1.jar");

        repository.getPlugins();
        repository.getPlugins();
        assertThat(server.getRequestCount()).isEqualTo(3);
        assertThat(repository.getPlugin("missing")).isNull();
        assertThat(server.getRequestCount()).isEqualTo(3);

        repository.refresh();
        assertThat(server.getRequestCount()).isEqualTo(6);
    }


    @Test
    public void failedRefreshPropagatesAndLeavesAnEmptyCache_currentBehavior()
    {
        // CHARACTERIZATION: refresh() only catches MalformedURLException; the client's checked HttpResponseException escapes undeclared (Groovy), and the plugin map was already reset, so later getPlugins() calls return {} without retrying.
        routes.put("/api/storage/repo/" + GROUP, new MockResponse().setResponseCode(500).setBody("boom"));

        assertThatThrownBy(() -> repository.getPlugins())
            .isInstanceOf(org.apache.http.client.HttpResponseException.class)
            .hasMessageContaining("status code: 500");
        int requests = server.getRequestCount();

        assertThat(repository.getPlugins()).isEmpty();
        assertThat(server.getRequestCount()).isEqualTo(requests);
    }


    @Test
    public void contextPathIsDroppedFromReleaseUrls_currentBehavior() throws IOException
    {
        // CHARACTERIZATION: with an Artifactory URL that has a context path, API calls keep it but release URLs are built from getUri(), which drops it.
        Artifactory withContext = ArtifactoryClientBuilder.create().setUrl(baseUrl + "/artifactory").setAccessToken("t0ken").build();
        ArtifactoryConfigurationProperties properties = new ArtifactoryConfigurationProperties();
        properties.setRepository("repo");
        properties.setGroupId(GROUP);
        ArtifactoryRepository contextRepository = new ArtifactoryRepository(withContext, new ArtifactoryDownloader(withContext), properties);
        routes.put("/artifactory/api/storage/repo/" + GROUP, folderResponse(GROUP, child("a", true)));
        routes.put("/artifactory/api/storage/repo/" + GROUP + "/a", folderResponse(GROUP + "/a", child("1", true)));
        routes.put("/artifactory/api/search/gavc?g=com%2Ffree-now%2Fplugins&a=a&v=1&repos=repo", searchResponse("/artifactory", GROUP + "/a/1", "a-1.jar"));

        PluginInfo plugin = contextRepository.getPlugin("a");

        assertThat(plugin.releases.get(0).url).isEqualTo(baseUrl + "/repo/" + GROUP + "/a/1/a-1.jar");
    }


    private void folder(String path, String... children)
    {
        routes.put("/api/storage/repo/" + path, folderResponse(path, children));
    }


    private MockResponse folderResponse(String path, String... children)
    {
        return new MockResponse().setHeader("Content-Type", "application/json")
            .setBody("{\"repo\":\"repo\",\"path\":\"/" + path + "\",\"children\":[" + String.join(",", children) + "]}");
    }


    private static String child(String name, boolean folder)
    {
        return "{\"uri\":\"/" + name + "\",\"folder\":" + folder + "}";
    }


    private void search(String artifact, String version, String... files)
    {
        routes.put("/api/search/gavc?g=com%2Ffree-now%2Fplugins&a=" + artifact + "&v=" + version + "&repos=repo",
            searchResponse("", GROUP + "/" + artifact + "/" + version, files));
    }


    private MockResponse searchResponse(String context, String folder, String... files)
    {
        StringBuilder results = new StringBuilder();
        for (String file : files)
        {
            if (results.length() > 0)
            {
                results.append(',');
            }
            results.append("{\"uri\":\"").append(baseUrl).append(context).append("/api/storage/repo/").append(folder).append('/').append(file).append("\"}");
        }
        return new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"results\":[" + results + "]}");
    }
}

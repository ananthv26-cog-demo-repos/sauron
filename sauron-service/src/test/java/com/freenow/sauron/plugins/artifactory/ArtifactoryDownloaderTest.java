package com.freenow.sauron.plugins.artifactory;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.ArtifactoryClientBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArtifactoryDownloaderTest
{
    private MockWebServer server;

    private String baseUrl;

    private ArtifactoryDownloader downloader;

    private Path downloaded;


    @Before
    public void setUp() throws IOException
    {
        server = new MockWebServer();
        ((okhttp3.mockwebserver.QueueDispatcher) server.getDispatcher()).setFailFast(true);
        server.start();
        baseUrl = "http://localhost:" + server.getPort();
        Artifactory artifactory = ArtifactoryClientBuilder.create().setUrl(baseUrl).setAccessToken("t0ken").build();
        downloader = new ArtifactoryDownloader(artifactory);
    }


    @After
    public void tearDown() throws IOException
    {
        server.shutdown();
        if (downloaded != null)
        {
            Files.deleteIfExists(downloaded);
            Files.deleteIfExists(downloaded.getParent());
        }
    }


    @Test
    public void downloadsFromTheRepositoryNamedByTheFirstPathSegmentIntoAFreshTempDir() throws Exception
    {
        server.enqueue(new MockResponse().setBody("JAR-BYTES"));

        downloaded = downloader.downloadFile(new URL(baseUrl + "/repo/com/free-now/plugins/a/1/a-1.jar"));

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).isEqualTo("/repo/com/free-now/plugins/a/1/a-1.jar");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer t0ken");
        assertThat(downloaded.getFileName().toString()).isEqualTo("a-1.jar");
        assertThat(downloaded.getParent().getFileName().toString()).startsWith("plugins");
        assertThat(downloaded.getParent().getParent()).isEqualTo(Path.of(System.getProperty("java.io.tmpdir")).toRealPath());
        assertThat(new String(Files.readAllBytes(downloaded), StandardCharsets.UTF_8)).isEqualTo("JAR-BYTES");
    }


    @Test
    public void eachDownloadUsesItsOwnTempDir() throws Exception
    {
        server.enqueue(new MockResponse().setBody("1"));
        server.enqueue(new MockResponse().setBody("2"));

        Path first = downloader.downloadFile(new URL(baseUrl + "/repo/a.jar"));
        downloaded = downloader.downloadFile(new URL(baseUrl + "/repo/a.jar"));

        assertThat(first.getParent()).isNotEqualTo(downloaded.getParent());
        Files.deleteIfExists(first);
        Files.deleteIfExists(first.getParent());
    }


    @Test
    public void urlOutsideArtifactoryIsStillFetchedFromArtifactory_currentBehavior() throws Exception
    {
        // CHARACTERIZATION: a URL that is not under the Artifactory URI is not rejected; its scheme becomes the repository name and the request goes to Artifactory.
        server.enqueue(new MockResponse().setBody("X"));

        downloaded = downloader.downloadFile(new URL("http://elsewhere/x.jar"));

        assertThat(server.takeRequest().getPath()).isEqualTo("/http:/elsewhere/x.jar");
        assertThat(downloaded.getFileName().toString()).isEqualTo("x.jar");
    }


    @Test
    public void httpErrorIsLoggedAndReturnsNull() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThat(downloader.downloadFile(new URL(baseUrl + "/repo/missing.jar"))).isNull();
    }
}

package com.freenow.sauron.plugins;

import com.freenow.sauron.support.LocalPluginRepository;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginState;
import org.pf4j.update.DefaultUpdateRepository;
import org.pf4j.update.FileDownloader;
import org.pf4j.update.FileVerifier;
import org.pf4j.update.PluginInfo;
import org.pf4j.update.UpdateManager;
import org.pf4j.update.UpdateRepository;

import static com.freenow.sauron.support.LocalPluginRepository.PLUGIN_ID;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uses a real PF4J plugin manager and a real pf4j-update repository on disk; only the Spring event publisher is replaced by a list.
 */
public class AutomaticPluginUpdaterCharacterizationTest
{
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private final List<Object> events = new ArrayList<>();

    private Path pluginsRoot;

    private Path repositoryDir;

    private Path jarDir;

    private PluginManager pluginManager;


    @Before
    public void setUp() throws Exception
    {
        pluginsRoot = temp.newFolder("plugins-root").toPath();
        repositoryDir = temp.newFolder("repository").toPath();
        jarDir = temp.newFolder("jars").toPath();
        pluginManager = new DefaultPluginManager(pluginsRoot);
    }


    @Test
    public void updateInstallsAndStartsAvailablePluginsAndPublishesOneSuccessEvent() throws Exception
    {
        LocalPluginRepository.write(repositoryDir, jarDir, "0.0.7");

        updater(localRepository()).update();

        assertThat(pluginManager.getPlugin(PLUGIN_ID)).isNotNull();
        assertThat(pluginManager.getPlugin(PLUGIN_ID).getPluginState()).isEqualTo(PluginState.STARTED);
        assertThat(pluginManager.getPlugin(PLUGIN_ID).getDescriptor().getVersion()).isEqualTo("0.0.7");
        assertThat(Files.list(pluginsRoot).map(p -> p.getFileName().toString())).containsExactly("console-output-0.0.7.jar");
        assertThat(pluginManager.getExtensions(SauronExtension.class, PLUGIN_ID)).hasSize(1);
        assertSuccessEvents(1);
    }


    @Test
    public void updatePicksTheHighestSemanticVersion() throws Exception
    {
        LocalPluginRepository.write(repositoryDir, jarDir, "0.0.9", "0.0.10");

        updater(localRepository()).update();

        assertThat(Files.list(pluginsRoot).map(p -> p.getFileName().toString())).containsExactly("console-output-0.0.10.jar");
    }


    @Test
    public void emptyRepositoryStillPublishesSuccess() throws Exception
    {
        LocalPluginRepository.writeJson(repositoryDir, "[]");

        updater(localRepository()).update();

        assertThat(pluginManager.getPlugins()).isEmpty();
        assertSuccessEvents(1);
    }


    @Test
    public void failedInstallStillPublishesSuccess_currentBehavior() throws Exception
    {
        // CHARACTERIZATION: a plugin whose download fails is only logged; the update still reports success, so health goes UP and the Rabbit listener starts without the plugin.
        LocalPluginRepository.writeJson(repositoryDir,
            "[{\"id\":\"" + PLUGIN_ID + "\",\"releases\":[{\"version\":\"0.0.7\",\"date\":\"2021-03-29T10:23:51Z\",\"url\":\"" + jarDir.resolve("missing.jar").toUri() + "\"}]}]");

        updater(localRepository()).update();

        assertThat(pluginManager.getPlugins()).isEmpty();
        assertSuccessEvents(1);
    }


    @Test
    public void pluginWithoutReleasesAbortsTheWholeUpdate_currentBehavior() throws Exception
    {
        // CHARACTERIZATION: a repository entry with no releases makes getLastPluginRelease return null; the NullPointerException is not a PluginRuntimeException, so the whole update aborts and no event is published (health stays DOWN).
        LocalPluginRepository.writeJson(repositoryDir, "[{\"id\":\"broken\",\"releases\":[]}]");

        updater(localRepository()).update();

        assertThat(events).isEmpty();
    }


    @Test
    public void repositoryFailurePublishesNoEvent()
    {
        UpdateRepository failing = new FailingRepository();

        updater(failing).update();

        assertThat(events).isEmpty();
    }


    @Test
    public void updateReplacesAnInstalledPluginWithANewerRelease() throws Exception
    {
        LocalPluginRepository.write(repositoryDir, jarDir, "0.0.7");
        AutomaticPluginUpdater updater = updater(localRepository());
        updater.update();

        LocalPluginRepository.write(repositoryDir, jarDir, "0.0.7", "0.0.8");
        updater.update();

        assertThat(Files.list(pluginsRoot).map(p -> p.getFileName().toString())).containsExactly("console-output-0.0.8.jar");
        assertThat(pluginManager.getPlugin(PLUGIN_ID).getPluginState()).isEqualTo(PluginState.STARTED);
        assertSuccessEvents(2);
    }


    @Test
    public void forceReloadDeletesAndReinstallsThePlugin() throws Exception
    {
        LocalPluginRepository.write(repositoryDir, jarDir, "0.0.7");
        AutomaticPluginUpdater updater = updater(localRepository());
        updater.update();
        Object firstWrapper = pluginManager.getPlugin(PLUGIN_ID);

        updater.forceReload(PLUGIN_ID);

        assertThat(pluginManager.getPlugin(PLUGIN_ID)).isNotNull().isNotSameAs(firstWrapper);
        assertThat(pluginManager.getPlugin(PLUGIN_ID).getPluginState()).isEqualTo(PluginState.STARTED);
        assertSuccessEvents(2);
    }


    @Test
    public void forceReloadOfAnUnknownPluginDoesNotRunAnUpdate_currentBehavior() throws Exception
    {
        // CHARACTERIZATION: deletePlugin throws for an id that is not loaded, so forceReload never reaches update(); a missing plugin cannot be installed through /forcereload.
        LocalPluginRepository.write(repositoryDir, jarDir, "0.0.7");

        updater(localRepository()).forceReload(PLUGIN_ID);

        assertThat(pluginManager.getPlugins()).isEmpty();
        assertThat(events).isEmpty();
    }


    @Test
    public void installReportedAsFailedIsLoggedAndStillPublishesSuccess() throws Exception
    {
        LocalPluginRepository.write(repositoryDir, jarDir, "0.0.7");
        UpdateManager updateManager = new UpdateManager(pluginManager, Collections.singletonList(localRepository()))
        {
            @Override
            public synchronized boolean installPlugin(String id, String version)
            {
                return false;
            }
        };

        new AutomaticPluginUpdater(updateManager, pluginManager, events::add).update();

        assertThat(pluginManager.getPlugins()).isEmpty();
        assertSuccessEvents(1);
    }


    @Test
    public void updateReportedAsFailedOrThrowingKeepsTheOldVersionAndStillPublishesSuccess() throws Exception
    {
        LocalPluginRepository.write(repositoryDir, jarDir, "0.0.7");
        updater(localRepository()).update();
        LocalPluginRepository.write(repositoryDir, jarDir, "0.0.7", "0.0.8");
        List<String> attempts = new ArrayList<>();
        UpdateManager updateManager = new UpdateManager(pluginManager, Collections.singletonList(localRepository()))
        {
            @Override
            public synchronized boolean updatePlugin(String id, String version)
            {
                attempts.add(id + "@" + version);
                if (attempts.size() == 1)
                {
                    return false;
                }
                throw new PluginRuntimeException("cannot update");
            }
        };
        AutomaticPluginUpdater updater = new AutomaticPluginUpdater(updateManager, pluginManager, events::add);

        updater.update();
        updater.update();

        assertThat(attempts).containsExactly("console-output@0.0.8", "console-output@0.0.8");
        assertThat(Files.list(pluginsRoot).map(p -> p.getFileName().toString())).containsExactly("console-output-0.0.7.jar");
        assertSuccessEvents(3);
    }


    private AutomaticPluginUpdater updater(UpdateRepository repository)
    {
        UpdateManager updateManager = new UpdateManager(pluginManager, Collections.singletonList(repository));
        return new AutomaticPluginUpdater(updateManager, pluginManager, events::add);
    }


    private UpdateRepository localRepository() throws Exception
    {
        return new DefaultUpdateRepository("local", repositoryDir.toUri().toURL());
    }


    private void assertSuccessEvents(int count)
    {
        assertThat(events).hasSize(count).allSatisfy(event -> {
            assertThat(event).isInstanceOf(PluginsLoadedEvent.class);
            assertThat(((PluginsLoadedEvent) event).isSuccess()).isTrue();
        });
    }


    private static class FailingRepository implements UpdateRepository
    {
        @Override
        public String getId()
        {
            return "failing";
        }


        @Override
        public URL getUrl()
        {
            return null;
        }


        @Override
        public Map<String, PluginInfo> getPlugins()
        {
            throw new IllegalStateException("repository down");
        }


        @Override
        public PluginInfo getPlugin(String id)
        {
            throw new IllegalStateException("repository down");
        }


        @Override
        public void refresh()
        {
        }


        @Override
        public FileDownloader getFileDownloader()
        {
            return null;
        }


        @Override
        public FileVerifier getFileVerifier()
        {
            return null;
        }
    }
}

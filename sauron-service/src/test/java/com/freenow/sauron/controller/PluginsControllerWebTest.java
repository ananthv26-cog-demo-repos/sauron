package com.freenow.sauron.controller;

import com.freenow.sauron.plugins.AutomaticPluginUpdater;
import com.freenow.sauron.support.LocalPluginRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.update.DefaultUpdateRepository;
import org.pf4j.update.UpdateManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import static com.freenow.sauron.support.LocalPluginRepository.PLUGIN_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract of /api/v1/plugins backed by a real PF4J plugin manager. The updater bean is a stand-in that forwards to a real
 * AutomaticPluginUpdater, so the @Scheduled method does not run during the test.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(controllers = PluginsController.class, properties = "spring.cloud.config.enabled=false")
public class PluginsControllerWebTest
{
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PluginManager pluginManager;

    @MockBean
    private AutomaticPluginUpdater updater;

    private PluginManager realPluginManager;


    @Before
    public void setUp() throws Exception
    {
        Path repository = temp.newFolder("repository").toPath();
        LocalPluginRepository.write(repository, temp.newFolder("jars").toPath(), "0.0.7");
        realPluginManager = new DefaultPluginManager(temp.newFolder("plugins-root").toPath());
        UpdateManager updateManager = new UpdateManager(realPluginManager,
            Collections.singletonList(new DefaultUpdateRepository("local", repository.toUri().toURL())));
        AutomaticPluginUpdater realUpdater = new AutomaticPluginUpdater(updateManager, realPluginManager, event -> { });

        when(pluginManager.getPlugins()).thenAnswer(inv -> realPluginManager.getPlugins());
        doAnswer(inv -> {
            realUpdater.update();
            return null;
        }).when(updater).update();
        doAnswer(inv -> {
            realUpdater.forceReload(inv.getArgument(0));
            return null;
        }).when(updater).forceReload(anyString());
    }


    @After
    public void tearDown() throws IOException
    {
        realPluginManager.stopPlugins();
        realPluginManager.unloadPlugins();
    }


    @Test
    public void emptyListsBeforeAnyReload() throws Exception
    {
        mvc.perform(get("/api/v1/plugins")).andExpect(status().isOk()).andExpect(content().json("[]", true));
        mvc.perform(get("/api/v1/plugins/ids")).andExpect(status().isOk()).andExpect(content().json("[]", true));
    }


    @Test
    public void reloadInstallsFromTheRepositoryAndTheListsShowTheDescriptor() throws Exception
    {
        mvc.perform(put("/api/v1/plugins/reload")).andExpect(status().isOk()).andExpect(content().string(""));

        mvc.perform(get("/api/v1/plugins/ids")).andExpect(status().isOk()).andExpect(content().json("[\"console-output\"]", true));
        String descriptors = mvc.perform(get("/api/v1/plugins")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(descriptors).isEqualTo("[{\"pluginId\":\"console-output\",\"pluginDescription\":\"\",\"pluginClass\":\"com.freenow.sauron.plugins.SauronPlugin\","
            + "\"version\":\"0.0.7\",\"requires\":\"*\",\"provider\":\"freenow\",\"dependencies\":[],\"license\":null}]");
    }


    @Test
    public void forceReloadReinstallsAnInstalledPlugin() throws Exception
    {
        mvc.perform(put("/api/v1/plugins/reload")).andExpect(status().isOk());
        Object before = realPluginManager.getPlugin(PLUGIN_ID);

        mvc.perform(put("/api/v1/plugins/forcereload/" + PLUGIN_ID)).andExpect(status().isOk()).andExpect(content().string(""));

        assertThat(realPluginManager.getPlugin(PLUGIN_ID)).isNotNull().isNotSameAs(before);
    }


    @Test
    public void forceReloadOfAnUnknownPluginStillReturns200() throws Exception
    {
        mvc.perform(put("/api/v1/plugins/forcereload/unknown")).andExpect(status().isOk());

        assertThat(realPluginManager.getPlugins()).isEmpty();
    }


    @Test
    public void wrongMethods() throws Exception
    {
        mvc.perform(get("/api/v1/plugins/reload")).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/v1/plugins/forcereload/x")).andExpect(status().isMethodNotAllowed());
    }
}

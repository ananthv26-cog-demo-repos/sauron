package com.freenow.sauron.plugins;

import com.freenow.sauron.model.DataSet;
import com.freenow.sauron.properties.PluginsConfigurationProperties;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.DefaultPluginFactory;
import org.pf4j.DefaultPluginManager;
import org.pf4j.Extension;
import org.pf4j.ExtensionPoint;
import org.pf4j.Plugin;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SauronPluginTest
{
    @Rule
    public TemporaryFolder pluginsRoot = new TemporaryFolder();

    private PluginManager pluginManager;


    @Before
    public void setUp()
    {
        pluginManager = new DefaultPluginManager(pluginsRoot.getRoot().toPath());
    }


    @After
    public void tearDown()
    {
        pluginManager.stopPlugins();
        pluginManager.unloadPlugins();
    }


    @Test
    public void sauronExtensionIsAPf4jExtensionPoint()
    {
        assertTrue(ExtensionPoint.class.isAssignableFrom(SauronExtension.class));
        assertTrue(Plugin.class.isAssignableFrom(SauronPlugin.class));
    }


    @Test
    public void pf4jDiscoversAnnotatedSauronExtensions()
    {
        pluginManager.loadPlugins();
        pluginManager.startPlugins();

        List<SauronExtension> extensions = pluginManager.getExtensions(SauronExtension.class);

        assertEquals(1, extensions.size());
        assertEquals(MarkingExtension.class, extensions.get(0).getClass());
    }


    @Test
    public void discoveredExtensionReceivesPropertiesAndDataSet()
    {
        SauronExtension extension = pluginManager.getExtensions(SauronExtension.class).get(0);
        PluginsConfigurationProperties properties = new PluginsConfigurationProperties();
        properties.put("marking", Collections.singletonMap("value", "configured"));
        DataSet input = DataSet.builder().serviceName("svc").build();

        DataSet output = extension.apply(properties, input);

        assertSame(input, output);
        assertEquals(Optional.of("configured"), output.getStringAdditionalInformation("marking"));
    }


    @Test
    public void pf4jInstantiatesSauronPluginThroughItsWrapperConstructor()
    {
        DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor(
            "sample", "sample plugin", SauronPlugin.class.getName(), "1.0.0", "*", "freenow", "Apache-2.0");
        PluginWrapper wrapper = new PluginWrapper(pluginManager, descriptor, pluginsRoot.getRoot().toPath(), getClass().getClassLoader());

        Plugin plugin = new DefaultPluginFactory().create(wrapper);

        assertEquals(SauronPlugin.class, plugin.getClass());
        assertSame(wrapper, plugin.getWrapper());
    }


    @Test
    public void sauronPluginLifecycleHooksAreNoOps()
    {
        DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor(
            "sample", "sample plugin", SauronPlugin.class.getName(), "1.0.0", "*", "freenow", "Apache-2.0");
        PluginWrapper wrapper = new PluginWrapper(pluginManager, descriptor, pluginsRoot.getRoot().toPath(), getClass().getClassLoader());
        SauronPlugin plugin = new SauronPlugin(wrapper);

        plugin.start();
        plugin.stop();
        plugin.delete();

        assertSame(wrapper, plugin.getWrapper());
    }


    @Extension
    public static class MarkingExtension implements SauronExtension
    {
        @Override
        public DataSet apply(PluginsConfigurationProperties properties, DataSet input)
        {
            return input.setAdditionalInformation("marking", properties.getPluginConfigurationProperty("marking", "value").orElse("unset"));
        }
    }
}

package com.freenow.sauron.plugins;

import com.freenow.sauron.model.DataSet;
import com.freenow.sauron.properties.PluginsConfigurationProperties;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.DefaultPluginManager;
import org.pf4j.Extension;
import org.pf4j.ExtensionPoint;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SauronExtensionTest
{
    @Rule
    public TemporaryFolder pluginsRoot = new TemporaryFolder();


    @Extension
    public static class TaggingExtension implements SauronExtension
    {
        @Override
        public DataSet apply(PluginsConfigurationProperties properties, DataSet input)
        {
            Object tag = properties.getPluginConfigurationProperty("tagging", "tag").orElse("none");
            return input.setAdditionalInformation("tag", tag);
        }
    }


    @Test
    public void sauronExtensionIsAPf4jExtensionPoint()
    {
        assertTrue(ExtensionPoint.class.isAssignableFrom(SauronExtension.class));
    }


    @Test
    public void pluginManagerDiscoversAnnotatedSauronExtensionOnTheClasspath()
    {
        PluginManager pluginManager = new DefaultPluginManager(pluginsRoot.getRoot().toPath());
        pluginManager.loadPlugins();
        pluginManager.startPlugins();

        List<SauronExtension> extensions = pluginManager.getExtensions(SauronExtension.class);

        assertEquals(1, extensions.size());
        assertEquals(TaggingExtension.class, extensions.get(0).getClass());
        assertTrue(pluginManager.getPlugins().isEmpty());
    }


    @Test
    public void extensionReceivesPluginPropertiesAndDataSet()
    {
        PluginsConfigurationProperties properties = new PluginsConfigurationProperties();
        properties.put("tagging", Collections.singletonMap("tag", "blue"));
        DataSet input = DataSet.builder().serviceName("svc").build();

        DataSet output = new TaggingExtension().apply(properties, input);

        assertSame(input, output);
        assertEquals(Optional.of("blue"), output.getStringAdditionalInformation("tag"));
        assertEquals(Optional.of("none"),
            new TaggingExtension().apply(new PluginsConfigurationProperties(), new DataSet()).getStringAdditionalInformation("tag"));
    }


    @Test
    public void sauronPluginKeepsTheWrapperItWasCreatedWith()
    {
        DefaultPluginDescriptor descriptor =
            new DefaultPluginDescriptor("sample", "", SauronPlugin.class.getName(), "1.0.0", "", "", "");
        PluginWrapper wrapper = new PluginWrapper(
            new DefaultPluginManager(pluginsRoot.getRoot().toPath()), descriptor, pluginsRoot.getRoot().toPath(), getClass().getClassLoader());

        SauronPlugin plugin = new SauronPlugin(wrapper);

        assertSame(wrapper, plugin.getWrapper());
        assertEquals("sample", plugin.getWrapper().getPluginId());
    }
}

package com.freenow.sauron.properties;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PluginsConfigurationPropertiesTest
{
    private static PluginsConfigurationProperties properties()
    {
        Map<String, Object> checker = new HashMap<>();
        checker.put("enabled", true);
        checker.put("nullValue", null);

        PluginsConfigurationProperties properties = new PluginsConfigurationProperties();
        properties.put("dependency-checker", checker);
        return properties;
    }


    @Test
    public void returnsConfiguredProperty()
    {
        assertEquals(Optional.of(true), properties().getPluginConfigurationProperty("dependency-checker", "enabled"));
    }


    @Test
    public void returnsEmptyForUnknownPlugin()
    {
        assertEquals(Optional.empty(), properties().getPluginConfigurationProperty("unknown", "enabled"));
    }


    @Test
    public void returnsEmptyForUnknownProperty()
    {
        assertEquals(Optional.empty(), properties().getPluginConfigurationProperty("dependency-checker", "missing"));
    }


    @Test
    public void returnsEmptyForPropertyExplicitlySetToNull()
    {
        assertEquals(Optional.empty(), properties().getPluginConfigurationProperty("dependency-checker", "nullValue"));
    }
}

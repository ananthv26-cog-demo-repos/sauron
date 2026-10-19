package com.freenow.sauron.properties;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PluginsConfigurationPropertiesTest
{
    private static PluginsConfigurationProperties bind(Map<String, String> source)
    {
        return new Binder(new MapConfigurationPropertySource(source))
            .bind("sauron.plugins", PluginsConfigurationProperties.class)
            .get();
    }


    @Test
    public void returnsConfiguredProperty()
    {
        PluginsConfigurationProperties properties = new PluginsConfigurationProperties();
        Map<String, Object> plugin = new HashMap<>();
        plugin.put("url", "http://sonar");
        properties.put("sonarapi-report", plugin);

        assertEquals(Optional.of("http://sonar"), properties.getPluginConfigurationProperty("sonarapi-report", "url"));
    }


    @Test
    public void returnsEmptyForUnknownPluginOrProperty()
    {
        PluginsConfigurationProperties properties = new PluginsConfigurationProperties();
        properties.put("known", Collections.singletonMap("p", "v"));

        assertEquals(Optional.empty(), properties.getPluginConfigurationProperty("unknown", "p"));
        assertEquals(Optional.empty(), properties.getPluginConfigurationProperty("known", "missing"));
    }


    @Test
    public void returnsEmptyForPropertyExplicitlySetToNull()
    {
        PluginsConfigurationProperties properties = new PluginsConfigurationProperties();
        Map<String, Object> plugin = new HashMap<>();
        plugin.put("p", null);
        properties.put("known", plugin);

        assertEquals(Optional.empty(), properties.getPluginConfigurationProperty("known", "p"));
    }


    @Test(expected = NullPointerException.class)
    public void throwsWhenPluginEntryIsNull_currentBehavior()
    {
        // CHARACTERIZATION: getOrDefault only defaults for absent keys, so a plugin mapped to null NPEs instead of returning empty.
        PluginsConfigurationProperties properties = new PluginsConfigurationProperties();
        properties.put("known", null);

        properties.getPluginConfigurationProperty("known", "p");
    }


    @Test
    public void isBoundFromTheSauronPluginsPrefix()
    {
        assertEquals("sauron.plugins", PluginsConfigurationProperties.class.getAnnotation(ConfigurationProperties.class).value());
        assertEquals("", PluginsConfigurationProperties.class.getAnnotation(ConfigurationProperties.class).prefix());
    }


    @Test
    public void springBindingKeepsScalarValuesAsStrings()
    {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("sauron.plugins.readme-checker.minLength", "10");
        source.put("sauron.plugins.readme-checker.enabled", "true");
        source.put("sauron.plugins.sonarapi-report.url", "http://sonar");

        PluginsConfigurationProperties properties = bind(source);

        assertEquals(Optional.of("10"), properties.getPluginConfigurationProperty("readme-checker", "minLength"));
        assertEquals(Optional.of("true"), properties.getPluginConfigurationProperty("readme-checker", "enabled"));
        assertEquals(Optional.of("http://sonar"), properties.getPluginConfigurationProperty("sonarapi-report", "url"));
        assertEquals(2, properties.size());
    }


    @Test
    public void springBindingTurnsNestedKeysIntoMaps()
    {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("sauron.plugins.elasticsearch-output.elasticsearch.host", "localhost");
        source.put("sauron.plugins.elasticsearch-output.elasticsearch.port", "9200");

        PluginsConfigurationProperties properties = bind(source);

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("host", "localhost");
        expected.put("port", "9200");
        assertEquals(Optional.of(expected), properties.getPluginConfigurationProperty("elasticsearch-output", "elasticsearch"));
    }


    @Test
    public void springBindingTurnsIndexedListsIntoMapsKeyedByIndex_currentBehavior()
    {
        // CHARACTERIZATION: list[0]/list[1] bind as a Map {"0":..,"1":..}, not a List; plugins casting to List would fail.
        Map<String, String> source = new LinkedHashMap<>();
        source.put("sauron.plugins.readme-checker.list[0]", "a");
        source.put("sauron.plugins.readme-checker.list[1]", "b");

        PluginsConfigurationProperties properties = bind(source);

        Object list = properties.getPluginConfigurationProperty("readme-checker", "list").get();
        assertTrue(list instanceof Map);
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("0", "a");
        expected.put("1", "b");
        assertEquals(expected, list);
    }


    @Test
    public void springBindingKeepsPluginIdAndPropertyKeyCase()
    {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("sauron.plugins.my-plugin.camelCase", "x");
        source.put("sauron.plugins.my-plugin.Upper-Case", "y");

        PluginsConfigurationProperties properties = bind(source);

        assertEquals(Optional.of("x"), properties.getPluginConfigurationProperty("my-plugin", "camelCase"));
        assertEquals(Optional.of("y"), properties.getPluginConfigurationProperty("my-plugin", "Upper-Case"));
        assertEquals(Optional.empty(), properties.getPluginConfigurationProperty("my-plugin", "camelcase"));
    }
}

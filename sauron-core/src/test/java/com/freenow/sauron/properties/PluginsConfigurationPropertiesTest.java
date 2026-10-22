package com.freenow.sauron.properties;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConverterNotFoundException;
import org.springframework.core.env.MapPropertySource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class PluginsConfigurationPropertiesTest
{
    @Test
    public void returnsConfiguredValueForPluginAndProperty()
    {
        PluginsConfigurationProperties properties = new PluginsConfigurationProperties();
        properties.put("elasticsearch-output", Collections.singletonMap("port", 9200));

        assertEquals(Optional.of(9200), properties.getPluginConfigurationProperty("elasticsearch-output", "port"));
    }


    @Test
    public void returnsEmptyForUnknownPluginUnknownPropertyOrNullValue()
    {
        Map<String, Object> config = new HashMap<>();
        config.put("present", "x");
        config.put("nullValue", null);
        PluginsConfigurationProperties properties = new PluginsConfigurationProperties();
        properties.put("plugin", config);

        assertEquals(Optional.empty(), properties.getPluginConfigurationProperty("unknown", "present"));
        assertEquals(Optional.empty(), properties.getPluginConfigurationProperty("plugin", "unknown"));
        assertEquals(Optional.empty(), properties.getPluginConfigurationProperty("plugin", "nullValue"));
        assertEquals(Optional.empty(), properties.getPluginConfigurationProperty(null, "present"));
        assertEquals(Optional.empty(), properties.getPluginConfigurationProperty("plugin", null));
        assertEquals(Optional.empty(), new PluginsConfigurationProperties().getPluginConfigurationProperty("plugin", "present"));
    }


    @Test
    public void pluginMappedToNullThrows_currentBehavior()
    {
        // CHARACTERIZATION: getOrDefault returns the stored null (not the empty default), so a plugin key with a null map throws NPE instead of returning empty.
        PluginsConfigurationProperties properties = new PluginsConfigurationProperties();
        properties.put("plugin", null);

        try
        {
            properties.getPluginConfigurationProperty("plugin", "any");
            fail("expected NullPointerException");
        }
        catch (NullPointerException expected)
        {
            assertNotNull(expected);
        }
    }


    @Test
    public void pluginIdAndPropertyLookupsAreCaseSensitive_currentBehavior()
    {
        // CHARACTERIZATION: plugin ids are matched exactly, so "Elastic" in config is invisible to a lookup for "elastic".
        PluginsConfigurationProperties properties = bind(map(
            "sauron.plugins.Elastic.port", "9200",
            "sauron.plugins.Elastic.camelCase", "cc"
        ));

        assertEquals(Optional.of("9200"), properties.getPluginConfigurationProperty("Elastic", "port"));
        assertEquals(Optional.empty(), properties.getPluginConfigurationProperty("elastic", "port"));
        assertEquals(Optional.of("cc"), properties.getPluginConfigurationProperty("Elastic", "camelCase"));
        assertEquals(Optional.empty(), properties.getPluginConfigurationProperty("Elastic", "camelcase"));
    }


    @Test
    public void isBoundUnderSauronPluginsPrefix()
    {
        ConfigurationProperties annotation = PluginsConfigurationProperties.class.getAnnotation(ConfigurationProperties.class);

        assertEquals("sauron.plugins", annotation.value());
        assertEquals("", annotation.prefix());
    }


    @Test
    public void springBinderKeepsKeysAsWrittenAndValuesAsStrings()
    {
        PluginsConfigurationProperties properties = bind(map(
            "sauron.plugins.dependency-checker.maven-home", "/opt/maven",
            "sauron.plugins.dependency-checker.nested.deep", "x",
            "sauron.plugins.readme-checker.minLength", "50",
            "sauron.plugins.readme-checker.UPPER_KEY", "U",
            "sauron.plugins.readme-checker.empty", ""
        ));

        assertEquals(new java.util.HashSet<>(Arrays.asList("dependency-checker", "readme-checker")), properties.keySet());
        assertEquals(Optional.of("/opt/maven"), properties.getPluginConfigurationProperty("dependency-checker", "maven-home"));
        assertEquals(Optional.of(Collections.singletonMap("deep", "x")),
            properties.getPluginConfigurationProperty("dependency-checker", "nested"));
        assertEquals(Optional.of("50"), properties.getPluginConfigurationProperty("readme-checker", "minLength"));
        assertEquals(Optional.of("U"), properties.getPluginConfigurationProperty("readme-checker", "UPPER_KEY"));
        assertEquals(Optional.of(""), properties.getPluginConfigurationProperty("readme-checker", "empty"));
        assertEquals(LinkedHashMap.class, properties.get("dependency-checker").getClass());
    }


    @Test
    public void springBinderTurnsIndexedListsIntoMapsKeyedByIndex_currentBehavior()
    {
        // CHARACTERIZATION: host[0]/host[1] bind as a Map {"0":..,"1":..}, not a List, because the value type is Object.
        PluginsConfigurationProperties properties = bind(map(
            "sauron.plugins.elastic.host[0]", "h0",
            "sauron.plugins.elastic.host[1]", "h1"
        ));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("0", "h0");
        expected.put("1", "h1");
        assertEquals(Optional.of(expected), properties.getPluginConfigurationProperty("elastic", "host"));
    }


    @Test
    public void springBinderFailsWhenPluginKeyHasScalarValue_currentBehavior()
    {
        // CHARACTERIZATION: "sauron.plugins.foo=" (a plugin with an empty scalar instead of a map) fails binding, which would stop the service at startup.
        try
        {
            bind(map("sauron.plugins.foo", ""));
            fail("expected BindException");
        }
        catch (BindException e)
        {
            assertEquals("sauron.plugins.foo", e.getName().toString());
            assertEquals(ConverterNotFoundException.class, e.getCause().getClass());
        }
    }


    @Test
    public void springBinderLeavesNothingBoundWithoutSauronPluginsProperties()
    {
        assertFalse(new Binder(new MapConfigurationPropertySource(map("other.key", "v")))
            .bind("sauron.plugins", Bindable.of(PluginsConfigurationProperties.class))
            .isBound());
    }


    @Configuration
    @EnableConfigurationProperties(PluginsConfigurationProperties.class)
    static class EnablingConfiguration
    {
    }


    @Test
    public void enableConfigurationPropertiesRegistersABeanBoundFromTheEnvironment()
    {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext())
        {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", map(
                "sauron.plugins.elasticsearch-output.host", "localhost",
                "sauron.plugins.elasticsearch-output.port", "9200"
            )));
            context.register(EnablingConfiguration.class);
            context.refresh();

            PluginsConfigurationProperties properties = context.getBean(PluginsConfigurationProperties.class);

            assertEquals(Collections.singleton("elasticsearch-output"), properties.keySet());
            assertEquals(Optional.of("localhost"), properties.getPluginConfigurationProperty("elasticsearch-output", "host"));
            assertEquals(Optional.of("9200"), properties.getPluginConfigurationProperty("elasticsearch-output", "port"));
        }
    }


    private static PluginsConfigurationProperties bind(Map<String, Object> source)
    {
        return new Binder(new MapConfigurationPropertySource(source))
            .bind("sauron.plugins", Bindable.of(PluginsConfigurationProperties.class))
            .get();
    }


    private static Map<String, Object> map(String... keyValues)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2)
        {
            result.put(keyValues[i], keyValues[i + 1]);
        }
        return result;
    }
}

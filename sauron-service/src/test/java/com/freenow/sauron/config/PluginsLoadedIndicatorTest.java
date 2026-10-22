package com.freenow.sauron.config;

import com.freenow.sauron.plugins.PluginsLoadedEvent;
import org.junit.After;
import org.junit.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;

public class PluginsLoadedIndicatorTest
{
    @After
    public void reset()
    {
        new PluginsLoadedIndicator().onPluginLoadedEvent(event(false));
    }


    @Test
    public void downWithDetailUntilASuccessfulLoadEvent()
    {
        PluginsLoadedIndicator indicator = new PluginsLoadedIndicator();
        indicator.onPluginLoadedEvent(event(false));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsOnlyKeys("Plugins Startup");
        assertThat(health.getDetails().get("Plugins Startup")).isEqualTo("Plugins have not been loaded yet");
    }


    @Test
    public void upWithoutDetailsAfterSuccessAndDownAgainAfterFailure()
    {
        PluginsLoadedIndicator indicator = new PluginsLoadedIndicator();

        indicator.onPluginLoadedEvent(event(true));
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(indicator.health().getDetails()).isEmpty();

        indicator.onPluginLoadedEvent(event(false));
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }


    @Test
    public void healthStateIsSharedAcrossInstances_currentBehavior()
    {
        // CHARACTERIZATION: the flag is a static field, so every instance (and every Spring context in the JVM) shares one health state.
        new PluginsLoadedIndicator().onPluginLoadedEvent(event(true));

        assertThat(new PluginsLoadedIndicator().health().getStatus()).isEqualTo(Status.UP);
    }


    private static PluginsLoadedEvent event(boolean success)
    {
        PluginsLoadedEvent event = new PluginsLoadedEvent();
        event.setSuccess(success);
        return event;
    }
}

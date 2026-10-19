package com.freenow.sauron.config;

import com.freenow.sauron.plugins.PluginsLoadedEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;

public class PluginsLoadedIndicatorTest
{
    private final PluginsLoadedIndicator indicator = new PluginsLoadedIndicator();


    private static PluginsLoadedEvent event(boolean success)
    {
        PluginsLoadedEvent event = new PluginsLoadedEvent();
        event.setSuccess(success);
        return event;
    }


    @Before
    @After
    public void resetSharedState()
    {
        indicator.onPluginLoadedEvent(event(false));
    }


    @Test
    public void downWithDetailUntilPluginsAreLoaded()
    {
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsOnlyKeys("Plugins Startup").containsValue("Plugins have not been loaded yet");
    }


    @Test
    public void upWithoutDetailsAfterSuccessfulLoad()
    {
        indicator.onPluginLoadedEvent(event(true));

        Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).isEmpty();
    }


    @Test
    public void failedLoadAfterSuccessReportsNotLoadedYet_currentBehavior()
    {
        // CHARACTERIZATION: a failed reload reports "have not been loaded yet", even after an earlier success.
        indicator.onPluginLoadedEvent(event(true));
        indicator.onPluginLoadedEvent(event(false));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(indicator.health().getDetails()).containsValue("Plugins have not been loaded yet");
    }


    @Test
    public void stateIsSharedAcrossInstances_currentBehavior()
    {
        // CHARACTERIZATION: health state is a static field, so every instance (and every Spring context in the JVM) shares it.
        indicator.onPluginLoadedEvent(event(true));

        assertThat(new PluginsLoadedIndicator().health().getStatus()).isEqualTo(Status.UP);
    }
}

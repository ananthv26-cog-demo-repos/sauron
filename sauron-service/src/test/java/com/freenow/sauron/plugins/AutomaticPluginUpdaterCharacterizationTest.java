package com.freenow.sauron.plugins;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginManager;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginWrapper;
import org.pf4j.update.PluginInfo;
import org.pf4j.update.UpdateManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AutomaticPluginUpdaterCharacterizationTest
{
    private UpdateManager updateManager;

    private PluginManager pluginManager;

    private ApplicationEventPublisher publisher;

    private AutomaticPluginUpdater updater;


    @Before
    public void setUp()
    {
        updateManager = mock(UpdateManager.class);
        pluginManager = mock(PluginManager.class);
        publisher = mock(ApplicationEventPublisher.class);
        updater = new AutomaticPluginUpdater(updateManager, pluginManager, publisher);
        when(updateManager.getUpdates()).thenReturn(Collections.emptyList());
        when(updateManager.getAvailablePlugins()).thenReturn(Collections.emptyList());
    }


    private static PluginInfo plugin(String id)
    {
        PluginInfo info = new PluginInfo();
        info.id = id;
        return info;
    }


    private void latestRelease(String id, String version)
    {
        PluginInfo.PluginRelease release = new PluginInfo.PluginRelease();
        release.version = version;
        when(updateManager.getLastPluginRelease(id)).thenReturn(release);
    }


    private void installedVersion(String id, String version)
    {
        PluginWrapper wrapper = mock(PluginWrapper.class);
        PluginDescriptor descriptor = mock(PluginDescriptor.class);
        when(descriptor.getVersion()).thenReturn(version);
        when(wrapper.getDescriptor()).thenReturn(descriptor);
        when(pluginManager.getPlugin(id)).thenReturn(wrapper);
    }


    private PluginsLoadedEvent publishedEvent()
    {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(PluginsLoadedEvent.class);
        return (PluginsLoadedEvent) captor.getValue();
    }


    @Test
    public void updateRefreshesThenUpdatesThenInstallsAndPublishesSuccess()
    {
        when(updateManager.getUpdates()).thenReturn(Collections.singletonList(plugin("old")));
        when(updateManager.getAvailablePlugins()).thenReturn(Collections.singletonList(plugin("new")));
        latestRelease("old", "2.0");
        latestRelease("new", "1.0");
        installedVersion("old", "1.0");
        when(updateManager.updatePlugin("old", "2.0")).thenReturn(true);
        when(updateManager.installPlugin("new", "1.0")).thenReturn(true);

        updater.update();

        org.mockito.InOrder order = inOrder(updateManager, publisher);
        order.verify(updateManager).refresh();
        order.verify(updateManager).updatePlugin("old", "2.0");
        order.verify(updateManager).installPlugin("new", "1.0");
        order.verify(publisher).publishEvent(any(Object.class));
        assertThat(publishedEvent().isSuccess()).isTrue();
    }


    @Test
    public void everyPluginIsProcessed()
    {
        when(updateManager.getAvailablePlugins()).thenReturn(Arrays.asList(plugin("a"), plugin("b"), plugin("c")));
        for (String id : Arrays.asList("a", "b", "c"))
        {
            latestRelease(id, "1.0");
        }

        updater.update();

        verify(updateManager).installPlugin("a", "1.0");
        verify(updateManager).installPlugin("b", "1.0");
        verify(updateManager).installPlugin("c", "1.0");
    }


    @Test
    public void rejectedInstallOrUpdateStillReportsSuccess_currentBehavior()
    {
        // CHARACTERIZATION: installPlugin/updatePlugin returning false is only logged; the PluginsLoadedEvent still says success.
        when(updateManager.getUpdates()).thenReturn(Collections.singletonList(plugin("old")));
        when(updateManager.getAvailablePlugins()).thenReturn(Collections.singletonList(plugin("new")));
        latestRelease("old", "2.0");
        latestRelease("new", "1.0");
        installedVersion("old", "1.0");

        updater.update();

        verify(updateManager).updatePlugin("old", "2.0");
        verify(updateManager).installPlugin("new", "1.0");
        assertThat(publishedEvent().isSuccess()).isTrue();
    }


    @Test
    public void pluginRuntimeExceptionDuringInstallIsSwallowedAndStillReportsSuccess_currentBehavior()
    {
        // CHARACTERIZATION: a PluginRuntimeException for one plugin is logged and the cycle still publishes success.
        when(updateManager.getUpdates()).thenReturn(Collections.singletonList(plugin("old")));
        when(updateManager.getAvailablePlugins()).thenReturn(Arrays.asList(plugin("bad"), plugin("good")));
        latestRelease("old", "2.0");
        latestRelease("bad", "1.0");
        latestRelease("good", "1.0");
        installedVersion("old", "1.0");
        when(updateManager.updatePlugin("old", "2.0")).thenThrow(new PluginRuntimeException("corrupt"));
        when(updateManager.installPlugin("bad", "1.0")).thenThrow(new PluginRuntimeException("corrupt"));

        updater.update();

        verify(updateManager).installPlugin("good", "1.0");
        assertThat(publishedEvent().isSuccess()).isTrue();
    }


    @Test
    public void otherExceptionDuringInstallAbortsTheCycleWithoutAnyEvent_currentBehavior()
    {
        // CHARACTERIZATION: a non-PluginRuntimeException failure is caught at cycle level and no PluginsLoadedEvent is published at all (not even success=false).
        when(updateManager.getAvailablePlugins()).thenReturn(Collections.singletonList(plugin("missing-release")));

        updater.update();

        verify(publisher, never()).publishEvent(any(Object.class));
    }


    @Test
    public void refreshFailurePublishesNoEventAndSkipsUpdates()
    {
        doThrow(new IllegalStateException("artifactory down")).when(updateManager).refresh();

        updater.update();

        verify(updateManager, never()).getUpdates();
        verify(updateManager, never()).getAvailablePlugins();
        verify(publisher, never()).publishEvent(any(Object.class));
    }


    @Test
    public void forceReloadDeletesThePluginThenRunsAnUpdateCycle()
    {
        updater.forceReload("p");

        org.mockito.InOrder order = inOrder(pluginManager, updateManager, publisher);
        order.verify(pluginManager).deletePlugin("p");
        order.verify(updateManager).refresh();
        order.verify(publisher).publishEvent(any(Object.class));
    }


    @Test
    public void forceReloadSwallowsDeleteFailuresAndSkipsTheUpdate()
    {
        when(pluginManager.deletePlugin("p")).thenThrow(new IllegalArgumentException("unknown plugin"));

        updater.forceReload("p");

        verify(updateManager, never()).refresh();
        verify(publisher, never()).publishEvent(any(Object.class));
    }


    @Test
    public void updateIsScheduledEveryFiveMinutesAfterTheLastRun() throws Exception
    {
        Scheduled scheduled = AutomaticPluginUpdater.class.getMethod("update").getAnnotation(Scheduled.class);

        assertThat(scheduled.fixedDelay()).isEqualTo(300000L);
        assertThat(scheduled.fixedRate()).isEqualTo(-1L);
        assertThat(scheduled.cron()).isEmpty();
    }


    @Test
    public void pluginsLoadedEventDefaultsToFailure()
    {
        assertThat(new PluginsLoadedEvent().isSuccess()).isFalse();
    }
}

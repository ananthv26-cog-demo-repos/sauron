package com.freenow.sauron.controller;

import com.freenow.sauron.plugins.AutomaticPluginUpdater;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/api/v1/plugins")
public class PluginsController
{
    private final AutomaticPluginUpdater automaticPluginUpdater;

    private final PluginManager pluginManager;


    @Autowired
    public PluginsController(AutomaticPluginUpdater automaticPluginUpdater, PluginManager pluginManager)
    {
        this.automaticPluginUpdater = automaticPluginUpdater;
        this.pluginManager = pluginManager;
    }


    @GetMapping
    @Operation(summary = "Return all loaded plugins.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Return a list of all loaded plugins"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error.")
    })
    public ResponseEntity<List<PluginDescriptor>> list()
    {
        return ResponseEntity.ok(pluginManager.getPlugins().stream().map(PluginWrapper::getDescriptor).collect(Collectors.toList()));
    }


    @GetMapping("/ids")
    @Operation(summary = "Return all loaded plugins id.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Return a list of all loaded plugins id"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error.")
    })
    public ResponseEntity<List<String>> listPluginsId()
    {
        return ResponseEntity.ok(pluginManager.getPlugins().stream().map(PluginWrapper::getDescriptor).map(PluginDescriptor::getPluginId).collect(Collectors.toList()));
    }


    @PutMapping("/reload")
    @Operation(summary = "Request a plugin synchronous reloading process.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "New reloading process has been started"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error.")
    })
    public ResponseEntity<Void> reload()
    {
        automaticPluginUpdater.update();
        return ResponseEntity.ok().build();
    }


    @PutMapping("/forcereload/{pluginId}")
    @Operation(summary = "Force a plugin by id to be reloaded.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Plugin has been reloaded"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error.")
    })
    public ResponseEntity<Void> forceReload(@PathVariable String pluginId)
    {
        automaticPluginUpdater.forceReload(pluginId);
        return ResponseEntity.ok().build();
    }
}
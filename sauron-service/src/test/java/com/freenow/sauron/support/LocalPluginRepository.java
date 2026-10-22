package com.freenow.sauron.support;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Writes a pf4j-update plugins.json and the console-output test plugin jar into a folder, the same layout the service reads from its local plugins directory.
 */
public final class LocalPluginRepository
{
    public static final String PLUGIN_ID = "console-output";

    private LocalPluginRepository()
    {
    }


    public static Path pluginJar() throws URISyntaxException
    {
        return Paths.get(Objects.requireNonNull(LocalPluginRepository.class.getClassLoader().getResource("console-output.jar")).toURI());
    }


    public static void write(Path repositoryDir, Path jarDir, String... versions) throws IOException, URISyntaxException
    {
        Files.createDirectories(repositoryDir);
        Files.createDirectories(jarDir);
        StringBuilder releases = new StringBuilder();
        for (String version : versions)
        {
            Path jar = jarDir.resolve(PLUGIN_ID + "-" + version + ".jar");
            Files.copy(pluginJar(), jar, StandardCopyOption.REPLACE_EXISTING);
            if (releases.length() > 0)
            {
                releases.append(',');
            }
            releases.append("{\"version\":\"").append(version).append("\",\"date\":\"2021-03-29T10:23:51Z\",\"url\":\"").append(jar.toUri()).append("\"}");
        }
        writeJson(repositoryDir, "[{\"id\":\"" + PLUGIN_ID + "\",\"description\":\"console\",\"releases\":[" + releases + "]}]");
    }


    public static void writeJson(Path repositoryDir, String json) throws IOException
    {
        Files.createDirectories(repositoryDir);
        Files.write(repositoryDir.resolve("plugins.json"), json.getBytes(StandardCharsets.UTF_8));
    }
}

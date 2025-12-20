package com.particle_life.app.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class ResourceAccess {
    public static String readTextFile(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)));
    }

    /**
     * Lists all files in the given directory.
     *
     * @param directory Path relative to the app's working directory.
     *                  Must not start with "/" or "./".
     *                  Examples for allowed paths: "textures", "assets/music", ...
     */
    public static List<Path> listFiles(String directory) throws IOException {
        File file = new File(directory);

        // return empty list if directory doesn't exist
        if (!file.exists()) return List.of();

        return Files.walk(file.toPath(), 1)
                .skip(1)  // first entry is just the directory
                .collect(Collectors.toList());
    }
}

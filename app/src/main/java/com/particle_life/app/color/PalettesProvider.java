package com.particle_life.app.color;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.particle_life.app.io.ResourceAccess;

public class PalettesProvider {

    public List<Palette> create() throws Exception {
        List<Palette> palettes = new ArrayList<>();

        try {
            palettes.addAll(loadPalettesFromFiles());
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }

        return palettes;
    }

    private List<Palette> loadPalettesFromFiles() throws IOException, URISyntaxException {
        List<Palette> palettes = new ArrayList<>();

        List<Path> paletteFiles = ResourceAccess.listFiles("palettes");

        paletteFiles.sort(Path::compareTo);

        for (Path path : paletteFiles) {
            String fileContent;
            try {
                fileContent = Files.readString(path);
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }

            Optional<Palette> palette = parsePalette(fileContent);

            if (palette.isPresent()) {
                palettes.add(palette.get());
            }
        }

        return palettes;
    }

    private Optional<Palette> parsePalette(String fileContent) {

        String[] colorStrings = fileContent.split("\\r?\\n");

        List<Color> list = new ArrayList<>();
        for (String colorString : colorStrings) {
            Optional<Color> color = parseColor(colorString);
            color.ifPresent(list::add);
        }
        Color[] colors = new Color[list.size()];
        int i = 0;
        for (Color color : list) {
            colors[i] = color;
            i++;
        }

        if (colors.length > 0) {
            return Optional.of(new InterpolatingPalette(colors));
        } else {
            return Optional.empty();
        }
    }

    private Optional<Color> parseColor(String s) {

        String[] elements = s.split(" ");

        if (elements.length != 3) {
            return Optional.empty();
        }

        int[] colorValues;
        try {
            colorValues = Arrays.stream(elements).mapToInt(Integer::parseInt).toArray();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        return Optional.of(new Color(
                colorValues[0] / 255f,
                colorValues[1] / 255f,
                colorValues[2] / 255f,
                1f
        ));
    }
}

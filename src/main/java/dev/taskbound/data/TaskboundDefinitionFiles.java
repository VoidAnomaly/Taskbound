package dev.taskbound.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Editable server definitions. Bundled files are first-launch seeds, never runtime fallback data. */
public final class TaskboundDefinitionFiles {
    private static final String SEED_ROOT = "/taskbound_defaults/";
    private static final String MARKER = ".initialized";
    public static final List<String> DIRECTORIES = List.of(
            "task_givers", "task_categories", "task_templates", "rewards", "level_rewards");

    private TaskboundDefinitionFiles() { }

    /** Seed once; a completed installation keeps both edits and deliberate deletions on later starts. */
    public static synchronized void initialize(Path root) throws IOException {
        if (Files.exists(root.resolve(MARKER))) return;
        Files.createDirectories(root);
        try (var reader = new BufferedReader(new InputStreamReader(resource("index.txt"), StandardCharsets.UTF_8))) {
            for (String entry : reader.lines().filter(line -> !line.isBlank()).toList()) {
                Path target = root.resolve(entry).normalize();
                if (!target.startsWith(root.normalize()) || !entry.startsWith("data/taskbound/")) {
                    throw new IOException("Invalid bundled definition path: " + entry);
                }
                if (Files.exists(target)) continue;
                Files.createDirectories(target.getParent());
                // Publish only complete files, and never replace a file already supplied by the owner.
                Path temporary = Files.createTempFile(target.getParent(), ".taskbound-", ".tmp");
                try (InputStream input = resource(entry)) {
                    Files.copy(input, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    try {
                        Files.move(temporary, target);
                    } catch (FileAlreadyExistsException ignored) {
                        // Another creator won the race; keep their file.
                    }
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
        }
        Files.writeString(root.resolve(MARKER), "Taskbound defaults initialized. Keep this file to preserve deletions.\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    /** IDs follow normal datapack paths: data/{namespace}/{directory}/{path}.json. */
    public static Map<String, JsonElement> read(Path root, String directory) throws IOException {
        if (!DIRECTORIES.contains(directory)) throw new IllegalArgumentException("Unknown definition directory: " + directory);
        var result = new LinkedHashMap<String, JsonElement>();
        Path data = root.resolve("data");
        if (!Files.isDirectory(data)) return result;
        try (var namespaces = Files.list(data)) {
            for (Path namespace : namespaces.filter(Files::isDirectory).sorted().toList()) {
                Path definitions = namespace.resolve(directory);
                if (!Files.isDirectory(definitions)) continue;
                try (var paths = Files.walk(definitions)) {
                    for (Path file : paths.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList()) {
                        String relative = definitions.relativize(file).toString().replace('\\', '/');
                        String id = namespace.getFileName() + ":" + relative.substring(0, relative.length() - 5);
                        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                            JsonElement json = JsonParser.parseReader(reader);
                            if (!json.isJsonObject()) throw new JsonParseException("Expected a JSON object");
                            result.put(id, json);
                        } catch (JsonParseException exception) {
                            throw new JsonParseException("Invalid Taskbound definition in " + file, exception);
                        }
                    }
                }
            }
        }
        return result;
    }

    private static InputStream resource(String path) throws IOException {
        InputStream input = TaskboundDefinitionFiles.class.getResourceAsStream(SEED_ROOT + path);
        if (input == null) throw new IOException("Missing bundled Taskbound default: " + path);
        return input;
    }
}

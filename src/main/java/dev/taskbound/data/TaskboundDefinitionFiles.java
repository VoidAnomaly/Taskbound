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
        if (Files.exists(root.resolve(MARKER))) {
            upgradeReleasedDefaults(root);
            upgradeFarmerIcon(root);
            return;
        }
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
        upgradeReleasedDefaults(root);
        upgradeFarmerIcon(root);
    }

    /** Preserve customized givers while adopting the author's icon correction in the earlier stock preview. */
    private static void upgradeFarmerIcon(Path root) throws IOException {
        String entry = "data/taskbound/task_givers/gathering.json";
        Path target = updateTarget(root, entry);
        if (!Files.isRegularFile(target) || !sha256(Files.readAllBytes(target)).equals(
                "21d6b77a7fe1f5c6639c1176454d00a9d55721259f89fa78e8d69692c2ae7b03")) return;
        Path backup = root.resolve(".updates/farmer-icon").resolve(entry);
        Files.createDirectories(backup.getParent());
        if (!Files.exists(backup)) Files.copy(target, backup);
        Path temporary = Files.createTempFile(target.getParent(), ".taskbound-update-", ".tmp");
        try (InputStream input = resource(entry)) {
            Files.copy(input, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Released upgrades add new definitions once and replace only byte-identical original defaults. */
    private static void upgradeReleasedDefaults(Path root) throws IOException {
        Path marker = root.resolve(".defaults-1.1");
        if (Files.exists(marker)) return;
        // Adopt the preview's completion marker without replaying its defaults update.
        Path previewMarker = root.resolve(".defaults-1.2");
        if (Files.exists(previewMarker)) {
            Files.move(previewMarker, marker);
            return;
        }
        com.google.gson.JsonObject update;
        try (var reader = new InputStreamReader(resource("updates/1.1.json"), StandardCharsets.UTF_8)) {
            update = JsonParser.parseReader(reader).getAsJsonObject();
        }
        for (JsonElement addition : update.getAsJsonArray("additions")) {
            String entry = addition.getAsString();
            Path target = updateTarget(root, entry);
            if (!Files.exists(target)) publishSeed(target, entry);
        }
        for (var replacement : update.getAsJsonObject("replacements").entrySet()) {
            String entry = replacement.getKey();
            Path target = updateTarget(root, entry);
            // A missing file is an intentional deletion; an edited file remains owned by the user.
            if (!Files.isRegularFile(target) || !sha256(Files.readAllBytes(target)).equals(replacement.getValue().getAsString())) continue;
            // Changing a stock giver's category list would also hide customized old tasks. Keep that catalog owner-controlled.
            var guards = update.getAsJsonObject("preserve_if_changed");
            if (guards != null && guards.has(entry)) {
                boolean customized = false;
                for (var dependency : guards.getAsJsonObject(entry).entrySet()) {
                    Path original = updateTarget(root, dependency.getKey());
                    if (!Files.isRegularFile(original) || !sha256(Files.readAllBytes(original)).equals(dependency.getValue().getAsString())) {
                        customized = true;
                        break;
                    }
                }
                if (customized) continue;
            }
            Path backup = root.resolve(".updates/1.1").resolve(entry);
            Files.createDirectories(backup.getParent());
            if (!Files.exists(backup)) Files.copy(target, backup);
            Path temporary = Files.createTempFile(target.getParent(), ".taskbound-update-", ".tmp");
            try (InputStream input = resource(entry)) {
                Files.copy(input, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        Files.writeString(marker, "Taskbound 1.1 defaults applied once. Keep to preserve edits/deletions.\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private static Path updateTarget(Path root, String entry) throws IOException {
        Path target = root.resolve(entry).normalize();
        if (!entry.startsWith("data/taskbound/") || !target.startsWith(root.normalize()))
            throw new IOException("Invalid default update path: " + entry);
        return target;
    }

    private static void publishSeed(Path target, String entry) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".taskbound-update-", ".tmp");
        try (InputStream input = resource(entry)) {
            Files.copy(input, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(temporary, target); } catch (FileAlreadyExistsException ignored) { }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
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

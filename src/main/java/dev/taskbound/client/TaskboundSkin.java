package dev.taskbound.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.taskbound.TaskboundMod;
import dev.taskbound.core.GeneratedTask;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Native pixel sprites, with tiled nine-slice borders and resource-pack overrides. */
final class TaskboundSkin {
    private static final ResourceLocation FONT = id("guild_pixel");
    private final Map<String, Sprite> sprites = new LinkedHashMap<>();
    private final Map<String, Integer> colors = new LinkedHashMap<>();

    static TaskboundSkin load() {
        TaskboundSkin skin = new TaskboundSkin();
        skin.read("textures/gui/taskbound/pixel_defaults.json");
        skin.read("textures/gui/taskbound/skin.json");
        return skin;
    }

    private void read(String path) {
        Minecraft.getInstance().getResourceManager().getResource(id(path)).ifPresent(resource -> {
            try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root.has("sprites")) root.getAsJsonObject("sprites").entrySet().forEach(entry -> {
                    try {
                        JsonObject value = entry.getValue().getAsJsonObject();
                        int tw = value.get("texture_width").getAsInt();
                        int th = value.get("texture_height").getAsInt();
                        int x = number(value, "x", 0), y = number(value, "y", 0);
                        int w = number(value, "width", tw), h = number(value, "height", th);
                        int left = 0, top = 0, right = 0, bottom = 0;
                        boolean sliced = value.has("nine_slice");
                        if (sliced) {
                            var insets = value.getAsJsonArray("nine_slice");
                            if (insets.size() != 4) throw new IllegalArgumentException("Expected four slice insets");
                            left = insets.get(0).getAsInt(); top = insets.get(1).getAsInt();
                            right = insets.get(2).getAsInt(); bottom = insets.get(3).getAsInt();
                        }
                        if (tw < 1 || th < 1 || w < 1 || h < 1 || x < 0 || y < 0 || x + w > tw || y + h > th
                                || left < 0 || top < 0 || right < 0 || bottom < 0 || left + right >= w || top + bottom >= h) {
                            throw new IllegalArgumentException("Invalid sprite bounds or slice insets");
                        }
                        sprites.put(entry.getKey(), new Sprite(ResourceLocation.parse(value.get("texture").getAsString()),
                                tw, th, x, y, w, h, left, top, right, bottom, sliced,
                                value.has("stretch") && value.get("stretch").getAsBoolean()));
                    } catch (RuntimeException exception) {
                        TaskboundMod.LOGGER.warn("Ignoring invalid Taskbound sprite {}", entry.getKey(), exception);
                    }
                });
                if (root.has("colors")) root.getAsJsonObject("colors").entrySet().forEach(entry -> {
                    String value = entry.getValue().getAsString().replace("#", "");
                    int color = (int) Long.parseLong(value, 16);
                    colors.put(entry.getKey(), value.length() <= 6 ? color | 0xFF000000 : color);
                });
            } catch (Exception exception) {
                TaskboundMod.LOGGER.warn("Could not read Taskbound UI definition {}", path, exception);
            }
        });
    }

    static Component text(Component component) {
        return component.copy().withStyle(style -> style.withFont(FONT));
    }

    int color(String name) { return colors.getOrDefault(name, 0xFF342518); }

    void draw(GuiGraphics graphics, String name, int x, int y, int width, int height) {
        Sprite sprite = sprites.get(name);
        if (sprite == null || width < 1 || height < 1) return;
        if (!sprite.sliced) {
            // Icons retain their native pixel size; the caller supplies a placement box.
            int w = Math.min(width, sprite.width), h = Math.min(height, sprite.height);
            blit(graphics, sprite, x + (width - w) / 2, y + (height - h) / 2, sprite.x, sprite.y, w, h);
            return;
        }
        int l = Math.min(sprite.left, width / 2), r = Math.min(sprite.right, width - l);
        int t = Math.min(sprite.top, height / 2), b = Math.min(sprite.bottom, height - t);
        int[] dx = {x, x + l, x + width - r}, dy = {y, y + t, y + height - b};
        int[] dw = {l, width - l - r, r}, dh = {t, height - t - b, b};
        int[] sx = {sprite.x, sprite.x + sprite.left, sprite.x + sprite.width - r};
        int[] sy = {sprite.y, sprite.y + sprite.top, sprite.y + sprite.height - b};
        int[] sw = {l, sprite.width - sprite.left - sprite.right, r};
        int[] sh = {t, sprite.height - sprite.top - sprite.bottom, b};
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            if (sprite.stretch && dw[col] > 0 && dh[row] > 0 && sw[col] > 0 && sh[row] > 0) {
                graphics.blit(sprite.texture, dx[col], dy[row], dw[col], dh[row], sx[col], sy[row], sw[col], sh[row], sprite.textureWidth, sprite.textureHeight);
            } else tile(graphics, sprite, dx[col], dy[row], dw[col], dh[row], sx[col], sy[row], sw[col], sh[row]);
        }
    }

    private static void tile(GuiGraphics graphics, Sprite sprite, int x, int y, int width, int height,
            int sx, int sy, int sw, int sh) {
        if (sw < 1 || sh < 1) return;
        for (int oy = 0; oy < height; oy += sh) for (int ox = 0; ox < width; ox += sw) {
            blit(graphics, sprite, x + ox, y + oy, sx, sy, Math.min(sw, width - ox), Math.min(sh, height - oy));
        }
    }

    private static void blit(GuiGraphics graphics, Sprite sprite, int x, int y, int sx, int sy, int w, int h) {
        if (w > 0 && h > 0) graphics.blit(sprite.texture, x, y, (float) sx, (float) sy, w, h, sprite.textureWidth, sprite.textureHeight);
    }

    void board(GuiGraphics graphics, int x, int y, int width, int height) {
        draw(graphics, "board/body", x + 10, y + 10, width - 20, height - 20);
        draw(graphics, "backgrounds/wood_frame", x, y, width, height);
    }

    void journal(GuiGraphics graphics, int x, int y, int width, int height) {
        int left = width / 2, top = y + 14, bookHeight = Math.max(1, height - 36);
        draw(graphics, "journal_cover/left", x + 8, top, left - 8, bookHeight);
        draw(graphics, "journal_cover/right", x + left, top, width - left - 8, bookHeight);
        // The shaded top meets the right page stack; the tail lies over the lower cover.
        draw(graphics, "journal/bookmark", x + left + (width - left - 8) / 4 - 8,
                top + bookHeight - 12, 17, 19);
    }

    void guildIcon(GuiGraphics graphics, String guildId, String fallback, int x, int y) {
        guildIcon(graphics, guildId, fallback, x, y, false);
    }

    void guildIconLarge(GuiGraphics graphics, String guildId, String fallback, int x, int y) {
        guildIcon(graphics, guildId, fallback, x, y, true);
    }

    void guildPlaque(GuiGraphics graphics, String guildId, String fallback, int x, int y) {
        draw(graphics, "board/body", x + 5, y + 8, 30, 27);
        // Lighten only the icon backing while retaining the wood grain beneath it.
        graphics.fill(x + 5, y + 8, x + 35, y + 35, color("journal_icon_backing"));
        // Reuse the board's wood grain with narrower borders around a journal icon.
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(0.5F, 0.5F, 1);
        draw(graphics, "backgrounds/wood_frame", 0, 0, 80, 80);
        graphics.pose().popPose();
        guildIconLarge(graphics, guildId, fallback, x + 8, y + 11);
    }

    private void guildIcon(GuiGraphics graphics, String guildId, String fallback, int x, int y, boolean large) {
        String custom = "guild." + guildId + (large ? ".large" : "");
        int size = large ? 24 : 16;
        if (sprites.containsKey(custom)) draw(graphics, custom, x, y, size, size);
        else graphics.renderItem(TaskBoardText.item(fallback), x + (size - 16) / 2, y + (size - 16) / 2);
    }

    void taskIcon(GuiGraphics graphics, GeneratedTask task, int x, int y) {
        String template = task.templateId();
        if (template.contains("night_patrol") && !task.conditions().containsKey("icon")) {
            graphics.renderItem(TaskBoardText.item("minecraft:campfire"), x, y);
            return;
        }
        graphics.renderItem(TaskBoardText.icon(task), x, y);
    }

    void locationIcon(GuiGraphics graphics, GeneratedTask task, int x, int y) {
        graphics.renderItem(TaskBoardText.item("minecraft:compass"), x, y);
    }

    void timingIcon(GuiGraphics graphics, GeneratedTask task, int x, int y) {
        graphics.renderItem(TaskBoardText.item("minecraft:clock"), x + 4, y);
    }

    void rating(GuiGraphics graphics, GeneratedTask task, int x, int y) {
        draw(graphics, "difficulty/rating_" + Mth.clamp(task.difficulty().tier().ordinal() + 1, 0, 5), x, y, 39, 7);
    }

    void scrollbar(GuiGraphics graphics, int x, int y, int height, int offset, int visible, int total) {
        if (total <= visible || height <= 0) return;
        draw(graphics, "scrollbar/track", x, y, 5, height);
        int thumb = Math.min(height, Math.max(8, height * visible / total));
        int top = y + (height - thumb) * Mth.clamp(offset, 0, total - visible) / (total - visible);
        draw(graphics, "scrollbar/thumb", x, top, 5, thumb);
    }

    void countedProgress(GuiGraphics graphics, int x, int y, int width, int height, long points, long required) {
        if (width < 1 || height < 1) return;
        graphics.fill(x, y, x + width, y + height, color("muted"));
        int innerWidth = Math.max(0, width - 2);
        if (innerWidth == 0 || height <= 2) return;
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, color("border"));
        int fill = progressPointOffset(innerWidth, points, required);
        if (fill > 0) draw(graphics, "board/progress_fill", x + 1, y + 1, fill, height - 2);
    }

    private static int progressPointOffset(int innerWidth, long points, long required) {
        if (required <= 0) return innerWidth; // No next threshold means maximum reputation.
        return (int) Math.round(innerWidth * (Math.max(0L, Math.min(points, required)) / (double) required));
    }

    static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static int number(JsonObject value, String key, int fallback) { return value.has(key) ? value.get(key).getAsInt() : fallback; }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(TaskboundMod.MOD_ID, path); }
    private record Sprite(ResourceLocation texture, int textureWidth, int textureHeight, int x, int y, int width, int height,
                          int left, int top, int right, int bottom, boolean sliced, boolean stretch) { }
}

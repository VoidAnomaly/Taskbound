package dev.taskbound.client;

import dev.taskbound.core.GeneratedObjective;
import dev.taskbound.menu.TaskHandInMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import org.lwjgl.glfw.GLFW;

/** Native container slots let the player choose exactly which physical items to submit. */
public final class TaskHandInScreen extends AbstractContainerScreen<TaskHandInMenu> {
    private static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("container/slot");
    private TaskboundSkin skin;
    private TaskboundButton confirm;
    private TaskboundButton requirementsUp;
    private TaskboundButton requirementsDown;
    private Component taskTitle;
    private List<Component> requirements = List.of();
    private List<RequirementLine> requirementLines = List.of();
    private int requirementScroll;

    public TaskHandInScreen(TaskHandInMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = TaskHandInMenu.WIDTH;
        imageHeight = menu.imageHeight();
        inventoryLabelX = TaskHandInMenu.INVENTORY_X;
        inventoryLabelY = menu.inventoryY() - 10;
    }

    @Override
    protected void init() {
        super.init();
        skin = TaskboundSkin.load();
        taskTitle = TaskboundSkin.text(TaskBoardText.taskTitle(menu.task()));
        requirements = requirementBullets();
        requirementLines = wrapRequirements(imageWidth - 24);
        if (requirementLines.size() > menu.requirementRows()) requirementLines = wrapRequirements(imageWidth - 58);
        requirementScroll = Math.min(requirementScroll, maximumRequirementScroll());
        requirementsUp = addRequirementArrow(imageWidth - 41, "\u2191", "up", -1);
        requirementsDown = addRequirementArrow(imageWidth - 26, "\u2193", "down", 1);
        updateRequirementScroll();
        int buttonsY = topPos + menu.inventoryY() - 25;
        addRenderableWidget(new TaskboundButton(skin, leftPos + 12, buttonsY, 72, 14,
                Component.translatable("gui.cancel"), false, this::onClose));
        confirm = addRenderableWidget(new TaskboundButton(skin, leftPos + imageWidth - 84, buttonsY, 72, 14,
                Component.translatable("taskbound.hand_in.confirm"), false, () -> {
                    if (menu.ready() && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
                    }
                }));
        updateConfirm();
    }

    @Override
    protected void containerTick() {
        updateConfirm();
    }

    private void updateConfirm() {
        if (confirm == null) return;
        confirm.active = menu.ready();
        confirm.setTooltip(Tooltip.create(Component.translatable(menu.ready()
                ? "taskbound.hand_in.confirm_hint" : "taskbound.hand_in.choose_hint")));
    }

    private TaskboundButton addRequirementArrow(int x, String label, String symbol, int direction) {
        return addRenderableWidget(new TaskboundButton(skin, leftPos + x, topPos + TaskHandInMenu.REQUIREMENTS_Y,
                14, TaskHandInMenu.REQUIREMENT_ROW_HEIGHT, Component.literal(label), false,
                () -> scrollRequirements(direction), (graphics, button) -> {
                    skin.draw(graphics, "buttons/page_" + (!button.active ? "disabled" : button.isHoveredOrFocused() ? "hover" : "normal"),
                            button.getX(), button.getY(), button.getWidth(), button.getHeight());
                    skin.draw(graphics, "symbols/" + symbol, button.getX() + 2, button.getY(), 10, 10);
                }));
    }

    private int maximumRequirementScroll() { return Math.max(0, requirementLines.size() - menu.requirementRows()); }

    private void scrollRequirements(int direction) {
        requirementScroll = Math.clamp(requirementScroll + direction, 0, maximumRequirementScroll());
        updateRequirementScroll();
    }

    private void updateRequirementScroll() {
        requirementsUp.visible = requirementsDown.visible = maximumRequirementScroll() > 0;
        requirementsUp.active = requirementScroll > 0;
        requirementsDown.active = requirementScroll < maximumRequirementScroll();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateConfirm();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (!menu.getCarried().isEmpty()) return;
        if (hoveredSlot != null && hoveredSlot.index < menu.offered().getContainerSize() && !hoveredSlot.hasItem()) {
            graphics.renderTooltip(font, requirementTooltip(hoveredSlot.index), Optional.empty(), mouseX, mouseY);
        } else if (isHovering(10, 8, imageWidth - 20, 22, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(taskTitle, TaskboundSkin.text(Component.translatable("taskbound.hand_in.choose_hint"))),
                    Optional.empty(), mouseX, mouseY);
        } else if (isHovering(12, TaskHandInMenu.REQUIREMENTS_Y, imageWidth - (requirementsUp.visible ? 58 : 24),
                menu.requirementRows() * TaskHandInMenu.REQUIREMENT_ROW_HEIGHT, mouseX, mouseY)) {
            int line = requirementScroll + (mouseY - topPos - TaskHandInMenu.REQUIREMENTS_Y) / TaskHandInMenu.REQUIREMENT_ROW_HEIGHT;
            if (line < requirementLines.size()) {
                graphics.renderTooltip(font, font.split(requirements.get(requirementLines.get(line).requirement()), 230), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        skin.draw(graphics, "board/body", leftPos + 4, topPos + 4, imageWidth - 8, imageHeight - 8);
        skin.draw(graphics, "board/paper", leftPos + 5, topPos + 6, imageWidth - 10, imageHeight - 12);
        // The journal's plaques already reuse this half-size frame to keep its border narrow.
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos, topPos, 0);
        graphics.pose().scale(.5F, .5F, 1);
        skin.draw(graphics, "backgrounds/wood_frame", 0, 0, imageWidth * 2, imageHeight * 2);
        graphics.pose().popPose();
        for (var slot : menu.slots) {
            if (!slot.isActive()) continue;
            graphics.blitSprite(SLOT, leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18);
            if (slot.index >= menu.offered().getContainerSize() || slot.hasItem()) continue;
            ItemStack expected = expectedItem(menu.requirementForSlot(slot.index));
            // Use the recipe book's ghost-item path, tinted with the existing parchment palette.
            graphics.renderFakeItem(expected, leftPos + slot.x, topPos + slot.y);
            graphics.fill(RenderType.guiGhostRecipeOverlay(), leftPos + slot.x, topPos + slot.y,
                    leftPos + slot.x + 16, topPos + slot.y + 16, 0x88000000 | skin.color("light") & 0x00FFFFFF);
            if (!tagChoice(menu.requirementForSlot(slot.index))) graphics.renderItemDecorations(font, expected, leftPos + slot.x, topPos + slot.y,
                    String.valueOf(menu.requiredCountForSlot(slot.index)));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        var lines = font.split(taskTitle, imageWidth - 24);
        int lineCount = Math.min(2, lines.size());
        for (int line = 0; line < lineCount; line++) {
            if (line == 1 && lines.size() > 2) {
                var tail = new StringBuilder();
                lines.get(line).accept((index, style, codepoint) -> { tail.appendCodePoint(codepoint); return true; });
                var clipped = TaskboundSkin.text(Component.literal(tail + "\u2026"));
                graphics.drawString(font, TaskBoardText.elide(font, clipped, imageWidth - 24), 12, 10 + font.lineHeight,
                        skin.color("ink"), false);
            } else graphics.drawString(font, lines.get(line), 12, 10 + line * font.lineHeight, skin.color("ink"), false);
        }
        for (int row = 0; row < menu.requirementRows() && requirementScroll + row < requirementLines.size(); row++) {
            graphics.drawString(font, requirementLines.get(requirementScroll + row).text(), 12,
                    TaskHandInMenu.REQUIREMENTS_Y + row * TaskHandInMenu.REQUIREMENT_ROW_HEIGHT, skin.color("muted"), false);
        }
        graphics.drawString(font, TaskboundSkin.text(playerInventoryTitle), inventoryLabelX, inventoryLabelY, skin.color("ink"), false);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        var result = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        if (hoveredSlot != null && hoveredSlot.index < menu.offered().getContainerSize()) {
            result.add(Component.empty());
            result.addAll(requirementTooltip(hoveredSlot.index));
        }
        return result;
    }

    private List<Component> requirementTooltip(int index) {
        GeneratedObjective objective = menu.requirementForSlot(index);
        var lines = new ArrayList<Component>();
        lines.add(TaskboundSkin.text(Component.translatable(tagChoice(objective) ? "taskbound.hand_in.tag_total" : "taskbound.hand_in.required",
                tagChoice(objective) ? ((Number)objective.parameters().get("required_count")).intValue() : menu.requiredCountForSlot(index), expectedName(objective))));
        TaskBoardText.objectiveConditions(objective).forEach(line -> lines.add(TaskboundSkin.text(line)));
        lines.add(TaskboundSkin.text(Component.translatable("taskbound.hand_in.choose_hint")));
        return lines;
    }

    private List<Component> requirementBullets() {
        var result = new ArrayList<Component>();
        for (var objective : menu.task().objectives()) {
            if (!objective.type().equals("hand_in_item")) continue;
            int count = ((Number) objective.parameters().get("required_count")).intValue();
            result.add(TaskboundSkin.text(Component.literal("\u2022 ").append(
                    Component.translatable(tagChoice(objective) ? "taskbound.hand_in.tag_total" : "taskbound.hand_in.required", count, expectedName(objective)))));
        }
        return List.copyOf(result);
    }

    private List<RequirementLine> wrapRequirements(int width) {
        var result = new ArrayList<RequirementLine>();
        for (int index = 0; index < requirements.size(); index++) {
            for (var line : font.split(requirements.get(index), width)) result.add(new RequirementLine(line, index));
        }
        return List.copyOf(result);
    }

    private ItemStack expectedItem(GeneratedObjective objective) {
        ItemStack stack;
        if (tagChoice(objective)) {
            var tag = net.minecraft.tags.TagKey.create(Registries.ITEM, ResourceLocation.parse(objective.parameters().get("item_tag").toString()));
            stack = net.minecraft.core.registries.BuiltInRegistries.ITEM.getTag(tag)
                    .flatMap(set -> set.stream().findFirst()).map(holder -> new ItemStack(holder.value()))
                    .orElseGet(() -> TaskBoardText.item("minecraft:paper"));
        } else stack = TaskBoardText.item(objective.parameters().get("item").toString());
        if (minecraft.level != null && objective.parameters().get("potion") instanceof String potion) {
            minecraft.level.registryAccess().registryOrThrow(Registries.POTION).getHolder(ResourceLocation.parse(potion))
                    .ifPresent(holder -> stack.set(DataComponents.POTION_CONTENTS, new PotionContents(holder)));
        }
        return stack;
    }

    private static boolean tagChoice(GeneratedObjective objective) {
        return !objective.parameters().containsKey("item") && objective.parameters().containsKey("item_tag");
    }

    private Component expectedName(GeneratedObjective objective) {
        return tagChoice(objective) ? TaskBoardText.namedResource(objective.parameters().get("item_tag").toString()) : expectedItem(objective).getHoverName();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (isHovering(12, TaskHandInMenu.REQUIREMENTS_Y, imageWidth - 24,
                menu.requirementRows() * TaskHandInMenu.REQUIREMENT_ROW_HEIGHT, mouseX, mouseY)
                && maximumRequirementScroll() > 0 && vertical != 0) {
            scrollRequirements(vertical > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return keyCode == GLFW.GLFW_KEY_TAB || super.keyPressed(keyCode, scanCode, modifiers);
    }

    private record RequirementLine(FormattedCharSequence text, int requirement) { }
}

package dev.taskbound.api.magic;

import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
public interface MagicProgressListener {
    void onMagicAction(ServerPlayer player, MagicActionData action);
}

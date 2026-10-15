package com.shriisoot.spiderman.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.shriisoot.spiderman.entity.SpiderManEntity;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Talk to Spider-Man in plain chat ("spidey, get 32 wood") or with /spidey &lt;order&gt;.
 */
public final class SpideyCommands {
    private static final Pattern CHAT_ORDER = Pattern.compile("^\\s*(?:hey\\s+)?(?:spider[\\s-]?man|spidey|spiderman)\\b[\\s,:!.-]*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final double HEARING_RANGE = 96.0;

    private SpideyCommands() {
    }

    public static void init() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            Matcher matcher = CHAT_ORDER.matcher(message.signedContent());
            if (matcher.matches()) {
                String order = matcher.group(1);
                sender.getServer().execute(() -> dispatch(sender, order, false));
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("spidey")
                        .then(Commands.argument("order", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    return dispatch(player, StringArgumentType.getString(ctx, "order"), true);
                                }))));
    }

    private static int dispatch(ServerPlayer player, String order, boolean fromCommand) {
        List<SpiderManEntity> helpers = player.serverLevel().getEntitiesOfClass(SpiderManEntity.class,
                player.getBoundingBox().inflate(HEARING_RANGE),
                spidey -> spidey.isAlive() && (spidey.isOwnedBy(player) || spidey.getOwnerUUID() == null));
        if (helpers.isEmpty()) {
            if (fromCommand || !order.isBlank()) {
                player.sendSystemMessage(Component.literal("No Spider-Man within earshot. Use a Spider-Man Spawn Egg to call one in!")
                        .withStyle(ChatFormatting.GRAY));
            }
            return 0;
        }
        String text = order.isBlank() ? "status" : order.toLowerCase(Locale.ROOT);
        for (SpiderManEntity spidey : helpers) {
            spidey.receiveOrder(player, text);
        }
        return helpers.size();
    }
}

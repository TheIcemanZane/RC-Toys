package rctoys.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import rctoys.client.input.ControllerConfig;
import rctoys.client.input.ControllerManager;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class RCToysClientCommands {
    private RCToysClientCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("rctoys")
                .then(literal("controller")
                        .then(literal("list").executes(ctx -> {
                            var src = ctx.getSource();
                            var list = ControllerManager.listConnected();

                            if (list.isEmpty()) {
                                src.sendFeedback(Component.literal("[rctoys] No controllers detected.").withStyle(ChatFormatting.RED));
                                return 0;
                            }

                            src.sendFeedback(Component.literal("[rctoys] Connected controllers:").withStyle(ChatFormatting.GOLD));
                            for (var c : list) {
                                src.sendFeedback(Component.literal(String.format(
                                        "  jid=%d | %s | gamepad=%s | guid=%s",
                                        c.jid(), c.name(), c.isGamepad(), c.guid()
                                )));
                            }
                            return list.size();
                        }))

                        .then(literal("status").executes(ctx -> {
                            var src = ctx.getSource();
                            ControllerConfig cfg = ControllerConfig.load();

                            if (cfg.selectedGuid == null || cfg.selectedGuid.isBlank()) {
                                src.sendFeedback(Component.literal("[rctoys] Controller selection: AUTO (first connected).").withStyle(ChatFormatting.YELLOW));
                                var first = ControllerManager.firstConnected();
                                if (first.isPresent()) {
                                    var info = ControllerManager.getByJid(first.get()).orElse(null);
                                    if (info != null) {
                                        src.sendFeedback(Component.literal(String.format(
                                                "[rctoys] Current: jid=%d | %s | gamepad=%s | guid=%s",
                                                info.jid(), info.name(), info.isGamepad(), info.guid()
                                        )).withStyle(ChatFormatting.GRAY));
                                    }
                                }
                                return 1;
                            }

                            var jid = ControllerManager.findJidByGuid(cfg.selectedGuid);
                            if (jid.isPresent()) {
                                var info = ControllerManager.getByJid(jid.get()).orElse(null);
                                if (info != null) {
                                    src.sendFeedback(Component.literal(String.format(
                                            "[rctoys] Selected: jid=%d | %s | gamepad=%s | guid=%s",
                                            info.jid(), info.name(), info.isGamepad(), info.guid()
                                    )).withStyle(ChatFormatting.GREEN));
                                    return 1;
                                }
                            }

                            src.sendFeedback(Component.literal("[rctoys] Selected controller GUID not currently connected: " + cfg.selectedGuid)
                                    .withStyle(ChatFormatting.RED));
                            return 0;
                        }))

                        .then(literal("clear").executes(ctx -> {
                            var src = ctx.getSource();
                            ControllerConfig cfg = ControllerConfig.load();
                            cfg.selectedGuid = "";
                            cfg.save();
                            src.sendFeedback(Component.literal("[rctoys] Cleared controller selection (AUTO mode).").withStyle(ChatFormatting.GREEN));
                            return 1;
                        }))

                        .then(literal("select")
                                .then(argument("jid", IntegerArgumentType.integer(1, 16))
                                        .executes(ctx -> {
                                            var src = ctx.getSource();
                                            int jid = IntegerArgumentType.getInteger(ctx, "jid");

                                            var infoOpt = ControllerManager.getByJid(jid);
                                            if (infoOpt.isEmpty()) {
                                                src.sendFeedback(Component.literal("[rctoys] No controller present at jid=" + jid)
                                                        .withStyle(ChatFormatting.RED));
                                                return 0;
                                            }

                                            var info = infoOpt.get();
                                            ControllerConfig cfg = ControllerConfig.load();
                                            cfg.selectedGuid = info.guid();
                                            cfg.save();

                                            src.sendFeedback(Component.literal(String.format(
                                                    "[rctoys] Selected controller: jid=%d | %s | gamepad=%s",
                                                    info.jid(), info.name(), info.isGamepad()
                                            )).withStyle(ChatFormatting.GREEN));

                                            if (!info.isGamepad()) {
                                                src.sendFeedback(Component.literal(
                                                        "[rctoys] Note: isGamepad=false. You may need SDL mappings or raw joystick fallback."
                                                ).withStyle(ChatFormatting.YELLOW));
                                            }

                                            return 1;
                                        })))
                )
        );
    }
}
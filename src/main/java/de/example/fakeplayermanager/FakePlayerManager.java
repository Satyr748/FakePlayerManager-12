package de.example.fakeplayermanager;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class FakePlayerManager implements ModInitializer {
    public static final String MOD_ID = "fakeplayermanager";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final int HARD_LIMIT = 500;

    private static final Queue<SpawnTask> SPAWN_QUEUE = new ArrayDeque<>();
    private static final Set<String> MANAGED_BOTS = new HashSet<>();
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

    // One bot per server tick by default. This avoids a large synchronous spawn burst.
    private static final int SPAWNS_PER_TICK = 1;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("fakeplayer")
                .then(literal("spawn")
                    .then(argument("count", IntegerArgumentType.integer(1, HARD_LIMIT))
                        .executes(ctx -> queueSpawns(
                            ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "count"),
                            null
                        ))
                        .then(argument("kit", StringArgumentType.word())
                            .executes(ctx -> queueSpawns(
                                ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "count"),
                                StringArgumentType.getString(ctx, "kit")
                            ))
                        )
                    )
                )
                .then(literal("clear").executes(ctx -> clearManaged(ctx.getSource())))
                .then(literal("count").executes(ctx -> countManaged(ctx.getSource())))
            );
        });

        ServerTickEvents.END_SERVER_TICK.register(FakePlayerManager::processQueue);
        LOGGER.info("FakePlayerManager loaded. Requires VexBot to be installed.");
    }

    private static int queueSpawns(CommandSourceStack source, int count, String kit) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        if (MANAGED_BOTS.size() + SPAWN_QUEUE.size() + count > HARD_LIMIT) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
                "Limit is " + HARD_LIMIT + " managed fakeplayers."
            ));
            return 0;
        }

        Vec3 origin = player.position();
        String dimension = player.level().dimension().location().toString();

        // Spread the initial positions in a compact square to avoid stacking every bot
        // at exactly the same coordinates.
        for (int i = 0; i < count; i++) {
            int index = SPAWN_QUEUE.size() + i;
            int side = 10;
            double xOffset = (index % side) - (side / 2.0);
            double zOffset = (index / side) % side - (side / 2.0);
            String name = "FPM" + String.format("%04d", NEXT_ID.getAndIncrement());
            SPAWN_QUEUE.add(new SpawnTask(
                player,
                name,
                origin.x + xOffset,
                origin.y,
                origin.z + zOffset,
                dimension,
                kit
            ));
        }

        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
            "Queued " + count + " fakeplayers. Spawn queue: " + SPAWN_QUEUE.size()
        ), true);
        return count;
    }

    private static void processQueue(MinecraftServer server) {
        for (int i = 0; i < SPAWNS_PER_TICK && !SPAWN_QUEUE.isEmpty(); i++) {
            SpawnTask task = SPAWN_QUEUE.poll();
            if (task == null) return;

            try {
                ServerPlayer executor = task.executor();
                if (executor == null || !executor.isAlive() || executor.server != server) {
                    LOGGER.warn("Skipped {} because the command executor is no longer online.", task.name());
                    continue;
                }

                CommandSourceStack commandSource = executor.createCommandSourceStack();
                String spawn = "vexbot spawn " + task.name() + " "
                    + task.x() + " " + task.y() + " " + task.z();

                int result = server.getCommands().performPrefixedCommand(commandSource, spawn);
                if (result > 0) {
                    MANAGED_BOTS.add(task.name());

                    if (task.kit() != null && !task.kit().isBlank()) {
                        String giveKit = "vexbot givekit " + task.kit() + " " + task.name();
                        server.getCommands().performPrefixedCommand(commandSource, giveKit);
                    }
                } else {
                    LOGGER.warn("VexBot did not confirm spawn for {}. Check VexBot command syntax/permissions.", task.name());
                }
            } catch (Exception exception) {
                LOGGER.error("Could not spawn managed fakeplayer {}", task.name(), exception);
            }
        }
    }

    private static int clearManaged(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        CommandSourceStack console = server.createCommandSourceStack();
        int removed = 0;

        for (String name : Set.copyOf(MANAGED_BOTS)) {
            server.getCommands().performPrefixedCommand(console, "vexbot kill " + name);
            removed++;
        }
        MANAGED_BOTS.clear();
        SPAWN_QUEUE.clear();

        final int count = removed;
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
            "Cleared " + count + " tracked fakeplayers and the pending queue."
        ), true);
        return removed;
    }

    private static int countManaged(CommandSourceStack source) {
        int count = MANAGED_BOTS.size();
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
            "Managed fakeplayers: " + count + " / " + HARD_LIMIT
                + " | queued: " + SPAWN_QUEUE.size()
        ), false);
        return count;
    }

    private record SpawnTask(
        ServerPlayer executor,
        String name,
        double x,
        double y,
        double z,
        String dimension,
        String kit
    ) {}
}

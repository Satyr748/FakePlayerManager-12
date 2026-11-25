
package de.example.fakeplayermanager;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class FakePlayerManager implements ModInitializer {

    private static final int MAX_DUMMIES = 500;
    private static final int SPAWNS_PER_TICK = 2;

    private static final Map<UUID, ArmorStand> DUMMIES =
            new HashMap<>();

    private static final Queue<SpawnTask> QUEUE =
            new ArrayDeque<>();

    private static int nextId = 1;

    @Override
    public void onInitialize() {

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> {

                    dispatcher.register(
                            literal("fakeplayer")

                                    .then(literal("spawn")
                                            .then(argument(
                                                    "count",
                                                    IntegerArgumentType.integer(1, MAX_DUMMIES)
                                            ).executes(context ->
                                                    spawn(
                                                            context.getSource(),
                                                            IntegerArgumentType.getInteger(
                                                                    context, "count"
                                                            )
                                                    )
                                            ))
                                    )

                                    .then(literal("count")
                                            .executes(context ->
                                                    count(context.getSource())
                                            )
                                    )

                                    .then(literal("clear")
                                            .executes(context ->
                                                    clear(context.getSource())
                                            )
                                    )
                    );
                }
        );

        ServerTickEvents.END_SERVER_TICK.register(
                FakePlayerManager::processQueue
        );
    }

    private static int spawn(
            CommandSourceStack source,
            int amount
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        var player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();

        cleanRemoved();

        if (DUMMIES.size() + QUEUE.size() + amount > MAX_DUMMIES) {

            source.sendFailure(
                    Component.literal("Maximum limit: " + MAX_DUMMIES)
            );

            return 0;
        }

        double originX = player.getX();
        double originY = player.getY();
        double originZ = player.getZ();

        for (int i = 0; i < amount; i++) {

            int index = QUEUE.size() + i;

            double x = originX + (index % 10) * 2;
            double z = originZ + (index / 10) * 2;

            String name = "Dummy_" + nextId++;

            QUEUE.add(new SpawnTask(
                    level,
                    x,
                    originY,
                    z,
                    name
            ));
        }

        source.sendSuccess(
                () -> Component.literal(
                        amount + " dummy players queued."
                ),
                true
        );

        return amount;
    }

    private static void processQueue(MinecraftServer server) {

        for (int i = 0; i < SPAWNS_PER_TICK; i++) {

            SpawnTask task = QUEUE.poll();

            if (task == null) {
                return;
            }

            try {

                ArmorStand dummy = EntityType.ARMOR_STAND.create(
                        task.level(),
                        EntitySpawnReason.COMMAND
                );

                if (dummy == null) {
                    continue;
                }

                // FIX: yaw and pitch must be floating-point values.
                dummy.moveTo(
                        task.x(),
                        task.y(),
                        task.z(),
                        0.0F,
                        0.0F
                );

                dummy.setCustomName(
                        Component.literal(task.name())
                );

                dummy.setCustomNameVisible(true);
                dummy.setNoGravity(true);
                dummy.setInvulnerable(true);
                dummy.setShowArms(true);

                if (task.level().addFreshEntity(dummy)) {
                    DUMMIES.put(dummy.getUUID(), dummy);
                }

            } catch (Exception exception) {

                System.err.println(
                        "[FakePlayerManager] Spawn failed: "
                                + exception.getMessage()
                );
            }
        }
    }

    private static int count(CommandSourceStack source) {

        cleanRemoved();

        int amount = DUMMIES.size();

        source.sendSuccess(
                () -> Component.literal(
                        "Dummy players: " + amount
                                + " / " + MAX_DUMMIES
                                + " | Queued: " + QUEUE.size()
                ),
                false
        );

        return amount;
    }

    private static int clear(CommandSourceStack source) {

        int removed = 0;

        for (ArmorStand dummy : DUMMIES.values()) {

            if (dummy != null && dummy.isAlive()) {
                dummy.discard();
                removed++;
            }
        }

        DUMMIES.clear();
        QUEUE.clear();

        final int total = removed;

        source.sendSuccess(
                () -> Component.literal(
                        "Removed " + total + " dummy players."
                ),
                true
        );

        return total;
    }

    private static void cleanRemoved() {

        DUMMIES.entrySet().removeIf(
                entry -> entry.getValue() == null
                        || !entry.getValue().isAlive()
        );
    }

    private record SpawnTask(
            ServerLevel level,
            double x,
            double y,
            double z,
            String name
    ) {}
}

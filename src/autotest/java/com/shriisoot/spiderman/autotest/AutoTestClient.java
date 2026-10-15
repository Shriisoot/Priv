package com.shriisoot.spiderman.autotest;

import com.google.gson.GsonBuilder;
import com.shriisoot.spiderman.client.ClientWebCache;
import com.shriisoot.spiderman.entity.SpiderManEntity;
import com.shriisoot.spiderman.entity.WebLineEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Dev-only end-to-end test: boots a flat creative world, builds a scene, drives Spider-Man and the
 * Web Shooter through every feature, records assertions + screenshots, then quits the game.
 * Lives in its own source set and is never packaged into the release jar.
 */
public class AutoTestClient implements ClientModInitializer {
    private static final int G = -60; // feet level on a default superflat world
    private static final int MAX_TICKS = 20 * 60 * 9;

    private final Deque<Step> steps = new ArrayDeque<>();
    private final List<Map<String, Object>> results = new ArrayList<>();
    private final List<String> chat = new ArrayList<>();
    private final Map<String, Object> metrics = new LinkedHashMap<>();
    private Minecraft mc;
    private boolean worldRequested;
    private int titleTicks;
    private boolean started;
    private int ticks;
    private int cameraId = -1;
    private int webShots;
    private int zipShots;

    private interface Step {
        boolean tick();
    }

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("spiderman.autotest")) {
            return;
        }
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                chat.add(message.getString());
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    // ------------------------------------------------------------------ driver

    private void tick(Minecraft client) {
        mc = client;
        ticks++;
        if (ticks > MAX_TICKS) {
            check("global timeout not hit", false, "harness exceeded " + MAX_TICKS + " ticks");
            finish();
            return;
        }
        if (!worldRequested) {
            mc.options.pauseOnLostFocus = false;
            mc.options.onboardAccessibility = false;
            mc.options.tutorialStep = TutorialSteps.NONE;
            if (mc.screen != null && !(mc.screen instanceof TitleScreen)) {
                mc.setScreen(new TitleScreen());
                return;
            }
            // The title screen exists behind the resource-loading overlay; wait for loading to finish.
            if (mc.screen instanceof TitleScreen && mc.getOverlay() == null && ++titleTicks > 40) {
                worldRequested = true;
                log("resources loaded, creating world");
                createWorld();
            }
            return;
        }
        if (!started) {
            if (mc.player != null && mc.level != null && mc.screen == null) {
                started = true;
                buildScript();
            }
            if (ticks % 100 == 0) {
                log("waiting for world, screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()));
            }
            return;
        }
        watchForPoses();
        while (!steps.isEmpty()) {
            if (steps.peek().tick()) {
                steps.poll();
            } else {
                break;
            }
        }
        if (steps.isEmpty()) {
            finish();
        }
    }

    private void createWorld() {
        LevelSettings settings = new LevelSettings("autotest", GameType.CREATIVE, false, Difficulty.EASY, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("autotest", settings, new WorldOptions(20240607L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                mc.screen);
    }

    // ------------------------------------------------------------------ step helpers

    private void run(Runnable r) {
        steps.add(() -> {
            r.run();
            return true;
        });
    }

    private void waitTicks(int n) {
        int[] left = {n};
        steps.add(() -> --left[0] <= 0);
    }

    private void waitUntil(String label, BooleanSupplier condition, int timeout) {
        int[] left = {timeout};
        int[] waited = {0};
        steps.add(() -> {
            waited[0]++;
            if (condition.getAsBoolean()) {
                metrics.put("wait:" + label, waited[0]);
                return true;
            }
            if (--left[0] <= 0) {
                check(label, false, "timed out after " + timeout + " ticks");
                return true;
            }
            return false;
        });
    }

    private void command(String cmd) {
        run(() -> mc.player.connection.sendCommand(cmd));
    }

    private void shot(String name) {
        run(() -> screenshot(name));
    }

    private void screenshot(String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> {
        });
    }

    private void check(String name, boolean ok, String detail) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("check", name);
        r.put("pass", ok);
        r.put("detail", detail);
        r.put("tick", ticks);
        results.add(r);
        log((ok ? "PASS " : "FAIL ") + name + " - " + detail);
    }

    private void log(String s) {
        System.out.println("[AUTOTEST] " + s);
    }

    private IntegratedServer server() {
        return mc.getSingleplayerServer();
    }

    private <T> T onServer(Function<ServerLevel, T> fn) {
        return server().submit(() -> fn.apply(server().overworld())).join();
    }

    private SpiderManEntity serverSpidey(ServerLevel level) {
        List<SpiderManEntity> list = level.getEntitiesOfClass(SpiderManEntity.class, new AABB(-200, -70, -200, 200, 100, 200), Entity::isAlive);
        return list.isEmpty() ? null : list.get(0);
    }

    private SpiderManEntity clientSpidey() {
        if (mc.level == null) {
            return null;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof SpiderManEntity s && s.isAlive()) {
                return s;
            }
        }
        return null;
    }

    /** Floating, invisible camera. Feet position is offset so the eye lands exactly on (x, y, z). */
    private void camera(double x, double y, double z, Vec3 lookAt) {
        run(() -> {
            double dx = lookAt.x - x, dy = lookAt.y - y, dz = lookAt.z - z;
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
            float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
            cameraId = onServer(level -> {
                ArmorStand stand = cameraId >= 0 && level.getEntity(cameraId) instanceof ArmorStand a ? a : null;
                if (stand == null) {
                    stand = new ArmorStand(level, x, y, z);
                    stand.setInvisible(true);
                    stand.setNoGravity(true);
                    stand.setSilent(true);
                    level.addFreshEntity(stand);
                }
                double eye = stand.getEyeHeight();
                stand.moveTo(x, y - eye, z, yaw, pitch);
                stand.setYHeadRot(yaw);
                stand.setYBodyRot(yaw);
                return stand.getId();
            });
        });
        waitTicks(6);
        run(() -> {
            Entity cam = mc.level.getEntity(cameraId);
            if (cam != null) {
                mc.setCameraEntity(cam);
            }
        });
        waitTicks(2);
    }

    private void playerCamera(CameraType type) {
        run(() -> {
            mc.setCameraEntity(mc.player);
            mc.options.setCameraType(type);
        });
    }

    private Vec3 spideyPos() {
        SpiderManEntity s = clientSpidey();
        return s == null ? new Vec3(0, G, 0) : s.position();
    }

    /** Opportunistic screenshots of short-lived poses. */
    private void watchForPoses() {
        SpiderManEntity s = clientSpidey();
        if (s == null) {
            return;
        }
        if (s.getAnim() == SpiderManEntity.ANIM_WEB && s.webPose > 0.9F && webShots < 6 && ticks % 3 == 0) {
            screenshot(String.format(Locale.ROOT, "pose_web_%02d", webShots++));
            metrics.put("sawWebPose", true);
        }
        if (s.getAnim() == SpiderManEntity.ANIM_ZIP && s.zipPose > 0.8F && zipShots < 4 && ticks % 3 == 0) {
            screenshot(String.format(Locale.ROOT, "pose_zip_%02d", zipShots++));
            metrics.put("sawZipPose", true);
        }
    }

    private int countLogs() {
        int n = 0;
        for (ItemStack stack : mc.player.getInventory().items) {
            if (stack.is(ItemTags.LOGS)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private boolean chatContains(String needle) {
        for (String line : chat) {
            if (line.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ the scenario

    private void buildScript() {
        log("world ready, building scenario");
        run(() -> {
            mc.options.hideGui = true;
            mc.options.fov().set(70);
            mc.options.renderDistance().set(8);
        });
        for (String c : new String[] {
                "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
                "gamerule keepInventory true", "gamerule sendCommandFeedback false", "time set 6000", "weather clear",
                "tp @s 0 " + G + " -3 0 0",
                // trees (features generate natural, non-persistent leaves)
                "place feature minecraft:oak 12 " + G + " 0", "place feature minecraft:birch 15 " + G + " 6",
                "place feature minecraft:oak 10 " + G + " -7", "place feature minecraft:oak 16 " + G + " -3",
                // a "player-built" log wall with no leaves: Spider-Man must never touch it
                "fill -8 " + G + " -8 -8 " + (G + 3) + " -4 minecraft:oak_log",
                // climbing wall
                "fill -4 " + G + " 14 4 " + (G + 6) + " 14 minecraft:stone_bricks",
                // web-swing beam far away
                "fill 40 " + (G + 22) + " 20 90 " + (G + 22) + " 22 minecraft:stone",
        }) {
            command(c);
        }
        waitTicks(40);
        run(() -> {
            int logs = onServer(level -> {
                int n = 0;
                for (BlockPos p : BlockPos.betweenClosed(5, G, -12, 22, G + 12, 12)) {
                    if (level.getBlockState(p).is(BlockTags.LOGS)) n++;
                }
                return n;
            });
            metrics.put("treeLogsPlaced", logs);
            check("trees placed", logs >= 12, logs + " natural logs in the tree area");
        });

        // --- summon & ownership
        command("summon spiderman:spider_man 3 " + G + " 0");
        waitTicks(30);
        run(() -> {
            String owner = onServer(level -> {
                SpiderManEntity s = serverSpidey(level);
                return s == null ? "missing" : String.valueOf(s.getOwnerUUID());
            });
            check("spider-man spawned and owned by player", owner.equals(mc.player.getUUID().toString()), "owner=" + owner);
            check("greeting sent", chatContains("[Spider-Man]"), "chat lines: " + chat.size());
        });

        // --- idle shots: the owner stands just behind the camera (inside follow range) so he looks into the lens
        command("tp @s 3.5 " + G + " 4.6 180 0");
        run(() -> onServer(level -> {
            SpiderManEntity s = serverSpidey(level);
            s.setMode(SpiderManEntity.Mode.FOLLOW);
            s.teleportTo(3.5, G, 0.5);
            return null;
        }));
        waitTicks(10);
        camera(3.5, G + 1.55, 3.3, new Vec3(3.5, G + 1.05, 0.5));
        waitTicks(30);
        shot("01_idle_front");
        camera(6.6, G + 1.4, 0.9, new Vec3(3.5, G + 1.0, 0.5));
        waitTicks(10);
        shot("02_idle_side");
        camera(3.7, G + 1.6, -2.9, new Vec3(3.5, G + 1.0, 0.5));
        waitTicks(10);
        shot("03_idle_back");

        // --- perch
        command("spidey stay");
        waitTicks(60);
        camera(3.5, G + 1.2, 3.3, new Vec3(3.5, G + 0.7, 0.5));
        waitTicks(5);
        shot("04_perch_front");
        camera(6.4, G + 1.0, 1.4, new Vec3(3.5, G + 0.7, 0.5));
        waitTicks(5);
        shot("05_perch_side");
        run(() -> {
            String mode = onServer(level -> String.valueOf(serverSpidey(level).getMode()));
            check("stay command -> STAY mode", mode.equals("STAY"), "mode=" + mode);
        });

        // --- plain chat path (signed chat -> regex -> order)
        run(() -> mc.player.connection.sendChat("Hey spidey, status?"));
        waitUntil("chat order answered", () -> chatContains("Carrying"), 60);

        // --- gathering wood
        command("tp @s 4 " + G + " -1 90 0");
        command("spidey follow");
        waitTicks(10);
        run(() -> metrics.put("logsBefore", countLogs()));
        command("spidey get 10 wood");
        camera(1, G + 5, 11, new Vec3(12, G + 3, 0));
        int[] gatherShots = {0};
        int[] gatherTicks = {0};
        steps.add(() -> {
            gatherTicks[0]++;
            if (gatherTicks[0] % 40 == 0 && gatherShots[0] < 14) {
                screenshot(String.format(Locale.ROOT, "06_gather_%02d", gatherShots[0]++));
            }
            int got = countLogs() - (int) metrics.get("logsBefore");
            if (got >= 10) {
                metrics.put("gatherTicks", gatherTicks[0]);
                check("gathered and delivered 10 logs", true, got + " logs delivered in " + gatherTicks[0] + " ticks");
                return true;
            }
            if (gatherTicks[0] > 20 * 150) {
                check("gathered and delivered 10 logs", false, "only " + got + " logs after 150s; chat=" + chat);
                return true;
            }
            return false;
        });
        waitTicks(20);
        run(() -> {
            int houseLogs = onServer(level -> {
                int n = 0;
                for (BlockPos p : BlockPos.betweenClosed(-8, G, -8, -8, G + 3, -4)) {
                    if (level.getBlockState(p).is(BlockTags.LOGS)) n++;
                }
                return n;
            });
            check("player-built log wall untouched", houseLogs == 20, houseLogs + "/20 logs remain");
            boolean empty = onServer(level -> serverSpidey(level).getInventory().isEmpty());
            check("spider-man handed over everything", empty, "inventory empty=" + empty);
            check("web-yank pose observed", metrics.containsKey("sawWebPose"), "web pose screenshots: " + webShots);
        });
        shot("07_after_gather");

        // --- natural-language edge cases
        command("spidey don't leave the tree alone");
        waitTicks(5);
        run(() -> check("'don't leave ...' does not dismiss him", onServer(level -> serverSpidey(level) != null), "still here"));
        command("spidey stop chopping wood");
        waitTicks(5);
        run(() -> {
            String mode = onServer(level -> String.valueOf(serverSpidey(level).getMode()));
            check("'stop chopping wood' means stay, not a new job", mode.equals("STAY"), "mode=" + mode);
        });
        command("gamerule mobGriefing false");
        command("spidey get 5 wood");
        waitTicks(5);
        run(() -> {
            String mode = onServer(level -> String.valueOf(serverSpidey(level).getMode()));
            check("respects mobGriefing=false", !mode.equals("WORK") && chatContains("mobGriefing"), "mode=" + mode);
        });
        command("gamerule mobGriefing true");
        command("spidey follow");

        // --- wall climb (player becomes a spectator so Spider-Man stops following)
        command("gamemode spectator");
        command("tp @s 0 " + G + " 2");
        waitTicks(10);
        run(() -> onServer(level -> {
            SpiderManEntity s = serverSpidey(level);
            s.setMode(SpiderManEntity.Mode.FOLLOW);
            s.teleportTo(0.5, G, 12.0);
            s.getNavigation().moveTo(0.5, G + 7, 14.5, 1.0);
            return null;
        }));
        camera(9, G + 4, 11.5, new Vec3(0, G + 3.5, 13.5));
        double[] maxY = {G};
        int[] climbTicks = {0};
        steps.add(() -> {
            climbTicks[0]++;
            maxY[0] = Math.max(maxY[0], spideyPos().y);
            SpiderManEntity s = clientSpidey();
            if (s != null && s.isClimbing()) {
                metrics.put("sawClimbing", true);
            }
            if (climbTicks[0] % 8 == 0 && climbTicks[0] <= 96) {
                screenshot(String.format(Locale.ROOT, "08_climb_%02d", climbTicks[0] / 8));
            }
            if (climbTicks[0] >= 110) {
                metrics.put("climbMaxY", maxY[0]);
                check("climbs the wall", maxY[0] >= G + 5, "max y " + String.format(Locale.ROOT, "%.2f", maxY[0]) + " (wall top " + (G + 7) + ")");
                return true;
            }
            return false;
        });

        // --- web-zip to a far-away owner
        command("gamemode creative");
        command("tp @s 0 " + G + " -26 0 0");
        run(() -> onServer(level -> {
            SpiderManEntity s = serverSpidey(level);
            s.teleportTo(0.5, G + 7, 14.5);
            s.setMode(SpiderManEntity.Mode.FOLLOW);
            return null;
        }));
        camera(14, G + 6, -6, new Vec3(0, G + 3, -6));
        int[] zipTicks = {0};
        steps.add(() -> {
            zipTicks[0]++;
            if (zipTicks[0] % 10 == 0 && zipTicks[0] <= 80) {
                screenshot(String.format(Locale.ROOT, "09_zip_%02d", zipTicks[0] / 10));
            }
            double d = spideyPos().distanceTo(mc.player.position());
            if (d < 6) {
                check("web-zips to a distant owner", true, "arrived in " + zipTicks[0] + " ticks; zip pose seen=" + metrics.containsKey("sawZipPose"));
                return true;
            }
            if (zipTicks[0] > 300) {
                check("web-zips to a distant owner", false, "still " + String.format(Locale.ROOT, "%.1f", d) + " blocks away");
                return true;
            }
            return false;
        });

        // --- combat
        command("summon minecraft:zombie 6 " + G + " -36");
        camera(12, G + 4, -24, new Vec3(3, G + 1, -32));
        int[] fightTicks = {0};
        steps.add(() -> {
            fightTicks[0]++;
            if (fightTicks[0] % 12 == 0 && fightTicks[0] <= 96) {
                screenshot(String.format(Locale.ROOT, "10_fight_%02d", fightTicks[0] / 12));
            }
            boolean zombieAlive = onServer(level -> !level.getEntitiesOfClass(Zombie.class, new AABB(-60, -70, -80, 60, 0, 20), Entity::isAlive).isEmpty());
            if (!zombieAlive) {
                check("defeats a zombie", true, "in " + fightTicks[0] + " ticks");
                return true;
            }
            if (fightTicks[0] > 600) {
                check("defeats a zombie", false, "zombie still alive after 30s");
                return true;
            }
            return false;
        });

        // --- player web-swing
        command("gamemode survival");
        command("clear @s");
        command("give @s spiderman:web_shooter");
        command("tp @s 60 " + G + " 31 180 -60");
        waitTicks(20);
        run(() -> {
            mc.player.getInventory().selected = 0;
            mc.player.setYRot(180F);
            mc.player.setXRot(-62F);
            metrics.put("healthBeforeSwing", mc.player.getHealth());
        });
        playerCamera(CameraType.THIRD_PERSON_BACK);
        waitTicks(4);
        run(() -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
        waitUntil("player web anchors on the beam", () -> {
            WebLineEntity w = ClientWebCache.get(mc.player);
            return w != null && w.isAnchored();
        }, 40);
        shot("11_swing_attached");
        run(() -> {
            mc.player.setXRot(10F);
            mc.options.keyShift.setDown(true);
        });
        double[] swing = {0, -1000, 0};
        int[] swingTicks = {0};
        steps.add(() -> {
            swingTicks[0]++;
            if (swingTicks[0] == 22) {
                mc.options.keyShift.setDown(false);
                mc.options.keyUp.setDown(true);
            }
            Vec3 v = mc.player.getDeltaMovement();
            swing[0] = Math.max(swing[0], v.length());
            swing[1] = Math.max(swing[1], mc.player.getY());
            swing[2] += Math.abs(v.z);
            if (swingTicks[0] % 5 == 0 && swingTicks[0] <= 90) {
                screenshot(String.format(Locale.ROOT, "12_swing_%02d", swingTicks[0] / 5));
            }
            return swingTicks[0] >= 100;
        });
        run(() -> {
            metrics.put("swingMaxSpeed", swing[0]);
            metrics.put("swingMaxY", swing[1]);
            check("rope lifts and swings the player", swing[1] > G + 6 && swing[0] > 0.4,
                    String.format(Locale.ROOT, "max height +%.1f, max speed %.2f b/t", swing[1] - G, swing[0]));
            metrics.put("velocityBeforeRelease", mc.player.getDeltaMovement().length());
            mc.options.keyUp.setDown(false);
            mc.options.keyJump.setDown(true);
        });
        waitTicks(2);
        run(() -> mc.options.keyJump.setDown(false));
        waitUntil("jump releases the web", () -> ClientWebCache.get(mc.player) == null, 20);
        shot("13_released");
        waitUntil("player lands after release", () -> mc.player.onGround(), 200);
        waitTicks(10);
        run(() -> {
            float before = (float) metrics.get("healthBeforeSwing");
            check("no fall damage after swinging", mc.player.getHealth() >= before,
                    "health " + mc.player.getHealth() + " (was " + before + ")");
        });

        // --- web-zip (sneak + use)
        command("tp @s 50 " + G + " 31 180 -64");
        waitTicks(10);
        run(() -> {
            mc.player.setYRot(180F);
            mc.player.setXRot(-64F);
            mc.options.keyShift.setDown(true);
            metrics.put("healthBeforeZip", mc.player.getHealth());
        });
        waitTicks(4);
        run(() -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
        waitTicks(2);
        run(() -> mc.options.keyShift.setDown(false));
        double[] zipMinDist = {1000};
        Vec3[] anchor = {null};
        int[] zt = {0};
        steps.add(() -> {
            zt[0]++;
            WebLineEntity w = ClientWebCache.get(mc.player);
            if (w != null && w.isAnchored()) {
                anchor[0] = w.position();
                metrics.put("zipMode", w.getMode());
            }
            if (anchor[0] != null) {
                zipMinDist[0] = Math.min(zipMinDist[0], mc.player.getEyePosition().distanceTo(anchor[0]));
            }
            if (zt[0] % 4 == 0 && zt[0] <= 28) {
                screenshot(String.format(Locale.ROOT, "14_zip_%02d", zt[0] / 4));
            }
            return zt[0] >= 60;
        });
        run(() -> check("sneak-zip pulls the player to the anchor", anchor[0] != null && zipMinDist[0] < 3.0
                        && Integer.valueOf(WebLineEntity.MODE_ZIP).equals(((Number) metrics.getOrDefault("zipMode", -1)).intValue()),
                String.format(Locale.ROOT, "closest approach %.2f blocks, mode=%s", zipMinDist[0], metrics.get("zipMode"))));
        waitUntil("player lands after zip", () -> mc.player.onGround() && ClientWebCache.get(mc.player) == null, 300);
        waitTicks(10);
        run(() -> {
            float before = (float) metrics.get("healthBeforeZip");
            check("no fall damage after zip", mc.player.getHealth() >= before, "health " + mc.player.getHealth() + " (was " + before + ")");
        });

        // --- yank a mob with the web shooter
        command("tp @s 70 " + G + " 0 0 0");
        command("summon minecraft:pig 70 " + G + " 14");
        waitTicks(15);
        run(() -> {
            mc.player.setYRot(0F);
            mc.player.setXRot(4F);
            metrics.put("pigStart", onServer(level -> {
                List<Pig> pigs = level.getEntitiesOfClass(Pig.class, new AABB(60, -70, 0, 80, 0, 30));
                return pigs.isEmpty() ? Double.NaN : pigs.get(0).position().z;
            }));
        });
        run(() -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
        waitTicks(30);
        run(() -> {
            double start = (double) metrics.get("pigStart");
            double end = onServer(level -> {
                List<Pig> pigs = level.getEntitiesOfClass(Pig.class, new AABB(60, -70, -10, 80, 0, 30));
                return pigs.isEmpty() ? Double.NaN : pigs.get(0).position().z;
            });
            check("web yanks a mob toward the player", start - end > 4, String.format(Locale.ROOT, "pig moved %.1f blocks closer", start - end));
        });

        // --- inventory shot for item icons
        command("give @s spiderman:spider_man_spawn_egg 3");
        waitTicks(5);
        run(() -> {
            mc.options.hideGui = false;
            mc.setScreen(new InventoryScreen(mc.player));
        });
        waitTicks(10);
        shot("15_inventory");
        run(() -> mc.setScreen(null));

        // --- spider-man close-up again in daylight for the gallery
        command("gamemode spectator");
        run(() -> onServer(level -> {
            SpiderManEntity s = serverSpidey(level);
            s.teleportTo(30.5, G, 0.5);
            s.setMode(SpiderManEntity.Mode.STAY);
            return null;
        }));
        command("tp @s 30.5 " + G + " 6 180 0");
        run(() -> mc.options.hideGui = true);
        camera(28.5, G + 1.5, 3.2, new Vec3(30.5, G + 1.0, 0.5));
        waitTicks(70);
        shot("16_hero_perch");
        run(() -> onServer(level -> {
            serverSpidey(level).setMode(SpiderManEntity.Mode.FOLLOW);
            return null;
        }));
        waitTicks(40);
        shot("17_hero_stand");
    }

    private void finish() {
        Map<String, Object> out = new LinkedHashMap<>();
        long passed = results.stream().filter(r -> Boolean.TRUE.equals(r.get("pass"))).count();
        out.put("passed", passed);
        out.put("failed", results.size() - passed);
        out.put("results", results);
        out.put("metrics", metrics);
        out.put("chat", chat);
        File file = new File(mc.gameDirectory, "autotest-results.json");
        try {
            Files.writeString(file.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(out), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log("could not write results: " + e);
        }
        log("DONE passed=" + passed + " failed=" + (results.size() - passed));
        steps.clear();
        mc.stop();
    }
}

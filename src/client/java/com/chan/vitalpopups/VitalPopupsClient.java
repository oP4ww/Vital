package com.chan.vitalpopups;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class VitalPopupsClient implements ClientModInitializer {
    public static final String MOD_ID = "vitalpopups";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");

    private static final int POPUP_LIFETIME_TICKS = 24;
    private static final int STATE_STALE_AFTER_TICKS = 40;
    private static final int DEFAULT_HIT_REVEAL_TICKS = 60;
    private static final int HEART_ICON_SLOTS = 10;
    private static final char HEART_FULL_GLYPH = '\u2764';
    private static final char HEART_HALF_GLYPH = '\u2764';
    private static final char HEART_EMPTY_GLYPH = '\u2764';

    private static final Map<UUID, EntityVitals> ENTITY_VITALS = new HashMap<>();
    private static final List<Popup> POPUPS = new ArrayList<>();
    private static final List<LivingEntity> RENDERABLES = new ArrayList<>();

    private static KeyMapping toggleAllKey;
    private static KeyMapping toggleHealthKey;
    private static KeyMapping toggleDamageKey;
    private static KeyMapping toggleHealingKey;
    private static KeyMapping toggleAttackedOnlyKey;
    private static KeyMapping toggleLookTargetKey;
    private static KeyMapping toggleHeartIconsKey;
    private static KeyMapping toggleExactValuesKey;
    private static KeyMapping openConfigKey;

    private static Config config = Config.defaults();
    private static long currentTick = 0L;

    @Override
    public void onInitializeClient() {
        loadConfig();
        registerKeys();
        ClientTickEvents.END_CLIENT_TICK.register(VitalPopupsClient::onEndClientTick);
        LevelRenderEvents.END_MAIN.register(VitalPopupsClient::onEndMainRender);
    }

    public static Screen createConfigScreen(Screen parent) {
        return new ConfigScreen(parent);
    }

    private static void registerKeys() {
        KeyMapping.Category category = resolveKeyCategory();

        toggleAllKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.vitalpopups.toggle_all",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                category
        ));

        toggleHealthKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.vitalpopups.toggle_health",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                category
        ));

        toggleDamageKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.vitalpopups.toggle_popups",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                category
        ));

        toggleHealingKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.vitalpopups.toggle_healing",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_L,
                category
        ));

        toggleAttackedOnlyKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.vitalpopups.toggle_attacked_only",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                category
        ));

        toggleLookTargetKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.vitalpopups.toggle_look_target",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                category
        ));

        toggleHeartIconsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.vitalpopups.toggle_heart_icons",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_U,
                category
        ));

        toggleExactValuesKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.vitalpopups.toggle_exact_values",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_Y,
                category
        ));

        openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.vitalpopups.open_config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                category
        ));
    }

    private static KeyMapping.Category resolveKeyCategory() {
        for (String categoryName : new String[]{"Vital", "key.category." + MOD_ID + ".controls"}) {
            try {
                var registerString = KeyMapping.Category.class.getMethod("register", String.class);
                return (KeyMapping.Category) registerString.invoke(null, categoryName);
            } catch (Exception ignored) {
                try {
                    var registerString = KeyMapping.Category.class.getDeclaredMethod("register", String.class);
                    registerString.setAccessible(true);
                    return (KeyMapping.Category) registerString.invoke(null, categoryName);
                } catch (Exception ignoredToo) {
                    // Try next registration strategy.
                }
            }
        }

        try {
            Class<?> idClass;
            try {
                idClass = Class.forName("net.minecraft.util.Identifier");
            } catch (ClassNotFoundException ignored) {
                idClass = Class.forName("net.minecraft.resources.ResourceLocation");
            }

            Object identifier = idClass.getMethod("fromNamespaceAndPath", String.class, String.class)
                    .invoke(null, MOD_ID, "controls");

            return (KeyMapping.Category) KeyMapping.Category.class
                    .getMethod("register", idClass)
                    .invoke(null, identifier);
        } catch (Exception exception) {
            LOGGER.warn("Falling back to GAMEPLAY key category for Vital.", exception);
            return KeyMapping.Category.GAMEPLAY;
        }
    }

    private static void onEndClientTick(Minecraft client) {
        currentTick++;
        handleKeys(client);

        if (client.level == null || client.player == null) {
            ENTITY_VITALS.clear();
            POPUPS.clear();
            RENDERABLES.clear();
            return;
        }

        rebuildRenderableEntityList(client);
        updateTrackedVitals();
        cleanupExpiredState();
    }

    private static void handleKeys(Minecraft client) {
        while (toggleAllKey.consumeClick()) {
            config.modEnabled = !config.modEnabled;
            saveConfig();
            announce(client, "Vital Popups: " + onOff(config.modEnabled));
        }

        while (toggleHealthKey.consumeClick()) {
            config.healthIndicatorsEnabled = !config.healthIndicatorsEnabled;
            saveConfig();
            announce(client, "Health indicators: " + onOff(config.healthIndicatorsEnabled));
        }

        while (toggleDamageKey.consumeClick()) {
            config.damagePopupsEnabled = !config.damagePopupsEnabled;
            saveConfig();
            announce(client, "Damage popups: " + onOff(config.damagePopupsEnabled));
        }

        while (toggleHealingKey.consumeClick()) {
            config.healingPopupsEnabled = !config.healingPopupsEnabled;
            saveConfig();
            announce(client, "Healing popups: " + onOff(config.healingPopupsEnabled));
        }

        while (toggleAttackedOnlyKey.consumeClick()) {
            config.showOnRecentHitOnly = !config.showOnRecentHitOnly;
            saveConfig();
            announce(client, "Show on hit only: " + onOff(config.showOnRecentHitOnly));
        }

        while (toggleLookTargetKey.consumeClick()) {
            config.showWhenLookingAtTarget = !config.showWhenLookingAtTarget;
            saveConfig();
            announce(client, "Show while looking at target: " + onOff(config.showWhenLookingAtTarget));
        }

        while (toggleHeartIconsKey.consumeClick()) {
            config.showHeartIcons = !config.showHeartIcons;
            saveConfig();
            announce(client, "Heart icons: " + onOff(config.showHeartIcons));
        }

        while (toggleExactValuesKey.consumeClick()) {
            config.showExactValues = !config.showExactValues;
            saveConfig();
            announce(client, "Exact health values: " + onOff(config.showExactValues));
        }

        while (openConfigKey.consumeClick()) {
            client.setScreen(createConfigScreen(client.screen));
        }
    }

    private static void announce(Minecraft client, String message) {
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(message));
        }
    }

    private static void rebuildRenderableEntityList(Minecraft client) {
        RENDERABLES.clear();

        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity == null) {
            cameraEntity = client.player;
        }
        Vec3 cameraPos = cameraEntity.getEyePosition();
        double maxDistanceSquared = getRenderDistanceSquared();

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }

            if (!shouldTrack(client, living, cameraPos, maxDistanceSquared)) {
                continue;
            }

            RENDERABLES.add(living);
        }

        RENDERABLES.sort(Comparator.comparingDouble(entity -> cameraPos.distanceToSqr(getAnchorPosition(entity))));

        int maxTracked = config.maxTrackedEntities;
        if (RENDERABLES.size() > maxTracked) {
            RENDERABLES.subList(maxTracked, RENDERABLES.size()).clear();
        }
    }

    private static boolean shouldTrack(Minecraft client, LivingEntity living, Vec3 cameraPos, double maxDistanceSquared) {
        if (!living.isAlive() || living.isRemoved()) {
            return false;
        }

        if (living instanceof ArmorStand) {
            return false;
        }

        if (living == client.player) {
            return false;
        }

        if (living.isInvisible()) {
            return false;
        }

        return cameraPos.distanceToSqr(getAnchorPosition(living)) <= maxDistanceSquared;
    }

    private static void updateTrackedVitals() {
        for (LivingEntity entity : RENDERABLES) {
            UUID id = entity.getUUID();
            float health = clampMin(entity.getHealth(), 0.0F);
            float absorption = clampMin(entity.getAbsorptionAmount(), 0.0F);
            float maxHealth = Math.max(entity.getMaxHealth(), 1.0F);
            float combined = health + absorption;

            EntityVitals vitals = ENTITY_VITALS.get(id);

            if (vitals == null) {
                ENTITY_VITALS.put(id, new EntityVitals(health, absorption, combined, maxHealth, currentTick, 0L));
                continue;
            }

            float delta = combined - vitals.lastCombined;

            if (Math.abs(delta) >= 0.10F) {
                spawnPopup(entity, delta);
            }

            if (delta < -0.10F) {
                vitals.revealUntilTick = currentTick + config.hitRevealTicks;
            }

            vitals.lastHealth = health;
            vitals.lastAbsorption = absorption;
            vitals.lastCombined = combined;
            vitals.lastMaxHealth = maxHealth;
            vitals.lastSeenTick = currentTick;
        }
    }

    private static void spawnPopup(LivingEntity entity, float delta) {
        boolean healing = delta > 0.0F;

        if (healing && !config.healingPopupsEnabled) {
            return;
        }

        if (!healing && !config.damagePopupsEnabled) {
            return;
        }

        Vec3 base = getAnchorPosition(entity).add(randomOffset(entity.getUUID()), 0.35D, 0.0D);

        for (int i = POPUPS.size() - 1; i >= 0; i--) {
            Popup popup = POPUPS.get(i);

            if (popup.source.equals(entity.getUUID())
                    && popup.healing == healing
                    && currentTick - popup.createdTick <= 5L) {
                popup.amount += Math.abs(delta);
                popup.base = base;
                popup.createdTick = currentTick;
                return;
            }
        }

        POPUPS.add(new Popup(entity.getUUID(), base, Math.abs(delta), healing, currentTick));
    }

    private static double randomOffset(UUID uuid) {
        long bits = uuid.getLeastSignificantBits() ^ currentTick;
        return (((bits & 15L) / 15.0D) - 0.5D) * 0.45D;
    }

    private static void cleanupExpiredState() {
        ENTITY_VITALS.entrySet().removeIf(entry -> currentTick - entry.getValue().lastSeenTick > STATE_STALE_AFTER_TICKS);
        POPUPS.removeIf(popup -> currentTick - popup.createdTick > POPUP_LIFETIME_TICKS);
    }

    private static void onEndMainRender(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();

        if (!config.modEnabled || client.player == null || client.level == null) {
            return;
        }

        PoseStack poseStack = context.poseStack();
        MultiBufferSource.BufferSource bufferSource = context.bufferSource();
        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity == null) {
            cameraEntity = client.player;
        }
        Vec3 cameraPos = cameraEntity.getEyePosition();

        if (config.healthIndicatorsEnabled) {
            for (LivingEntity entity : RENDERABLES) {
                renderHealthIndicator(client, poseStack, bufferSource, cameraPos, entity);
            }
        }

        if (config.damagePopupsEnabled || config.healingPopupsEnabled) {
            for (Popup popup : POPUPS) {
                renderPopup(client, poseStack, bufferSource, cameraPos, popup);
            }
        }

        bufferSource.endBatch();
    }

    private static void renderHealthIndicator(
            Minecraft client,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Vec3 cameraPos,
            LivingEntity entity
    ) {
        EntityVitals vitals = ENTITY_VITALS.get(entity.getUUID());

        if (vitals == null) {
            return;
        }

        if (!shouldRenderIndicatorNow(client, entity, vitals)) {
            return;
        }

        float health = vitals.lastHealth;
        float maxHealth = Math.max(vitals.lastMaxHealth, 1.0F);
        int indicatorAlpha = Mth.clamp(config.healthIndicatorAlpha, 64, 255);
        int neutralTextColor = withAlpha(0xFFFFFF, indicatorAlpha);

        Vec3 anchor = getAnchorPosition(entity).add(0.0D, entity instanceof Player ? 0.18D : 0.0D, 0.0D);
        double yOffset = 0.0D;

        if (config.showHeartIcons) {
            renderBillboardSequence(client, poseStack, bufferSource, cameraPos, anchor.add(0.0D, yOffset, 0.0D),
                    buildHeartSequence(health, maxHealth), 1.00F, indicatorAlpha, false);
            yOffset -= 0.22D;
        }

        if (config.showExactValues) {
            renderBillboardText(client, poseStack, bufferSource, cameraPos, anchor.add(0.0D, yOffset, 0.0D),
                    formatNumber(health) + "/" + formatNumber(maxHealth), neutralTextColor, 0.76F, indicatorAlpha);
            yOffset -= 0.20D;
        }

        if (!config.showHeartIcons && !config.showExactValues) {
            renderBillboardText(client, poseStack, bufferSource, cameraPos, anchor, formatNumber(health), neutralTextColor, 0.90F, indicatorAlpha);
        }
    }

    private static void renderPopup(
            Minecraft client,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Vec3 cameraPos,
            Popup popup
    ) {
        if ((popup.healing && !config.healingPopupsEnabled) || (!popup.healing && !config.damagePopupsEnabled)) {
            return;
        }

        float progress = clamp((float) (currentTick - popup.createdTick) / (float) POPUP_LIFETIME_TICKS, 0.0F, 1.0F);
        float drift = 0.18F + progress * 0.85F;
        int alpha = (int) (config.popupOpacity * (1.0F - progress));

        if (alpha <= 6) {
            return;
        }

        String prefix = popup.healing ? "+" : "-";
        String text = prefix + formatNumber(popup.amount);
        int color = popup.healing ? withAlpha(0xFFFFFF, alpha) : withAlpha(0xFF5555, alpha);
        Vec3 position = popup.base.add(0.0D, drift, 0.0D);
        float scale = popup.healing ? 1.05F : 1.15F;

        renderBillboardText(client, poseStack, bufferSource, cameraPos, position, text, color, scale, alpha);
    }

    private static boolean shouldRenderIndicatorNow(Minecraft client, LivingEntity entity, EntityVitals vitals) {
        boolean showRecentHit = config.showOnRecentHitOnly && currentTick <= vitals.revealUntilTick;
        boolean showLookTarget = config.showWhenLookingAtTarget && isLookTarget(client, entity);

        if (!config.showOnRecentHitOnly && !config.showWhenLookingAtTarget) {
            return true;
        }

        return showRecentHit || showLookTarget;
    }

    private static boolean isLookTarget(Minecraft client, LivingEntity entity) {
        HitResult hitResult = client.hitResult;
        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return false;
        }
        return entityHitResult.getEntity() == entity;
    }

    private static void renderBillboardText(
            Minecraft client,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Vec3 cameraPos,
            Vec3 worldPosition,
            String text,
            int color,
            float scale,
            int alpha
    ) {
        if (cameraPos.distanceToSqr(worldPosition) > getRenderDistanceSquared()) {
            return;
        }

        Font font = client.font;
        Vec3 relative = worldPosition.subtract(cameraPos);

        poseStack.pushPose();
        poseStack.translate(relative.x, relative.y, relative.z);

        Entity cameraEntity = client.getCameraEntity();
        float yRot = cameraEntity != null ? cameraEntity.getViewYRot(1.0F) : 0.0F;
        float xRot = cameraEntity != null ? cameraEntity.getViewXRot(1.0F) : 0.0F;
        poseStack.mulPose(Axis.YP.rotationDegrees(-yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(xRot));

        float renderScale = 0.025F * scale;
        poseStack.scale(-renderScale, -renderScale, renderScale);

        Matrix4f matrix = poseStack.last().pose();
        float x = -font.width(text) / 2.0F;
        int shadedColor = withAlpha((color & 0x00FFFFFF) | 0x000000, alpha / 2);

        font.drawInBatch(
                text,
                x + 1.0F,
                1.0F,
                shadedColor,
                false,
                matrix,
                bufferSource,
                Font.DisplayMode.NORMAL,
                0,
                15728880
        );

        font.drawInBatch(
                text,
                x,
                0.0F,
                color,
                false,
                matrix,
                bufferSource,
                Font.DisplayMode.NORMAL,
                0,
                15728880
        );

        poseStack.popPose();
    }

    private static void renderBillboardSequence(
            Minecraft client,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Vec3 cameraPos,
            Vec3 worldPosition,
            FormattedCharSequence sequence,
            float scale,
            int alpha,
            boolean drawShadow
    ) {
        if (cameraPos.distanceToSqr(worldPosition) > getRenderDistanceSquared()) {
            return;
        }

        Font font = client.font;
        Vec3 relative = worldPosition.subtract(cameraPos);

        poseStack.pushPose();
        poseStack.translate(relative.x, relative.y, relative.z);

        Entity cameraEntity = client.getCameraEntity();
        float yRot = cameraEntity != null ? cameraEntity.getViewYRot(1.0F) : 0.0F;
        float xRot = cameraEntity != null ? cameraEntity.getViewXRot(1.0F) : 0.0F;
        poseStack.mulPose(Axis.YP.rotationDegrees(-yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(xRot));

        float renderScale = 0.025F * scale;
        poseStack.scale(-renderScale, -renderScale, renderScale);

        Matrix4f matrix = poseStack.last().pose();
        float x = -font.width(sequence) / 2.0F;

        if (drawShadow) {
            font.drawInBatch(
                    sequence,
                    x + 1.0F,
                    1.0F,
                    withAlpha(0x000000, alpha / 2),
                    false,
                    matrix,
                    bufferSource,
                    Font.DisplayMode.NORMAL,
                    0,
                    15728880
            );
        }

        font.drawInBatch(
                sequence,
                x,
                0.0F,
                withAlpha(0xFFFFFF, alpha),
                false,
                matrix,
                bufferSource,
                Font.DisplayMode.NORMAL,
                0,
                15728880
        );

        poseStack.popPose();
    }

    private static Vec3 getAnchorPosition(LivingEntity entity) {
        return entity.position().add(0.0D, entity.getBbHeight() + 0.55D, 0.0D);
    }

    private static FormattedCharSequence buildHeartSequence(float health, float maxHealth) {
        int totalHearts = Mth.clamp((int) Math.ceil(maxHealth / 2.0F), 1, HEART_ICON_SLOTS);
        int fullHearts = Mth.clamp((int) Math.floor(health / 2.0F), 0, HEART_ICON_SLOTS);
        boolean hasHalfHeart = fullHearts < totalHearts && (health - (fullHearts * 2.0F)) >= 0.5F;

        var component = Component.empty();
        for (int i = 0; i < totalHearts; i++) {
            int color;
            char glyph;

            if (i < fullHearts) {
                glyph = HEART_FULL_GLYPH;
                color = 0xFF2B2B;
            } else if (i == fullHearts && hasHalfHeart) {
                glyph = HEART_HALF_GLYPH;
                color = 0xB52020;
            } else {
                glyph = HEART_EMPTY_GLYPH;
                color = 0x2A2A2A;
            }

            component = component.append(Component.literal(String.valueOf(glyph)).withColor(color));
            if (i < totalHearts - 1) {
                component = component.append(Component.literal(" "));
            }
        }

        return component.getVisualOrderText();
    }








    private static String formatNumber(float value) {
        if (Math.abs(value - Math.round(value)) < 0.05F) {
            return Integer.toString(Math.round(value));
        }

        return String.format(Locale.ROOT, "%.1f", value);
    }


    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clampMin(float value, float min) {
        return Math.max(min, value);
    }

    private static int withAlpha(int color, int alpha) {
        int clampedAlpha = Math.max(0, Math.min(255, alpha));
        return (clampedAlpha << 24) | (color & 0x00FFFFFF);
    }

    private static double getRenderDistanceSquared() {
        return config.renderDistance * config.renderDistance;
    }

    private static String onOff(boolean value) {
        return value ? "enabled" : "disabled";
    }

    private static void loadConfig() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                config = Config.defaults();
                saveConfig();
                return;
            }

            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                Config loaded = GSON.fromJson(reader, Config.class);
                config = loaded != null ? loaded.sanitized() : Config.defaults();
            }
        } catch (Exception exception) {
            LOGGER.warn("Failed to load Vital Popups config from {}. Using defaults.", CONFIG_PATH, exception);
            config = Config.defaults();
            saveConfig();
        }
    }

    private static void saveConfig() {
        try {
            config = config.sanitized();
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException exception) {
            LOGGER.warn("Failed to save Vital Popups config to {}.", CONFIG_PATH, exception);
        }
    }

    private static final class EntityVitals {
        private float lastHealth;
        private float lastAbsorption;
        private float lastCombined;
        private float lastMaxHealth;
        private long lastSeenTick;
        private long revealUntilTick;

        private EntityVitals(float lastHealth, float lastAbsorption, float lastCombined, float lastMaxHealth, long lastSeenTick, long revealUntilTick) {
            this.lastHealth = lastHealth;
            this.lastAbsorption = lastAbsorption;
            this.lastCombined = lastCombined;
            this.lastMaxHealth = lastMaxHealth;
            this.lastSeenTick = lastSeenTick;
            this.revealUntilTick = revealUntilTick;
        }
    }


    private static final class Popup {
        private final UUID source;
        private Vec3 base;
        private float amount;
        private final boolean healing;
        private long createdTick;

        private Popup(UUID source, Vec3 base, float amount, boolean healing, long createdTick) {
            this.source = source;
            this.base = base;
            this.amount = amount;
            this.healing = healing;
            this.createdTick = createdTick;
        }
    }

    private static final class Config {
        private boolean modEnabled = true;
        private boolean healthIndicatorsEnabled = true;
        private boolean damagePopupsEnabled = true;
        private boolean healingPopupsEnabled = true;
        private boolean showHeartIcons = true;
        private boolean showExactValues = false;
        private boolean showOnRecentHitOnly = false;
        private boolean showWhenLookingAtTarget = false;
        private boolean showAbsorptionAsGold = true;
        private int renderDistance = 24;
        private int maxTrackedEntities = 32;
        private int hitRevealTicks = DEFAULT_HIT_REVEAL_TICKS;
        private int healthIndicatorAlpha = 255;
        private int popupOpacity = 255;

        private static Config defaults() {
            return new Config();
        }

        private Config copy() {
            Config copy = new Config();
            copy.modEnabled = this.modEnabled;
            copy.healthIndicatorsEnabled = this.healthIndicatorsEnabled;
            copy.damagePopupsEnabled = this.damagePopupsEnabled;
            copy.healingPopupsEnabled = this.healingPopupsEnabled;
            copy.showHeartIcons = this.showHeartIcons;
            copy.showExactValues = this.showExactValues;
            copy.showOnRecentHitOnly = this.showOnRecentHitOnly;
            copy.showWhenLookingAtTarget = this.showWhenLookingAtTarget;
            copy.showAbsorptionAsGold = this.showAbsorptionAsGold;
            copy.renderDistance = this.renderDistance;
            copy.maxTrackedEntities = this.maxTrackedEntities;
            copy.hitRevealTicks = this.hitRevealTicks;
            copy.healthIndicatorAlpha = this.healthIndicatorAlpha;
            copy.popupOpacity = this.popupOpacity;
            return copy;
        }

        private Config sanitized() {
            renderDistance = Mth.clamp(renderDistance, 8, 48);
            maxTrackedEntities = Mth.clamp(maxTrackedEntities, 8, 96);
            hitRevealTicks = Mth.clamp(hitRevealTicks, 20, 200);
            healthIndicatorAlpha = Mth.clamp(healthIndicatorAlpha, 64, 255);
            popupOpacity = Mth.clamp(popupOpacity, 96, 255);
            return this;
        }
    }

    private static final class ConfigScreen extends Screen {
        private final Screen parent;
        private Config editing;

        protected ConfigScreen(Screen parent) {
            super(Component.translatable("text.vitalpopups.title"));
            this.parent = parent;
            this.editing = config.copy();
        }

        @Override
        protected void init() {
            super.init();
            clearWidgets();
            int centerX = width / 2;
            int y = Math.max(24, height / 2 - 116);
            int buttonWidth = 300;
            int halfWidth = 146;
            int gap = 8;

            addRenderableWidget(toggleButton(centerX - buttonWidth / 2, y, buttonWidth, 20,
                    "text.vitalpopups.mod_enabled",
                    () -> editing.modEnabled,
                    value -> editing.modEnabled = value));
            y += 24;

            addRenderableWidget(toggleButton(centerX - buttonWidth / 2, y, buttonWidth, 20,
                    "text.vitalpopups.health_indicators",
                    () -> editing.healthIndicatorsEnabled,
                    value -> editing.healthIndicatorsEnabled = value));
            y += 28;

            addRenderableWidget(toggleButton(centerX - halfWidth - gap / 2, y, halfWidth, 20,
                    "text.vitalpopups.show_recent_hit_only",
                    () -> editing.showOnRecentHitOnly,
                    value -> editing.showOnRecentHitOnly = value));

            addRenderableWidget(toggleButton(centerX + gap / 2, y, halfWidth, 20,
                    "text.vitalpopups.show_look_target",
                    () -> editing.showWhenLookingAtTarget,
                    value -> editing.showWhenLookingAtTarget = value));
            y += 24;

            addRenderableWidget(toggleButton(centerX - halfWidth - gap / 2, y, halfWidth, 20,
                    "text.vitalpopups.show_heart_icons",
                    () -> editing.showHeartIcons,
                    value -> editing.showHeartIcons = value));

            addRenderableWidget(toggleButton(centerX + gap / 2, y, halfWidth, 20,
                    "text.vitalpopups.show_exact_values",
                    () -> editing.showExactValues,
                    value -> editing.showExactValues = value));
            y += 28;

            addRenderableWidget(toggleButton(centerX - halfWidth - gap / 2, y, halfWidth, 20,
                    "text.vitalpopups.damage_popups",
                    () -> editing.damagePopupsEnabled,
                    value -> editing.damagePopupsEnabled = value));

            addRenderableWidget(toggleButton(centerX + gap / 2, y, halfWidth, 20,
                    "text.vitalpopups.healing_popups",
                    () -> editing.healingPopupsEnabled,
                    value -> editing.healingPopupsEnabled = value));
            y += 28;

            addRenderableWidget(new RevealTimeSlider(centerX - buttonWidth / 2, y, buttonWidth, 20, editing));
            y += 24;
            addRenderableWidget(new DistanceSlider(centerX - buttonWidth / 2, y, buttonWidth, 20, editing));
            y += 24;
            addRenderableWidget(new AlphaSlider(centerX - buttonWidth / 2, y, buttonWidth, 20, editing));
            y += 24;
            addRenderableWidget(new PopupAlphaSlider(centerX - buttonWidth / 2, y, buttonWidth, 20, editing));
            y += 30;

            addRenderableWidget(Button.builder(Component.translatable("text.vitalpopups.reset"), button -> {
                editing = Config.defaults();
                init();
            }).bounds(centerX - halfWidth - gap / 2, y, halfWidth, 20).build());

            addRenderableWidget(Button.builder(Component.translatable("text.vitalpopups.done"), button -> {
                config = editing.sanitized();
                saveConfig();
                if (minecraft != null) {
                    minecraft.setScreen(parent);
                }
            }).bounds(centerX + gap / 2, y, halfWidth, 20).build());
        }


        @Override
        public void onClose() {
            if (minecraft != null) {
                minecraft.setScreen(parent);
            }
        }

        private Button toggleButton(int x, int y, int width, int height, String key, BooleanGetter getter, BooleanSetter setter) {
            return Button.builder(toggleLabel(key, getter.get()), button -> {
                setter.set(!getter.get());
                button.setMessage(toggleLabel(key, getter.get()));
            }).bounds(x, y, width, height).build();
        }

        private Component toggleLabel(String key, boolean value) {
            String stateKey = value ? "text.vitalpopups.on" : "text.vitalpopups.off";
            return Component.translatable(key).append(": ").append(Component.translatable(stateKey));
        }
    }

    private static final class RevealTimeSlider extends AbstractSliderButton {
        private final Config config;

        private RevealTimeSlider(int x, int y, int width, int height, Config config) {
            super(x, y, width, height, Component.empty(), (config.hitRevealTicks - 20.0D) / 180.0D);
            this.config = config;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double seconds = config.hitRevealTicks / 20.0D;
            setMessage(Component.translatable("text.vitalpopups.hit_reveal_time")
                    .append(": ")
                    .append(String.format(Locale.ROOT, "%.1fs", seconds)));
        }

        @Override
        protected void applyValue() {
            config.hitRevealTicks = Mth.clamp((int) Math.round(20.0D + value * 180.0D), 20, 200);
            updateMessage();
        }
    }

    private static final class DistanceSlider extends AbstractSliderButton {
        private final Config config;

        private DistanceSlider(int x, int y, int width, int height, Config config) {
            super(x, y, width, height, Component.empty(), (config.renderDistance - 8.0D) / 40.0D);
            this.config = config;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("text.vitalpopups.render_distance")
                    .append(": ")
                    .append(Integer.toString(config.renderDistance))
                    .append(" ")
                    .append(Component.translatable("text.vitalpopups.blocks")));
        }

        @Override
        protected void applyValue() {
            config.renderDistance = Mth.clamp((int) Math.round(8.0D + value * 40.0D), 8, 48);
            updateMessage();
        }
    }

    private static final class TrackedSlider extends AbstractSliderButton {
        private final Config config;

        private TrackedSlider(int x, int y, int width, int height, Config config) {
            super(x, y, width, height, Component.empty(), (config.maxTrackedEntities - 8.0D) / 88.0D);
            this.config = config;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("text.vitalpopups.max_tracked_entities")
                    .append(": ")
                    .append(Integer.toString(config.maxTrackedEntities)));
        }

        @Override
        protected void applyValue() {
            int snapped = (int) Math.round((8.0D + value * 88.0D) / 4.0D) * 4;
            config.maxTrackedEntities = Mth.clamp(snapped, 8, 96);
            updateMessage();
        }
    }

    private static final class AlphaSlider extends AbstractSliderButton {
        private final Config config;

        private AlphaSlider(int x, int y, int width, int height, Config config) {
            super(x, y, width, height, Component.empty(), (config.healthIndicatorAlpha - 64.0D) / 191.0D);
            this.config = config;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int percent = (int) Math.round((config.healthIndicatorAlpha / 255.0D) * 100.0D);
            setMessage(Component.translatable("text.vitalpopups.health_indicator_alpha")
                    .append(": ")
                    .append(Integer.toString(percent))
                    .append("%"));
        }

        @Override
        protected void applyValue() {
            config.healthIndicatorAlpha = Mth.clamp((int) Math.round(64.0D + value * 191.0D), 64, 255);
            updateMessage();
        }
    }

    private static final class PopupAlphaSlider extends AbstractSliderButton {
        private final Config config;

        private PopupAlphaSlider(int x, int y, int width, int height, Config config) {
            super(x, y, width, height, Component.empty(), (config.popupOpacity - 96.0D) / 159.0D);
            this.config = config;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int percent = (int) Math.round((config.popupOpacity / 255.0D) * 100.0D);
            setMessage(Component.translatable("text.vitalpopups.popup_opacity")
                    .append(": ")
                    .append(Integer.toString(percent))
                    .append("%"));
        }

        @Override
        protected void applyValue() {
            config.popupOpacity = Mth.clamp((int) Math.round(96.0D + value * 159.0D), 96, 255);
            updateMessage();
        }
    }

    @FunctionalInterface
    private interface BooleanGetter {
        boolean get();
    }

    @FunctionalInterface
    private interface BooleanSetter {
        void set(boolean value);
    }
}

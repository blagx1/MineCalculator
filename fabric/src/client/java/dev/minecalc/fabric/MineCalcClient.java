package dev.minecalc.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import dev.minecalc.common.config.MineCalcConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;

public final class MineCalcClient implements ClientModInitializer {
    public static final String MOD_ID = "minecalc";
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "main")
    );
    private static KeyMapping openCalculatorKey;

    @Override
    public void onInitializeClient() {
        Path settings = Minecraft.getInstance().gameDirectory.toPath().resolve("config")
                .resolve("minecalc").resolve("settings.properties");
        MineCalcConfig config = new MineCalcConfig();
        try { config.load(settings); } catch (IOException ignored) { }
        openCalculatorKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.minecalc.open", InputConstants.Type.KEYSYM,
                config.openKey(), CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openCalculatorKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new MineCalcScreen());
                } else if (client.screen instanceof MineCalcScreen screen) {
                    screen.requestCloseAnimated();
                }
            }
        });
    }

    public static void updateOpenKey(int key) {
        if (openCalculatorKey != null) {
            openCalculatorKey.setKey(InputConstants.Type.KEYSYM.getOrCreate(key));
            KeyMapping.resetMapping();
        }
    }
}

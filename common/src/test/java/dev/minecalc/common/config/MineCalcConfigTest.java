package dev.minecalc.common.config;

import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-free settings persistence, migration, and range smoke tests. */
public final class MineCalcConfigTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("minecalculator-config-test");
        Path file = dir.resolve("settings.properties");
        Files.writeString(file, "windowX=41\nwindowY=73\nactiveTab=history\ntheme=purple\nuiScale=small\n");
        MineCalcConfig config = new MineCalcConfig();
        config.load(file);
        check(config.windowX() == 41 && config.windowY() == 73, "legacy window position");
        check("history".equals(config.activeTab()), "legacy active tab");
        config.setActiveTab("settings");
        check("appearance".equals(config.activeTab()), "legacy settings tab migration");
        config.setActiveTab("history");
        check("purple".equals(config.theme()), "legacy theme");
        check(config.scalePercent() == 85, "legacy 85 percent scale migration");
        check(config.rememberWindowPosition(), "remember position default on");
        config.setTheme("light");
        config.setOpacity(65);
        config.setScalePercent(73);
        config.setOpenKey(76);
        config.setCalculateKey(257);
        config.setClearKey(261);
        config.setCloseKey(256);
        config.save(file);
        MineCalcConfig loaded = new MineCalcConfig();
        loaded.load(file);
        check("light".equals(loaded.theme()), "light theme persistence");
        check(loaded.opacity() == 65, "opacity persistence");
        check(loaded.scalePercent() == 73, "scale persistence");
        loaded.setScalePercent(10);
        check(loaded.scalePercent() == 50, "scale lower bound");
        loaded.setScalePercent(150);
        check(loaded.scalePercent() == 100, "scale upper bound");
        check(loaded.openKey() == 76, "open key persistence");
        check(loaded.calculateKey() == 257 && loaded.clearKey() == 261 && loaded.closeKey() == 256, "shortcut persistence");
        Files.deleteIfExists(file);
        Files.deleteIfExists(dir);
        System.out.println("MineCalculator config smoke tests passed.");
    }
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}

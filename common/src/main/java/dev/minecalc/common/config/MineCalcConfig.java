package dev.minecalc.common.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Platform-neutral settings model. Reads legacy 0.5/0.6 settings safely. */
public final class MineCalcConfig {
    private int windowX = -1;
    private int windowY = -1;
    private String activeTab = "calculator";
    private String theme = "blue";
    private int scalePercent = 100;
    private int opacity = 100;
    private boolean animationsEnabled = true;
    private boolean rememberWindowPosition = true;
    private int openKey = 75;
    private int calculateKey = 257;
    private int clearKey = 261;
    private int closeKey = 256;

    public int windowX() { return windowX; }
    public int windowY() { return windowY; }
    public String activeTab() { return activeTab; }
    public String theme() { return theme; }
    public int scalePercent() { return scalePercent; }
    public int opacity() { return opacity; }
    public boolean animationsEnabled() { return animationsEnabled; }
    public boolean rememberWindowPosition() { return rememberWindowPosition; }
    public int openKey() { return openKey; }
    public int calculateKey() { return calculateKey; }
    public int clearKey() { return clearKey; }
    public int closeKey() { return closeKey; }

    public void setWindowPosition(int x, int y) { windowX = x; windowY = y; }
    public void setActiveTab(String tab) {
        activeTab = switch (tab == null ? "" : tab) {
            case "history", "appearance", "controls", "additional" -> tab;
            case "settings" -> "appearance";
            default -> "calculator";
        };
    }
    public void setTheme(String value) {
        theme = switch (value == null ? "" : value.toLowerCase()) {
            case "purple", "turquoise", "light" -> value.toLowerCase();
            default -> "blue";
        };
    }
    public void setScalePercent(int value) { scalePercent = clamp(value, 50, 100); }
    public void setOpacity(int value) { opacity = clamp(value, 40, 100); }
    public void setAnimationsEnabled(boolean value) { animationsEnabled = value; }
    public void setRememberWindowPosition(boolean value) { rememberWindowPosition = value; }
    public void setOpenKey(int value) { openKey = value < 0 ? 75 : value; }
    public void setCalculateKey(int value) { calculateKey = value < 0 ? 257 : value; }
    public void setClearKey(int value) { clearKey = value < 0 ? 261 : value; }
    public void setCloseKey(int value) { closeKey = value < 0 ? 256 : value; }

    public void load(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(path)) { p.load(in); }
        windowX = parseInt(p.getProperty("windowX"), -1);
        windowY = parseInt(p.getProperty("windowY"), -1);
        setActiveTab(p.getProperty("activeTab", "calculator"));
        setTheme(p.getProperty("theme", "blue"));
        // Migrate old 85/100/115 scale presets into the new supported 50-100 range.
        int oldScale = switch (p.getProperty("uiScale", "standard")) {
            case "small" -> 85; case "large" -> 100; default -> 100;
        };
        setScalePercent(parseInt(p.getProperty("scalePercent"), oldScale));
        setOpacity(parseInt(p.getProperty("opacity"), 100));
        setAnimationsEnabled(Boolean.parseBoolean(p.getProperty("animationsEnabled", "true")));
        setRememberWindowPosition(Boolean.parseBoolean(p.getProperty("rememberWindowPosition", "true")));
        setOpenKey(parseInt(p.getProperty("openKey"), 75));
        setCalculateKey(parseInt(p.getProperty("calculateKey"), 257));
        setClearKey(parseInt(p.getProperty("clearKey"), 261));
        setCloseKey(parseInt(p.getProperty("closeKey"), 256));
    }

    public void save(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Properties p = new Properties();
        p.setProperty("configVersion", "3");
        p.setProperty("windowX", Integer.toString(windowX));
        p.setProperty("windowY", Integer.toString(windowY));
        p.setProperty("activeTab", activeTab);
        p.setProperty("theme", theme);
        p.setProperty("scalePercent", Integer.toString(scalePercent));
        p.setProperty("opacity", Integer.toString(opacity));
        p.setProperty("animationsEnabled", Boolean.toString(animationsEnabled));
        p.setProperty("rememberWindowPosition", Boolean.toString(rememberWindowPosition));
        p.setProperty("openKey", Integer.toString(openKey));
        p.setProperty("calculateKey", Integer.toString(calculateKey));
        p.setProperty("clearKey", Integer.toString(clearKey));
        p.setProperty("closeKey", Integer.toString(closeKey));
        try (OutputStream out = Files.newOutputStream(path)) { p.store(out, "MineCalculator 1.0.2 settings"); }
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}

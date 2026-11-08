package dev.minecalc.client;

import dev.minecalc.common.config.MineCalcConfig;
import dev.minecalc.common.history.CalculationEntry;
import dev.minecalc.common.history.HistoryManager;
import dev.minecalc.common.history.HistoryStore;
import dev.minecalc.common.math.Calculator;
import dev.minecalc.common.math.CalculatorException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.IntConsumer;

/** MineCalculator 1.1 client screen. */
public final class MineCalcScreen extends Screen {

    private static final Identifier SCHUELER_FONT = Identifier.fromNamespaceAndPath("minecalc", "schueler");

    private Component styledText(String value) {
        return Component.literal(value).withStyle(style -> style.withFont(new FontDescription.Resource(SCHUELER_FONT)).withBold(true));
    }

    private float textScaleFactor() {
        // Text follows the same UI scale as panels and controls.
        // Keeping a separate compensating scale here caused mismatched sizing
        // and incorrect text measurements when the interface scale changed.
        return 1.0f;
    }

    private void drawText(GuiGraphics g, String value, int x, int y, int color, boolean shadow) {
        // Keep text readable when the overall interface is scaled down.
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        float textScale = textScaleFactor();
        g.pose().scale(textScale, textScale);
        g.drawString(font, styledText(value), 0, 0, color, false);
        g.pose().popMatrix();
    }

    private void drawBoldText(GuiGraphics g, String value, int x, int y, int color) {
        Component bold = Component.literal(value).withStyle(style ->
                style.withFont(new FontDescription.Resource(SCHUELER_FONT)).withBold(true));
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        float textScale = textScaleFactor();
        g.pose().scale(textScale, textScale);
        g.drawString(font, bold, 0, 0, color, false);
        g.pose().popMatrix();
    }

    private int textWidth(String value) {
        return (int) Math.ceil(font.width(styledText(value)) * textScaleFactor());
    }

    private static final int BASE_WIDTH = 560;
    private static final int BASE_HEIGHT = 390;
    private static final int HEADER_HEIGHT = 34;
    private static final int SIDEBAR_WIDTH = 116;
    private static final int COLLAPSED_SIDEBAR_WIDTH = 42;
    private static final int CONTENT_GAP = 14;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM  HH:mm").withZone(ZoneId.systemDefault());
    private static final String[] KEYS = {"7", "8", "9", "÷", "4", "5", "6", "×",
            "1", "2", "3", "−", "C", "0", ".", "+", "(", ")", "%", "="};
    private static final String[] SECTION_ICONS = {"▤", "◷", "✦", "⌘", "☷"};

    private final HistoryManager history = new HistoryManager();
    private final MineCalcConfig config = new MineCalcConfig();
    private final Path configPath;
    private final Path historyPath;

    private String expression = "";
    private String result = "0";
    private String error = "";
    private String section = "calculator";
    private String theme = "blue";
    private int scalePercent = 100;
    private boolean sidebarCollapsed;
    private int opacity = 100;
    private boolean animationsEnabled = true;
    private boolean rememberWindowPosition = true;
    private boolean dragging;
    private boolean draggingHistoryScrollbar;
    private boolean draggingAppearanceSlider;
    private String activeAppearanceSlider = "";
    private int historyScrollbarDragOffset;
    private boolean capturingOpenKey;
    private String capturingKeyAction = "";
    private String keyBindWarning = "";
    private boolean confirmClearHistory;
    private long clearHistoryStartedAt;
    private boolean clearHistoryPending;
    private boolean confirmReset;
    private int panelX;
    private int panelY;
    private int historyScroll;
    private long sectionChangedAt;
    private long lastCalculationAt;
    private long themeChangedAt;
    private int previousAccent = 0xFF438FE8;
    private final long openedAt = System.currentTimeMillis();
    private long closeStartedAt;
    private boolean closing;
    private long toggleAnimationStartedAt;
    private boolean toggleAnimationTarget;
    private static IntConsumer openKeyUpdater = key -> { };

    /** Installed by the active loader adapter to update its native key mapping. */
    public static void setOpenKeyUpdater(IntConsumer updater) {
        openKeyUpdater = updater == null ? key -> { } : updater;
    }


    public MineCalcScreen() {
        super(Component.literal("MineCalculator"));
        Path root = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("minecalc");
        configPath = root.resolve("settings.properties");
        historyPath = root.resolve("history.tsv");
        try {
            config.load(configPath);
            history.replaceAll(HistoryStore.load(historyPath));
        } catch (IOException ignored) {
            // The calculator remains usable if local files are unavailable.
        }
        section = config.activeTab();
        theme = config.theme();
        scalePercent = nearestScale(config.scalePercent());
        config.setScalePercent(scalePercent);
        opacity = config.opacity();
        animationsEnabled = config.animationsEnabled();
        rememberWindowPosition = config.rememberWindowPosition();
        panelX = config.windowX();
        panelY = config.windowY();
    }

    private static int nearestScale(int value) {
        int[] choices = {50, 60, 70, 80, 90, 100};
        int best = choices[0];
        for (int choice : choices) if (Math.abs(choice - value) < Math.abs(best - value)) best = choice;
        return best;
    }

    private float scaleFactor() {
        // Keep typography and controls legible at the smallest configured UI scales.
        return scalePercent / 100.0f;
    }
    private int panelWidth() { return Math.min(BASE_WIDTH, Math.max(300, (int) (width / scaleFactor()) - 24)); }
    private int panelHeight() { return Math.min(BASE_HEIGHT, Math.max(290, (int) (height / scaleFactor()) - 24)); }
    private int logicalX(double x) { return (int) Math.floor(x / scaleFactor()); }
    private int logicalY(double y) { return (int) Math.floor(y / scaleFactor()); }
    private int sidebarWidth() { return sidebarCollapsed ? COLLAPSED_SIDEBAR_WIDTH : SIDEBAR_WIDTH; }
    private int sidebarLayoutWidth() {
        return sidebarCollapsed
                ? Math.max(COLLAPSED_SIDEBAR_WIDTH, (SIDEBAR_WIDTH - 22) / 2 + 26)
                : SIDEBAR_WIDTH;
    }
    private int contentX() { return panelX + sidebarLayoutWidth() + CONTENT_GAP; }
    private int contentWidth() { return Math.max(130, panelWidth() - sidebarLayoutWidth() - CONTENT_GAP - 16); }
    private boolean is(String value) { return section.equals(value); }
    private String tr(String ru, String en) {
        return Minecraft.getInstance().getLanguageManager().getSelected().startsWith("en") ? en : ru;
    }

    private static final int PAL_BG = 0, PAL_PANEL = 1, PAL_SIDEBAR = 2, PAL_CARD = 3,
            PAL_TEXT = 4, PAL_MUTED = 5, PAL_BUTTON = 6, PAL_BORDER = 7;
    private String previousTheme = "blue";
    private boolean light() { return "light".equals(theme); }
    private int palette(String id, int role) {
        boolean l = "light".equals(id);
        return switch (role) {
            case PAL_BG -> switch (id) { case "purple" -> 0xFF171321; case "turquoise" -> 0xFF102021; case "light" -> 0xFFF1F3F7; default -> 0xFF151D29; };
            case PAL_PANEL -> switch (id) { case "purple" -> 0xFF211B30; case "turquoise" -> 0xFF192B2D; case "light" -> 0xFFFAFBFD; default -> 0xFF202A37; };
            case PAL_SIDEBAR -> switch (id) { case "purple" -> 0xFF1D172B; case "turquoise" -> 0xFF142527; case "light" -> 0xFFE6EAF0; default -> 0xFF1B2430; };
            case PAL_CARD -> switch (id) { case "purple" -> 0xFF2D253E; case "turquoise" -> 0xFF203638; case "light" -> 0xFFF5F7FA; default -> 0xFF263241; };
            case PAL_TEXT -> l ? 0xFF202A36 : 0xFFE7EDF5;
            case PAL_MUTED -> switch (id) { case "purple" -> 0xFFB0A2C9; case "turquoise" -> 0xFF9BBDBD; case "light" -> 0xFF687586; default -> 0xFF8D9BAD; };
            case PAL_BUTTON -> switch (id) { case "purple" -> 0xFF3A304D; case "turquoise" -> 0xFF294346; case "light" -> 0xFFE8EDF3; default -> 0xFF303D4D; };
            case PAL_BORDER -> switch (id) { case "purple" -> 0xFF594A70; case "turquoise" -> 0xFF42696A; case "light" -> 0xFFCBD4DF; default -> 0xFF435267; };
            default -> 0xFFFFFFFF;
        };
    }
    private int themeColor(int role) {
        int target = palette(theme, role);
        if (!animationsEnabled || themeChangedAt == 0) return target;
        float p = Math.min(1.0f, (System.currentTimeMillis() - themeChangedAt) / 240.0f);
        return blend(palette(previousTheme, role), target, p);
    }
    private int bg() { return themeColor(PAL_BG); }
    private int panel() { return themeColor(PAL_PANEL); }
    private int sidebar() { return themeColor(PAL_SIDEBAR); }
    private int card() { return themeColor(PAL_CARD); }
    private int textColor() { return themeColor(PAL_TEXT); }
    private int muted() { return themeColor(PAL_MUTED); }
    private int baseAccent() { return switch (theme) {
        case "purple" -> 0xFF9B70E8; case "turquoise" -> 0xFF35BEB8; case "light" -> 0xFF438FE8; default -> 0xFF438FE8;
    }; }
    private int accent() {
        int target = baseAccent();
        if (!animationsEnabled || themeChangedAt == 0) return target;
        float progress = Math.min(1.0f, (System.currentTimeMillis() - themeChangedAt) / 180.0f);
        return blend(previousAccent, target, progress);
    }
    private int accentSoft() { return light() ? blend(accent(), 0xFFFFFFFF, 0.84f) : blend(accent(), 0xFF182331, 0.68f); }
    private int buttonBg() { return themeColor(PAL_BUTTON); }
    private int alphaColor(int color, int alpha) { return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF); }
    private int opacityAlpha() { return 255 * opacity / 100; }
    private int blend(int a, int b, float amount) {
        int r = (int) (((a >> 16) & 255) * (1 - amount) + ((b >> 16) & 255) * amount);
        int g = (int) (((a >> 8) & 255) * (1 - amount) + ((b >> 8) & 255) * amount);
        int bl = (int) ((a & 255) * (1 - amount) + (b & 255) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private void ensurePosition() {
        int w = panelWidth(), h = panelHeight();
        int logicalWidth = Math.max(1, (int) (width / scaleFactor()));
        int logicalHeight = Math.max(1, (int) (height / scaleFactor()));
        if (panelX < 0 || panelY < 0) {
            panelX = (logicalWidth - w) / 2;
            panelY = (logicalHeight - h) / 2;
        }
        panelX = Math.max(0, Math.min(panelX, Math.max(0, logicalWidth - w)));
        panelY = Math.max(0, Math.min(panelY, Math.max(0, logicalHeight - h)));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);
        ensurePosition();
        g.pose().pushMatrix();
        g.pose().scale(scaleFactor(), scaleFactor());
        int x = panelX, y = panelY, w = panelWidth(), h = panelHeight();
        if (animationsEnabled) {
            long elapsed = System.currentTimeMillis() - (closing ? closeStartedAt : openedAt);
            float progress = Math.max(0.0f, Math.min(1.0f, elapsed / 140.0f));
            float windowScale = closing ? 1.0f - 0.045f * progress : 0.955f + 0.045f * progress;
            g.pose().translate(x + w / 2.0f, y + h / 2.0f);
            g.pose().scale(windowScale, windowScale);
            g.pose().translate(-(x + w / 2.0f), -(y + h / 2.0f));
        }
        int mx = logicalX(mouseX), my = logicalY(mouseY);

        fillRounded(g, x - 2, y - 2, w + 4, h + 4, 9, 0x66000000);
        fillRounded(g, x, y, w, h, 8, alphaColor(bg(), opacityAlpha()));

        // Header and sidebar are adjacent, not overlapping. This prevents
        // alpha from being composited twice when the opacity slider is changed.
        int surface = alphaColor(panel(), opacityAlpha());
        fillTopRounded(g, x, y, w, HEADER_HEIGHT, 8, surface);
        g.fill(x, y + HEADER_HEIGHT, x + sidebarWidth(), y + h, surface);
        drawSidebar(g, mx, my, x, y, h);

        drawText(g, "MineCalculator", x + 14, y + 13, textColor(), false);
        drawText(g, "v1.1", x + w - 43, y + 13, muted(), false);
        g.fill(x + sidebarWidth(), y + HEADER_HEIGHT, x + sidebarWidth() + 1, y + h,
                alphaColor(light() ? 0xFFDCE2E9 : 0xFF303B49, opacityAlpha()));

        float sectionProgress = animationsEnabled && sectionChangedAt > 0
                ? Math.min(1.0f, Math.max(0.0f, (System.currentTimeMillis() - sectionChangedAt) / 170.0f)) : 1.0f;
        g.pose().pushMatrix();
        g.pose().translate((1.0f - sectionProgress) * 8.0f, 0.0f);
        switch (section) {
            case "history" -> renderHistory(g, mx, my, x, y, w, h);
            case "appearance" -> renderAppearance(g, mx, my, x, y, w, h);
            case "controls" -> renderControls(g, mx, my, x, y, w, h);
            case "additional" -> renderAdditional(g, mx, my, x, y, w, h);
            default -> renderCalculator(g, mx, my, x, y, w, h);
        }
        g.pose().popMatrix();
        g.pose().popMatrix();
        super.render(g, mouseX, mouseY, delta);
    }

    private void drawSidebar(GuiGraphics g, int mx, int my, int x, int y, int h) {
        String[] ids = {"calculator", "history", "appearance", "controls", "additional"};
        String[] labels = {tr("Калькулятор", "Calculator"), tr("История", "History"), tr("Внешний вид", "Appearance"), tr("Управление", "Controls"), tr("Дополнительно", "Advanced")};
        int sw = sidebarWidth();
        int arrowX = x + 7;
        int arrowY = y + HEADER_HEIGHT + 5;
        boolean arrowHover = inside(mx, my, arrowX, arrowY, 26, 24);
        fillRounded(g, arrowX, arrowY, 26, 24, 5, arrowHover ? card() : buttonBg());
        String arrow = sidebarCollapsed ? "<" : ">";
        float arrowScale = 1.55f;
        g.pose().pushMatrix();
        g.pose().translate(arrowX + 13.0f, arrowY + 12.0f);
        g.pose().scale(arrowScale, arrowScale);
        drawText(g, arrow, -textWidth(arrow) / 2, -4, accent(), false);
        g.pose().popMatrix();

        int itemY = y + HEADER_HEIGHT + 36;
        for (int i = 0; i < ids.length; i++) {
            int iy = itemY + i * 43;
            boolean active = section.equals(ids[i]);
            int itemX = x + (sidebarCollapsed ? 4 : 8);
            int itemW = sw - (sidebarCollapsed ? 8 : 16);
            boolean hover = inside(mx, my, itemX, iy, itemW, 36);
            if (active || hover) fillRounded(g, itemX, iy, itemW, 36, 5, active ? accentSoft() : card());

            float iconScale = i == 1 ? 1.85f : (i == 0 ? 1.75f : 1.45f);
            int iconCenterX = sidebarCollapsed ? x + sw / 2 : x + 27;
            Component icon = i == 1
                    ? Component.literal(SECTION_ICONS[i]).withStyle(style -> style.withBold(true))
                    : Component.literal(SECTION_ICONS[i]);
            // Center the actual scaled glyph bounds in each 36px row. The previous
            // fixed iy+10 baseline left icons visibly above the row's horizontal center.
            float glyphWidth = font.width(icon) * iconScale;
            float glyphHeight = font.lineHeight * iconScale;
            g.pose().pushMatrix();
            g.pose().translate(iconCenterX - glyphWidth / 2.0f, iy + 18.0f - glyphHeight / 2.0f);
            g.pose().scale(iconScale, iconScale);
            g.drawString(font, icon, 0, 0, active ? accent() : muted(), false);
            g.pose().popMatrix();
            if (!sidebarCollapsed) {
                String visibleLabel = fit(labels[i], SIDEBAR_WIDTH - 54);
                drawText(g, visibleLabel, x + 47, iy + (36 - 9) / 2, active ? textColor() : muted(), false);
            }
        }
        if (!sidebarCollapsed) drawText(g, "By blagx1", x + 16, y + h - 22, accent(), false);
    }

    private void drawSectionTitle(GuiGraphics g, String title, int x, int y) {
        drawBoldText(g, title, x, y + 14, textColor());
    }

    private void renderCalculator(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
        int cx = contentX(), cw = contentWidth();
        drawSectionTitle(g, tr("Калькулятор", "Calculator"), cx, y + HEADER_HEIGHT + 8);
        int displayY = y + 78;
        fillRounded(g, cx, displayY, cw, 72, 7, alphaColor(card(), opacityAlpha()));
        drawText(g, tr("ВЫРАЖЕНИЕ", "EXPRESSION"), cx + 10, displayY + 8, muted(), false);
        String shown = expression.isEmpty() ? tr("Введите выражение", "Enter an expression") : expression;
        drawText(g, fit(shown, cw - 20), cx + 10, displayY + 24, expression.isEmpty() ? muted() : textColor(), false);
        if (animationsEnabled && lastCalculationAt > 0 && System.currentTimeMillis() - lastCalculationAt < 180) {
            float t = Math.min(1.0f, (System.currentTimeMillis() - lastCalculationAt) / 180.0f);
            float pop = 0.94f + 0.06f * t;
            g.pose().pushMatrix();
            g.pose().translate(cx + 10, displayY + 49);
            g.pose().scale(pop, pop);
            drawText(g, "= " + fit(result, cw - 24), 0, 0, accent(), false);
            g.pose().popMatrix();
        } else drawText(g, "= " + fit(result, cw - 24), cx + 10, displayY + 49, accent(), false);
        int actionY = displayY + 80;
        drawText(g, fit(error.isEmpty() ? tr("Enter — вычислить · Backspace — удалить", "Enter — calculate · Backspace — delete") : error, cw), cx + 2,
                actionY, error.isEmpty() ? muted() : 0xFFFF6868, false);

        int gap = 4;
        int buttonW = Math.max(20, (cw - gap * 3) / 4);
        int startY = actionY + 18;
        int copyY = y + h - 34;
        int available = Math.max(80, copyY - startY - 7 - gap * 4);
        int buttonH = Math.max(14, Math.min(29, available / 5));
        for (int i = 0; i < KEYS.length; i++) {
            int row = i / 4, col = i % 4;
            int bx = cx + col * (buttonW + gap), by = startY + row * (buttonH + gap);
            if (by + buttonH <= copyY - 5) drawButton(g, KEYS[i], bx, by, buttonW, buttonH, mx, my, isAccentKey(KEYS[i]));
        }
        drawButton(g, tr("Копировать результат", "Copy result"), cx, copyY, cw, 25, mx, my, false);
    }

    private void renderHistory(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
        int cx = contentX(), cw = contentWidth();
        drawSectionTitle(g, tr("История", "History"), cx, y + HEADER_HEIGHT + 8);
        drawButton(g, confirmClearHistory ? tr("Подтвердить?", "Confirm?") : tr("Очистить", "Clear"), cx + cw - 92, y + 45, 92, 23, mx, my, false);
        List<CalculationEntry> entries = history.getEntries();
        int top = y + 78, bottom = y + h - 14, rowH = 48;
        int visible = Math.max(1, (bottom - top) / rowH);
        int maxScroll = Math.max(0, entries.size() - visible);
        historyScroll = Math.max(0, Math.min(historyScroll, maxScroll));
        if (entries.isEmpty()) {
            String empty = tr("История пока пуста", "History is empty");
            drawText(g, empty, cx + (cw - textWidth(empty)) / 2, top + 22, muted(), false);
            return;
        }
        int rowW = cw - 2;
        for (int i = 0; i < visible && i + historyScroll < entries.size(); i++) {
            CalculationEntry entry = entries.get(i + historyScroll);
            int ry = top + i * rowH;
            boolean fading = clearHistoryPending;
            float fade = fading ? Math.max(0.0f, 1.0f - (System.currentTimeMillis() - clearHistoryStartedAt) / 220.0f) : 1.0f;
            int slide = fading ? (int) ((1.0f - fade) * 18.0f) : 0;
            boolean hover = inside(mx, my, cx, ry, rowW, rowH - 4);
            int rowColor = alphaColor(hover ? accentSoft() : card(), (int) (255 * fade));
            fillRounded(g, cx - slide, ry, rowW, rowH - 4, 6, rowColor);
            drawText(g, fit(entry.expression(), rowW - 112), cx + 9 - slide, ry + 7, alphaColor(textColor(), (int)(255 * fade)), false);
            drawText(g, "= " + fit(entry.result(), rowW - 112), cx + 9 - slide, ry + 23, alphaColor(accent(), (int)(255 * fade)), false);
            String time = TIME_FORMAT.format(entry.createdAt());
            drawText(g, time, cx + rowW - textWidth(time) - 8 - slide, ry + 7, alphaColor(muted(), (int)(255 * fade)), false);
            drawText(g, "↶", cx + rowW - 48 - slide, ry + 25, alphaColor(accent(), (int)(255 * fade)), false);
            drawText(g, "⧉", cx + rowW - 23 - slide, ry + 25, alphaColor(muted(), (int)(255 * fade)), false);
        }
        if (entries.size() > visible) {
            int trackX = cx + cw - 5;
            int trackH = Math.max(1, bottom - top - 12);
            int thumbH = Math.max(22, trackH * visible / entries.size());
            int travel = Math.max(1, trackH - thumbH);
            int thumbY = top + (int) Math.round((double) historyScroll / maxScroll * travel);
            g.fill(trackX, top, trackX + 3, top + trackH, buttonBg());
            g.fill(trackX - 1, thumbY, trackX + 4, thumbY + thumbH, accent());
            drawText(g, tr("Колесо мыши — прокрутка", "Scroll with mouse wheel"), cx + 2, bottom - 9, muted(), false);
        }
    }

    private void renderAppearance(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
        int cx = contentX(), cw = contentWidth();
        drawSectionTitle(g, tr("Внешний вид", "Appearance"), cx, y + HEADER_HEIGHT + 8);
        drawText(g, tr("Цветовая тема", "Color theme"), cx, y + 81, textColor(), false);
        String[] names = {tr("Синяя", "Blue"), tr("Фиолетовая", "Purple"), tr("Бирюзовая", "Turquoise"), tr("Светлая", "Light")};
        String[] ids = {"blue", "purple", "turquoise", "light"};
        int gap = 5, sw = (cw - gap * 3) / 4;
        for (int i = 0; i < ids.length; i++) {
            int bx = cx + i * (sw + gap), by = y + 99;
            boolean selected = theme.equals(ids[i]);
            int color = switch (ids[i]) { case "purple" -> 0xFF9B70E8; case "turquoise" -> 0xFF35BEB8; case "light" -> 0xFFDAE2EC; default -> 0xFF438FE8; };
            fillRounded(g, bx, by, sw, 43, 5, selected ? accentSoft() : card());
            fillRounded(g, bx + 7, by + 8, 11, 11, 3, color);
            drawText(g, names[i], bx + (sw - textWidth(names[i])) / 2, by + 27, textColor(), false);
            if (selected) fillRounded(g, bx + 5, by + 39, sw - 10, 3, 2, accent());
        }
        drawText(g, tr("Масштаб интерфейса: ", "Interface scale: ") + scalePercent + "%", cx, y + 164, textColor(), false);
        drawScaleChoices(g, cx, y + 178, cw);
        drawText(g, tr("Прозрачность: ", "Opacity: ") + opacity + "%", cx, y + 219, textColor(), false);
        drawSlider(g, cx, y + 237, cw, opacity, 40, 100);
        drawToggle(g, tr("Плавные анимации", "Smooth animations"), animationsEnabled, cx, y + 273, cw, mx, my);
    }

    private void renderControls(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
        int cx = contentX(), cw = contentWidth();
        drawSectionTitle(g, tr("Управление", "Controls"), cx, y + HEADER_HEIGHT + 8);
        drawText(g, tr("Горячие клавиши", "Keyboard shortcuts"), cx, y + 81, textColor(), false);
        drawKeyRow(g, tr("Открыть / закрыть", "Open / close"), "open", keyName(config.openKey()), cx, y + 96, cw, mx, my);
        drawKeyRow(g, tr("Вычислить", "Calculate"), "calculate", keyName(config.calculateKey()), cx, y + 127, cw, mx, my);
        drawKeyRow(g, tr("Очистить выражение", "Clear expression"), "clear", keyName(config.clearKey()), cx, y + 158, cw, mx, my);
        drawKeyRow(g, tr("Закрыть окно", "Close window"), "close", keyName(config.closeKey()), cx, y + 189, cw, mx, my);
        if (!keyBindWarning.isEmpty()) drawWrapped(g, keyBindWarning, cx, y + 222, cw, 12, 2, 0xFFFF6868);
    }

    private void renderAdditional(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
        int cx = contentX(), cw = contentWidth();
        drawSectionTitle(g, tr("Дополнительно", "Advanced"), cx, y + HEADER_HEIGHT + 8);
        drawText(g, tr("Поведение окна", "Window behavior"), cx, y + 81, textColor(), false);
        drawToggle(g, tr("Запоминать положение окна", "Remember window position"), rememberWindowPosition, cx, y + 101, cw, mx, my);
        drawWrapped(g, tr("Положение сохраняется после закрытия MineCalculator.", "Position is saved when MineCalculator closes."), cx, y + 135, cw, 12, 2, muted());
        drawWrapped(g, tr("При смене разрешения окно остаётся в пределах экрана.", "The window stays on screen when resolution changes."), cx, y + 164, cw, 12, 2, muted());
        drawText(g, tr("Версия мода: v1.1", "Mod version: v1.1"), cx, y + 211, textColor(), false);
        drawButton(g, confirmReset ? tr("Подтвердить сброс", "Confirm reset") : tr("Сбросить настройки", "Reset settings"), cx, y + 239, cw, 28, mx, my, false);
        drawWrapped(g, tr("Сброс не удаляет историю вычислений.", "Reset does not delete calculation history."), cx, y + 276, cw, 12, 2, muted());
    }

    private void drawWrapped(GuiGraphics g, String value, int x, int y, int maxWidth, int lineHeight, int maxLines, int color) {
        StringBuilder line = new StringBuilder();
        int lineIndex = 0;
        for (String word : value.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (textWidth(candidate) > maxWidth && !line.isEmpty()) {
                if (lineIndex >= maxLines) return;
                drawText(g, line.toString(), x, y + lineIndex++ * lineHeight, color, false);
                line.setLength(0);
                line.append(word);
            } else {
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
        }
        if (!line.isEmpty() && lineIndex < maxLines) drawText(g, line.toString(), x, y + lineIndex * lineHeight, color, false);
    }

    private void drawKeyRow(GuiGraphics g, String label, String action, String key, int x, int y, int w, int mx, int my) {
        drawText(g, label, x, y + 9, muted(), false);
        boolean active = capturingOpenKey && capturingKeyAction.equals(action);
        drawButton(g, active ? tr("Нажмите клавишу…", "Press a key…") : key, x + w - 112, y, 112, 25, mx, my, active);
    }

    private void drawToggle(GuiGraphics g, String label, boolean enabled, int x, int y, int w, int mx, int my) {
        drawText(g, fit(label, w - 34), x, y + 7, textColor(), false);
        int bx = x + w - 21;
        int outline = light() ? 0xFF9AA7B6 : 0xFF65758A;
        float progress = !animationsEnabled || toggleAnimationStartedAt == 0
                ? 1.0f : Math.min(1.0f, (System.currentTimeMillis() - toggleAnimationStartedAt) / 150.0f);
        int from = toggleAnimationTarget ? bg() : accent();
        int to = enabled ? accent() : bg();
        int fill = progress >= 1.0f ? to : blend(from, to, progress);
        fillRounded(g, bx, y + 2, 17, 17, 3, outline);
        fillRounded(g, bx + 1, y + 3, 15, 15, 2, fill);
        if (enabled && progress > 0.15f) {
            int checkAlpha = (int) (255 * Math.min(1.0f, (progress - 0.15f) / 0.55f));
            int check = alphaColor(0xFFFFFFFF, checkAlpha);
            g.fill(bx + 4, y + 10, bx + 7, y + 13, check);
            g.fill(bx + 6, y + 8, bx + 13, y + 11, check);
        }
    }

    private void drawSlider(GuiGraphics g, int x, int y, int w, int value, int min, int max) {
        int trackY = y + 5, trackW = Math.max(20, w - 8);
        fillRounded(g, x, trackY, trackW, 5, 3, buttonBg());
        int filled = (int) ((long) (value - min) * trackW / (max - min));
        if (filled > 0) fillRounded(g, x, trackY, filled, 5, 3, accent());
        fillRounded(g, x + filled - 5, trackY - 4, 10, 13, 5, accent());
    }

    private void drawButton(GuiGraphics g, String label, int x, int y, int w, int h, int mx, int my, boolean selected) {
        boolean hover = inside(mx, my, x, y, w, h);
        int color = selected ? accentSoft() : hover ? blend(buttonBg(), accent(), 0.16f) : buttonBg();
        int border = selected ? accent() : themeColor(PAL_BORDER);
        // The border is part of the button surface, not a mask placed over it.
        fillRounded(g, x, y, w, h, 4, border);
        fillRounded(g, x + 1, y + 1, w - 2, h - 2, 3, color);
        if (selected) fillRounded(g, x + 5, y + h - 3, w - 10, 1, 1, accent());
        String visibleLabel = fit(label, w - 8);
        drawText(g, visibleLabel, x + (w - textWidth(visibleLabel)) / 2, y + Math.max(2, (h - 8) / 2), textColor(), false);
    }


    // Header surface: only the two outside top corners are rounded.
    // The bottom edge stays perfectly square so it meets the sidebar without gaps.
    private void fillTopRounded(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(w / 2, h)));
        if (r == 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        int alpha = (color >>> 24) & 255;
        int rgb = color & 0x00FFFFFF;
        for (int row = 0; row < h; row++) {
            double inset = 0.0;
            if (row < r) {
                double dy = r - (row + 0.5);
                inset = r - Math.sqrt(Math.max(0.0, r * r - dy * dy));
            }
            int left = (int) Math.ceil(inset);
            int right = w - left;
            if (right > left) g.fill(x + left, y + row, x + right, y + row + 1, color);
            double coverage = left - inset;
            if (left > 0 && coverage > 0.02) {
                int edgeAlpha = (int) Math.round(alpha * Math.min(1.0, coverage));
                if (edgeAlpha > 0) {
                    int edgeColor = (edgeAlpha << 24) | rgb;
                    g.fill(x + left - 1, y + row, x + left, y + row + 1, edgeColor);
                    g.fill(x + w - left, y + row, x + w - left + 1, y + row + 1, edgeColor);
                }
            }
        }
    }

    private void fillRounded(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0) { g.fill(x, y, x + w, y + h, color); return; }
        int alpha = (color >>> 24) & 255;
        int rgb = color & 0x00FFFFFF;
        // Draw horizontal scanlines with fractional edge coverage. This gives
        // the corners a soft antialiased edge instead of chunky stair steps.
        for (int row = 0; row < h; row++) {
            double inset = 0.0;
            if (row < r) {
                double dy = r - (row + 0.5);
                inset = r - Math.sqrt(Math.max(0.0, r * r - dy * dy));
            } else if (row >= h - r) {
                double dy = r - (h - row - 0.5);
                inset = r - Math.sqrt(Math.max(0.0, r * r - dy * dy));
            }
            int left = (int) Math.ceil(inset);
            int right = w - left;
            if (right > left) g.fill(x + left, y + row, x + right, y + row + 1, color);
            double edgeCoverage = left - inset;
            if (left > 0 && edgeCoverage > 0.02) {
                int edgeAlpha = (int) Math.round(alpha * Math.min(1.0, edgeCoverage));
                if (edgeAlpha > 0) {
                    int edgeColor = (edgeAlpha << 24) | rgb;
                    g.fill(x + left - 1, y + row, x + left, y + row + 1, edgeColor);
                    g.fill(x + w - left, y + row, x + w - left + 1, y + row + 1, edgeColor);
                }
            }
        }
    }

    private void drawScaleChoices(GuiGraphics g, int x, int y, int width) {
        int[] values = {50, 60, 70, 80, 90, 100};
        int size = Math.min(30, Math.max(22, (width - 25) / 6));
        int gap = Math.max(3, (width - size * 6) / 5);
        for (int i = 0; i < values.length; i++) {
            int bx = x + i * (size + gap);
            boolean selected = scalePercent == values[i];
            int border = selected ? accent() : themeColor(PAL_BORDER);
            fillRounded(g, bx, y, size, size, 4, border);
            fillRounded(g, bx + 1, y + 1, size - 2, size - 2, 3, selected ? accent() : buttonBg());
            String value = Integer.toString(values[i]);
            drawText(g, value, bx + (size - textWidth(value)) / 2, y + (size - 8) / 2, selected ? 0xFFFFFFFF : textColor(), false);
        }
    }

    private boolean isAccentKey(String key) { return "=".equals(key) || "C".equals(key); }
    private boolean inside(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private String fit(String value, int maxWidth) {
        if (value == null) return "";
        if (textWidth(value) <= maxWidth) return value;
        String ellipsis = "…";
        int end = value.length();
        while (end > 0 && textWidth(value.substring(0, end) + ellipsis) > maxWidth) end--;
        return value.substring(0, end) + ellipsis;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (closing) return true;
        ensurePosition();
        double mx = click.x() / scaleFactor(), my = click.y() / scaleFactor();
        int x = panelX, y = panelY, w = panelWidth(), h = panelHeight();
        if (click.button() == 0 && inside(mx, my, x, y, w, HEADER_HEIGHT)) { dragging = true; return true; }
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        int sw = sidebarWidth();
        int arrowX = x + 7;
        int arrowY = y + HEADER_HEIGHT + 5;
        if (inside(mx, my, arrowX, arrowY, 26, 24)) {
            sidebarCollapsed = !sidebarCollapsed;
            return true;
        }
        int itemY = y + HEADER_HEIGHT + 36;
        int itemX = x + (sidebarCollapsed ? 4 : 8);
        int itemW = sw - (sidebarCollapsed ? 8 : 16);
        if (inside(mx, my, itemX, itemY, itemW, 36 * 5 + 43 - 7)) {
            int index = Math.max(0, Math.min(4, ((int) my - itemY) / 43));
            String[] ids = {"calculator", "history", "appearance", "controls", "additional"};
            if (!section.equals(ids[index])) { section = ids[index]; sectionChangedAt = System.currentTimeMillis(); confirmClearHistory = false; confirmReset = false; saveConfig(); }
            return true;
        }
        int cx = contentX(), cw = contentWidth();
        if (is("calculator")) {
            int displayY = y + 78, actionY = displayY + 80, startY = actionY + 18;
            int gap = 4, buttonW = Math.max(20, (cw - gap * 3) / 4), copyY = y + h - 34;
            int available = Math.max(80, copyY - startY - 7 - gap * 4), buttonH = Math.max(14, Math.min(29, available / 5));
            for (int i = 0; i < KEYS.length; i++) {
                int bx = cx + (i % 4) * (buttonW + gap), by = startY + (i / 4) * (buttonH + gap);
                if (by + buttonH <= copyY - 5 && inside(mx, my, bx, by, buttonW, buttonH)) { press(KEYS[i]); return true; }
            }
            if (inside(mx, my, cx, copyY, cw, 25)) { Minecraft.getInstance().keyboardHandler.setClipboard(result); return true; }
        } else if (is("history")) {
            if (inside(mx, my, cx + cw - 92, y + 45, 92, 23)) {
                if (confirmClearHistory && !history.getEntries().isEmpty()) { clearHistoryPending = true; clearHistoryStartedAt = System.currentTimeMillis(); }
                confirmClearHistory = !confirmClearHistory; return true;
            }
            List<CalculationEntry> entries = history.getEntries();
            int top = y + 78, rowH = 48, bottom = y + h - 14;
            int visible = Math.max(1, (bottom - top) / rowH);
            int maxScroll = Math.max(0, entries.size() - visible);
            int trackX = cx + cw - 7, trackH = Math.max(1, bottom - top - 12);
            int thumbH = Math.max(22, trackH * visible / Math.max(1, entries.size()));
            int travel = Math.max(1, trackH - thumbH);
            int thumbY = top + (int) Math.round((double) historyScroll / Math.max(1, maxScroll) * travel);
            if (entries.size() > visible && inside(mx, my, trackX - 4, top, 12, trackH)) {
                draggingHistoryScrollbar = true;
                historyScrollbarDragOffset = inside(mx, my, trackX - 4, thumbY, 12, thumbH) ? (int) my - thumbY : thumbH / 2;
                updateHistoryScrollFromMouse(my, top, trackH, thumbH, maxScroll); return true;
            }
            for (int i = 0; i < visible && i + historyScroll < entries.size(); i++) {
                int ry = top + i * rowH;
                if (inside(mx, my, cx, ry, cw - 2, rowH - 4)) {
                    CalculationEntry entry = entries.get(i + historyScroll);
                    int rowRight = cx + cw - 2;
                    if (mx >= rowRight - 34) Minecraft.getInstance().keyboardHandler.setClipboard(entry.result());
                    else { expression = entry.expression(); result = "—"; error = ""; section = "calculator"; sectionChangedAt = System.currentTimeMillis(); saveConfig(); }
                    return true;
                }
            }
        } else if (is("appearance")) {
            String[] themes = {"blue", "purple", "turquoise", "light"};
            int gap = 5, themeButtonW = (cw - gap * 3) / 4;

            for (int i = 0; i < 4; i++) {
                if (inside(mx, my, cx + i * (themeButtonW + gap), y + 99, themeButtonW, 43)) {
                    if (!theme.equals(themes[i])) {
                        previousTheme = theme;
                        previousAccent = baseAccent();
                        theme = themes[i];
                        themeChangedAt = System.currentTimeMillis();
                    }
                    saveConfig();
                    return true;
                }
            }

            int scaleButton = Math.min(30, Math.max(22, (cw - 25) / 6));
            int scaleGap = Math.max(3, (cw - scaleButton * 6) / 5);
            int[] scaleChoices = {50, 60, 70, 80, 90, 100};
            for (int i = 0; i < scaleChoices.length; i++) {
                int bx = cx + i * (scaleButton + scaleGap);
                if (inside(mx, my, bx, y + 178, scaleButton, scaleButton)) {
                    scalePercent = scaleChoices[i]; ensurePosition(); saveConfig(); return true;
                }
            }
            if (inside(mx, my, cx - 3, y + 232, cw + 6, 18)) { draggingAppearanceSlider = true; activeAppearanceSlider = "opacity"; opacity = sliderValue(mx, cx, cw, 40, 100); return true; }
            if (inside(mx, my, cx, y + 273, cw, 23)) { animationsEnabled = !animationsEnabled; toggleAnimationTarget = animationsEnabled; toggleAnimationStartedAt = System.currentTimeMillis(); saveConfig(); return true; }
        } else if (is("controls")) {
            String[] actions = {"open", "calculate", "clear", "close"};
            int[] rowY = {y + 96, y + 127, y + 158, y + 189};
            for (int i = 0; i < actions.length; i++) if (inside(mx, my, cx + cw - 112, rowY[i], 112, 25)) {
                capturingOpenKey = true; capturingKeyAction = actions[i]; return true;
            }
        } else if (is("additional")) {
            if (inside(mx, my, cx, y + 101, cw, 23)) { rememberWindowPosition = !rememberWindowPosition; toggleAnimationTarget = rememberWindowPosition; toggleAnimationStartedAt = System.currentTimeMillis(); saveConfig(); return true; }
            if (inside(mx, my, cx, y + 239, cw, 28)) {
                if (confirmReset) { resetSettings(); confirmReset = false; }
                else confirmReset = true;
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private void updateHistoryScrollFromMouse(double mouseY, int top, int trackHeight, int thumbHeight, int maxScroll) {
        if (maxScroll <= 0) { historyScroll = 0; return; }
        int travel = Math.max(1, trackHeight - thumbHeight);
        double fraction = Math.max(0.0, Math.min(1.0, (mouseY - historyScrollbarDragOffset - top) / travel));
        historyScroll = (int) Math.round(fraction * maxScroll);
    }

    private int sliderValue(double mouseX, int x, int w, int min, int max) {
        double fraction = Math.max(0, Math.min(1, (mouseX - x) / Math.max(1, w)));
        return min + (int) Math.round(fraction * (max - min));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (draggingHistoryScrollbar && click.button() == 0) {
            double my = click.y() / scaleFactor();
            int top = panelY + 78, bottom = panelY + panelHeight() - 14;
            int trackH = Math.max(1, bottom - top - 12);
            int visible = Math.max(1, (bottom - top) / 48);
            int count = history.getEntries().size();
            int maxScroll = Math.max(0, count - visible);
            int thumbH = Math.max(22, trackH * visible / Math.max(1, count));
            updateHistoryScrollFromMouse(my, top, trackH, thumbH, maxScroll);
            return true;
        }
        if (dragging && click.button() == 0) {
            panelX += (int) Math.round(offsetX / scaleFactor());
            panelY += (int) Math.round(offsetY / scaleFactor());
            ensurePosition(); return true;
        }
        if (draggingAppearanceSlider && click.button() == 0) {
            double mx = click.x() / scaleFactor();
            int cx = contentX(), cw = contentWidth();
            if ("opacity".equals(activeAppearanceSlider)) opacity = sliderValue(mx, cx, cw, 40, 100);
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (draggingHistoryScrollbar) { draggingHistoryScrollbar = false; return true; }
        if (draggingAppearanceSlider) { draggingAppearanceSlider = false; activeAppearanceSlider = ""; saveConfig(); return true; }
        if (dragging) { dragging = false; saveConfig(); return true; }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        mouseX /= scaleFactor(); mouseY /= scaleFactor();
        if (is("history") && inside(mouseX, mouseY, panelX, panelY, panelWidth(), panelHeight())) {
            int top = panelY + 78, bottom = panelY + panelHeight() - 14;
            int visible = Math.max(1, (bottom - top) / 48);
            int maxScroll = Math.max(0, history.getEntries().size() - visible);
            int steps = Math.max(1, (int) Math.ceil(Math.abs(verticalAmount)));
            historyScroll = Math.max(0, Math.min(maxScroll, historyScroll + (verticalAmount < 0 ? steps : -steps)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (closing) return true;
        int key = input.key();
        if (capturingOpenKey) {
            if (key == GLFW.GLFW_KEY_ESCAPE && !"close".equals(capturingKeyAction)) {
                capturingOpenKey = false; capturingKeyAction = ""; return true;
            }
            if (hasKeyConflict(capturingKeyAction, key)) {
                keyBindWarning = tr("Эта клавиша уже назначена другой команде MineCalculator.", "This key is already assigned to another MineCalculator action.");
                capturingOpenKey = false; capturingKeyAction = ""; return true;
            }
            keyBindWarning = "";
            switch (capturingKeyAction) {
                case "open" -> { config.setOpenKey(key); openKeyUpdater.accept(key); }
                case "calculate" -> config.setCalculateKey(key);
                case "clear" -> config.setClearKey(key);
                case "close" -> config.setCloseKey(key);
                default -> { }
            }
            capturingOpenKey = false; capturingKeyAction = ""; saveConfig(); return true;
        }
        if (key == config.closeKey()) { requestCloseAnimated(); return true; }
        if (key == config.calculateKey() || key == GLFW.GLFW_KEY_KP_ENTER) { calculate(); return true; }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!expression.isEmpty()) expression = expression.substring(0, expression.length() - 1);
            error = ""; return true;
        }
        if (key == config.clearKey()) { expression = ""; result = "0"; error = ""; return true; }
        if ((input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_C) {
            if (!result.isBlank()) Minecraft.getInstance().keyboardHandler.setClipboard(result); return true;
        }
        String typed = keyToText(key, (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0);
        if (typed != null) { expression += typed; error = ""; return true; }
        return super.keyPressed(input);
    }

    private String keyToText(int key, boolean shift) {
        if (shift && key == GLFW.GLFW_KEY_9) return "(";
        if (shift && key == GLFW.GLFW_KEY_0) return ")";
        if (shift && key == GLFW.GLFW_KEY_8) return "*";
        if (shift && key == GLFW.GLFW_KEY_5) return "%";
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) return Character.toString((char) ('0' + key - GLFW.GLFW_KEY_0));
        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) return Character.toString((char) ('0' + key - GLFW.GLFW_KEY_KP_0));
        return switch (key) {
            case GLFW.GLFW_KEY_KP_ADD -> "+";
            case GLFW.GLFW_KEY_EQUAL -> shift ? "+" : "=";
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> "-";
            case GLFW.GLFW_KEY_SLASH, GLFW.GLFW_KEY_KP_DIVIDE -> "/";
            case GLFW.GLFW_KEY_KP_MULTIPLY -> "*";
            case GLFW.GLFW_KEY_PERIOD, GLFW.GLFW_KEY_KP_DECIMAL -> ".";
            default -> null;
        };
    }

    private void press(String key) {
        if ("=".equals(key)) calculate();
        else if ("C".equals(key)) { expression = ""; result = "0"; error = ""; }
        else { expression += key.equals("−") ? "-" : key; error = ""; }
    }
    private void calculate() {
        try {
            BigDecimal value = Calculator.calculate(expression);
            result = Calculator.format(value); error = "";
            history.add(new CalculationEntry(expression, result, Instant.now())); lastCalculationAt = System.currentTimeMillis(); saveHistory(); 
        } catch (CalculatorException exception) { error = translateCalculatorError(exception.getMessage()); }
    }
    private String translateCalculatorError(String message) {
        if (!Minecraft.getInstance().getLanguageManager().getSelected().startsWith("en")) return message;
        return switch (message) {
            case "Введите выражение" -> "Enter an expression";
            case "Деление на ноль невозможно" -> "Division by zero is not allowed";
            case "Выражение слишком длинное" -> "Expression is too long";
            default -> message.replace("Не закрыта скобка", "Missing closing parenthesis")
                    .replace("Ожидалось число или скобка", "Expected a number or parenthesis")
                    .replace("Ожидалось число", "Expected a number")
                    .replace("Лишний символ", "Unexpected character")
                    .replace("Некорректное число", "Invalid number");
        };
    }

    private void saveHistory() {
        try { HistoryStore.save(historyPath, history.getEntries()); }
        catch (IOException ignored) { error = tr("Не удалось сохранить историю", "Could not save history"); }
    }
    private void saveConfig() {
        config.setWindowPosition(rememberWindowPosition ? panelX : -1, rememberWindowPosition ? panelY : -1);
        config.setRememberWindowPosition(rememberWindowPosition);
        config.setActiveTab(section); config.setTheme(theme); config.setScalePercent(scalePercent);
        config.setOpacity(opacity); config.setAnimationsEnabled(animationsEnabled);
        try { config.save(configPath); } catch (IOException ignored) { /* Local settings are non-fatal. */ }
    }
    private void resetSettings() {
        theme = "blue"; scalePercent = 100; opacity = 100; animationsEnabled = true;
        rememberWindowPosition = true;
        config.setOpenKey(GLFW.GLFW_KEY_K); config.setCalculateKey(GLFW.GLFW_KEY_ENTER);
        config.setClearKey(GLFW.GLFW_KEY_DELETE); config.setCloseKey(GLFW.GLFW_KEY_ESCAPE);
        openKeyUpdater.accept(GLFW.GLFW_KEY_K);
        panelX = -1; panelY = -1; ensurePosition(); saveConfig(); confirmReset = false;
    }
    private boolean hasKeyConflict(String action, int key) {
        return (!"open".equals(action) && config.openKey() == key)
                || (!"calculate".equals(action) && config.calculateKey() == key)
                || (!"clear".equals(action) && config.clearKey() == key)
                || (!"close".equals(action) && config.closeKey() == key);
    }

    private String keyName(int key) {
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) return Character.toString((char) ('A' + key - GLFW.GLFW_KEY_A));
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) return Character.toString((char) ('0' + key - GLFW.GLFW_KEY_0));
        return switch (key) { case GLFW.GLFW_KEY_SPACE -> "SPACE"; case GLFW.GLFW_KEY_ENTER -> "ENTER"; case GLFW.GLFW_KEY_DELETE -> "DELETE"; case GLFW.GLFW_KEY_ESCAPE -> "ESCAPE"; case GLFW.GLFW_KEY_BACKSPACE -> "BACKSPACE"; case GLFW.GLFW_KEY_F1 -> "F1"; case GLFW.GLFW_KEY_F2 -> "F2"; case GLFW.GLFW_KEY_F3 -> "F3"; case GLFW.GLFW_KEY_F4 -> "F4"; case GLFW.GLFW_KEY_F5 -> "F5"; case GLFW.GLFW_KEY_F6 -> "F6"; case GLFW.GLFW_KEY_F7 -> "F7"; case GLFW.GLFW_KEY_F8 -> "F8"; case GLFW.GLFW_KEY_F9 -> "F9"; case GLFW.GLFW_KEY_F10 -> "F10"; case GLFW.GLFW_KEY_F11 -> "F11"; case GLFW.GLFW_KEY_F12 -> "F12"; default -> "KEY " + key; };
    }

    public void requestCloseAnimated() {
        if (!closing) {
            closing = true;
            closeStartedAt = System.currentTimeMillis();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (clearHistoryPending && System.currentTimeMillis() - clearHistoryStartedAt >= 220) {
            history.clear(); historyScroll = 0; clearHistoryPending = false; saveHistory();
        }
        if (closing && (!animationsEnabled || System.currentTimeMillis() - closeStartedAt >= 140)) {
            Minecraft.getInstance().setScreen(null);
        }
    }

    @Override public void onClose() { saveConfig(); saveHistory(); super.onClose(); }
    @Override public boolean isPauseScreen() { return false; }
}

package dev.minecalc.common.history;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Small dependency-free local store. Each line contains epoch millis and
 * Base64-encoded UTF-8 expression/result fields.
 */
public final class HistoryStore {
    private HistoryStore() {}

    public static List<CalculationEntry> load(Path file) throws IOException {
        List<CalculationEntry> result = new ArrayList<>();
        if (!Files.exists(file)) return result;

        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (result.size() >= HistoryManager.MAX_ENTRIES) break;
            String[] parts = line.split("\\t", -1);
            if (parts.length != 3) continue;
            try {
                long millis = Long.parseLong(parts[0]);
                String expression = decode(parts[1]);
                String value = decode(parts[2]);
                result.add(new CalculationEntry(expression, value, Instant.ofEpochMilli(millis)));
            } catch (RuntimeException ignored) {
                // Ignore a damaged line; preserve the remaining history.
            }
        }
        return result;
    }

    public static void save(Path file, List<CalculationEntry> entries) throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);

        List<String> lines = new ArrayList<>();
        for (CalculationEntry entry : entries.stream().limit(HistoryManager.MAX_ENTRIES).toList()) {
            lines.add(entry.createdAt().toEpochMilli() + "\t"
                    + encode(entry.expression()) + "\t" + encode(entry.result()));
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}

package dev.minecalc.common.history;

import java.util.ArrayList;
import java.util.List;

/** Keeps the newest ten calculations in memory. Persistence is handled by HistoryStore. */
public final class HistoryManager {
    public static final int MAX_ENTRIES = 10;
    private final List<CalculationEntry> entries = new ArrayList<>();

    public synchronized void add(CalculationEntry entry) {
        entries.add(0, entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
    }

    public synchronized List<CalculationEntry> getEntries() {
        return List.copyOf(entries);
    }

    public synchronized void replaceAll(List<CalculationEntry> loaded) {
        entries.clear();
        if (loaded == null) return;
        for (CalculationEntry entry : loaded) {
            if (entry != null && entries.size() < MAX_ENTRIES) entries.add(entry);
        }
    }

    public synchronized void clear() {
        entries.clear();
    }
}

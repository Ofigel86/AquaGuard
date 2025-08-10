package dev.aquaguard.freeze;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FreezeManager {
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    public void set(UUID id, boolean on) { if (on) frozen.add(id); else frozen.remove(id); }
    public boolean is(UUID id) { return frozen.contains(id); }
}

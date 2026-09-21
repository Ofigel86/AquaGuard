package dev.aquaguard.webhook;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.core.FlagRecord;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DiscordWebhook {
    private final AquaGuard plugin;
    private final Map<String, Long> lastSent = new ConcurrentHashMap<>();

    public DiscordWebhook(AquaGuard plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("webhook.enabled", false)
                && url() != null && url().startsWith("http");
    }

    public String url() {
        return plugin.getConfig().getString("webhook.url", "");
    }

    public void maybeSend(FlagRecord record) {
        if (!enabled()) return;
        double min = plugin.getConfig().getDouble("webhook.min-vl", 4);
        if (record.vl() < min && record.total() < min) return;
        long cooldown = plugin.getConfig().getLong("webhook.cooldown-ms", 2500);
        String key = record.uuid() + ":" + record.check();
        long now = System.currentTimeMillis();
        Long prev = lastSent.get(key);
        if (prev != null && now - prev < cooldown) return;
        lastSent.put(key, now);
        String json = payload(record);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> post(json));
    }

    public void test(UUID actor) {
        if (!enabled()) return;
        String json = "{"
                + "\"username\":\"AquaGuard\","
                + "\"embeds\":[{\"title\":\"Webhook test\",\"description\":\"AquaGuard online\",\"color\":5025616}]"
                + "}";
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> post(json));
    }

    private String payload(FlagRecord record) {
        int color = plugin.getConfig().getInt("webhook.color", 16744272);
        return "{"
                + "\"username\":\"AquaGuard\","
                + "\"embeds\":[{"
                + "\"title\":\"" + esc(record.check()) + "\","
                + "\"description\":\"" + esc(record.name()) + " flagged\","
                + "\"color\":" + color + ","
                + "\"fields\":["
                + field("VL", String.format(java.util.Locale.US, "%.1f", record.vl()), true)
                + "," + field("Total", String.format(java.util.Locale.US, "%.1f", record.total()), true)
                + "," + field("Ping", Integer.toString(record.ping()), true)
                + "," + field("World", record.world(), true)
                + "," + field("Debug", record.debug(), false)
                + "]}]}";
    }

    private static String field(String name, String value, boolean inline) {
        return "{\"name\":\"" + esc(name) + "\",\"value\":\"" + esc(value) + "\",\"inline\":" + inline + "}";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }

    private void post(String json) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url()).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "AquaGuard");
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(body.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }
            int code = conn.getResponseCode();
            if (code >= 300) plugin.getLogger().warning("Webhook HTTP " + code);
        } catch (Exception ex) {
            plugin.getLogger().warning("Webhook: " + ex.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}

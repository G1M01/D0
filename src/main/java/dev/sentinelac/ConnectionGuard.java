package dev.sentinelac;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ConnectionGuard implements Listener {
    private final SentinelACPlugin plugin;
    private final Object lock = new Object();
    private final Map<String, IpState> addresses = new HashMap<String, IpState>();
    private volatile Settings settings;
    private int activeConnections;
    private int pendingConnections;
    private long globalWindowStart;
    private int globalBurstCount;

    public ConnectionGuard(SentinelACPlugin plugin) {
        this.plugin = plugin;
        reloadSettings();
    }

    public void reloadSettings() {
        settings = new Settings(plugin);
    }

    public int getConnectionCount() {
        synchronized (lock) {
            return activeConnections + pendingConnections;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        Settings current = settings;
        if (!current.enabled) {
            return;
        }
        String name = event.getName();
        if (current.validateHandshake && (name == null || name.trim().isEmpty() || name.length() > 16)) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "Invalid login name.");
            return;
        }
        InetAddress address = event.getAddress();
        if (address == null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "Unable to validate connection.");
            return;
        }

        String ip = address.getHostAddress();
        long now = System.currentTimeMillis();
        String denial = null;
        synchronized (lock) {
            cleanup(now, current);
            if (activeConnections + pendingConnections >= current.maxGlobal) {
                denial = "Server is full.";
            } else {
                IpState state = addresses.get(ip);
                if (state == null) {
                    if (addresses.size() >= current.maxTrackedIps) {
                        denial = "Connection limit reached.";
                    } else {
                        state = new IpState(now);
                        addresses.put(ip, state);
                    }
                }
                if (denial == null) {
                    updateWindow(state, now, current.windowMillis);
                    updateGlobalWindow(now, current.windowMillis);
                    if (state.active + state.pending >= current.maxPerIp) {
                        denial = "Too many connections from your address.";
                    } else if (state.burstCount >= current.maxPerIpBurst
                            || globalBurstCount >= current.maxGlobalBurst) {
                        denial = "Connection rate limit exceeded.";
                    } else {
                        state.burstCount++;
                        state.lastSeen = now;
                        state.pendingExpiries.add(now + current.reservationMillis);
                        state.pending++;
                        pendingConnections++;
                        globalBurstCount++;
                    }
                }
            }
        }
        if (denial != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, denial);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        if (!settings.enabled || event.getAddress() == null) {
            return;
        }
        String ip = event.getAddress().getHostAddress();
        synchronized (lock) {
            IpState state = addresses.get(ip);
            if (state == null || !removeReservation(state)) {
                return;
            }
            state.lastSeen = System.currentTimeMillis();
            if (event.getResult() == PlayerLoginEvent.Result.ALLOWED) {
                state.active++;
                activeConnections++;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!settings.enabled || event.getPlayer().getAddress() == null) {
            return;
        }
        String ip = event.getPlayer().getAddress().getAddress().getHostAddress();
        synchronized (lock) {
            IpState state = addresses.get(ip);
            if (state != null && state.active > 0) {
                state.active--;
                activeConnections--;
                state.lastSeen = System.currentTimeMillis();
            }
        }
    }

    private void cleanup(long now, Settings current) {
        Iterator<Map.Entry<String, IpState>> iterator = addresses.entrySet().iterator();
        while (iterator.hasNext()) {
            IpState state = iterator.next().getValue();
            Iterator<Long> expiries = state.pendingExpiries.iterator();
            while (expiries.hasNext()) {
                if (expiries.next().longValue() <= now) {
                    expiries.remove();
                    state.pending--;
                    pendingConnections--;
                }
            }
            if (state.pending == 0 && state.active == 0
                    && now - state.lastSeen > Math.max(current.reservationMillis, current.windowMillis)) {
                iterator.remove();
            }
        }
    }

    private static void updateWindow(IpState state, long now, long duration) {
        if (now - state.windowStart >= duration) {
            state.windowStart = now;
            state.burstCount = 0;
        }
    }

    private void updateGlobalWindow(long now, long duration) {
        if (now - globalWindowStart >= duration) {
            globalWindowStart = now;
            globalBurstCount = 0;
        }
    }

    private static boolean removeReservation(IpState state) {
        if (state.pendingExpiries.isEmpty()) {
            return false;
        }
        state.pendingExpiries.remove(0);
        state.pending--;
        return true;
    }

    private static final class IpState {
        private final List<Long> pendingExpiries = new ArrayList<Long>();
        private long windowStart;
        private long lastSeen;
        private int active;
        private int pending;
        private int burstCount;

        private IpState(long now) {
            this.windowStart = now;
            this.lastSeen = now;
        }
    }

    private static final class Settings {
        private final boolean enabled;
        private final boolean validateHandshake;
        private final int maxGlobal;
        private final int maxPerIp;
        private final int maxTrackedIps;
        private final int maxPerIpBurst;
        private final int maxGlobalBurst;
        private final long windowMillis;
        private final long reservationMillis;

        private Settings(SentinelACPlugin plugin) {
            enabled = plugin.getConfig().getBoolean("modules.connection.enabled", true);
            validateHandshake = plugin.getConfig().getBoolean("modules.connection.handshake_validation", true);
            maxGlobal = Math.max(1, plugin.getConfig().getInt("modules.connection.max_global_connections", 500));
            maxPerIp = Math.max(1, plugin.getConfig().getInt("modules.connection.max_connections_per_ip", 5));
            maxTrackedIps = Math.max(100, plugin.getConfig().getInt("modules.connection.max_tracked_ips", 10000));
            maxPerIpBurst = Math.max(1, plugin.getConfig().getInt("modules.connection.burst.max_per_ip", 8));
            maxGlobalBurst = Math.max(1, plugin.getConfig().getInt("modules.connection.burst.max_global", 60));
            int windowSeconds = Math.max(1, plugin.getConfig().getInt("modules.connection.burst.window_seconds", 10));
            int reservationSeconds = Math.max(5,
                    plugin.getConfig().getInt("modules.connection.reservation_timeout_seconds", 30));
            windowMillis = windowSeconds * 1000L;
            reservationMillis = reservationSeconds * 1000L;
        }
    }
}

package dev.sentinelac;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class EvidenceStore {
    private final SentinelACPlugin plugin;
    private Connection connection;
    private ThreadPoolExecutor writer;

    public EvidenceStore(SentinelACPlugin plugin) {
        this.plugin = plugin;
    }

    public void open() throws Exception {
        Path directory = plugin.getDataFolder().toPath();
        Files.createDirectories(directory);
        String fileName = plugin.getConfig().getString("storage.file", "evidence.db");
        Path database = directory.resolve(fileName).normalize();
        if (!database.startsWith(directory.normalize())) {
            throw new IOException("Storage file must remain inside the plugin data directory.");
        }
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=3000");
            statement.execute("CREATE TABLE IF NOT EXISTS evidence ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid TEXT NOT NULL, "
                    + "player_name TEXT NOT NULL, check_name TEXT NOT NULL, details TEXT NOT NULL, "
                    + "violations REAL NOT NULL, created_at INTEGER NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS admin_actions ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, actor TEXT NOT NULL, action TEXT NOT NULL, "
                    + "target TEXT NOT NULL, details TEXT NOT NULL, created_at INTEGER NOT NULL)");
        }
        int capacity = Math.max(64, plugin.getConfig().getInt("storage.queue_capacity", 4096));
        writer = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(capacity), runnable -> {
                    Thread thread = new Thread(runnable, "SentinelAC-Storage");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public void recordEvidence(final UUID uuid, final String player, final String check,
                               final String details, final double violations) {
        submit(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO evidence(player_uuid, player_name, check_name, details, violations, created_at)"
                            + " VALUES(?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, player);
                statement.setString(3, check);
                statement.setString(4, details);
                statement.setDouble(5, violations);
                statement.setLong(6, System.currentTimeMillis());
                statement.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Could not persist check evidence.", ex);
            }
        });
    }

    public void recordAction(final String actor, final String action, final String target, final String details) {
        submit(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO admin_actions(actor, action, target, details, created_at) VALUES(?, ?, ?, ?, ?)")) {
                statement.setString(1, actor);
                statement.setString(2, action);
                statement.setString(3, target);
                statement.setString(4, details);
                statement.setLong(5, System.currentTimeMillis());
                statement.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Could not persist administrative action.", ex);
            }
        });
    }

    private void submit(Runnable task) {
        if (writer == null || writer.isShutdown()) {
            plugin.getLogger().severe("Evidence write requested while storage is unavailable.");
            return;
        }
        try {
            writer.execute(task);
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            plugin.getLogger().log(Level.SEVERE, "Evidence queue is full; record was not persisted.", ex);
        }
    }

    public void close() throws IOException {
        if (writer != null) {
            writer.shutdown();
            try {
                if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                    writer.shutdownNow();
                    if (!writer.awaitTermination(2, TimeUnit.SECONDS)) {
                        throw new IOException("Storage writer did not stop; database left open to avoid concurrent close.");
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                writer.shutdownNow();
                throw new IOException("Interrupted while stopping storage writer.", ex);
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ex) {
                throw new IOException("Could not close evidence database.", ex);
            }
        }
    }
}

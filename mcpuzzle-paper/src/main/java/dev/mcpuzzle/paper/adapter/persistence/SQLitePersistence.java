package dev.mcpuzzle.paper.adapter.persistence;

import dev.mcpuzzle.core.domain.AbandonReason;
import dev.mcpuzzle.core.domain.Checkpoint;
import dev.mcpuzzle.core.domain.CompletedRun;
import dev.mcpuzzle.core.domain.HintProgress;
import dev.mcpuzzle.core.domain.LeaderboardEntry;
import dev.mcpuzzle.core.domain.LeaderboardQuery;
import dev.mcpuzzle.core.domain.MapVersion;
import dev.mcpuzzle.core.domain.PartyRoster;
import dev.mcpuzzle.core.domain.PuzzleSession;
import dev.mcpuzzle.core.domain.PuzzleSessionSnapshot;
import dev.mcpuzzle.core.domain.RunMetrics;
import dev.mcpuzzle.core.domain.SaveGame;
import dev.mcpuzzle.core.domain.SaveSlot;
import dev.mcpuzzle.core.domain.SessionId;
import dev.mcpuzzle.core.domain.SessionState;
import dev.mcpuzzle.core.domain.StartupRecoveryReport;
import dev.mcpuzzle.core.domain.SuspendReason;
import dev.mcpuzzle.core.port.RunHistoryRepository;
import dev.mcpuzzle.core.port.SaveGameRepository;
import dev.mcpuzzle.core.port.SessionRepository;
import dev.mcpuzzle.core.port.StartupRecoveryRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SQLite persistence boundary for Paper. Every JDBC call is serialized onto one
 * bounded worker; no Bukkit/Paper type is accepted or stored by this adapter.
 */
public final class SQLitePersistence implements
        SessionRepository,
        SaveGameRepository,
        RunHistoryRepository,
        StartupRecoveryRepository,
        AutoCloseable {

    private static final int SCHEMA_VERSION = 1;
    private static final int QUEUE_CAPACITY = 256;
    private static final String SNAPSHOT_COLUMNS = """
            session_id, map_version, state, leader_id, roster_locked,
            current_room, room_count, room_attempt_revision,
            active_play_time, failures, hints_used, active_since,
            last_activity_at, suspend_reason, abandon_reason, captured_at,
            checkpoint_completed_room, checkpoint_next_room, checkpoint_saved_at
            """;

    private final ThreadPoolExecutor executor;
    private final Object lifecycleLock = new Object();
    private final AtomicReference<Thread> workerThread = new AtomicReference<>();
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private CompletableFuture<Void> closeFuture;
    private Connection connection;

    private SQLitePersistence() {
        this.executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                // One physical slot is reserved so deterministic close can always
                // be queued behind every accepted operation.
                new ArrayBlockingQueue<>(QUEUE_CAPACITY + 1),
                runnable -> {
                    Thread thread = new Thread(runnable, "mcpuzzle-sqlite");
                    thread.setDaemon(false);
                    workerThread.set(thread);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    /** Opens and migrates the database on the dedicated database worker. */
    public static CompletionStage<SQLitePersistence> open(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        SQLitePersistence persistence = new SQLitePersistence();
        CompletableFuture<SQLitePersistence> result = new CompletableFuture<>();
        persistence.executor.execute(() -> {
            try {
                persistence.initialize(databasePath);
                result.complete(persistence);
            } catch (Throwable failure) {
                persistence.closeConnectionQuietly();
                persistence.accepting.set(false);
                persistence.executor.shutdown();
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    @Override
    public CompletionStage<Void> save(PuzzleSessionSnapshot session) {
        Objects.requireNonNull(session, "session");
        PuzzleSession.rehydrate(session);
        return submit(() -> {
            inTransaction(() -> {
                writeSession(session);
                return null;
            });
            return null;
        });
    }

    @Override
    public CompletionStage<Optional<PuzzleSessionSnapshot>> findById(SessionId sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        return submit(() -> Optional.ofNullable(readSession(sessionId)));
    }

    @Override
    public CompletionStage<Collection<PuzzleSessionSnapshot>> findByMember(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return submit(() -> {
            List<SessionId> ids = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT session_id FROM session_roster
                    WHERE player_id = ? ORDER BY session_id
                    """)) {
                statement.setString(1, playerId.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        ids.add(sessionId(resultSet.getString(1)));
                    }
                }
            }
            List<PuzzleSessionSnapshot> snapshots = new ArrayList<>(ids.size());
            for (SessionId id : ids) {
                PuzzleSessionSnapshot snapshot = readSession(id);
                if (snapshot != null) {
                    snapshots.add(snapshot);
                }
            }
            return List.copyOf(snapshots);
        });
    }

    @Override
    public CompletionStage<Collection<PuzzleSessionSnapshot>> findUnfinished() {
        return submit(() -> {
            List<SessionId> ids = querySessionIds("""
                    SELECT session_id FROM session_snapshots
                    WHERE state NOT IN ('COMPLETED', 'ABANDONED', 'CLEANUP')
                    ORDER BY captured_at, session_id
                    """);
            List<PuzzleSessionSnapshot> result = new ArrayList<>(ids.size());
            for (SessionId id : ids) {
                PuzzleSessionSnapshot snapshot = readSession(id);
                if (snapshot != null) {
                    result.add(snapshot);
                }
            }
            return List.copyOf(result);
        });
    }

    @Override
    public CompletionStage<Void> delete(SessionId sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        return submit(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM session_snapshots WHERE session_id = ?")) {
                statement.setString(1, sessionId.toString());
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public CompletionStage<Void> upsert(SaveGame saveGame) {
        Objects.requireNonNull(saveGame, "saveGame");
        return submit(() -> {
            inTransaction(() -> {
                writeSave(saveGame);
                return null;
            });
            return null;
        });
    }

    @Override
    public CompletionStage<Optional<SaveGame>> find(
            UUID ownerId,
            String mazeId,
            int slotNumber,
            Instant now
    ) {
        validateSaveKey(ownerId, mazeId, slotNumber);
        Objects.requireNonNull(now, "now");
        return submit(() -> Optional.ofNullable(readSave(ownerId, mazeId.trim(), slotNumber, now)));
    }

    @Override
    public CompletionStage<List<SaveGame>> listVisible(UUID ownerId, String mazeId, Instant now) {
        Objects.requireNonNull(ownerId, "ownerId");
        String normalizedMazeId = requireText(mazeId, "mazeId");
        Objects.requireNonNull(now, "now");
        return submit(() -> {
            List<Integer> slots = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT slot_number FROM save_games
                    WHERE owner_id = ? AND maze_id = ?
                      AND (expires_epoch_second > ?
                           OR (expires_epoch_second = ? AND expires_nano > ?))
                    ORDER BY slot_number
                    """)) {
                statement.setString(1, ownerId.toString());
                statement.setString(2, normalizedMazeId);
                bindInstantBoundary(statement, 3, now);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        slots.add(resultSet.getInt(1));
                    }
                }
            }
            List<SaveGame> saves = new ArrayList<>(slots.size());
            for (int slot : slots) {
                SaveGame save = readSave(ownerId, normalizedMazeId, slot, now);
                if (save != null) {
                    saves.add(save);
                }
            }
            return List.copyOf(saves);
        });
    }

    /** Lists saves visible to either their current owner or their immutable saved party leader. */
    public CompletionStage<List<SaveGame>> listVisibleToPrincipal(UUID principalId, String mazeId, Instant now) {
        Objects.requireNonNull(principalId, "principalId");
        String normalizedMazeId = requireText(mazeId, "mazeId");
        Objects.requireNonNull(now, "now");
        return submit(() -> {
            List<OwnedSlot> slots = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT owner_id, slot_number FROM save_games
                    WHERE maze_id = ? AND (owner_id = ? OR leader_id = ?)
                      AND (expires_epoch_second > ?
                           OR (expires_epoch_second = ? AND expires_nano > ?))
                    ORDER BY slot_number, owner_id
                    """)) {
                statement.setString(1, normalizedMazeId);
                statement.setString(2, principalId.toString());
                statement.setString(3, principalId.toString());
                bindInstantBoundary(statement, 4, now);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        slots.add(new OwnedSlot(UUID.fromString(resultSet.getString(1)), resultSet.getInt(2)));
                    }
                }
            }
            List<SaveGame> saves = new ArrayList<>(slots.size());
            for (OwnedSlot slot : slots) {
                SaveGame save = readSave(slot.ownerId(), normalizedMazeId, slot.slotNumber(), now);
                if (save != null) saves.add(save);
            }
            return List.copyOf(saves);
        });
    }

    @Override
    public CompletionStage<Boolean> delete(UUID ownerId, String mazeId, int slotNumber) {
        validateSaveKey(ownerId, mazeId, slotNumber);
        String normalizedMazeId = mazeId.trim();
        return submit(() -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM save_games
                    WHERE owner_id = ? AND maze_id = ? AND slot_number = ?
                    """)) {
                statement.setString(1, ownerId.toString());
                statement.setString(2, normalizedMazeId);
                statement.setInt(3, slotNumber);
                return statement.executeUpdate() == 1;
            }
        });
    }

    /**
     * Performs authorization and deletion in one SQL statement. This prevents a
     * save transfer between an asynchronous permission read and the delete.
     */
    public CompletionStage<Boolean> deleteAuthorized(
            UUID ownerId,
            String mazeId,
            int slotNumber,
            UUID actorId,
            boolean operator
    ) {
        validateSaveKey(ownerId, mazeId, slotNumber);
        Objects.requireNonNull(actorId, "actorId");
        String normalizedMazeId = mazeId.trim();
        return submit(() -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM save_games
                    WHERE owner_id = ? AND maze_id = ? AND slot_number = ?
                      AND (? = 1 OR owner_id = ? OR leader_id = ?)
                    """)) {
                statement.setString(1, ownerId.toString());
                statement.setString(2, normalizedMazeId);
                statement.setInt(3, slotNumber);
                statement.setInt(4, operator ? 1 : 0);
                statement.setString(5, actorId.toString());
                statement.setString(6, actorId.toString());
                return statement.executeUpdate() == 1;
            }
        });
    }

    @Override
    public CompletionStage<Boolean> transferOwnership(
            UUID currentOwnerId,
            String mazeId,
            int slotNumber,
            UUID newOwnerId
    ) {
        validateSaveKey(currentOwnerId, mazeId, slotNumber);
        Objects.requireNonNull(newOwnerId, "newOwnerId");
        String normalizedMazeId = mazeId.trim();
        return submit(() -> inTransaction(() -> {
            SaveGame existing = readSaveWithoutExpiry(currentOwnerId, normalizedMazeId, slotNumber);
            if (existing == null) {
                return false;
            }
            existing.transferOwnership(newOwnerId); // validates original-roster membership
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE save_games SET owner_id = ?
                    WHERE owner_id = ? AND maze_id = ? AND slot_number = ?
                    """)) {
                statement.setString(1, newOwnerId.toString());
                statement.setString(2, currentOwnerId.toString());
                statement.setString(3, normalizedMazeId);
                statement.setInt(4, slotNumber);
                return statement.executeUpdate() == 1;
            }
        }));
    }

    @Override
    public CompletionStage<Integer> purgeExpired(Instant now) {
        Objects.requireNonNull(now, "now");
        return submit(() -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM save_games
                    WHERE expires_epoch_second < ?
                       OR (expires_epoch_second = ? AND expires_nano <= ?)
                    """)) {
                statement.setLong(1, now.getEpochSecond());
                statement.setLong(2, now.getEpochSecond());
                statement.setInt(3, now.getNano());
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public CompletionStage<Void> record(CompletedRun run) {
        Objects.requireNonNull(run, "run");
        return submit(() -> {
            inTransaction(() -> {
                writeCompletedRun(run);
                return null;
            });
            return null;
        });
    }

    @Override
    public CompletionStage<Optional<CompletedRun>> find(SessionId runId) {
        Objects.requireNonNull(runId, "runId");
        return submit(() -> Optional.ofNullable(readCompletedRun(runId)));
    }

    @Override
    public CompletionStage<List<LeaderboardEntry>> leaderboard(LeaderboardQuery query) {
        Objects.requireNonNull(query, "query");
        return submit(() -> {
            List<SessionId> ids = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT run_id FROM completed_runs
                    WHERE maze_id = ? AND map_version = ? AND party_size = ?
                    ORDER BY active_seconds ASC, active_nano ASC,
                             completed_epoch_second ASC, completed_nano ASC, run_id ASC
                    LIMIT ?
                    """)) {
                statement.setString(1, query.mazeId());
                statement.setString(2, query.mapVersion().value());
                statement.setInt(3, query.partySize());
                statement.setInt(4, query.limit());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        ids.add(sessionId(resultSet.getString(1)));
                    }
                }
            }
            List<LeaderboardEntry> entries = new ArrayList<>(ids.size());
            for (int index = 0; index < ids.size(); index++) {
                CompletedRun run = readCompletedRun(ids.get(index));
                if (run != null) {
                    entries.add(new LeaderboardEntry(index + 1, run));
                }
            }
            return List.copyOf(entries);
        });
    }

    @Override
    public CompletionStage<StartupRecoveryReport> recoverAfterRestart() {
        return submit(() -> inTransaction(() -> {
            List<SessionId> transientAdmissions = querySessionIds("""
                    SELECT session_id FROM session_snapshots
                    WHERE state IN ('WAITING', 'QUEUED') ORDER BY session_id
                    """);
            List<SessionId> interruptedRuns = querySessionIds("""
                    SELECT session_id FROM session_snapshots
                    WHERE state IN ('PROVISIONING', 'ACTIVE') ORDER BY session_id
                    """);
            List<SessionId> suspended = querySessionIds("""
                    SELECT session_id FROM session_snapshots
                    WHERE state = 'SUSPENDED' ORDER BY session_id
                    """);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        DELETE FROM session_snapshots
                        WHERE state IN ('WAITING', 'QUEUED', 'PROVISIONING', 'ACTIVE')
                        """);
            }
            return new StartupRecoveryReport(transientAdmissions, interruptedRuns, suspended);
        }));
    }

    /** Completes after all previously queued operations and the JDBC close. */
    public CompletionStage<Void> closeAsync() {
        synchronized (lifecycleLock) {
            if (closeFuture != null) {
                return closeFuture;
            }
            accepting.set(false);
            closeFuture = new CompletableFuture<>();
            CompletableFuture<Void> result = closeFuture;
            try {
                executor.execute(() -> {
                    try {
                        if (connection != null) {
                            connection.close();
                            connection = null;
                        }
                        result.complete(null);
                    } catch (Throwable failure) {
                        result.completeExceptionally(failure);
                    } finally {
                        executor.shutdown();
                    }
                });
            } catch (RejectedExecutionException failure) {
                executor.shutdown();
                result.completeExceptionally(failure);
            }
            return result;
        }
    }

    @Override
    public void close() {
        if (Thread.currentThread() == workerThread.get()) {
            throw new IllegalStateException("close() cannot block the database worker; use closeAsync()");
        }
        closeAsync().toCompletableFuture().join();
    }

    private void initialize(Path databasePath) throws Exception {
        Path absolute = databasePath.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + absolute);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
        }
        migrate();
    }

    private void migrate() throws SQLException {
        int version;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {
            version = resultSet.next() ? resultSet.getInt(1) : 0;
        }
        if (version > SCHEMA_VERSION) {
            throw new SQLException("Database schema " + version + " is newer than supported " + SCHEMA_VERSION);
        }
        if (version == 0) {
            inTransaction(() -> {
                applyMigrationOne();
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA user_version = 1");
                }
                return null;
            });
        }
    }

    private void applyMigrationOne() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE session_snapshots (
                        session_id TEXT PRIMARY KEY,
                        maze_id TEXT NOT NULL,
                        map_version TEXT NOT NULL,
                        state TEXT NOT NULL CHECK (state IN
                          ('WAITING','QUEUED','PROVISIONING','ACTIVE','SUSPENDED','COMPLETED','ABANDONED','CLEANUP')),
                        leader_id TEXT NOT NULL,
                        roster_locked INTEGER NOT NULL CHECK (roster_locked IN (0,1)),
                        current_room INTEGER NOT NULL CHECK (current_room >= 1),
                        room_count INTEGER NOT NULL CHECK (room_count >= 1),
                        room_attempt_revision INTEGER NOT NULL CHECK (room_attempt_revision >= 0),
                        active_play_time TEXT NOT NULL,
                        failures INTEGER NOT NULL CHECK (failures >= 0),
                        hints_used INTEGER NOT NULL CHECK (hints_used >= 0),
                        active_since TEXT,
                        last_activity_at TEXT,
                        suspend_reason TEXT,
                        abandon_reason TEXT,
                        captured_at TEXT NOT NULL,
                        checkpoint_completed_room INTEGER,
                        checkpoint_next_room INTEGER,
                        checkpoint_saved_at TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE session_roster (
                        session_id TEXT NOT NULL,
                        position INTEGER NOT NULL CHECK (position BETWEEN 0 AND 3),
                        player_id TEXT NOT NULL,
                        PRIMARY KEY (session_id, position),
                        UNIQUE (session_id, player_id),
                        FOREIGN KEY (session_id) REFERENCES session_snapshots(session_id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE session_hints (
                        session_id TEXT NOT NULL,
                        room INTEGER NOT NULL CHECK (room >= 1),
                        tier INTEGER NOT NULL CHECK (tier BETWEEN 1 AND 3),
                        PRIMARY KEY (session_id, room, tier),
                        FOREIGN KEY (session_id) REFERENCES session_snapshots(session_id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("CREATE INDEX session_roster_player_idx ON session_roster(player_id)");
            statement.execute("CREATE INDEX session_state_idx ON session_snapshots(state)");

            statement.execute("""
                    CREATE TABLE save_games (
                        owner_id TEXT NOT NULL,
                        maze_id TEXT NOT NULL,
                        slot_number INTEGER NOT NULL CHECK (slot_number BETWEEN 1 AND 3),
                        expires_epoch_second INTEGER NOT NULL,
                        expires_nano INTEGER NOT NULL CHECK (expires_nano BETWEEN 0 AND 999999999),
                        session_id TEXT NOT NULL,
                        map_version TEXT NOT NULL,
                        state TEXT NOT NULL CHECK (state = 'SUSPENDED'),
                        leader_id TEXT NOT NULL,
                        roster_locked INTEGER NOT NULL CHECK (roster_locked = 1),
                        current_room INTEGER NOT NULL CHECK (current_room >= 1),
                        room_count INTEGER NOT NULL CHECK (room_count >= 1),
                        room_attempt_revision INTEGER NOT NULL CHECK (room_attempt_revision >= 0),
                        active_play_time TEXT NOT NULL,
                        failures INTEGER NOT NULL CHECK (failures >= 0),
                        hints_used INTEGER NOT NULL CHECK (hints_used >= 0),
                        active_since TEXT,
                        last_activity_at TEXT,
                        suspend_reason TEXT NOT NULL,
                        abandon_reason TEXT,
                        captured_at TEXT NOT NULL,
                        checkpoint_completed_room INTEGER NOT NULL,
                        checkpoint_next_room INTEGER NOT NULL,
                        checkpoint_saved_at TEXT NOT NULL,
                        PRIMARY KEY (owner_id, maze_id, slot_number)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE save_roster (
                        owner_id TEXT NOT NULL,
                        maze_id TEXT NOT NULL,
                        slot_number INTEGER NOT NULL,
                        position INTEGER NOT NULL CHECK (position BETWEEN 0 AND 3),
                        player_id TEXT NOT NULL,
                        PRIMARY KEY (owner_id, maze_id, slot_number, position),
                        UNIQUE (owner_id, maze_id, slot_number, player_id),
                        FOREIGN KEY (owner_id, maze_id, slot_number)
                          REFERENCES save_games(owner_id, maze_id, slot_number)
                          ON DELETE CASCADE ON UPDATE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE save_hints (
                        owner_id TEXT NOT NULL,
                        maze_id TEXT NOT NULL,
                        slot_number INTEGER NOT NULL,
                        room INTEGER NOT NULL CHECK (room >= 1),
                        tier INTEGER NOT NULL CHECK (tier BETWEEN 1 AND 3),
                        PRIMARY KEY (owner_id, maze_id, slot_number, room, tier),
                        FOREIGN KEY (owner_id, maze_id, slot_number)
                          REFERENCES save_games(owner_id, maze_id, slot_number)
                          ON DELETE CASCADE ON UPDATE CASCADE
                    )
                    """);
            statement.execute("CREATE INDEX save_expiry_idx ON save_games(expires_epoch_second, expires_nano)");

            statement.execute("""
                    CREATE TABLE completed_runs (
                        run_id TEXT PRIMARY KEY,
                        maze_id TEXT NOT NULL,
                        map_version TEXT NOT NULL,
                        leader_id TEXT NOT NULL,
                        party_size INTEGER NOT NULL CHECK (party_size BETWEEN 1 AND 4),
                        active_seconds INTEGER NOT NULL CHECK (active_seconds >= 0),
                        active_nano INTEGER NOT NULL CHECK (active_nano BETWEEN 0 AND 999999999),
                        failures INTEGER NOT NULL CHECK (failures >= 0),
                        hints_used INTEGER NOT NULL CHECK (hints_used >= 0),
                        completed_epoch_second INTEGER NOT NULL,
                        completed_nano INTEGER NOT NULL CHECK (completed_nano BETWEEN 0 AND 999999999)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE completed_run_roster (
                        run_id TEXT NOT NULL,
                        position INTEGER NOT NULL CHECK (position BETWEEN 0 AND 3),
                        player_id TEXT NOT NULL,
                        PRIMARY KEY (run_id, position),
                        UNIQUE (run_id, player_id),
                        FOREIGN KEY (run_id) REFERENCES completed_runs(run_id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE INDEX leaderboard_idx ON completed_runs(
                        maze_id, map_version, party_size,
                        active_seconds, active_nano,
                        completed_epoch_second, completed_nano, run_id
                    )
                    """);
        }
    }

    private void writeSession(PuzzleSessionSnapshot snapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO session_snapshots (
                    session_id, maze_id, map_version, state, leader_id, roster_locked,
                    current_room, room_count, room_attempt_revision, active_play_time,
                    failures, hints_used, active_since, last_activity_at, suspend_reason,
                    abandon_reason, captured_at, checkpoint_completed_room,
                    checkpoint_next_room, checkpoint_saved_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(session_id) DO UPDATE SET
                    maze_id=excluded.maze_id, map_version=excluded.map_version,
                    state=excluded.state, leader_id=excluded.leader_id,
                    roster_locked=excluded.roster_locked, current_room=excluded.current_room,
                    room_count=excluded.room_count, room_attempt_revision=excluded.room_attempt_revision,
                    active_play_time=excluded.active_play_time, failures=excluded.failures,
                    hints_used=excluded.hints_used, active_since=excluded.active_since,
                    last_activity_at=excluded.last_activity_at, suspend_reason=excluded.suspend_reason,
                    abandon_reason=excluded.abandon_reason, captured_at=excluded.captured_at,
                    checkpoint_completed_room=excluded.checkpoint_completed_room,
                    checkpoint_next_room=excluded.checkpoint_next_room,
                    checkpoint_saved_at=excluded.checkpoint_saved_at
                """)) {
            statement.setString(1, snapshot.id().toString());
            statement.setString(2, snapshot.mazeId());
            bindSnapshot(statement, 3, snapshot);
            statement.executeUpdate();
        }
        replaceSessionChildren(snapshot);
    }

    private PuzzleSessionSnapshot readSession(SessionId id) throws SQLException {
        SnapshotColumns columns;
        String mazeId;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT maze_id, " + SNAPSHOT_COLUMNS + " FROM session_snapshots WHERE session_id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                mazeId = resultSet.getString("maze_id");
                columns = readSnapshotColumns(resultSet);
            }
        }
        PartyRoster roster = loadSessionRoster(id, columns.leaderId());
        HintProgress hints = loadSessionHints(id);
        return columns.toSnapshot(mazeId, roster, hints);
    }

    private void replaceSessionChildren(PuzzleSessionSnapshot snapshot) throws SQLException {
        try (PreparedStatement deleteRoster = connection.prepareStatement(
                "DELETE FROM session_roster WHERE session_id = ?");
             PreparedStatement deleteHints = connection.prepareStatement(
                     "DELETE FROM session_hints WHERE session_id = ?")) {
            deleteRoster.setString(1, snapshot.id().toString());
            deleteRoster.executeUpdate();
            deleteHints.setString(1, snapshot.id().toString());
            deleteHints.executeUpdate();
        }
        try (PreparedStatement roster = connection.prepareStatement("""
                INSERT INTO session_roster(session_id, position, player_id) VALUES (?,?,?)
                """)) {
            bindRosterRows(roster, snapshot.id().toString(), snapshot.roster().members());
        }
        try (PreparedStatement hints = connection.prepareStatement("""
                INSERT INTO session_hints(session_id, room, tier) VALUES (?,?,?)
                """)) {
            bindHintRows(hints, snapshot.id().toString(), snapshot.hintProgress());
        }
    }

    private void writeSave(SaveGame saveGame) throws SQLException {
        SaveSlot slot = saveGame.slot();
        PuzzleSessionSnapshot snapshot = saveGame.snapshot();
        Instant expiresAt = slot.expiresAt();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO save_games (
                    owner_id, maze_id, slot_number, expires_epoch_second, expires_nano,
                    session_id, map_version, state, leader_id, roster_locked,
                    current_room, room_count, room_attempt_revision, active_play_time,
                    failures, hints_used, active_since, last_activity_at, suspend_reason,
                    abandon_reason, captured_at, checkpoint_completed_room,
                    checkpoint_next_room, checkpoint_saved_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(owner_id, maze_id, slot_number) DO UPDATE SET
                    expires_epoch_second=excluded.expires_epoch_second,
                    expires_nano=excluded.expires_nano, session_id=excluded.session_id,
                    map_version=excluded.map_version, state=excluded.state,
                    leader_id=excluded.leader_id, roster_locked=excluded.roster_locked,
                    current_room=excluded.current_room, room_count=excluded.room_count,
                    room_attempt_revision=excluded.room_attempt_revision,
                    active_play_time=excluded.active_play_time, failures=excluded.failures,
                    hints_used=excluded.hints_used, active_since=excluded.active_since,
                    last_activity_at=excluded.last_activity_at, suspend_reason=excluded.suspend_reason,
                    abandon_reason=excluded.abandon_reason, captured_at=excluded.captured_at,
                    checkpoint_completed_room=excluded.checkpoint_completed_room,
                    checkpoint_next_room=excluded.checkpoint_next_room,
                    checkpoint_saved_at=excluded.checkpoint_saved_at
                """)) {
            statement.setString(1, slot.ownerId().toString());
            statement.setString(2, slot.mazeId());
            statement.setInt(3, slot.number());
            statement.setLong(4, expiresAt.getEpochSecond());
            statement.setInt(5, expiresAt.getNano());
            statement.setString(6, snapshot.id().toString());
            bindSnapshot(statement, 7, snapshot);
            statement.executeUpdate();
        }
        replaceSaveChildren(saveGame);
    }

    private SaveGame readSave(UUID ownerId, String mazeId, int slot, Instant now) throws SQLException {
        SaveGame save = readSaveWithoutExpiry(ownerId, mazeId, slot);
        return save == null || save.slot().isExpiredAt(now) ? null : save;
    }

    private SaveGame readSaveWithoutExpiry(UUID ownerId, String mazeId, int slot) throws SQLException {
        SnapshotColumns columns;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + SNAPSHOT_COLUMNS + " FROM save_games "
                        + "WHERE owner_id = ? AND maze_id = ? AND slot_number = ?")) {
            statement.setString(1, ownerId.toString());
            statement.setString(2, mazeId);
            statement.setInt(3, slot);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                columns = readSnapshotColumns(resultSet);
            }
        }
        PartyRoster roster = loadSaveRoster(ownerId, mazeId, slot, columns.leaderId());
        HintProgress hints = loadSaveHints(ownerId, mazeId, slot);
        PuzzleSessionSnapshot snapshot = columns.toSnapshot(mazeId, roster, hints);
        Checkpoint checkpoint = snapshot.checkpoint().orElseThrow();
        SaveSlot metadata = new SaveSlot(
                slot,
                ownerId,
                mazeId,
                snapshot.mapVersion(),
                roster,
                checkpoint,
                snapshot.capturedAt()
        );
        return new SaveGame(metadata, snapshot);
    }

    private void replaceSaveChildren(SaveGame saveGame) throws SQLException {
        SaveSlot slot = saveGame.slot();
        try (PreparedStatement rosterDelete = connection.prepareStatement("""
                DELETE FROM save_roster WHERE owner_id=? AND maze_id=? AND slot_number=?
                """);
             PreparedStatement hintsDelete = connection.prepareStatement("""
                     DELETE FROM save_hints WHERE owner_id=? AND maze_id=? AND slot_number=?
                     """)) {
            bindSaveKey(rosterDelete, slot.ownerId(), slot.mazeId(), slot.number());
            rosterDelete.executeUpdate();
            bindSaveKey(hintsDelete, slot.ownerId(), slot.mazeId(), slot.number());
            hintsDelete.executeUpdate();
        }
        try (PreparedStatement roster = connection.prepareStatement("""
                INSERT INTO save_roster(owner_id,maze_id,slot_number,position,player_id)
                VALUES (?,?,?,?,?)
                """)) {
            for (int position = 0; position < saveGame.snapshot().roster().members().size(); position++) {
                roster.setString(1, slot.ownerId().toString());
                roster.setString(2, slot.mazeId());
                roster.setInt(3, slot.number());
                roster.setInt(4, position);
                roster.setString(5, saveGame.snapshot().roster().members().get(position).toString());
                roster.addBatch();
            }
            roster.executeBatch();
        }
        try (PreparedStatement hints = connection.prepareStatement("""
                INSERT INTO save_hints(owner_id,maze_id,slot_number,room,tier)
                VALUES (?,?,?,?,?)
                """)) {
            for (Map.Entry<Integer, Set<Integer>> entry
                    : saveGame.snapshot().hintProgress().unlockedByRoom().entrySet()) {
                for (int tier : entry.getValue()) {
                    hints.setString(1, slot.ownerId().toString());
                    hints.setString(2, slot.mazeId());
                    hints.setInt(3, slot.number());
                    hints.setInt(4, entry.getKey());
                    hints.setInt(5, tier);
                    hints.addBatch();
                }
            }
            hints.executeBatch();
        }
    }

    private void writeCompletedRun(CompletedRun run) throws SQLException {
        Duration active = run.metrics().activePlayTime();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO completed_runs(
                    run_id, maze_id, map_version, leader_id, party_size,
                    active_seconds, active_nano, failures, hints_used,
                    completed_epoch_second, completed_nano
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(run_id) DO UPDATE SET
                    maze_id=excluded.maze_id, map_version=excluded.map_version,
                    leader_id=excluded.leader_id, party_size=excluded.party_size,
                    active_seconds=excluded.active_seconds, active_nano=excluded.active_nano,
                    failures=excluded.failures, hints_used=excluded.hints_used,
                    completed_epoch_second=excluded.completed_epoch_second,
                    completed_nano=excluded.completed_nano
                """)) {
            statement.setString(1, run.runId().toString());
            statement.setString(2, run.mazeId());
            statement.setString(3, run.mapVersion().value());
            statement.setString(4, run.roster().leaderId().toString());
            statement.setInt(5, run.roster().size());
            statement.setLong(6, active.getSeconds());
            statement.setInt(7, active.getNano());
            statement.setInt(8, run.metrics().failures());
            statement.setInt(9, run.metrics().hintsUsed());
            statement.setLong(10, run.completedAt().getEpochSecond());
            statement.setInt(11, run.completedAt().getNano());
            statement.executeUpdate();
        }
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM completed_run_roster WHERE run_id = ?")) {
            delete.setString(1, run.runId().toString());
            delete.executeUpdate();
        }
        try (PreparedStatement roster = connection.prepareStatement("""
                INSERT INTO completed_run_roster(run_id, position, player_id) VALUES (?,?,?)
                """)) {
            bindRosterRows(roster, run.runId().toString(), run.roster().members());
        }
    }

    private CompletedRun readCompletedRun(SessionId runId) throws SQLException {
        String mazeId;
        MapVersion mapVersion;
        UUID leaderId;
        RunMetrics metrics;
        Instant completedAt;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT maze_id,map_version,leader_id,active_seconds,active_nano,
                       failures,hints_used,completed_epoch_second,completed_nano
                FROM completed_runs WHERE run_id=?
                """)) {
            statement.setString(1, runId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                mazeId = resultSet.getString("maze_id");
                mapVersion = new MapVersion(resultSet.getString("map_version"));
                leaderId = uuid(resultSet.getString("leader_id"));
                metrics = new RunMetrics(
                        Duration.ofSeconds(resultSet.getLong("active_seconds"), resultSet.getInt("active_nano")),
                        resultSet.getInt("failures"),
                        resultSet.getInt("hints_used")
                );
                completedAt = Instant.ofEpochSecond(
                        resultSet.getLong("completed_epoch_second"),
                        resultSet.getInt("completed_nano")
                );
            }
        }
        List<UUID> members = loadRoster("""
                SELECT player_id FROM completed_run_roster
                WHERE run_id=? ORDER BY position
                """, statement -> statement.setString(1, runId.toString()));
        return new CompletedRun(runId, mazeId, mapVersion, new PartyRoster(leaderId, members), metrics, completedAt);
    }

    private void bindSnapshot(PreparedStatement statement, int start, PuzzleSessionSnapshot snapshot)
            throws SQLException {
        int index = start;
        statement.setString(index++, snapshot.mapVersion().value());
        statement.setString(index++, snapshot.state().name());
        statement.setString(index++, snapshot.roster().leaderId().toString());
        statement.setInt(index++, snapshot.rosterLocked() ? 1 : 0);
        statement.setInt(index++, snapshot.currentRoom());
        statement.setInt(index++, snapshot.roomCount());
        statement.setLong(index++, snapshot.roomAttemptRevision());
        statement.setString(index++, snapshot.metrics().activePlayTime().toString());
        statement.setInt(index++, snapshot.metrics().failures());
        statement.setInt(index++, snapshot.metrics().hintsUsed());
        setOptionalInstant(statement, index++, snapshot.activeSince());
        setOptionalInstant(statement, index++, snapshot.lastActivityAt());
        setOptionalEnum(statement, index++, snapshot.lastSuspendReason().map(Enum::name));
        setOptionalEnum(statement, index++, snapshot.abandonReason().map(Enum::name));
        statement.setString(index++, snapshot.capturedAt().toString());
        if (snapshot.checkpoint().isPresent()) {
            Checkpoint checkpoint = snapshot.checkpoint().orElseThrow();
            statement.setInt(index++, checkpoint.completedRoom());
            statement.setInt(index++, checkpoint.nextRoom());
            statement.setString(index, checkpoint.savedAt().toString());
        } else {
            statement.setNull(index++, java.sql.Types.INTEGER);
            statement.setNull(index++, java.sql.Types.INTEGER);
            statement.setNull(index, java.sql.Types.VARCHAR);
        }
    }

    private SnapshotColumns readSnapshotColumns(ResultSet resultSet) throws SQLException {
        Integer checkpointCompleted = nullableInt(resultSet, "checkpoint_completed_room");
        Integer checkpointNext = nullableInt(resultSet, "checkpoint_next_room");
        String checkpointSaved = resultSet.getString("checkpoint_saved_at");
        Optional<Checkpoint> checkpoint = checkpointCompleted == null
                ? Optional.empty()
                : Optional.of(new Checkpoint(
                        checkpointCompleted,
                        Objects.requireNonNull(checkpointNext, "Persisted checkpoint next room"),
                        Instant.parse(Objects.requireNonNull(checkpointSaved, "Persisted checkpoint timestamp"))
                ));
        return new SnapshotColumns(
                sessionId(resultSet.getString("session_id")),
                new MapVersion(resultSet.getString("map_version")),
                SessionState.valueOf(resultSet.getString("state")),
                uuid(resultSet.getString("leader_id")),
                resultSet.getInt("roster_locked") != 0,
                resultSet.getInt("current_room"),
                resultSet.getInt("room_count"),
                resultSet.getLong("room_attempt_revision"),
                new RunMetrics(
                        Duration.parse(resultSet.getString("active_play_time")),
                        resultSet.getInt("failures"),
                        resultSet.getInt("hints_used")
                ),
                optionalInstant(resultSet.getString("active_since")),
                optionalInstant(resultSet.getString("last_activity_at")),
                optionalEnum(resultSet.getString("suspend_reason"), SuspendReason.class),
                optionalEnum(resultSet.getString("abandon_reason"), AbandonReason.class),
                Instant.parse(resultSet.getString("captured_at")),
                checkpoint
        );
    }

    private PartyRoster loadSessionRoster(SessionId id, UUID leaderId) throws SQLException {
        return new PartyRoster(leaderId, loadRoster("""
                SELECT player_id FROM session_roster
                WHERE session_id=? ORDER BY position
                """, statement -> statement.setString(1, id.toString())));
    }

    private HintProgress loadSessionHints(SessionId id) throws SQLException {
        return loadHints("""
                SELECT room,tier FROM session_hints
                WHERE session_id=? ORDER BY room,tier
                """, statement -> statement.setString(1, id.toString()));
    }

    private PartyRoster loadSaveRoster(UUID owner, String maze, int slot, UUID leader) throws SQLException {
        return new PartyRoster(leader, loadRoster("""
                SELECT player_id FROM save_roster
                WHERE owner_id=? AND maze_id=? AND slot_number=? ORDER BY position
                """, statement -> bindSaveKey(statement, owner, maze, slot)));
    }

    private HintProgress loadSaveHints(UUID owner, String maze, int slot) throws SQLException {
        return loadHints("""
                SELECT room,tier FROM save_hints
                WHERE owner_id=? AND maze_id=? AND slot_number=? ORDER BY room,tier
                """, statement -> bindSaveKey(statement, owner, maze, slot));
    }

    private List<UUID> loadRoster(String sql, StatementBinder binder) throws SQLException {
        List<UUID> members = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    members.add(uuid(resultSet.getString(1)));
                }
            }
        }
        return List.copyOf(members);
    }

    private HintProgress loadHints(String sql, StatementBinder binder) throws SQLException {
        Map<Integer, Set<Integer>> hints = new TreeMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    hints.computeIfAbsent(resultSet.getInt(1), ignored -> new LinkedHashSet<>())
                            .add(resultSet.getInt(2));
                }
            }
        }
        return new HintProgress(hints);
    }

    private void bindRosterRows(PreparedStatement statement, String id, List<UUID> members)
            throws SQLException {
        for (int position = 0; position < members.size(); position++) {
            statement.setString(1, id);
            statement.setInt(2, position);
            statement.setString(3, members.get(position).toString());
            statement.addBatch();
        }
        statement.executeBatch();
    }

    private void bindHintRows(PreparedStatement statement, String id, HintProgress progress)
            throws SQLException {
        for (Map.Entry<Integer, Set<Integer>> entry : progress.unlockedByRoom().entrySet()) {
            for (int tier : entry.getValue()) {
                statement.setString(1, id);
                statement.setInt(2, entry.getKey());
                statement.setInt(3, tier);
                statement.addBatch();
            }
        }
        statement.executeBatch();
    }

    private List<SessionId> querySessionIds(String sql) throws SQLException {
        List<SessionId> ids = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                ids.add(sessionId(resultSet.getString(1)));
            }
        }
        return List.copyOf(ids);
    }

    private <T> CompletionStage<T> submit(SqlCallable<T> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        synchronized (lifecycleLock) {
            if (!accepting.get()) {
                result.completeExceptionally(new IllegalStateException("SQLite persistence is closed"));
                return result;
            }
            if (executor.getQueue().size() >= QUEUE_CAPACITY) {
                result.completeExceptionally(new RejectedExecutionException(
                        "SQLite persistence queue is full (capacity " + QUEUE_CAPACITY + ")"));
                return result;
            }
            try {
                executor.execute(() -> {
                    try {
                        result.complete(operation.call());
                    } catch (Throwable failure) {
                        result.completeExceptionally(failure);
                    }
                });
            } catch (RejectedExecutionException failure) {
                result.completeExceptionally(new RejectedExecutionException(
                        "SQLite persistence queue is full (capacity " + QUEUE_CAPACITY + ")", failure));
            }
        }
        return result;
    }

    private <T> T inTransaction(SqlCallable<T> operation) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            T result = operation.call();
            connection.commit();
            return result;
        } catch (Throwable failure) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            if (failure instanceof SQLException sqlFailure) {
                throw sqlFailure;
            }
            if (failure instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            throw new SQLException("Transactional persistence operation failed", failure);
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private void closeConnectionQuietly() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // The opening failure is the primary error.
            }
            connection = null;
        }
    }

    private static void bindSaveKey(PreparedStatement statement, UUID owner, String maze, int slot)
            throws SQLException {
        statement.setString(1, owner.toString());
        statement.setString(2, maze);
        statement.setInt(3, slot);
    }

    private static void bindInstantBoundary(PreparedStatement statement, int index, Instant instant)
            throws SQLException {
        statement.setLong(index, instant.getEpochSecond());
        statement.setLong(index + 1, instant.getEpochSecond());
        statement.setInt(index + 2, instant.getNano());
    }

    private static void setOptionalInstant(
            PreparedStatement statement,
            int index,
            Optional<Instant> value
    ) throws SQLException {
        if (value.isPresent()) {
            statement.setString(index, value.orElseThrow().toString());
        } else {
            statement.setNull(index, java.sql.Types.VARCHAR);
        }
    }

    private static void setOptionalEnum(
            PreparedStatement statement,
            int index,
            Optional<String> value
    ) throws SQLException {
        if (value.isPresent()) {
            statement.setString(index, value.orElseThrow());
        } else {
            statement.setNull(index, java.sql.Types.VARCHAR);
        }
    }

    private static Optional<Instant> optionalInstant(String value) {
        return value == null ? Optional.empty() : Optional.of(Instant.parse(value));
    }

    private static <E extends Enum<E>> Optional<E> optionalEnum(String value, Class<E> type) {
        return value == null ? Optional.empty() : Optional.of(Enum.valueOf(type, value));
    }

    private static Integer nullableInt(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private static SessionId sessionId(String value) {
        return new SessionId(UUID.fromString(value));
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }

    private static void validateSaveKey(UUID ownerId, String mazeId, int slotNumber) {
        Objects.requireNonNull(ownerId, "ownerId");
        requireText(mazeId, "mazeId");
        if (slotNumber < SaveSlot.MIN_NUMBER || slotNumber > SaveSlot.MAX_NUMBER) {
            throw new IllegalArgumentException("Save slot number must be between 1 and 3");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }

    @FunctionalInterface
    private interface SqlCallable<T> {
        T call() throws Exception;
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private record SnapshotColumns(
            SessionId id,
            MapVersion mapVersion,
            SessionState state,
            UUID leaderId,
            boolean rosterLocked,
            int currentRoom,
            int roomCount,
            long roomAttemptRevision,
            RunMetrics metrics,
            Optional<Instant> activeSince,
            Optional<Instant> lastActivityAt,
            Optional<SuspendReason> suspendReason,
            Optional<AbandonReason> abandonReason,
            Instant capturedAt,
            Optional<Checkpoint> checkpoint
    ) {
        private PuzzleSessionSnapshot toSnapshot(
                String mazeId,
                PartyRoster roster,
                HintProgress hints
        ) {
            return new PuzzleSessionSnapshot(
                    id,
                    mazeId,
                    mapVersion,
                    state,
                    roster,
                    rosterLocked,
                    currentRoom,
                    roomCount,
                    roomAttemptRevision,
                    metrics,
                    hints,
                    checkpoint,
                    activeSince,
                    lastActivityAt,
                    suspendReason,
                    abandonReason,
                    capturedAt
            );
        }
    }

    private record OwnedSlot(UUID ownerId, int slotNumber) { }
}

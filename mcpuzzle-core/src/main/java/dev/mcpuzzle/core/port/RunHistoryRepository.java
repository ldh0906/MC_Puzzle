package dev.mcpuzzle.core.port;

import dev.mcpuzzle.core.domain.CompletedRun;
import dev.mcpuzzle.core.domain.LeaderboardEntry;
import dev.mcpuzzle.core.domain.LeaderboardQuery;
import dev.mcpuzzle.core.domain.SessionId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface RunHistoryRepository {
    CompletionStage<Void> record(CompletedRun run);

    CompletionStage<Optional<CompletedRun>> find(SessionId runId);

    CompletionStage<List<LeaderboardEntry>> leaderboard(LeaderboardQuery query);
}

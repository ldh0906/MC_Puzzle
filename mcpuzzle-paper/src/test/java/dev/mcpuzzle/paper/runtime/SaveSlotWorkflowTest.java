package dev.mcpuzzle.paper.runtime;

import dev.mcpuzzle.core.domain.MapVersion;
import dev.mcpuzzle.core.domain.Party;
import dev.mcpuzzle.core.domain.PuzzleSession;
import dev.mcpuzzle.core.domain.SessionId;
import dev.mcpuzzle.paper.adapter.persistence.SQLitePersistence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveSlotWorkflowTest {
    @TempDir Path directory;

    @Test
    void storesListsAndConsumesAnOverwriteBeforeNewAdmission() {
        SQLitePersistence persistence = SQLitePersistence.open(directory.resolve("workflow.db")).toCompletableFuture().join();
        try {
            UUID leader = UUID.randomUUID();
            UUID member = UUID.randomUUID();
            Instant start = Instant.parse("2026-08-05T00:00:00Z");
            Clock clock = Clock.fixed(start.plusSeconds(30), ZoneOffset.UTC);
            MapVersion version = new MapVersion("1.0.0-mvp");
            PuzzleSession session = suspendedAtRoomTwo(leader, member, version, start);
            SaveSlotWorkflow workflow = new SaveSlotWorkflow(persistence, clock, "a-to-z-archive-20", version);

            workflow.store(1, leader, session.party().toRoster(), session).toCompletableFuture().join();
            assertTrue(workflow.find(leader, 1).toCompletableFuture().join().isPresent());
            assertEquals(1, workflow.list(leader).toCompletableFuture().join().size());

            assertTrue(workflow.transfer(leader, 1, member).toCompletableFuture().join());
            assertEquals(1, workflow.list(member).toCompletableFuture().join().size());
            assertEquals(1, workflow.listForPrincipal(leader).toCompletableFuture().join().size());

            PuzzleSession newer = suspendedAtRoomTwo(leader, member, version, start);
            workflow.store(1, leader, newer.party().toRoster(), newer).toCompletableFuture().join();
            assertEquals(2, workflow.listForPrincipal(leader).toCompletableFuture().join().size(),
                    "같은 슬롯 번호라도 현재 소유자가 다르면 리더 목록에서 합치면 안 된다");

            UUID outsider = UUID.randomUUID();
            assertFalse(workflow.deleteAuthorized(member, 1, outsider, false).toCompletableFuture().join(),
                    "명단 외 플레이어는 이전된 저장을 삭제할 수 없다");
            assertTrue(workflow.deleteAuthorized(member, 1, leader, false).toCompletableFuture().join(),
                    "저장 당시 파티장은 현재 소유자가 달라져도 삭제할 수 있다");
            assertFalse(workflow.find(member, 1).toCompletableFuture().join().isPresent());
            assertTrue(workflow.delete(leader, 1).toCompletableFuture().join());
        } finally {
            persistence.close();
        }
    }

    private PuzzleSession suspendedAtRoomTwo(UUID leader, UUID member, MapVersion version, Instant start) {
        PuzzleSession session = PuzzleSession.create(SessionId.random(), "a-to-z-archive-20", version,
                Party.of(leader, java.util.List.of(leader, member)), 5);
        session.queue(leader);
        session.beginProvisioning();
        session.activate(start);
        session.completeCurrentRoom(start.plusSeconds(10));
        session.requestSuspend(leader, start.plusSeconds(20));
        return session;
    }
}

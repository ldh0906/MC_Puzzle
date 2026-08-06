package dev.mcpuzzle.core.application.admission;

import dev.mcpuzzle.core.domain.PartyRoster;
import dev.mcpuzzle.core.domain.SessionId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceAdmissionQueueTest {
    private static final RosterAvailability ALL_READY = ignored -> AvailabilityCheck.eligible();

    @Test
    void defaultsToTwoAndPreservesFifoWhileCancellingUnavailableFront() {
        InstanceAdmissionQueue queue = new InstanceAdmissionQueue();
        AdmissionRequest first = request();
        AdmissionRequest second = request();
        AdmissionRequest unavailable = request();
        AdmissionRequest fourth = request();

        assertEquals(List.of(first), queue.enqueueAndAdmit(first, ALL_READY).batch().admitted());
        assertEquals(List.of(second), queue.enqueueAndAdmit(second, ALL_READY).batch().admitted());
        assertTrue(queue.enqueueAndAdmit(unavailable, ALL_READY).batch().admitted().isEmpty());
        assertTrue(queue.enqueueAndAdmit(fourth, ALL_READY).batch().admitted().isEmpty());
        assertEquals(2, queue.maxActive());

        UUID unavailableMember = unavailable.roster().members().get(0);
        ReleaseResult release = queue.release(first.sessionId(), candidate ->
                candidate.sessionId().equals(unavailable.sessionId())
                        ? AvailabilityCheck.unavailable(
                                AvailabilityStatus.MEMBER_OFFLINE,
                                unavailableMember
                        )
                        : AvailabilityCheck.eligible()
        );

        assertTrue(release.released());
        assertEquals(List.of(fourth), release.batch().admitted());
        assertEquals(List.of(unavailable), release.batch().cancelled().stream()
                .map(CancelledAdmission::request)
                .toList());
        assertEquals(2, queue.activeCount());
        assertEquals(0, queue.waitingCount());
    }

    @Test
    void preventsDuplicateSessionsAndMemberOwnershipAcrossQueueAndActiveRuns() {
        InstanceAdmissionQueue queue = new InstanceAdmissionQueue(1);
        AdmissionRequest first = request();
        queue.enqueueAndAdmit(first, ALL_READY);

        assertEquals(
                AdmissionError.DUPLICATE_SESSION,
                queue.enqueueAndAdmit(first, ALL_READY).error().orElseThrow()
        );
        AdmissionRequest sameMember = new AdmissionRequest(
                SessionId.random(),
                first.roster(),
                Instant.now()
        );
        assertEquals(
                AdmissionError.MEMBER_ALREADY_QUEUED_OR_ACTIVE,
                queue.enqueueAndAdmit(sameMember, ALL_READY).error().orElseThrow()
        );
    }

    @Test
    void disconnectCancelsQueuedButReportsActiveForSessionSuspension() {
        InstanceAdmissionQueue queue = new InstanceAdmissionQueue(1);
        AdmissionRequest active = request();
        AdmissionRequest queued = request();
        queue.enqueueAndAdmit(active, ALL_READY);
        queue.enqueueAndAdmit(queued, ALL_READY);

        DisconnectImpact queuedImpact = queue.disconnect(queued.roster().members().get(0));
        assertEquals(queued, queuedImpact.cancelledQueued().orElseThrow());
        assertTrue(queuedImpact.activeSession().isEmpty());
        assertEquals(0, queue.waitingCount());

        DisconnectImpact activeImpact = queue.disconnect(active.roster().members().get(0));
        assertEquals(active, activeImpact.activeSession().orElseThrow());
        assertTrue(activeImpact.cancelledQueued().isEmpty());
        assertEquals(1, queue.activeCount());
    }

    @Test
    void duplicateConcurrentReleaseFreesCapacityExactlyOnce() throws Exception {
        InstanceAdmissionQueue queue = new InstanceAdmissionQueue(2);
        AdmissionRequest first = request();
        AdmissionRequest second = request();
        AdmissionRequest third = request();
        AdmissionRequest fourth = request();
        queue.enqueueAndAdmit(first, ALL_READY);
        queue.enqueueAndAdmit(second, ALL_READY);
        queue.enqueueAndAdmit(third, ALL_READY);
        queue.enqueueAndAdmit(fourth, ALL_READY);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ReleaseResult> left = executor.submit(() -> {
                start.await();
                return queue.release(first.sessionId(), ALL_READY);
            });
            Future<ReleaseResult> right = executor.submit(() -> {
                start.await();
                return queue.release(first.sessionId(), ALL_READY);
            });
            start.countDown();

            ReleaseResult one = left.get();
            ReleaseResult two = right.get();
            assertEquals(1, List.of(one, two).stream().filter(ReleaseResult::released).count());
            assertEquals(1, List.of(one, two).stream().filter(result -> !result.released()).count());
            ReleaseResult duplicate = one.released() ? two : one;
            assertEquals(AdmissionError.SESSION_NOT_ACTIVE, duplicate.error().orElseThrow());
        } finally {
            executor.shutdownNow();
        }

        assertEquals(2, queue.activeCount());
        assertEquals(List.of(fourth), queue.waitingSnapshot());
        assertTrue(queue.activeSnapshot().containsAll(List.of(second, third)));
        assertFalse(queue.activeSnapshot().contains(first));
    }

    private static AdmissionRequest request() {
        UUID leader = UUID.randomUUID();
        return new AdmissionRequest(
                SessionId.random(),
                new PartyRoster(leader, List.of(leader)),
                Instant.now()
        );
    }
}

package dev.mcpuzzle.core.application.admission;

import dev.mcpuzzle.core.domain.SessionId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Monitor-protected FIFO admission queue. Eligibility checks execute while the
 * monitor is held so deciding and reserving capacity is atomic; callbacks must
 * therefore be fast, side-effect free and non-reentrant.
 */
public final class InstanceAdmissionQueue {
    public static final int DEFAULT_MAX_ACTIVE = 2;

    private final int maxActive;
    private final Deque<AdmissionRequest> waiting = new ArrayDeque<>();
    private final Map<SessionId, AdmissionRequest> active = new LinkedHashMap<>();

    public InstanceAdmissionQueue() {
        this(DEFAULT_MAX_ACTIVE);
    }

    public InstanceAdmissionQueue(int maxActive) {
        if (maxActive < 1) {
            throw new IllegalArgumentException("Maximum active instances must be positive");
        }
        this.maxActive = maxActive;
    }

    public synchronized EnqueueResult enqueueAndAdmit(
            AdmissionRequest request,
            RosterAvailability availability
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(availability, "availability");
        if (containsSession(request.sessionId())) {
            return EnqueueResult.failure(AdmissionError.DUPLICATE_SESSION);
        }
        if (request.roster().members().stream().anyMatch(this::isMemberOwned)) {
            return EnqueueResult.failure(AdmissionError.MEMBER_ALREADY_QUEUED_OR_ACTIVE);
        }
        waiting.addLast(request);
        return EnqueueResult.success(drainLocked(availability));
    }

    public synchronized AdmissionBatch admitAvailable(RosterAvailability availability) {
        return drainLocked(Objects.requireNonNull(availability, "availability"));
    }

    public synchronized Optional<AdmissionRequest> cancelQueued(SessionId sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        AdmissionRequest match = waiting.stream()
                .filter(request -> request.sessionId().equals(sessionId))
                .findFirst()
                .orElse(null);
        if (match != null) {
            waiting.remove(match);
        }
        return Optional.ofNullable(match);
    }

    public synchronized DisconnectImpact disconnect(UUID memberId) {
        Objects.requireNonNull(memberId, "memberId");
        AdmissionRequest queued = waiting.stream()
                .filter(request -> request.roster().contains(memberId))
                .findFirst()
                .orElse(null);
        if (queued != null) {
            waiting.remove(queued);
        }
        AdmissionRequest activeRequest = active.values().stream()
                .filter(request -> request.roster().contains(memberId))
                .findFirst()
                .orElse(null);
        return new DisconnectImpact(Optional.ofNullable(queued), Optional.ofNullable(activeRequest));
    }

    public synchronized ReleaseResult release(
            SessionId sessionId,
            RosterAvailability availability
    ) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(availability, "availability");
        AdmissionRequest removed = active.remove(sessionId);
        if (removed == null) {
            return ReleaseResult.failure(AdmissionError.SESSION_NOT_ACTIVE);
        }
        return ReleaseResult.success(drainLocked(availability));
    }

    public synchronized int activeCount() {
        return active.size();
    }

    public synchronized int waitingCount() {
        return waiting.size();
    }

    public synchronized List<AdmissionRequest> waitingSnapshot() {
        return List.copyOf(waiting);
    }

    public synchronized List<AdmissionRequest> activeSnapshot() {
        return List.copyOf(active.values());
    }

    public int maxActive() {
        return maxActive;
    }

    private AdmissionBatch drainLocked(RosterAvailability availability) {
        List<AdmissionRequest> admitted = new ArrayList<>();
        List<CancelledAdmission> cancelled = new ArrayList<>();
        while (active.size() < maxActive && !waiting.isEmpty()) {
            AdmissionRequest candidate = waiting.peekFirst();
            AvailabilityCheck check = Objects.requireNonNull(
                    availability.check(candidate),
                    "availability result"
            );
            if (!check.isEligible()
                    && !candidate.roster().contains(check.unavailableMember().orElseThrow())) {
                throw new IllegalArgumentException("Unavailable member must belong to the queued roster");
            }
            waiting.removeFirst();
            if (!check.isEligible()) {
                cancelled.add(new CancelledAdmission(candidate, check));
                continue;
            }
            active.put(candidate.sessionId(), candidate);
            admitted.add(candidate);
        }
        return new AdmissionBatch(admitted, cancelled);
    }

    private boolean containsSession(SessionId sessionId) {
        return active.containsKey(sessionId)
                || waiting.stream().anyMatch(request -> request.sessionId().equals(sessionId));
    }

    private boolean isMemberOwned(UUID memberId) {
        return active.values().stream().anyMatch(request -> request.roster().contains(memberId))
                || waiting.stream().anyMatch(request -> request.roster().contains(memberId));
    }
}

package dev.mcpuzzle.core.application.admission;

@FunctionalInterface
public interface RosterAvailability {
    /** Must be side-effect free and must not call back into the queue. */
    AvailabilityCheck check(AdmissionRequest request);
}

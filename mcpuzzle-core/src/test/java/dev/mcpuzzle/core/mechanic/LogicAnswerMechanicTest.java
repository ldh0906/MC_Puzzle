package dev.mcpuzzle.core.mechanic;

import dev.mcpuzzle.core.domain.SessionId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogicAnswerMechanicTest {
    @Test
    void acceptsUnicodeAliasesWithoutDependingOnCaseWhitespaceOrPunctuation() {
        RoomAttemptId attempt = new RoomAttemptId(SessionId.random(), 1, 0);
        LogicAnswerMechanic mechanic = new LogicAnswerMechanic(
                new MechanicId("terminal"), attempt, List.of("C-4", "찰리 4"));

        assertTrue(mechanic.accepts("  c 4 "));
        assertTrue(mechanic.accepts("찰리-4"));
        assertEquals(MechanicOutcomeType.COMPLETED,
                mechanic.handle(attempt, new LogicAnswerMechanic.AnswerSubmitted("Ｃ－４")).type());
    }

    @Test
    void anIncorrectGuessDoesNotFailOrResetTheRoom() {
        RoomAttemptId attempt = new RoomAttemptId(SessionId.random(), 7, 0);
        LogicAnswerMechanic mechanic = new LogicAnswerMechanic(
                new MechanicId("terminal"), attempt, List.of("13"));

        MechanicOutcome result = mechanic.handle(attempt, new LogicAnswerMechanic.AnswerSubmitted("12"));
        assertEquals(MechanicOutcomeType.NO_CHANGE, result.type());
        assertEquals(MechanicStatus.ACTIVE, result.status());
    }
}

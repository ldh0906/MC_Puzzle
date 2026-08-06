package dev.mcpuzzle.paper.runtime;

import dev.mcpuzzle.core.domain.PartyRoster;
import dev.mcpuzzle.core.domain.SessionId;
import dev.mcpuzzle.paper.map.MapPack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaperRoomRuntimeEscortTest {
    @Test
    void ungatedCheckpointFailsButSoloCanGateEveryCheckpoint() throws Exception {
        MapPack.RoomDefinition room = roomFixture();
        MapPack.Escort escort = (MapPack.Escort) room.mechanics().get(0);
        UUID solo = UUID.randomUUID();
        PartyRoster roster = new PartyRoster(solo, List.of(solo));

        PaperRoomRuntime failed = new PaperRoomRuntime(SessionId.random(), 0, room, roster);
        assertEquals(PaperRoomRuntime.Signal.ROOM_FAILED, failed.escortReached(escort, false));

        PaperRoomRuntime solved = new PaperRoomRuntime(SessionId.random(), 0, room, roster);
        for (int checkpoint = 0; checkpoint < 7; checkpoint++) {
            assertEquals(PaperRoomRuntime.Signal.PROGRESSED, solved.escortReached(escort, true));
        }
        assertEquals(PaperRoomRuntime.Signal.ROOM_COMPLETED, solved.escortReached(escort, true));
    }

    private MapPack.RoomDefinition roomFixture() {
        MapPack.Position spawn = new MapPack.Position(0, 65, 0, 0, 0);
        List<MapPack.Position> checkpoints = java.util.stream.IntStream.range(0, 7)
                .mapToObj(index -> new MapPack.Position(index + 1, 65, 0, 0, 0)).toList();
        MapPack.Escort escort = new MapPack.Escort(
                "escort", "PROXIMITY_ESCORT",
                new MapPack.EscortEntity("ALLAY", "검증용 안내자", spawn, true),
                checkpoints, new MapPack.Position(8, 65, 0, 0, 0), 3.0, 0.2,
                "ANY_PARTY_MEMBER_NEAR_CHECKPOINT", true, "RESET_ROOM", "DESTINATION");
        MapPack.Bounds bounds = new MapPack.Bounds(
                new MapPack.Position(-2, 60, -2, 0, 0),
                new MapPack.Position(10, 75, 2, 0, 0));
        return new MapPack.RoomDefinition(
                "escort-fixture", 1, 19, "호송 상태 머신 검증", bounds, bounds, spawn, spawn,
                Optional.empty(), List.of(escort), List.of(), "시작", "완료", "실패");
    }
}

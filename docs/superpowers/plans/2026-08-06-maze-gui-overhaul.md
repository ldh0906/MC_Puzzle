# Maze GUI Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the minimal `/maze` inventory with a state-aware 54-slot player and administrator dashboard while preserving every existing command path.

**Architecture:** Keep `MazeRuntimeService` as the gameplay application boundary and `MazeMenu` as a thin Bukkit listener/controller. Add a typed action codec for safe inventory routing, keep item theming in a focused factory, and expose immutable asynchronous save/leaderboard snapshots that are applied to inventories only on the Paper main thread.

**Tech Stack:** Java 17, Paper API 1.20.1, Gradle Kotlin DSL, JUnit 5, SQLite persistence, Bukkit inventory events.

---

## File map

- Create `mcpuzzle-paper/src/main/java/dev/mcpuzzle/paper/gui/MazeMenuAction.java`: typed action encoding and defensive parsing.
- Create `mcpuzzle-paper/src/main/java/dev/mcpuzzle/paper/gui/MazeMenuItems.java`: Midnight Maze colors, custom model items, vanilla icons, player heads, and lore helpers.
- Rewrite `mcpuzzle-paper/src/main/java/dev/mcpuzzle/paper/gui/MazeMenu.java`: page navigation, click routing, confirmations, async refresh, and answer chat mode.
- Modify `mcpuzzle-core/src/main/java/dev/mcpuzzle/core/application/party/PartyService.java`: read-only received-invitation query.
- Modify `mcpuzzle-paper/src/main/java/dev/mcpuzzle/paper/runtime/MazeRuntimeService.java`: menu-facing immutable data queries and richer run view.
- Modify `mcpuzzle-paper/src/main/java/dev/mcpuzzle/paper/MCPuzzlePlugin.java`: pass admin actions and authoring service into the menu.
- Modify `mcpuzzle-paper/build.gradle.kts`: select PowerShell 7 or Windows PowerShell for resource-pack generation.
- Create `mcpuzzle-paper/src/test/java/dev/mcpuzzle/paper/gui/MazeMenuActionTest.java`: action protocol coverage.
- Modify `mcpuzzle-core/src/test/java/dev/mcpuzzle/core/application/party/PartyServiceTest.java`: received invitation lifecycle coverage.
- Modify `.gitignore`: ignore visual-companion scratch state created during approved design work.

### Task 1: Typed menu action protocol

**Files:**
- Create: `mcpuzzle-paper/src/test/java/dev/mcpuzzle/paper/gui/MazeMenuActionTest.java`
- Create: `mcpuzzle-paper/src/main/java/dev/mcpuzzle/paper/gui/MazeMenuAction.java`

- [ ] **Step 1: Write the failing action codec tests**

```java
@Test
void roundTripsActionsWithUuidSlotAndPageArguments() {
    UUID owner = UUID.randomUUID();
    MazeMenuAction action = MazeMenuAction.of(
            MazeMenuAction.Type.ADMIN_TRANSFER,
            owner.toString(), "2", UUID.randomUUID().toString());
    assertEquals(action, MazeMenuAction.decode(action.encode()).orElseThrow());
}

@Test
void rejectsUnknownMalformedAndDelimiterBearingActions() {
    assertTrue(MazeMenuAction.decode("missing").isEmpty());
    assertTrue(MazeMenuAction.decode("NOT_A_TYPE|1").isEmpty());
    assertThrows(IllegalArgumentException.class,
            () -> MazeMenuAction.of(MazeMenuAction.Type.MAIN, "bad|argument"));
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `.\gradlew.bat :mcpuzzle-paper:test --tests dev.mcpuzzle.paper.gui.MazeMenuActionTest`

Expected: compilation fails because `MazeMenuAction` does not exist.

- [ ] **Step 3: Implement the minimal immutable codec**

```java
public record MazeMenuAction(Type type, List<String> arguments) {
    private static final String SEPARATOR = "|";

    public MazeMenuAction {
        Objects.requireNonNull(type, "type");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        if (arguments.stream().anyMatch(value -> value == null || value.contains(SEPARATOR))) {
            throw new IllegalArgumentException("Menu arguments must not contain '|'");
        }
    }

    public static MazeMenuAction of(Type type, String... arguments) {
        return new MazeMenuAction(type, List.of(arguments));
    }

    public String encode() {
        return Stream.concat(Stream.of(type.name()), arguments.stream()).collect(Collectors.joining(SEPARATOR));
    }

    public static Optional<MazeMenuAction> decode(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String[] values = raw.split("\\|", -1);
        try {
            return Optional.of(new MazeMenuAction(Type.valueOf(values[0]),
                    Arrays.asList(values).subList(1, values.length)));
        } catch (IllegalArgumentException failure) {
            return Optional.empty();
        }
    }

    public enum Type {
        MAIN, CLOSE, PARTY, PARTY_CREATE, PARTY_INVITE_LIST, PARTY_INVITE,
        PARTY_INVITATIONS, PARTY_ACCEPT, PARTY_DECLINE, PARTY_KICK,
        PARTY_LEAVE, PARTY_DISBAND, SAVES, SAVE_START, SAVE_RESUME,
        SAVE_DELETE, HINTS, HINT_REQUEST, HINT_VIEW, HINT_APPROVE,
        HINT_DECLINE, LEADERBOARD, ANSWER_PROMPT, RUN_LEAVE, QUEUE_CANCEL,
        ADMIN, ADMIN_RELOAD, ADMIN_VERIFY_WORLD, ADMIN_WAND,
        ADMIN_SELECTION, ADMIN_PLAYERS, ADMIN_SAVES, ADMIN_DELETE,
        ADMIN_TRANSFER_PICK, ADMIN_TRANSFER, CONFIRM, CANCEL, PAGE
    }
}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `.\gradlew.bat :mcpuzzle-paper:test --tests dev.mcpuzzle.paper.gui.MazeMenuActionTest`

Expected: both tests pass.

- [ ] **Step 5: Commit only the codec and test**

```powershell
git add -- mcpuzzle-paper/src/main/java/dev/mcpuzzle/paper/gui/MazeMenuAction.java mcpuzzle-paper/src/test/java/dev/mcpuzzle/paper/gui/MazeMenuActionTest.java
git commit -m "feat: add typed maze menu actions"
```

### Task 2: Received party invitation query

**Files:**
- Modify: `mcpuzzle-core/src/test/java/dev/mcpuzzle/core/application/party/PartyServiceTest.java`
- Modify: `mcpuzzle-core/src/main/java/dev/mcpuzzle/core/application/party/PartyService.java`

- [ ] **Step 1: Write the failing invitation lifecycle test**

```java
@Test
void listsOnlyLiveInvitationsForTheTargetPlayer() {
    PartyService service = new PartyService();
    UUID firstLeader = UUID.randomUUID();
    UUID secondLeader = UUID.randomUUID();
    UUID target = UUID.randomUUID();
    PartyId accepted = success(service.create(firstLeader)).id();
    success(service.create(secondLeader));
    success(service.invite(firstLeader, target));
    success(service.invite(secondLeader, target));

    assertEquals(2, service.findInvitations(target).size());
    success(service.accept(target, accepted));
    assertTrue(service.findInvitations(target).isEmpty());
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `.\gradlew.bat :mcpuzzle-core:test --tests dev.mcpuzzle.core.application.party.PartyServiceTest.listsOnlyLiveInvitationsForTheTargetPlayer`

Expected: compilation fails because `findInvitations` does not exist.

- [ ] **Step 3: Implement a synchronized immutable invitation snapshot**

```java
public synchronized List<PartyView> findInvitations(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    return parties.entrySet().stream()
            .filter(entry -> entry.getValue().lifecycle == PartyLifecycle.OPEN)
            .filter(entry -> entry.getValue().pendingInvites.contains(playerId))
            .map(entry -> view(entry.getKey(), entry.getValue()))
            .toList();
}
```

- [ ] **Step 4: Run all core tests and verify GREEN**

Run: `.\gradlew.bat :mcpuzzle-core:test`

Expected: all core tests pass.

- [ ] **Step 5: Commit the query and regression test**

```powershell
git add -- mcpuzzle-core/src/main/java/dev/mcpuzzle/core/application/party/PartyService.java mcpuzzle-core/src/test/java/dev/mcpuzzle/core/application/party/PartyServiceTest.java
git commit -m "feat: expose received party invitations"
```

### Task 3: Menu-facing runtime snapshots

**Files:**
- Modify: `mcpuzzle-paper/src/main/java/dev/mcpuzzle/paper/runtime/MazeRuntimeService.java`
- Modify: `mcpuzzle-paper/src/test/java/dev/mcpuzzle/paper/runtime/SaveSlotWorkflowTest.java`

- [ ] **Step 1: Add a failing save listing test for principal visibility**

Extend the existing `SaveSlotWorkflowTest` fixture with two saves where the requesting principal owns one and belongs to the other roster, then assert:

```java
List<SaveGame> visible = workflow.listForPrincipal(principal).toCompletableFuture().join();
assertEquals(Set.of(1, 2), visible.stream().map(save -> save.slot().number()).collect(Collectors.toSet()));
```

- [ ] **Step 2: Run the focused test and verify RED or existing behavior**

Run: `.\gradlew.bat :mcpuzzle-paper:test --tests dev.mcpuzzle.paper.runtime.SaveSlotWorkflowTest`

Expected: the new visibility assertion passes only if the existing persistence contract already satisfies the GUI requirement. If it passes immediately, retain it as characterization coverage and do not alter persistence.

- [ ] **Step 3: Add immutable GUI query methods to the runtime**

```java
public List<PartyView> invitations(UUID playerId) {
    return parties.findInvitations(playerId);
}

public CompletionStage<List<SaveGame>> saves(Player viewer, UUID ownerId) {
    if (!viewer.getUniqueId().equals(ownerId) && !viewer.isOp()) {
        return CompletableFuture.failedFuture(new IllegalArgumentException("다른 플레이어의 세이브는 OP만 볼 수 있습니다."));
    }
    return viewer.isOp() && !viewer.getUniqueId().equals(ownerId)
            ? saveSlots.list(ownerId) : saveSlots.listForPrincipal(viewer.getUniqueId());
}

public CompletionStage<List<LeaderboardEntry>> leaderboardEntries(int partySize) {
    int size = Math.max(1, Math.min(4, partySize));
    return persistence.leaderboard(new LeaderboardQuery(map.mazeId(), map.mapVersion(), size, 10));
}

public record RunView(SessionId sessionId, SessionState state, int room, int roomCount,
                      int slot, PartyRoster roster, Set<Integer> unlockedHints) { }

private RunView view(RunContext ctx) {
    int room = ctx.session.currentRoom();
    return new RunView(ctx.session.id(), ctx.session.state(), room, ctx.session.roomCount(),
            ctx.slot, ctx.roster,
            Set.copyOf(ctx.session.hintProgress().unlockedByRoom().getOrDefault(room, Set.of())));
}
```

- [ ] **Step 4: Reuse the new leaderboard query from the legacy command method**

Change `leaderboard(Player, int)` to call `leaderboardEntries(size)` and keep its existing chat formatting, proving GUI and command paths share one data source.

- [ ] **Step 5: Run paper runtime and persistence tests**

Run: `.\gradlew.bat :mcpuzzle-paper:test --tests "dev.mcpuzzle.paper.runtime.*" --tests "dev.mcpuzzle.paper.adapter.persistence.*"`

Expected: all selected tests pass.

- [ ] **Step 6: Commit runtime snapshot changes**

```powershell
git add -- mcpuzzle-paper/src/main/java/dev/mcpuzzle/paper/runtime/MazeRuntimeService.java mcpuzzle-paper/src/test/java/dev/mcpuzzle/paper/runtime/SaveSlotWorkflowTest.java
git commit -m "feat: expose maze menu snapshots"
```

### Task 4: Midnight Maze item theme and state-aware pages

**Files:**
- Create: `mcpuzzle-paper/src/main/java/dev/mcpuzzle/paper/gui/MazeMenuItems.java`
- Rewrite: `mcpuzzle-paper/src/main/java/dev/mcpuzzle/paper/gui/MazeMenu.java`

- [ ] **Step 1: Add action-routing tests before changing the listener**

Extend `MazeMenuActionTest` with parameterized cases for every action carrying a UUID, slot, page, hint tier, or party size. Assert malformed numeric and UUID arguments are rejected by typed accessors `integer(index, min, max)` and `uuid(index)`.

- [ ] **Step 2: Run the focused tests and verify RED**

Run: `.\gradlew.bat :mcpuzzle-paper:test --tests dev.mcpuzzle.paper.gui.MazeMenuActionTest`

Expected: compilation fails because typed accessors are missing.

- [ ] **Step 3: Add validated accessors and make tests GREEN**

Implement `OptionalInt integer(int index, int min, int max)` and `Optional<UUID> uuid(int index)` without throwing on malformed stored actions. Re-run the focused test until all cases pass.

- [ ] **Step 4: Implement the focused item/theme factory**

`MazeMenuItems` must provide:

```java
ItemStack model(PuzzleItemModel model, String name, MazeMenuAction action, List<String> lore)
ItemStack material(Material material, String name, MazeMenuAction action, List<String> lore)
ItemStack playerHead(OfflinePlayer player, String name, MazeMenuAction action, List<String> lore)
ItemStack decoration(Material pane)
void frame(Inventory inventory, Material pane)
```

Use dark blue glass for borders, purple for sections, gold/green for primary actions, red for destructive actions, `HIDE_ATTRIBUTES`, and an empty display name on decoration panes.

- [ ] **Step 5: Rewrite the menu controller with explicit page ownership**

The controller must implement these public page entries:

```java
openMain(Player player)
openParty(Player player)
openInvitations(Player player, int page)
openInvitePlayers(Player player, int page)
openSaves(Player player)
openHints(Player player)
openLeaderboard(Player player, int partySize)
openAdmin(Player player)
```

Use a `MenuHolder(UUID playerId, MenuType type, UUID subjectId, int value, Inventory inventory)` to ensure async save and leaderboard callbacks update only the same still-open top inventory. Render a 54-slot main dashboard with central run/party status, party, saves, hints, leaderboard, received invitations, context action, help, admin (permission gated), and close buttons.

- [ ] **Step 6: Implement all player GUI flows**

Route typed actions to existing runtime methods for party creation/invite/accept/decline/kick/leave/disband, save start/resume/delete, hint request/view/approve/decline, leaderboard tabs, queue cancellation, and run leave. Reopen the relevant page after synchronous party mutations; close before long-running or world-mutating operations.

- [ ] **Step 7: Implement async save and leaderboard rendering safely**

Show a loading item immediately. Attach `whenComplete`, then schedule inventory mutation with `plugin.getServer().getScheduler().runTask(plugin, ...)`. Before rendering, require the player to be online and the current top inventory holder to be the exact holder created for that request.

- [ ] **Step 8: Compile and run GUI action tests**

Run: `.\gradlew.bat :mcpuzzle-paper:test --tests dev.mcpuzzle.paper.gui.MazeMenuActionTest`

Expected: action tests pass and Java compilation succeeds.

- [ ] **Step 9: Commit the player-facing GUI**

```powershell
git add -- mcpuzzle-paper/src/main/java/dev/mcpuzzle/paper/gui/MazeMenu.java mcpuzzle-paper/src/main/java/dev/mcpuzzle/paper/gui/MazeMenuItems.java mcpuzzle-paper/src/main/java/dev/mcpuzzle/paper/gui/MazeMenuAction.java mcpuzzle-paper/src/test/java/dev/mcpuzzle/paper/gui/MazeMenuActionTest.java
git commit -m "feat: build state-aware maze dashboard"
```

### Task 5: Answer input and administrator hub

**Files:**
- Modify: `mcpuzzle-paper/src/main/java/dev/mcpuzzle/paper/gui/MazeMenu.java`
- Modify: `mcpuzzle-paper/src/main/java/dev/mcpuzzle/paper/MCPuzzlePlugin.java`

- [ ] **Step 1: Add failing action protocol tests for confirmations**

Add tests asserting a confirmation action preserves the intended return page and destructive payload, while a confirmation with a missing slot or target UUID is rejected by typed accessors.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `.\gradlew.bat :mcpuzzle-paper:test --tests dev.mcpuzzle.paper.gui.MazeMenuActionTest`

Expected: at least one new confirmation validation assertion fails before the required accessor/route constraint exists.

- [ ] **Step 3: Add player answer capture**

Store `Map<UUID, Instant> answerPrompts`. `ANSWER_PROMPT` closes the menu, records `Instant.now().plusSeconds(60)`, and instructs the player to enter one chat line or `취소`. An `AsyncPlayerChatEvent` handler cancels only a live prompt, removes it atomically, and schedules `runtime.submitAnswer(player, message)` on the main thread. A `PlayerQuitEvent` handler removes stale prompts.

- [ ] **Step 4: Add permission-gated administrator routes**

Construct `MazeMenu` with `PluginReadiness`, `AuthoringWandService`, `Consumer<CommandSender> reloadAction`, and `Consumer<CommandSender> verifyWorldAction`. Recheck `mcpuzzle.admin` immediately before every admin action. Provide online-player selection, admin save viewing, delete confirmation, and transfer targets restricted to the saved roster.

- [ ] **Step 5: Update bootstrap wiring without adding duplicate listeners**

In `MCPuzzlePlugin.finishBootstrap`, construct `worldVerifier` before `MazeMenu`, pass the same authoring service and method references used by `MazeCommand`, and retain the existing single `register(menu, authoring, ...)` call.

- [ ] **Step 6: Run paper tests and compile**

Run: `.\gradlew.bat :mcpuzzle-paper:test`

Expected: all paper tests pass with no listener registration or API compilation errors.

- [ ] **Step 7: Commit answer and admin flows**

```powershell
git add -- mcpuzzle-paper/src/main/java/dev/mcpuzzle/paper/gui/MazeMenu.java mcpuzzle-paper/src/main/java/dev/mcpuzzle/paper/MCPuzzlePlugin.java mcpuzzle-paper/src/test/java/dev/mcpuzzle/paper/gui/MazeMenuActionTest.java
git commit -m "feat: add maze admin and answer gui flows"
```

### Task 6: Repository hygiene and full verification

**Files:**
- Modify: `.gitignore`
- Modify: `mcpuzzle-paper/build.gradle.kts`
- Verify only: `mcpuzzle-paper/src/main/resources/plugin.yml`
- Verify only: `server/verify-startup.ps1`

- [ ] **Step 1: Ignore design companion scratch files**

Add exactly these entries without changing existing patterns:

```gitignore
.superpowers/
.visual-companion.out
.visual-companion.err
```

- [ ] **Step 2: Make resource-pack generation portable across installed PowerShell variants**

The baseline full build fails when `pwsh.exe` is absent even though Windows PowerShell is available. Resolve the executable once at configuration time:

```kotlin
val powerShellExecutable = if (System.getenv("PATH").orEmpty().split(File.pathSeparator)
        .map(::File).any { it.resolve("pwsh.exe").isFile }) "pwsh.exe" else "powershell.exe"
```

Use `powerShellExecutable` in `buildPuzzleResourcePack.commandLine` and preserve the existing `-NoProfile`, `-ExecutionPolicy Bypass`, and `-File` arguments.

- [ ] **Step 3: Run all unit tests and the full build**

Run: `.\gradlew.bat clean test build`

Expected: `BUILD SUCCESSFUL`, zero failed tests, and a shaded plugin JAR at `mcpuzzle-paper/build/libs/mcpuzzle-paper-0.1.0-SNAPSHOT.jar`.

- [ ] **Step 4: Run Paper startup verification**

Run: `pwsh -NoProfile -ExecutionPolicy Bypass -File .\server\verify-startup.ps1`

Expected: plugin reaches `MCPuzzle READY`, validates 20 rooms, and the temporary verification server exits cleanly.

- [ ] **Step 5: Inspect source and user-owned diffs**

Run: `git status --short` and `git diff --check`.

Expected: only intentional source, test, documentation, and `.gitignore` changes are present in commits; pre-existing `server/**` configuration and world modifications remain unstaged and unchanged by this work.

- [ ] **Step 6: Commit repository hygiene and build portability**

```powershell
git add -- .gitignore mcpuzzle-paper/build.gradle.kts
git commit -m "build: support available PowerShell runtime"
```

- [ ] **Step 7: Perform the completion checklist**

Confirm main dashboard, party and invitation flow, saves, hints, leaderboard, answer prompt, run actions, admin permission checks, command compatibility, async main-thread handoff, and server startup evidence against `docs/superpowers/specs/2026-08-06-maze-gui-overhaul-design.md`.

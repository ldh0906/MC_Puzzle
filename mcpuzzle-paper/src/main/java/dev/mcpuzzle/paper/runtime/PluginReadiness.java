package dev.mcpuzzle.paper.runtime;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class PluginReadiness {
    public enum State { STARTING, READY, DEGRADED, STOPPING }
    private final AtomicReference<State> state = new AtomicReference<>(State.STARTING);
    private final AtomicReference<String> detail = new AtomicReference<>("시작 준비 중");

    public void ready() { state.set(State.READY); detail.set("정상"); }
    public void degraded(String reason) { state.set(State.DEGRADED); detail.set(reason); }
    public void stopping() { state.set(State.STOPPING); detail.set("종료 중"); }
    public State state() { return state.get(); }
    public String detail() { return detail.get(); }
    public boolean acceptsEntry() { return state.get() == State.READY; }
}

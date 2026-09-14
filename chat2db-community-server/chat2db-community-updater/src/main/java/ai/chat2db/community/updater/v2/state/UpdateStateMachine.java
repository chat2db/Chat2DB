package ai.chat2db.community.updater.v2.state;

import ai.chat2db.community.updater.v2.enums.UpdatePhaseEnum;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class UpdateStateMachine {

    private static final Map<UpdatePhaseEnum, Set<UpdatePhaseEnum>> TRANSITIONS = transitions();

    private UpdateStateMachine() {
    }

    public static void requireTransition(UpdatePhaseEnum current, UpdatePhaseEnum next) {
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(next)) {
            throw new IllegalStateException("Invalid update phase transition: " + current + " -> " + next);
        }
    }

    public static boolean isTerminal(UpdatePhaseEnum phase) {
        return phase == UpdatePhaseEnum.COMMITTED || phase == UpdatePhaseEnum.FAILED;
    }

    private static Map<UpdatePhaseEnum, Set<UpdatePhaseEnum>> transitions() {
        EnumMap<UpdatePhaseEnum, Set<UpdatePhaseEnum>> result = new EnumMap<>(UpdatePhaseEnum.class);
        allow(result, UpdatePhaseEnum.DISCOVERED, UpdatePhaseEnum.DOWNLOADING);
        allow(result, UpdatePhaseEnum.DOWNLOADING, UpdatePhaseEnum.VERIFIED);
        allow(result, UpdatePhaseEnum.VERIFIED, UpdatePhaseEnum.PRECHECKING);
        allow(result, UpdatePhaseEnum.PRECHECKING, UpdatePhaseEnum.PRECHECKED);
        allow(result, UpdatePhaseEnum.PRECHECKED, UpdatePhaseEnum.QUIESCING);
        allow(result, UpdatePhaseEnum.QUIESCING, UpdatePhaseEnum.READY_TO_SWITCH);
        allow(result, UpdatePhaseEnum.READY_TO_SWITCH, UpdatePhaseEnum.SWITCHING);
        allow(result, UpdatePhaseEnum.SWITCHING, UpdatePhaseEnum.STARTING_CANDIDATE);
        allow(result, UpdatePhaseEnum.STARTING_CANDIDATE, UpdatePhaseEnum.POSTCHECKING);
        allow(result, UpdatePhaseEnum.POSTCHECKING, UpdatePhaseEnum.RESTARTING_NORMAL);
        allow(result, UpdatePhaseEnum.RESTARTING_NORMAL, UpdatePhaseEnum.COMMITTED);
        for (UpdatePhaseEnum phase : UpdatePhaseEnum.values()) {
            if (!isTerminal(phase)) {
                result.computeIfAbsent(phase, ignored -> EnumSet.noneOf(UpdatePhaseEnum.class))
                    .add(UpdatePhaseEnum.FAILED);
            }
        }
        return Map.copyOf(result);
    }

    private static void allow(Map<UpdatePhaseEnum, Set<UpdatePhaseEnum>> transitions, UpdatePhaseEnum from,
            UpdatePhaseEnum... targets) {
        transitions.computeIfAbsent(from, ignored -> EnumSet.noneOf(UpdatePhaseEnum.class))
            .addAll(Set.of(targets));
    }
}

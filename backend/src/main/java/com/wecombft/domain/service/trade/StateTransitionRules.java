package com.wecombft.domain.service.trade;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class StateTransitionRules<E extends Enum<E>> {

    private final Map<E, Set<E>> allowedTransitions;

    private StateTransitionRules(Map<E, Set<E>> allowedTransitions) {
        this.allowedTransitions = Map.copyOf(allowedTransitions);
    }

    public static <E extends Enum<E>> StateTransitionRules<E> of(Map<E, Set<E>> allowedTransitions) {
        Objects.requireNonNull(allowedTransitions, "allowedTransitions must not be null");
        return new StateTransitionRules<>(allowedTransitions);
    }

    public boolean canTransition(E current, E next) {
        Objects.requireNonNull(current, "current state must not be null");
        Objects.requireNonNull(next, "next state must not be null");
        if (current == next) {
            return true;
        }
        return allowedTransitions.getOrDefault(current, Set.of()).contains(next);
    }

    public E requireTransition(E current, E next) {
        if (!canTransition(current, next)) {
            throw new IllegalStateException("%s cannot transition from %s to %s"
                    .formatted(current.getDeclaringClass().getSimpleName(), current.name(), next.name()));
        }
        return next;
    }
}

package com.figurestore.api.statemachine;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;

@Component
public class OrderStateMachine {
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "WAITING_DP", Set.of("DP_PAID", "CANCELLED"),
            "DP_PAID", Set.of("WAITING_PELUNASAN"),
            "WAITING_PELUNASAN", Set.of("FULL_PAID", "CANCELLED_DP_HANGUS"),
            "FULL_PAID", Set.of("SHIPPED"),
            "SHIPPED", Set.of("COMPLETED")
    );

    public void validate(String from, String to) {
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalStateException("Transisi status tidak valid: " + from + " -> " + to);
        }
    }
}

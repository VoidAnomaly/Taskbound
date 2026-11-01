package dev.taskbound.core;

/** Additional choices earned between the five catalog unlock levels. */
public final class OfferProgression {
    private OfferProgression() { }

    public static int offerCount(int level) {
        int reached = Math.clamp(level, 1, 20);
        return 3 + reached - 1 - reached / 5;
    }
}

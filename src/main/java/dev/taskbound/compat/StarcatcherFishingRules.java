package dev.taskbound.compat;

import java.util.Objects;

/** Pure compatibility rules kept separate from Starcatcher's reflected runtime API. */
final class StarcatcherFishingRules {
    static final String RIPPLE_CATFISH = "starcatcher:ripple_catfish";

    private StarcatcherFishingRules() {
    }

    static boolean isSupportedSpecies(String speciesId) {
        return RIPPLE_CATFISH.equals(speciesId);
    }

    static boolean candidateAvailable(
            boolean itemRegistered,
            boolean componentRegistered,
            boolean saveDataToItems
    ) {
        return itemRegistered && componentRegistered && saveDataToItems;
    }

    static CatchMeasurements measurements(Number sizeCentimeters, Number weightGrams, String rarity) {
        Objects.requireNonNull(sizeCentimeters, "sizeCentimeters");
        Objects.requireNonNull(weightGrams, "weightGrams");
        if (rarity == null || rarity.isBlank()) {
            throw new IllegalArgumentException("Starcatcher rarity cannot be blank");
        }
        return new CatchMeasurements(
                sizeCentimeters.doubleValue(),
                weightGrams.doubleValue(),
                "starcatcher:" + rarity
        );
    }

    record CatchMeasurements(double lengthCentimeters, double weightGrams, String rarityId) {
    }
}

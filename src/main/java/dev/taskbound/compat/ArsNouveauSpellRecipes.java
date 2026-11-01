package dev.taskbound.compat;

/** Pure recipe/action matching used by the reflection adapter and its JVM-only tests. */
final class ArsNouveauSpellRecipes {
    static final String CAST_SPELL = "taskbound:cast_spell";
    static final String SPELL_CASTING = "magic:spell_casting";
    static final String HARM = "ars_nouveau:glyph_harm";
    static final String PROJECTILE = "ars_nouveau:glyph_projectile";
    static final String TOUCH = "ars_nouveau:glyph_touch";

    private ArsNouveauSpellRecipes() {
    }

    static boolean isSupportedAction(String actionType) {
        return CAST_SPELL.equals(actionType) || SPELL_CASTING.equals(actionType);
    }

    static boolean isEligibleHarmRecipe(Iterable<String> recipe) {
        boolean hasHarm = false;
        boolean hasEntityCastForm = false;
        for (String part : recipe) {
            hasHarm |= HARM.equals(part);
            hasEntityCastForm |= PROJECTILE.equals(part) || TOUCH.equals(part);
        }
        return hasHarm && hasEntityCastForm;
    }
}

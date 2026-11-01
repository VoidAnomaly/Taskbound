package dev.taskbound.core;

/** Online-play timers keep their remaining allowance in the accepted task's saved progress. */
public final class TaskTimer {
    private static final String REMAINING = "time_remaining_ticks";
    private static final String LAST_TICK = "time_last_tick";

    private TaskTimer() { }

    public static boolean online(GeneratedTask task) {
        return task.conditions().get("time_limit_ticks") instanceof Number number && number.intValue() > 0;
    }

    public static long remaining(ActiveTask active) {
        Object saved = active.progress().additionalData().get(REMAINING);
        long duration = ((Number) active.task().conditions().get("time_limit_ticks")).longValue();
        return saved instanceof Number number ? Math.clamp(number.longValue(), 0, duration) : duration;
    }

    /** Called at acceptance and login, never during ordinary polling or a dimension change. */
    public static ActiveTask resume(ActiveTask active, long gameTime) {
        if (!online(active.task()) || active.task().status() != TaskStatus.ACTIVE) return active;
        long left = remaining(active);
        active.progress().putAdditionalData(REMAINING, left);
        active.progress().putAdditionalData(LAST_TICK, gameTime);
        return projected(active, gameTime, left);
    }

    /** One allowance tick per actual player tick; repeated calls in the same tick do not charge twice. */
    public static ActiveTask tick(ActiveTask active, long gameTime) {
        if (!online(active.task()) || active.task().status() != TaskStatus.ACTIVE) return active;
        long left = remaining(active);
        Object previous = active.progress().additionalData().get(LAST_TICK);
        if (previous instanceof Number number && gameTime > number.longValue()) left = Math.max(0, left - 1);
        active.progress().putAdditionalData(REMAINING, left);
        active.progress().putAdditionalData(LAST_TICK, gameTime);
        return projected(active, gameTime, left);
    }

    public static boolean expired(ActiveTask active, long gameTime) {
        if (active.task().status() != TaskStatus.ACTIVE) return false;
        return online(active.task()) && remaining(active) == 0
                || active.task().expiresAt() != null && gameTime >= active.task().expiresAt();
    }

    private static ActiveTask projected(ActiveTask active, long gameTime, long left) {
        // A zero allowance is rejected by expired(); the record itself requires a positive interval.
        long deadline = Math.max(Math.addExact(active.task().acceptedAt(), 1), Math.addExact(gameTime, left));
        return new ActiveTask(active.task().withTiming(active.task().acceptedAt(), deadline), active.progress());
    }
}

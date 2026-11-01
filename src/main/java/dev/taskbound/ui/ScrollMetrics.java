package dev.taskbound.ui;

/** One-dimensional scrollbar geometry; content units may be rows or pixels. */
public record ScrollMetrics(int trackLength, int total, int visible, int offset) {
    public ScrollMetrics {
        trackLength = Math.max(0, trackLength);
        total = Math.max(0, total);
        visible = Math.max(0, visible);
        offset = Math.max(0, Math.min(offset, Math.max(0, total - visible)));
    }

    public int maximum() { return Math.max(0, total - visible); }

    public int thumbLength() {
        if (maximum() == 0) return trackLength;
        return Math.min(trackLength, Math.max(8, (int) Math.round(trackLength * (double) visible / total)));
    }

    /** Thumb start in track pixels, with exactly reachable first and last content positions. */
    public int thumbStart() {
        return maximum() == 0 ? 0 : (int) Math.round((trackLength - thumbLength()) * (double) offset / maximum());
    }

    /** Content offset for a desired thumb start relative to the track, including drag overshoot. */
    public int offsetAt(double thumbStart) {
        int travel = trackLength - thumbLength();
        if (travel <= 0) return offset;
        double clampedStart = Math.max(0, Math.min(thumbStart, travel));
        return (int) Math.round(clampedStart * maximum() / travel);
    }
}

package com.timestop.core.rewind;

import com.timestop.core.rewind.data.EntityDelta;
import com.timestop.core.rewind.data.PlayerDelta;
import com.timestop.core.rewind.data.SharedEntitySnapshot;
import com.timestop.core.rewind.data.SharedInventory;
import com.timestop.core.rewind.data.TickFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Circular ring buffer that stores historical TickFrames with O(1) insertion and memory caps.
 * Supports exact reference-counted accounting for shared entity and player snapshots.
 */
public class TimelineBuffer {
    private static final Logger LOGGER = LoggerFactory.getLogger("TimeStopRewind");

    public static final int DEFAULT_MAX_FRAMES = 600; // 30 seconds @ 20 TPS
    public static final long DEFAULT_MAX_MEMORY_BYTES = 50 * 1024 * 1024; // 50 MB

    private TickFrame[] frames;
    private int capacity;
    private final long baseMemoryBytes;
    private long maxMemoryBytes;
    private long memoryEvictedFrames;

    private int writeHead = 0;
    private int frameCount = 0;
    private long framesMemory = 0;
    private long sharedSnapshotsMemory = 0;

    private final Map<SharedEntitySnapshot, Integer> activeEntitySnapshots = new IdentityHashMap<>();
    private final Map<SharedInventory, Integer> activeInventories = new IdentityHashMap<>();

    private boolean recording = true;
    private boolean rewinding = false;
    private boolean frozen = false;

    public TimelineBuffer() {
        this(DEFAULT_MAX_FRAMES, DEFAULT_MAX_MEMORY_BYTES);
    }

    public TimelineBuffer(int capacity, long maxMemoryBytes) {
        if (capacity < 1 || maxMemoryBytes < 1) throw new IllegalArgumentException("Timeline limits must be positive");
        this.capacity = capacity;
        this.baseMemoryBytes = maxMemoryBytes;
        this.maxMemoryBytes = memoryBudget(capacity);
        this.frames = new TickFrame[capacity];
    }

    public synchronized void pushFrame(TickFrame frame) {
        if (!recording || rewinding || frozen) return;

        frame.seal();

        // If overwriting an existing frame, subtract its memory and untrack shared allocations
        TickFrame oldFrame = frames[writeHead];
        if (oldFrame != null) {
            framesMemory -= oldFrame.getEstimatedMemoryBytes();
            untrackFrameSharedAllocations(oldFrame);
        }

        frames[writeHead] = frame;
        framesMemory += frame.getEstimatedMemoryBytes();
        trackFrameSharedAllocations(frame);

        writeHead = (writeHead + 1) % capacity;
        if (frameCount < capacity) {
            frameCount++;
        }

        enforceMemoryBudget();
    }

    private void trackFrameSharedAllocations(TickFrame frame) {
        for (EntityDelta delta : frame.getEntityDeltas()) {
            SharedEntitySnapshot snapshot = delta.sharedSnapshot();
            if (snapshot != null) {
                int count = activeEntitySnapshots.getOrDefault(snapshot, 0);
                if (count == 0) {
                    sharedSnapshotsMemory += snapshot.estimatedBytes();
                }
                activeEntitySnapshots.put(snapshot, count + 1);
            }
        }
        for (PlayerDelta delta : frame.getPlayerDeltas().values()) {
            SharedInventory inv = delta.sharedInventory();
            if (inv != null) {
                int count = activeInventories.getOrDefault(inv, 0);
                if (count == 0) {
                    sharedSnapshotsMemory += inv.estimatedBytes();
                }
                activeInventories.put(inv, count + 1);
            }
        }
    }

    private void untrackFrameSharedAllocations(TickFrame frame) {
        for (EntityDelta delta : frame.getEntityDeltas()) {
            SharedEntitySnapshot snapshot = delta.sharedSnapshot();
            if (snapshot != null) {
                Integer count = activeEntitySnapshots.get(snapshot);
                if (count != null) {
                    if (count <= 1) {
                        activeEntitySnapshots.remove(snapshot);
                        sharedSnapshotsMemory -= snapshot.estimatedBytes();
                    } else {
                        activeEntitySnapshots.put(snapshot, count - 1);
                    }
                }
            }
        }
        for (PlayerDelta delta : frame.getPlayerDeltas().values()) {
            SharedInventory inv = delta.sharedInventory();
            if (inv != null) {
                Integer count = activeInventories.get(inv);
                if (count != null) {
                    if (count <= 1) {
                        activeInventories.remove(inv);
                        sharedSnapshotsMemory -= inv.estimatedBytes();
                    } else {
                        activeInventories.put(inv, count - 1);
                    }
                }
            }
        }
    }

    private long memoryBudget(int frameCapacity) {
        // Configured MB is the budget for 30 seconds; retain that floor for shorter buffers.
        return Math.max(baseMemoryBytes, (baseMemoryBytes * frameCapacity + 599) / 600);
    }

    private void enforceMemoryBudget() {
        while (getTotalEstimatedBytes() > maxMemoryBytes && frameCount > 0) {
            int oldestIndex = (writeHead - frameCount + capacity) % capacity;
            TickFrame dropped = frames[oldestIndex];
            if (dropped != null) {
                framesMemory -= dropped.getEstimatedMemoryBytes();
                untrackFrameSharedAllocations(dropped);
            }
            frames[oldestIndex] = null;
            frameCount--;
            memoryEvictedFrames++;
        }
    }

    /**
     * Retrieves up to tickCount frames in reverse chronological order (newest to oldest).
     */
    public synchronized List<TickFrame> getFramesForRewind(int tickCount) {
        if (frameCount == 0 || tickCount <= 0) return Collections.emptyList();

        int count = Math.min(tickCount, frameCount);
        List<TickFrame> result = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            int index = (writeHead - 1 - i + capacity) % capacity;
            TickFrame frame = frames[index];
            if (frame != null) {
                result.add(frame);
            }
        }

        return result;
    }

    /**
     * Removes the most recent frames that were rewound.
     */
    public synchronized void removeRecentFrames(int count) {
        int toRemove = Math.min(count, frameCount);
        for (int i = 0; i < toRemove; i++) {
            writeHead = (writeHead - 1 + capacity) % capacity;
            TickFrame frame = frames[writeHead];
            if (frame != null) {
                framesMemory -= frame.getEstimatedMemoryBytes();
                untrackFrameSharedAllocations(frame);
                frames[writeHead] = null;
            }
            frameCount--;
        }
        if (framesMemory < 0) framesMemory = 0;
        if (sharedSnapshotsMemory < 0) sharedSnapshotsMemory = 0;
    }

    public synchronized void consumeFrames(List<TickFrame> selected, RewindPlan plan) {
        for (int i = 0; i < frames.length; i++) {
            final TickFrame current = frames[i];
            if (current == null || selected.stream().noneMatch(current::sameInterval)) continue;
            framesMemory -= current.getEstimatedMemoryBytes();
            untrackFrameSharedAllocations(current);
            TickFrame modified = current.without(plan);
            frames[i] = modified;
            framesMemory += modified.getEstimatedMemoryBytes();
            trackFrameSharedAllocations(modified);
        }
        trimEmptyRecentFrames();
    }

    /**
     * Prunes completely consumed/empty frames from the top of the buffer so they don't remain as ghost frames.
     */
    public synchronized void trimEmptyRecentFrames() {
        while (frameCount > 0) {
            int index = (writeHead - 1 + capacity) % capacity;
            TickFrame frame = frames[index];
            if (frame == null || frame.isEmpty()) {
                if (frame != null) {
                    framesMemory -= frame.getEstimatedMemoryBytes();
                    untrackFrameSharedAllocations(frame);
                    frames[index] = null;
                }
                writeHead = index;
                frameCount--;
            } else {
                break;
            }
        }
        if (framesMemory < 0) framesMemory = 0;
        if (sharedSnapshotsMemory < 0) sharedSnapshotsMemory = 0;
    }

    /** Resize live, retaining newest intervals and scaling the memory budget from its 30-second baseline. */
    public synchronized void resizeSeconds(int seconds) {
        if (seconds < 1 || seconds > 60) throw new IllegalArgumentException("History must be between 1 and 60 seconds");
        var retained = new ArrayList<>(getFramesForRewind(seconds * 20));
        Collections.reverse(retained);
        maxMemoryBytes = memoryBudget(seconds * 20);
        capacity = seconds * 20;
        frames = new TickFrame[capacity];
        writeHead = 0;
        frameCount = 0;
        framesMemory = 0;
        sharedSnapshotsMemory = 0;
        activeEntitySnapshots.clear();
        activeInventories.clear();
        for (var frame : retained) {
            frames[writeHead] = frame;
            writeHead = (writeHead + 1) % capacity;
            frameCount++;
            framesMemory += frame.getEstimatedMemoryBytes();
            trackFrameSharedAllocations(frame);
        }
        enforceMemoryBudget();
    }

    public synchronized void clear() {
        for (int i = 0; i < capacity; i++) {
            frames[i] = null;
        }
        writeHead = 0;
        frameCount = 0;
        framesMemory = 0;
        sharedSnapshotsMemory = 0;
        activeEntitySnapshots.clear();
        activeInventories.clear();
        memoryEvictedFrames = 0;
    }

    public synchronized long getMaxMemoryBytes() { return maxMemoryBytes; }
    public synchronized long getMemoryEvictedFrames() { return memoryEvictedFrames; }
    public synchronized int getFrameCount() { return frameCount; }
    public synchronized int getCapacity() { return capacity; }
    public synchronized int getActiveEntitySnapshotsCount() { return activeEntitySnapshots.size(); }
    public synchronized int getActiveInventoriesCount() { return activeInventories.size(); }

    public synchronized long getBufferRetainedBytes() {
        return Math.max(0L, framesMemory + sharedSnapshotsMemory);
    }

    public synchronized long getFramesMemoryBytes() { return Math.max(0L, framesMemory); }
    public synchronized long getSharedSnapshotsMemoryBytes() { return Math.max(0L, sharedSnapshotsMemory); }

    public synchronized long getTotalEstimatedBytes() {
        long total = getBufferRetainedBytes();
        if (this == TickRecorder.getInstance().getTimelineBuffer()) {
            total += TickRecorder.getInstance().getBaselineMemoryBytes();
            total += LocalRewind.getActivePlaybackMemoryBytes();
        }
        return total;
    }

    public boolean isRecording() { return recording; }
    public void setRecording(boolean recording) { this.recording = recording; }

    public boolean isRewinding() { return rewinding; }
    public void setRewinding(boolean rewinding) { this.rewinding = rewinding; }

    public boolean isFrozen() { return frozen; }
    public void setFrozen(boolean frozen) { this.frozen = frozen; }
}

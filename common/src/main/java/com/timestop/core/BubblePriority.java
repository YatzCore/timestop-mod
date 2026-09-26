package com.timestop.core;

import com.timestop.item.WatchTier;
import java.util.UUID;

/** Shared comparator: positive means the first field wins. */
public final class BubblePriority {
    public static int compare(WatchTier aTier, TimeMode aMode, UUID aId, WatchTier bTier, TimeMode bMode, UUID bId) {
        int tier = Integer.compare(aTier.getTierLevel(), bTier.getTierLevel());
        if (tier != 0) return tier;
        int stasis = Boolean.compare(aMode == TimeMode.TIME_STOP, bMode == TimeMode.TIME_STOP);
        return stasis != 0 ? stasis : aId.compareTo(bId);
    }
    private BubblePriority() {}
}

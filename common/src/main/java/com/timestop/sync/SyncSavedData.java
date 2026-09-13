package com.timestop.sync;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SyncSavedData extends SavedData {
    private static final String DATA_NAME = "timestop_sync";
    public static final String LEGACY_DATA_NAME = "timestop_friends";

    private final Map<UUID, Set<UUID>> syncedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastKnownNames = new ConcurrentHashMap<>();

    public SyncSavedData() {
    }

    public static SyncSavedData load(CompoundTag tag) {
        SyncSavedData data = new SyncSavedData();
        ListTag list = tag.contains("SyncPairs", Tag.TAG_LIST)
                ? tag.getList("SyncPairs", Tag.TAG_COMPOUND)
                : tag.getList("FriendPairs", Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag pairTag = list.getCompound(i);
            if (pairTag.hasUUID("PlayerA") && pairTag.hasUUID("PlayerB")) {
                UUID a = pairTag.getUUID("PlayerA");
                UUID b = pairTag.getUUID("PlayerB");
                data.syncedPlayers.computeIfAbsent(a, k -> ConcurrentHashMap.newKeySet()).add(b);
                data.syncedPlayers.computeIfAbsent(b, k -> ConcurrentHashMap.newKeySet()).add(a);
            }
        }

        ListTag namesList = tag.getList("NameCache", Tag.TAG_COMPOUND);
        for (int i = 0; i < namesList.size(); i++) {
            CompoundTag nTag = namesList.getCompound(i);
            if (nTag.hasUUID("UUID") && nTag.contains("Name")) {
                data.lastKnownNames.put(nTag.getUUID("UUID"), nTag.getString("Name"));
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        Set<String> processedPairs = ConcurrentHashMap.newKeySet();

        for (Map.Entry<UUID, Set<UUID>> entry : syncedPlayers.entrySet()) {
            UUID a = entry.getKey();
            for (UUID b : entry.getValue()) {
                String pairKey = a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
                if (processedPairs.add(pairKey)) {
                    CompoundTag pairTag = new CompoundTag();
                    pairTag.putUUID("PlayerA", a);
                    pairTag.putUUID("PlayerB", b);
                    list.add(pairTag);
                }
            }
        }
        tag.put("SyncPairs", list);

        ListTag namesList = new ListTag();
        for (Map.Entry<UUID, String> entry : lastKnownNames.entrySet()) {
            CompoundTag nTag = new CompoundTag();
            nTag.putUUID("UUID", entry.getKey());
            nTag.putString("Name", entry.getValue());
            namesList.add(nTag);
        }
        tag.put("NameCache", namesList);

        return tag;
    }

    public Map<UUID, Set<UUID>> getSyncedPlayers() {
        return syncedPlayers;
    }

    public Map<UUID, String> getLastKnownNames() {
        return lastKnownNames;
    }

    public static String getDataName() {
        return DATA_NAME;
    }
}

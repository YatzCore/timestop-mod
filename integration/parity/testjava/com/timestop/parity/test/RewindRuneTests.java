package com.timestop.parity.test;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
public class RewindRuneTests {
    public static net.minecraft.server.level.ServerPlayer survivalPlayer(GameTestHelper h) {
        var player = new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "rune-test"), net.minecraft.server.level.ClientInformation.createDefault());
        // This fixture has no client to answer required optional-mod handshakes.
        // Register the entity directly and absorb both forms of outgoing packets.
        player.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player, net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            // NeoForge queries negotiated channels during world-time synchronization.
            // This isolated fixture has no real client or negotiated payloads.
            public boolean hasChannel(net.minecraft.resources.ResourceLocation type) {return false;}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) {}
        };
        player.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(0, 2, 0)));
        h.getLevel().addNewPlayer(player);
        // Rewind resolves players through PlayerList, not the level entity list.
        // Populate both indexes without negotiating a nonexistent client.
        var list=h.getLevel().getServer().getPlayerList();
        try {
            var indexField=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
            indexField.setAccessible(true);
            @SuppressWarnings("unchecked") var index=(java.util.Map<java.util.UUID,net.minecraft.server.level.ServerPlayer>)indexField.get(list);
            var playersField=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");playersField.setAccessible(true);
            @SuppressWarnings("unchecked") var players=(java.util.List<net.minecraft.server.level.ServerPlayer>)playersField.get(list);
            players.add(player);index.put(player.getUUID(),player);
            var infoField=GameTestHelper.class.getDeclaredField("testInfo");infoField.setAccessible(true);
            var info=(net.minecraft.gametest.framework.GameTestInfo)infoField.get(h);
            info.addListener(new net.minecraft.gametest.framework.GameTestListener() {
                private void cleanup(){players.remove(player);index.remove(player.getUUID(),player);player.discard();}
                public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) {}
                public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){cleanup();}
                public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){cleanup();}
                public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestInfo next,net.minecraft.gametest.framework.GameTestRunner r){}
            });
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Could not register isolated regression player",e);}
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        return player;
    }


}

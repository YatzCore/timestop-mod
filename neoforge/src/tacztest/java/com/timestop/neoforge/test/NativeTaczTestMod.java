package com.timestop.neoforge.test;
@net.neoforged.fml.common.Mod("timestop_tacz_tests")
public final class NativeTaczTestMod {
    public NativeTaczTestMod() {
        // NeoForge deliberately disables automatic GameTest discovery in production.
        if (Boolean.getBoolean("timestop.nativeTaczPackagedTests"))
            for(var method:NativeTaczTests.class.getDeclaredMethods())
                net.minecraft.gametest.framework.GameTestRegistry.register(method,java.util.Set.of("timestop"));
    }
}

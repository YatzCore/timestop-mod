package com.timestop.fabric.test;

import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;

final class PortTestSupport {
    private PortTestSupport() {}
    static int command(Commands commands, CommandSourceStack source, String command) {
        int[] result = {0};
        commands.performPrefixedCommand(source.withCallback((success, value) -> result[0] = success ? value : 0), command);
        return result[0];
    }
}

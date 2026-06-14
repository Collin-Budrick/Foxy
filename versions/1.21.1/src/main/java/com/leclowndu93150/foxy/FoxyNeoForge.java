package com.leclowndu93150.foxy;

import me.cortex.voxy.commonImpl.VoxyCommon;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod("foxy")
public class FoxyNeoForge {

    public FoxyNeoForge(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (VoxyCommon.isAvailable()) {
            FoxyCommands.register(event.getDispatcher());
        }
    }
}

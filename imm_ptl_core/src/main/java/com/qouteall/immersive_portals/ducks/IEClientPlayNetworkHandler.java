package com.qouteall.immersive_portals.ducks;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.registry.DynamicRegistryManager;

import java.util.Map;

public interface IEClientPlayNetworkHandler {
    void ip_setWorld(ClientWorld world);
    
    Map getPlayerListEntries();
    
    void setPlayerListEntries(Map value);
    
    void portal_setRegistryManager(DynamicRegistryManager arg);
}

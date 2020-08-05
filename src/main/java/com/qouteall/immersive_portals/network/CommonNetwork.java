package com.qouteall.immersive_portals.network;

import com.qouteall.immersive_portals.CGlobal;
import com.qouteall.immersive_portals.Helper;
import com.qouteall.immersive_portals.ducks.IEClientPlayNetworkHandler;
import com.qouteall.immersive_portals.ducks.IEClientWorld;
import com.qouteall.immersive_portals.ducks.IEParticleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;

public class CommonNetwork {
    
    public static final MinecraftClient client = MinecraftClient.getInstance();
    
    private static int reportedError = 0;
    private static boolean isProcessingRedirectedMessage = false;
    
    public static void processRedirectedPacket(RegistryKey<World> dimension, Packet packet) {
        Runnable func = () -> {
            if (client.world == null) {
                return;
            }
            try {
                client.getProfiler().push("process_redirected_packet");
                
                ClientWorld packetWorld = CGlobal.clientWorldLoader.getWorld(dimension);
                
                doProcessRedirectedMessage(packetWorld, packet);
            }
            finally {
                client.getProfiler().pop();
            }
        };
        
        // execute mod packets in my task list
        // because if it's in minecraft task list, invoking client.execute() will get delayed
        // and the dimension redirect won't work
        if (packet instanceof CustomPayloadS2CPacket) {
            ClientNetworkingTaskList.executeOnMyTaskList(func);
        }
        else {
            ClientNetworkingTaskList.executeOnRenderThread(func);
        }
    }
    
    public static void doProcessRedirectedMessage(
        ClientWorld packetWorld,
        Packet packet
    ) {
        boolean oldIsProcessing = CommonNetwork.isProcessingRedirectedMessage;

//        if (oldIsProcessing) {
//            Helper.log("Nested redirect " + packet);
//        }
        
        isProcessingRedirectedMessage = true;
        
        ClientPlayNetworkHandler netHandler = ((IEClientWorld) packetWorld).getNetHandler();
        
        if ((netHandler).getWorld() != packetWorld) {
            ((IEClientPlayNetworkHandler) netHandler).setWorld(packetWorld);
            Helper.err("The world field of client net handler is wrong");
        }
        
        ClientWorld originalWorld = client.world;
        //some packet handling may use mc.world so switch it
        client.world = packetWorld;
        ((IEParticleManager) client.particleManager).mySetWorld(packetWorld);
        
        originalWorld.getProfiler().push("handle_redirected_packet");
        try {
            packet.apply(netHandler);
        }
        catch (Throwable e) {
            if (reportedError < 200) {
                reportedError += 1;
                throw new IllegalStateException(
                    "handling packet in " + packetWorld.getRegistryKey(), e
                );
            }
        }
        finally {
            client.world = originalWorld;
            ((IEParticleManager) client.particleManager).mySetWorld(originalWorld);
            
            originalWorld.getProfiler().pop();
            
            isProcessingRedirectedMessage = oldIsProcessing;
        }
    }
}

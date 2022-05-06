package qouteall.imm_ptl.core.platform_specific;

import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.PehkuiInterface;
import qouteall.imm_ptl.core.ducks.IECamera;
import qouteall.imm_ptl.core.portal.Portal;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleTypes;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.Validate;

public class PehkuiInterfaceInitializer {
    
    public static class OnPehkuiPresent extends PehkuiInterface.Invoker {
        
        private boolean loggedGetBaseMessage = false;
        private boolean loggedSetBaseMessage = false;
        private boolean loggedComputeThirdPersonMessage = false;
        private boolean loggedComputeBlockReachMessage = false;
        private boolean loggedComputeMotionMessage = false;
        
        @Override
        public boolean isPehkuiPresent() {
            return true;
        }
        
        @Override
        public void onClientPlayerTeleported(Portal portal) {
            onPlayerTeleportedClient(portal);
        }
        
        @Override
        public void onServerEntityTeleported(Entity entity, Portal portal) {
            onEntityTeleportedServer(entity, portal);
        }
        
        private void logErrorMessage(Entity entity, Throwable e, String situation) {
            e.printStackTrace();
            entity.sendSystemMessage(
                new LiteralText("Something went wrong with Pehkui (" + situation + ")"),
                Util.NIL_UUID
            );
        }
        
        @Override
        public float getBaseScale(Entity entity, float tickDelta) {
            try {
                return ScaleTypes.BASE.getScaleData(entity).getBaseScale(tickDelta);
            }
            catch (Throwable e) {
                if (!loggedGetBaseMessage) {
                    loggedGetBaseMessage = true;
                    logErrorMessage(entity, e, "getting scale");
                }
                return super.getBaseScale(entity, tickDelta);
            }
        }
        
        @Override
        public void setBaseScale(Entity entity, float scale) {
            try {
                final ScaleData data = ScaleTypes.BASE.getScaleData(entity);
                data.setScale(scale);
                data.setBaseScale(scale);
            }
            catch (Throwable e) {
                if (!loggedSetBaseMessage) {
                    loggedSetBaseMessage = true;
                    logErrorMessage(entity, e, "setting scale");
                }
            }
        }
        
        @Override
        public float computeThirdPersonScale(Entity entity, float tickDelta) {
            try {
                return ScaleTypes.THIRD_PERSON.getScaleData(entity).getScale(tickDelta);
            }
            catch (Throwable e) {
                if (!loggedComputeThirdPersonMessage) {
                    loggedComputeThirdPersonMessage = true;
                    logErrorMessage(entity, e, "getting third person scale");
                }
                return super.computeThirdPersonScale(entity, tickDelta);
            }
        }
        
        @Override
        public float computeBlockReachScale(Entity entity, float tickDelta) {
            try {
                return ScaleTypes.BLOCK_REACH.getScaleData(entity).getScale(tickDelta);
            }
            catch (Throwable e) {
                if (!loggedComputeBlockReachMessage) {
                    loggedComputeBlockReachMessage = true;
                    logErrorMessage(entity, e, "getting reach scale");
                }
                return super.computeBlockReachScale(entity, tickDelta);
            }
        }
        
        @Override
        public float computeMotionScale(Entity entity, float tickDelta) {
            try {
                return ScaleTypes.MOTION.getScaleData(entity).getScale(tickDelta);
            }
            catch (Throwable e) {
                if (!loggedComputeMotionMessage) {
                    loggedComputeMotionMessage = true;
                    logErrorMessage(entity, e, "getting motion scale");
                }
                return super.computeMotionScale(entity, tickDelta);
            }
        }
    }
    
    public static void init() {
        PehkuiInterface.invoker = new OnPehkuiPresent();
    }
    
    @Environment(EnvType.CLIENT)
    private static void onPlayerTeleportedClient(Portal portal) {
        if (portal.hasScaling() && portal.teleportChangesScale) {
            MinecraftClient client = MinecraftClient.getInstance();
            
            ClientPlayerEntity player = client.player;
            
            Validate.notNull(player);
            
            doScalingForEntity(player, portal);
            
            IECamera camera = (IECamera) client.gameRenderer.getCamera();
            camera.setCameraY(
                ((float) (camera.getCameraY() * portal.scaling)),
                ((float) (camera.getLastCameraY() * portal.scaling))
            );
        }
    }
    
    private static void onEntityTeleportedServer(Entity entity, Portal portal) {
        if (portal.hasScaling() && portal.teleportChangesScale) {
            doScalingForEntity(entity, portal);
            
            if (entity.getVehicle() != null) {
                doScalingForEntity(entity.getVehicle(), portal);
            }
        }
    }
    
    private static void doScalingForEntity(Entity entity, Portal portal) {
        Vec3d eyePos = McHelper.getEyePos(entity);
        Vec3d lastTickEyePos = McHelper.getLastTickEyePos(entity);
        
        float oldScale = PehkuiInterface.invoker.getBaseScale(entity);
        float newScale = transformScale(portal, oldScale);
        
        if (!entity.world.isClient && isScaleIllegal(newScale)) {
            newScale = 1;
            entity.sendSystemMessage(
                new LiteralText("Scale out of range"),
                Util.NIL_UUID
            );
        }
        
        PehkuiInterface.invoker.setBaseScale(entity, newScale);
        
        if (!entity.world.isClient) {
            IPGlobal.serverTaskList.addTask(() -> {
                McHelper.setEyePos(entity, eyePos, lastTickEyePos);
                McHelper.updateBoundingBox(entity);
                return true;
            });
        }
        else {
            McHelper.setEyePos(entity, eyePos, lastTickEyePos);
            McHelper.updateBoundingBox(entity);
        }
    }
    
    private static float transformScale(Portal portal, float oldScale) {
        float result = (float) (oldScale * portal.scaling);
        
        // avoid deviation accumulating
        if (Math.abs(result - 1.0f) < 0.0001f) {
            result = 1;
        }
        
        return result;
    }
    
    private static boolean isScaleIllegal(float scale) {
        return (scale > IPGlobal.scaleLimit) || (scale < (1.0f / (IPGlobal.scaleLimit * 2)));
    }
    
}

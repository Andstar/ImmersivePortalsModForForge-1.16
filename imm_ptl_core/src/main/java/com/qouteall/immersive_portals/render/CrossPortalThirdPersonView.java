package com.qouteall.immersive_portals.render;

import com.mojang.datafixers.util.Pair;
import com.qouteall.immersive_portals.CGlobal;
import com.qouteall.immersive_portals.ClientWorldLoader;
import com.qouteall.immersive_portals.PehkuiInterface;
import com.qouteall.immersive_portals.commands.PortalCommand;
import com.qouteall.immersive_portals.ducks.IECamera;
import com.qouteall.immersive_portals.portal.Portal;
import com.qouteall.immersive_portals.render.context_management.RenderStates;
import com.qouteall.immersive_portals.render.context_management.WorldRenderInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;

public class CrossPortalThirdPersonView {
    public static final MinecraftClient client = MinecraftClient.getInstance();
    
    // if rendered, return true
    public static boolean renderCrossPortalThirdPersonView() {
        if (!(isThirdPerson() || TransformationManager.isIsometricView)) {
            return false;
        }
        
        Entity cameraEntity = client.cameraEntity;
        
        Camera resuableCamera = new Camera();
        float cameraY = ((IECamera) RenderStates.originalCamera).getCameraY();
        ((IECamera) resuableCamera).setCameraY(cameraY, cameraY);
        resuableCamera.update(
            client.world, cameraEntity,
            true,
            isFrontView(),
            RenderStates.tickDelta
        );
        Vec3d originalCameraPos = resuableCamera.getPos();
        Vec3d isometricAdjustedOriginalCameraPos =
            TransformationManager.getIsometricAdjustedCameraPos(resuableCamera);
        
        resuableCamera.update(
            client.world, cameraEntity,
            false, false, RenderStates.tickDelta
        );
        Vec3d playerHeadPos = resuableCamera.getPos();
        
        Pair<Portal, Vec3d> portalHit = PortalCommand.raytracePortals(
            client.world, playerHeadPos, isometricAdjustedOriginalCameraPos, true
        ).orElse(null);
        
        if (portalHit == null) {
            return false;
        }
        
        Portal portal = portalHit.getFirst();
        Vec3d hitPos = portalHit.getSecond();
        
        double distance = getThirdPersonMaxDistance();
        
        Vec3d thirdPersonPos = originalCameraPos.subtract(playerHeadPos).normalize()
            .multiply(distance).add(playerHeadPos);
        
        if (!portal.isInteractable()) {
            return false;
        }
        
        Vec3d renderingCameraPos = getThirdPersonCameraPos(thirdPersonPos, portal, hitPos);
        ((IECamera) RenderStates.originalCamera).portal_setPos(renderingCameraPos);
        
        
        WorldRenderInfo worldRenderInfo = new WorldRenderInfo(
            ClientWorldLoader.getWorld(portal.dimensionTo), renderingCameraPos, portal.getAdditionalCameraTransformation(), false, null,
            client.options.viewDistance
        );
        
        CGlobal.renderer.invokeWorldRendering(worldRenderInfo);
        
        return true;
    }
    
    private static boolean isFrontView() {
        return client.options.getPerspective().isFrontView();
    }
    
    private static boolean isThirdPerson() {
        return !client.options.getPerspective().isFirstPerson();
    }
    
    /**
     * {@link Camera#update(BlockView, Entity, boolean, boolean, float)}
     */
    private static Vec3d getThirdPersonCameraPos(Vec3d endPos, Portal portal, Vec3d startPos) {
        Vec3d rtStart = portal.transformPoint(startPos);
        Vec3d rtEnd = portal.transformPoint(endPos);
        BlockHitResult blockHitResult = portal.getDestinationWorld().raycast(
            new RaycastContext(
                rtStart,
                rtEnd,
                RaycastContext.ShapeType.VISUAL,
                RaycastContext.FluidHandling.NONE,
                client.cameraEntity
            )
        );
        
        if (blockHitResult == null) {
            return rtStart.add(rtEnd.subtract(rtStart).normalize().multiply(
                getThirdPersonMaxDistance()
            ));
        }
        
        return blockHitResult.getPos();
    }
    
    private static double getThirdPersonMaxDistance() {
        return 4.0d * PehkuiInterface.computeThirdPersonScale.apply(client.player, client.getTickDelta());
    }

//    private static Vec3d getThirdPersonCameraPos(Portal portalHit, Camera resuableCamera) {
//        return CHelper.withWorldSwitched(
//            client.cameraEntity,
//            portalHit,
//            () -> {
//                World destinationWorld = portalHit.getDestinationWorld();
//                resuableCamera.update(
//                    destinationWorld,
//                    client.cameraEntity,
//                    true,
//                    isInverseView(),
//                    RenderStates.tickDelta
//                );
//                return resuableCamera.getPos();
//            }
//        );
//    }
}

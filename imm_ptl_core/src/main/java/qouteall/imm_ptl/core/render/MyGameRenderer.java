package qouteall.imm_ptl.core.render;

import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.render.Frustum;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.q_misc_util.Helper;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.OFInterface;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient;
import qouteall.imm_ptl.core.ducks.IEGameRenderer;
import qouteall.imm_ptl.core.ducks.IEMinecraftClient;
import qouteall.imm_ptl.core.ducks.IEParticleManager;
import qouteall.imm_ptl.core.ducks.IEPlayerListEntry;
import qouteall.imm_ptl.core.ducks.IEWorldRenderer;
import qouteall.imm_ptl.core.ducks.IEWorldRendererChunkInfo;
import qouteall.q_misc_util.my_util.LimitedLogger;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalLike;
import qouteall.imm_ptl.core.render.context_management.DimensionRenderHelper;
import qouteall.imm_ptl.core.render.context_management.FogRendererContext;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderDimensionRedirect;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderEffect;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.Util;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class MyGameRenderer {
    public static MinecraftClient client = MinecraftClient.getInstance();
    
    private static final LimitedLogger limitedLogger = new LimitedLogger(10);
    
    // portal rendering and outer world rendering uses different buffer builder storages
    // theoretically every layer of portal rendering should have its own buffer builder storage
    private static BufferBuilderStorage secondaryBufferBuilderStorage = new BufferBuilderStorage();
    
    public static void renderWorldNew(
        WorldRenderInfo worldRenderInfo,
        Consumer<Runnable> invokeWrapper
    ) {
        WorldRenderInfo.pushRenderInfo(worldRenderInfo);
        
        switchAndRenderTheWorld(
            worldRenderInfo.world,
            worldRenderInfo.cameraPos,
            worldRenderInfo.cameraPos,
            invokeWrapper,
            worldRenderInfo.renderDistance,
            worldRenderInfo.doRenderHand
        );
        
        WorldRenderInfo.popRenderInfo();
    }
    
    private static void switchAndRenderTheWorld(
        ClientWorld newWorld,
        Vec3d thisTickCameraPos,
        Vec3d lastTickCameraPos,
        Consumer<Runnable> invokeWrapper,
        int renderDistance,
        boolean doRenderHand
    ) {
        resetGlStates();
        
        Entity cameraEntity = client.cameraEntity;
        
        Vec3d oldEyePos = McHelper.getEyePos(cameraEntity);
        Vec3d oldLastTickEyePos = McHelper.getLastTickEyePos(cameraEntity);
        
        RegistryKey<World> oldEntityDimension = cameraEntity.world.getRegistryKey();
        ClientWorld oldEntityWorld = ((ClientWorld) cameraEntity.world);
        
        RegistryKey<World> newDimension = newWorld.getRegistryKey();
        
        //switch the camera entity pos
        McHelper.setEyePos(cameraEntity, thisTickCameraPos, lastTickCameraPos);
        cameraEntity.world = newWorld;
        
        WorldRenderer worldRenderer = ClientWorldLoader.getWorldRenderer(newDimension);
        
        CHelper.checkGlError();
        
        float tickDelta = RenderStates.tickDelta;
        
        if (IPCGlobal.useHackedChunkRenderDispatcher) {
            ((IEWorldRenderer) worldRenderer).ip_getBuiltChunkStorage().updateCameraPosition(
                cameraEntity.getX(),
                cameraEntity.getZ()
            );
        }
        
        
        IEGameRenderer ieGameRenderer = (IEGameRenderer) client.gameRenderer;
        DimensionRenderHelper helper =
            ClientWorldLoader.getDimensionRenderHelper(
                RenderDimensionRedirect.getRedirectedDimension(newDimension)
            );
//        PlayerListEntry playerListEntry = CHelper.getClientPlayerListEntry();
        Camera newCamera = new Camera();
        
        //store old state
        WorldRenderer oldWorldRenderer = client.worldRenderer;
        LightmapTextureManager oldLightmap = client.gameRenderer.getLightmapTextureManager();
//        GameMode oldGameMode = playerListEntry.getGameMode();
        boolean oldNoClip = client.player.noClip;
        boolean oldDoRenderHand = ieGameRenderer.getDoRenderHand();
        OFInterface.createNewRenderInfosNormal.accept(worldRenderer);
        ObjectArrayList oldVisibleChunks = ((IEWorldRenderer) oldWorldRenderer).ip_getVisibleChunks();
        HitResult oldCrosshairTarget = client.crosshairTarget;
        Camera oldCamera = client.gameRenderer.getCamera();
        ShaderEffect oldTransparencyShader = ((IEWorldRenderer) worldRenderer).portal_getTransparencyShader();
        BufferBuilderStorage oldBufferBuilder = ((IEWorldRenderer) worldRenderer).ip_getBufferBuilderStorage();
        BufferBuilderStorage oldClientBufferBuilder = client.getBufferBuilders();
        boolean oldChunkCullingEnabled = client.chunkCullingEnabled;
        Frustum oldFrustum = ((IEWorldRenderer) worldRenderer).portal_getFrustum();
        
        ((IEWorldRenderer) oldWorldRenderer).ip_setVisibleChunks(new ObjectArrayList());
        
        int oldRenderDistance = ((IEWorldRenderer) worldRenderer).portal_getRenderDistance();
        WorldRenderingPipeline irisPipeline = IrisInterface.invoker.getPipeline(worldRenderer);
        
        //switch
        ((IEMinecraftClient) client).setWorldRenderer(worldRenderer);
        client.world = newWorld;
        ieGameRenderer.setLightmapTextureManager(helper.lightmapTexture);
        
        client.getBlockEntityRenderDispatcher().world = newWorld;
//        ((IEPlayerListEntry) playerListEntry).setGameMode(GameMode.SPECTATOR);
        client.player.noClip = true;
        client.gameRenderer.setRenderHand(doRenderHand);
        
        FogRendererContext.swappingManager.pushSwapping(
            RenderDimensionRedirect.getRedirectedDimension(newDimension)
        );
        ((IEParticleManager) client.particleManager).ip_setWorld(newWorld);
        if (BlockManipulationClient.remotePointedDim == newDimension) {
            client.crosshairTarget = BlockManipulationClient.remoteHitResult;
        }
        ieGameRenderer.setCamera(newCamera);
        
        if (IPGlobal.useSecondaryEntityVertexConsumer) {
            ((IEWorldRenderer) worldRenderer).ip_setBufferBuilderStorage(secondaryBufferBuilderStorage);
            ((IEMinecraftClient) client).setBufferBuilderStorage(secondaryBufferBuilderStorage);
        }
        
        Object newSodiumContext = SodiumInterface.invoker.createNewContext();
        SodiumInterface.invoker.switchContextWithCurrentWorldRenderer(newSodiumContext);
        
        ((IEWorldRenderer) worldRenderer).portal_setTransparencyShader(null);
        ((IEWorldRenderer) worldRenderer).portal_setRenderDistance(renderDistance);
        
        IrisInterface.invoker.setPipeline(worldRenderer, null);
        
        if (IPGlobal.looseVisibleChunkIteration) {
            client.chunkCullingEnabled = false;
        }
        
        //update lightmap
        if (!RenderStates.isDimensionRendered(newDimension)) {
            helper.lightmapTexture.update(0);
        }
        
        //invoke rendering
        try {
            invokeWrapper.accept(() -> {
                client.getProfiler().push("render_portal_content");
                client.gameRenderer.renderWorld(
                    tickDelta,
                    Util.getMeasuringTimeNano(),
                    new MatrixStack()
                );
                client.getProfiler().pop();
            });
        }
        catch (Throwable e) {
            limitedLogger.invoke(e::printStackTrace);
        }
    
        SodiumInterface.invoker.switchContextWithCurrentWorldRenderer(newSodiumContext);
        
        //recover
        
        ((IEMinecraftClient) client).setWorldRenderer(oldWorldRenderer);
        client.world = oldEntityWorld;
        ieGameRenderer.setLightmapTextureManager(oldLightmap);
        client.getBlockEntityRenderDispatcher().world = oldEntityWorld;
//        ((IEPlayerListEntry) playerListEntry).setGameMode(oldGameMode);
        client.player.noClip = oldNoClip;
        client.gameRenderer.setRenderHand(oldDoRenderHand);
        
        ((IEParticleManager) client.particleManager).ip_setWorld(oldEntityWorld);
        client.crosshairTarget = oldCrosshairTarget;
        ieGameRenderer.setCamera(oldCamera);
        
        ((IEWorldRenderer) worldRenderer).portal_setTransparencyShader(oldTransparencyShader);
        
        FogRendererContext.swappingManager.popSwapping();
        
        ((IEWorldRenderer) oldWorldRenderer).ip_setVisibleChunks(oldVisibleChunks);
        
        ((IEWorldRenderer) worldRenderer).ip_setBufferBuilderStorage(oldBufferBuilder);
        ((IEMinecraftClient) client).setBufferBuilderStorage(oldClientBufferBuilder);
        
        ((IEWorldRenderer) worldRenderer).portal_setRenderDistance(oldRenderDistance);
        
        ((IEWorldRenderer) worldRenderer).portal_setFrustum(oldFrustum);
        
        IrisInterface.invoker.setPipeline(worldRenderer, irisPipeline);
        
        
        if (IPGlobal.looseVisibleChunkIteration) {
            client.chunkCullingEnabled = oldChunkCullingEnabled;
        }
        
        client.getEntityRenderDispatcher()
            .configure(
                client.world,
                oldCamera,
                client.targetedEntity
            );
        
        CHelper.checkGlError();
        
        //restore the camera entity pos
        cameraEntity.world = oldEntityWorld;
        McHelper.setEyePos(cameraEntity, oldEyePos, oldLastTickEyePos);
        
        resetGlStates();
    }
    
    /**
     *
     */
    public static void resetGlStates() {
//        GlStateManager.disableAlphaTest();
//        GlStateManager._enableCull();
//        GlStateManager._disableBlend();
//        net.minecraft.client.render.DiffuseLighting.disableGuiDepthLighting();
//        MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager().disable();
//        client.gameRenderer.getOverlayTexture().teardownOverlayColor();
    }
    
    public static void renderPlayerItself(Runnable doRenderEntity) {
        EntityRenderDispatcher entityRenderDispatcher =
            ((IEWorldRenderer) client.worldRenderer).ip_getEntityRenderDispatcher();
        PlayerListEntry playerListEntry = CHelper.getClientPlayerListEntry();
        GameMode originalGameMode = RenderStates.originalGameMode;
        
        Entity player = client.cameraEntity;
        assert player != null;
        
        Vec3d oldPos = player.getPos();
        Vec3d oldLastTickPos = McHelper.lastTickPosOf(player);
        GameMode oldGameMode = playerListEntry.getGameMode();
        
        McHelper.setPosAndLastTickPos(
            player, RenderStates.originalPlayerPos, RenderStates.originalPlayerLastTickPos
        );
        ((IEPlayerListEntry) playerListEntry).setGameMode(originalGameMode);
        
        doRenderEntity.run();
        
        McHelper.setPosAndLastTickPos(
            player, oldPos, oldLastTickPos
        );
        ((IEPlayerListEntry) playerListEntry).setGameMode(oldGameMode);
    }
    
    public static void resetFogState() {
        Camera camera = client.gameRenderer.getCamera();
        float g = client.gameRenderer.getViewDistance();
        
        Vec3d cameraPos = camera.getPos();
        double d = cameraPos.getX();
        double e = cameraPos.getY();
        double f = cameraPos.getZ();
        
        boolean bl2 = client.world.getSkyProperties().useThickFog(MathHelper.floor(d), MathHelper.floor(e)) ||
            client.inGameHud.getBossBarHud().shouldThickenFog();
        
        BackgroundRenderer.applyFog(camera, BackgroundRenderer.FogType.FOG_TERRAIN, Math.max(g - 16.0F, 32.0F), bl2);
        BackgroundRenderer.setFogBlack();
    }
    
    public static void updateFogColor() {
        BackgroundRenderer.render(
            client.gameRenderer.getCamera(),
            RenderStates.tickDelta,
            client.world,
            client.options.viewDistance,
            client.gameRenderer.getSkyDarkness(RenderStates.tickDelta)
        );
    }
    
    public static void resetDiffuseLighting(MatrixStack matrixStack) {
        DiffuseLighting.enableForLevel(matrixStack.peek().getModel());
    }
    
    public static void pruneRenderList(ObjectList<?> visibleChunks) {
        if (PortalRendering.isRendering()) {
            if (IPGlobal.cullSectionsBehind) {
                // this thing has no optimization effect -_-
                
                PortalLike renderingPortal = PortalRendering.getRenderingPortal();
                
                renderingPortal.doAdditionalRenderingCull(visibleChunks);
            }
        }
    }
    
    // frustum culling is done elsewhere
    // it's culling the sections behind the portal
    public static void cullRenderingSections(
        ObjectList<?> visibleChunks, PortalLike renderingPortal
    ) {
        if (renderingPortal instanceof Portal) {
            int firstInsideOne = Helper.indexOf(
                visibleChunks,
                obj -> {
                    ChunkBuilder.BuiltChunk builtChunk =
                        ((IEWorldRendererChunkInfo) obj).getBuiltChunk();
                    Box boundingBox = builtChunk.boundingBox;
                    
                    return FrustumCuller.isTouchingInsideContentArea(
                        ((Portal) renderingPortal), boundingBox
                    );
                }
            );
            
            if (firstInsideOne != -1) {
                visibleChunks.removeElements(0, firstInsideOne);
            }
            else {
                visibleChunks.clear();
            }
        }
    }
    
}

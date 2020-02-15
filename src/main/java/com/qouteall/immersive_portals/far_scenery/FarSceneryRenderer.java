package com.qouteall.immersive_portals.far_scenery;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.qouteall.immersive_portals.CGlobal;
import com.qouteall.immersive_portals.McHelper;
import com.qouteall.immersive_portals.ducks.IEGameRenderer;
import com.qouteall.immersive_portals.ducks.IEMinecraftClient;
import com.qouteall.immersive_portals.render.MyRenderHelper;
import com.qouteall.immersive_portals.render.SecondaryFrameBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import static org.lwjgl.opengl.GL11.GL_QUADS;

public class FarSceneryRenderer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static SecondaryFrameBuffer[] buffers;
    private static boolean isRenderingScenery = false;
    private static double[] cullingEquation;
    private static Vec3d boxCenter = Vec3d.ZERO;
    private static double currDistance = 1000;
    
    public static boolean isFarSceneryEnabled = false;
    
    public static void init() {
        buffers = new SecondaryFrameBuffer[6];
        buffers[0] = new SecondaryFrameBuffer();
        buffers[1] = new SecondaryFrameBuffer();
        buffers[2] = new SecondaryFrameBuffer();
        buffers[3] = new SecondaryFrameBuffer();
        buffers[4] = new SecondaryFrameBuffer();
        buffers[5] = new SecondaryFrameBuffer();
    }
    
    public static void updateFarScenery(
        double distance
    ) {
        boxCenter = mc.gameRenderer.getCamera().getPos();
        currDistance = distance;
        
        for (SecondaryFrameBuffer buffer : buffers) {
            buffer.prepare(1000, 1000);
        }
        
        for (Direction direction : Direction.values()) {
            renderFarSceneryFace(direction, distance);
        }
    }
    
    private static void renderFarSceneryFace(
        Direction direction,
        double distance
    ) {
        SecondaryFrameBuffer buffer = buffers[direction.ordinal()];
        
        Framebuffer oldFrameBuffer = mc.getFramebuffer();
        
        ((IEMinecraftClient) mc).setFrameBuffer(buffer.fb);
        buffer.fb.beginWrite(true);
        
        GlStateManager.clearColor(1, 0, 1, 1);
        GlStateManager.clearDepth(1);
        GlStateManager.clear(
            GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT,
            MinecraftClient.IS_SYSTEM_MAC
        );
        
        doRenderFarScenery(direction);
        
        ((IEMinecraftClient) mc).setFrameBuffer(oldFrameBuffer);
        oldFrameBuffer.beginWrite(true);
    }
    
    private static void doRenderFarScenery(
        Direction direction
    ) {
        Vec3d viewDirection = new Vec3d(direction.getVector());
        
        ClientPlayerEntity player = mc.player;
        float oldYaw = player.yaw;
        float oldPitch = player.pitch;
        
        setPlayerRotation(direction, player);
        updateCullingEquation(currDistance, direction);
        
        isRenderingScenery = true;
        ((IEGameRenderer) mc.gameRenderer).setIsRenderingPanorama(true);
        CGlobal.myGameRenderer.renderWorld(
            MyRenderHelper.partialTicks,
            mc.worldRenderer,
            mc.world,
            mc.gameRenderer.getCamera().getPos(),
            mc.world
        );
        isRenderingScenery = false;
        ((IEGameRenderer) mc.gameRenderer).setIsRenderingPanorama(false);
        
        player.yaw = oldYaw;
        player.pitch = oldPitch;
    }
    
    private static void setPlayerRotation(
        Direction direction,
        ClientPlayerEntity entity
    ) {
        switch (direction) {
            case DOWN:
                entity.pitch = 90;
                entity.yaw = 0;
                break;
            case UP:
                entity.pitch = -90;
                entity.yaw = 0;
                break;
            case NORTH:
                entity.pitch = 0;
                entity.yaw = 180;
                break;
            case SOUTH:
                entity.pitch = 0;
                entity.yaw = 0;
                break;
            case WEST:
                entity.pitch = 0;
                entity.yaw = 90;
                break;
            case EAST:
                entity.pitch = 0;
                entity.yaw = -90;
                break;
        }
    }
    
    public static boolean isRendering() {
        return isRenderingScenery;
    }
    
    public static double[] getCullingEquation() {
        return cullingEquation;
    }
    
    private static void updateCullingEquation(double distance, Direction direction) {
        Vec3d planeNormal = new Vec3d(direction.getVector());
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        
        Vec3d portalPos = planeNormal.multiply(distance);
        
        //equation: planeNormal * p + c > 0
        //-planeNormal * portalCenter = c
        double c = planeNormal.multiply(-1).dotProduct(portalPos);
        
        cullingEquation = new double[]{
            planeNormal.x,
            planeNormal.y,
            planeNormal.z,
            c
        };
    }
    
    private static void renderFarSceneryBox(
        MatrixStack matrixStack,
        double distance,
        Vec3d offsetToCamera
    ) {
        matrixStack.push();
        matrixStack.translate(
            -offsetToCamera.x, -offsetToCamera.y, -offsetToCamera.z
        );
        matrixStack.scale((float) distance, (float) distance, (float) distance);

//        matrixStack.scale(100,100,100);
        
        RenderSystem.enableTexture();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        //RenderSystem.enableBlend();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableAlphaTest();
        RenderSystem.disableFog();
        RenderSystem.shadeModel(7425);
        
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        
        bufferBuilder.begin(GL_QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        bufferBuilder.vertex(-1.0D, -1.0D, 1.0D).texture(1.0F, 0.0F)
            .color(255, 255, 255, 255).next();
        bufferBuilder.vertex(-1.0D, 1.0D, 1.0D).texture(1.0F, 1.0F)
            .color(255, 255, 255, 255).next();
        bufferBuilder.vertex(1.0D, 1.0D, 1.0D).texture(0.0F, 1.0F)
            .color(255, 255, 255, 255).next();
        bufferBuilder.vertex(1.0D, -1.0D, 1.0D).texture(0.0F, 0.0F)
            .color(255, 255, 255, 255).next();
        myBindTexture(buffers[Direction.SOUTH.ordinal()]);
        McHelper.runWithTransformation(
            matrixStack,
            tessellator::draw
        );
        
        bufferBuilder.begin(GL_QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        bufferBuilder.vertex(1.0D, -1.0D, 1.0D).texture(1.0F, 0.0F)
            .color(255, 255, 255, 255).next();
        bufferBuilder.vertex(1.0D, 1.0D, 1.0D).texture(1.0F, 1.0F)
            .color(255, 255, 255, 255).next();
        bufferBuilder.vertex(1.0D, 1.0D, -1.0D).texture(0.0F, 1.0F)
            .color(255, 255, 255, 255).next();
        bufferBuilder.vertex(1.0D, -1.0D, -1.0D).texture(0.0F, 0.0F)
            .color(255, 255, 255, 255).next();
        myBindTexture(buffers[Direction.EAST.ordinal()]);
        McHelper.runWithTransformation(
            matrixStack,
            tessellator::draw
        );
        
        bufferBuilder.begin(GL_QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        bufferBuilder.vertex(1.0D, -1.0D, -1.0D).texture(1.0F, 0.0F)
            .color(255, 255, 255, 255).next();
        bufferBuilder.vertex(1.0D, 1.0D, -1.0D).texture(1.0F, 1.0F)
            .color(255, 255, 255, 255).next();
        bufferBuilder.vertex(-1.0D, 1.0D, -1.0D).texture(0.0F, 1.0F)
            .color(255, 255, 255, 255).next();
        bufferBuilder.vertex(-1.0D, -1.0D, -1.0D).texture(0.0F, 0.0F)
            .color(255, 255, 255, 255).next();
        myBindTexture(buffers[Direction.NORTH.ordinal()]);
        McHelper.runWithTransformation(
            matrixStack,
            tessellator::draw
        );
        
        bufferBuilder.begin(GL_QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        bufferBuilder.vertex(-1.0D, -1.0D, -1.0D).texture(1.0F, 0.0F)
            .color(255, 255, 255, 255).next();
        bufferBuilder.vertex(-1.0D, 1.0D, -1.0D).texture(1.0F, 1.0F)
            .color(255, 255, 255, 255).next();
        bufferBuilder.vertex(-1.0D, 1.0D, 1.0D).texture(0.0F, 1.0F)
            .color(255, 255, 255, 255).next();
        bufferBuilder.vertex(-1.0D, -1.0D, 1.0D).texture(0.0F, 0.0F)
            .color(255, 255, 255, 255).next();
        myBindTexture(buffers[Direction.WEST.ordinal()]);
        McHelper.runWithTransformation(
            matrixStack,
            tessellator::draw
        );
        
        bufferBuilder.begin(GL_QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        bufferBuilder.vertex(-1.0D, -1.0D, -1.0D).texture(1.0F, 0.0F)
            .color(255, 255, 255, 255).next();
        bufferBuilder.vertex(-1.0D, -1.0D, 1.0D).texture(1.0F, 1.0F)
            .color(255, 255, 255, 255).next();
        bufferBuilder.vertex(1.0D, -1.0D, 1.0D).texture(0.0F, 1.0F)
            .color(255, 255, 255, 255).next();
        bufferBuilder.vertex(1.0D, -1.0D, -1.0D).texture(0.0F, 0.0F)
            .color(255, 255, 255, 255).next();
        myBindTexture(buffers[Direction.DOWN.ordinal()]);
        McHelper.runWithTransformation(
            matrixStack,
            tessellator::draw
        );
        
        bufferBuilder.begin(GL_QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        bufferBuilder.vertex(-1.0D, 1.0D, 1.0D).texture(1.0F, 0.0F)
            .color(255, 255, 255, 255).next();
        bufferBuilder.vertex(-1.0D, 1.0D, -1.0D).texture(1.0F, 1.0F)
            .color(255, 255, 255, 255).next();
        bufferBuilder.vertex(1.0D, 1.0D, -1.0D).texture(0.0F, 1.0F)
            .color(255, 255, 255, 255).next();
        bufferBuilder.vertex(1.0D, 1.0D, 1.0D).texture(0.0F, 0.0F)
            .color(255, 255, 255, 255).next();
        myBindTexture(buffers[Direction.UP.ordinal()]);
        McHelper.runWithTransformation(
            matrixStack,
            tessellator::draw
        );
        
        matrixStack.pop();
    }
    
    private static void myBindTexture(SecondaryFrameBuffer fb) {
        GlStateManager.activeTexture(GL13.GL_TEXTURE0);
        
        GlStateManager.bindTexture(fb.fb.colorAttachment);
        GlStateManager.texParameter(3553, 10241, 9729);
        GlStateManager.texParameter(3553, 10240, 9729);
        GlStateManager.texParameter(3553, 10242, 10496);
        GlStateManager.texParameter(3553, 10243, 10496);
    }
    
    public static void onBeforeTranslucentRendering(
        MatrixStack matrixStack
    ) {
        if (!isFarSceneryEnabled) {
            return;
        }
        
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        
        renderFarSceneryBox(
            matrixStack,
            currDistance,
            cameraPos.subtract(boxCenter)
        );
    }
}
package org.vivecraft.mixin.client_vr.renderer;

import org.joml.Matrix4f;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.mod_compat_vr.iris.IrisHelper;
import org.vivecraft.client.Xplat;
import org.vivecraft.client_xr.render_pass.RenderPassManager;
import org.vivecraft.client_xr.render_pass.RenderPassType;
import org.vivecraft.client_vr.extensions.GameRendererExtension;
import org.vivecraft.client_vr.extensions.LevelRendererExtension;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.settings.VRSettings;

import javax.annotation.Nullable;

// priority 999 to inject before iris, for the vrFast rendering
@Mixin(value = LevelRenderer.class, priority = 999)
public abstract class LevelRendererVRMixin implements ResourceManagerReloadListener, AutoCloseable, LevelRendererExtension {

    @Unique
    @Nullable
    public RenderTarget alphaSortVROccludedFramebuffer;
    @Unique
    @Nullable
    public RenderTarget alphaSortVRUnoccludedFramebuffer;
    @Unique
    @Nullable
    public RenderTarget alphaSortVRHandsFramebuffer;
    @Unique
    public float selR;
    @Unique
    public float selG;
    @Unique
    public float selB;

    @Unique
    private Entity capturedEntity;

    @Final
    @Shadow
    private Minecraft minecraft;
    @Shadow
    private ClientLevel level;
    @Shadow
    private PostChain transparencyChain;
    @Shadow
    private RenderTarget translucentTarget;
    @Shadow
    private RenderTarget itemEntityTarget;
    @Shadow
    private RenderTarget particlesTarget;
    @Shadow
    private RenderTarget weatherTarget;
    @Shadow
    private RenderTarget cloudsTarget;
    @Shadow
    private PostChain entityEffect;
    @Shadow
    private RenderTarget entityTarget;
    @Shadow
    private boolean needsFullRenderChunkUpdate;
    @Final
    @Shadow
    private RenderBuffers renderBuffers;
    @Unique
    private Entity renderedEntity;

    @Shadow
    protected abstract void renderHitOutline(PoseStack poseStack, VertexConsumer buffer, Entity entity, double d, double e, double g, BlockPos blockpos, BlockState blockstate);

    @Shadow
    private static void renderShape(PoseStack poseStack, VertexConsumer vertexConsumer, VoxelShape voxelShape, double d, double e, double f, float g, float h, float i, float j) {
    }

    @Override
    public Entity getRenderedEntity() {
        return this.renderedEntity;
    }

//NotFixed
//    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;floor(D)I", ordinal = 0), method = "renderSnowAndRain")
//    public int rain1(double d) {
//        Vec3 vec3 = ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_render.getEye(RenderPass.CENTER).getPosition();
//        if (ClientDataHolderVR.getInstance().currentPass == RenderPass.THIRD || ClientDataHolderVR.getInstance().currentPass == RenderPass.CAMERA) {
//            vec3 = ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_render.getEye(ClientDataHolderVR.getInstance().currentPass).getPosition();
//        }
//        return Mth.floor(vec3.x);
//    }
//
//    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;floor(D)I", ordinal = 1), method = "renderSnowAndRain")
//    public int rain2(double d) {
//        Vec3 vec3 = ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_render.getEye(RenderPass.CENTER).getPosition();
//        if (ClientDataHolderVR.getInstance().currentPass == RenderPass.THIRD || ClientDataHolderVR.getInstance().currentPass == RenderPass.CAMERA) {
//            vec3 = ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_render.getEye(ClientDataHolderVR.getInstance().currentPass).getPosition();
//        }
//        return Mth.floor(vec3.y);
//    }
//
//    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;floor(D)I", ordinal = 2), method = "renderSnowAndRain")
//    public int rain3(double d) {
//        Vec3 vec3 = ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_render.getEye(RenderPass.CENTER).getPosition();
//        if (ClientDataHolderVR.getInstance().currentPass == RenderPass.THIRD || ClientDataHolderVR.getInstance().currentPass == RenderPass.CAMERA) {
//            vec3 = ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_render.getEye(ClientDataHolderVR.getInstance().currentPass).getPosition();
//        }
//        return Mth.floor(vec3.z);
//    }

//	NotFixed
//	/**
//	 * @author
//	 * @reason
//	 */
//	@Overwrite
//	public void onResourceManagerReload(ResourceManager pResourceManager) {
//		ClientDataHolderVR.getInstance().vrRenderer.reinitFrameBuffers("Resource Reload");
//	}

//	@Redirect(at = @At(value = "NEW", target = "Lnet/minecraft/resources/ResourceLocation;"), method = "initTransparency")
//	public ResourceLocation vrShader(String string) {
//		return new ResourceLocation("shaders/post/vrtransparency.json");
//	}
//
//    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;graphicsChanged()V"), method = "allChanged()V")
//    public void removeGraphich(LevelRenderer l) {
//        return;
//    }

    //Moved for sodium
//	@Restriction(conflict = @Condition("sodium"))
//	@Inject(at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/LevelRenderer;needsFullRenderChunkUpdate:Z", ordinal = 1, shift = Shift.AFTER), method = "setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V")
//	public void alwaysUpdateCull(Camera camera, Frustum frustum, boolean bl, boolean bl2, CallbackInfo info) {
//		this.needsFullRenderChunkUpdate = true;
//	}

    /*
     * Start `renderLevel` lighting poll
     */

    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;pollLightUpdates()V"), method = "renderLevel")
    public void cancelPollLightUpdates(ClientLevel instance) {
    }

    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;runUpdates(IZZ)I"), method = "renderLevel")
    public int runLightingUpdates(LevelLightEngine instance, int i, boolean bl, boolean bl2) {
        if (RenderPassType.isVanilla() || ClientDataHolderVR.getInstance().currentPass == RenderPass.LEFT) {
            this.level.getProfiler().popPush("light_update_queue");
            this.level.pollLightUpdates();
            this.level.getProfiler().popPush("light_updates");
            boolean flag = this.level.isLightUpdateQueueEmpty();
            this.minecraft.level.getChunkSource().getLightEngine().runUpdates(Integer.MAX_VALUE, flag, true);
            instance.runUpdates(i, bl, bl2);
        }
        if (!RenderPassType.isVanilla()) {
            this.setShaderGroup();
        }
        return 0;
    }

    /*
     * End `renderLevel` lighting poll
     */

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;getRenderDistance()F", shift = Shift.BEFORE),
            method = "renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V ")
    public void stencil(PoseStack poseStack, float f, long l, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, CallbackInfo info) {
        this.minecraft.getProfiler().popPush("stencil");
        ((GameRendererExtension) gameRenderer).drawEyeStencil(false);
    }

    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z"), method = "renderLevel")
    public boolean captureEntity(EntityRenderDispatcher instance, Entity entity, Frustum frustum, double d, double e, double f) {
        this.capturedEntity = entity;
        return instance.shouldRender(entity, frustum, d, e, f);
    }

    @Inject(at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;tickCount:I", shift = Shift.BEFORE), method = "renderLevel")
    public void restoreLoc1(PoseStack poseStack, float f, long l, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, CallbackInfo ci) {
        if (!RenderPassType.isVanilla() && capturedEntity == camera.getEntity()) {
            ((GameRendererExtension) gameRenderer).restoreRVEPos((LivingEntity) capturedEntity);
        }
        this.renderedEntity = capturedEntity;
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderEntity(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V", shift = Shift.AFTER), method = "renderLevel")
    public void restoreLoc2(PoseStack poseStack, float f, long l, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, CallbackInfo ci) {
        if (!RenderPassType.isVanilla() && capturedEntity == camera.getEntity()) {
            ((GameRendererExtension) gameRenderer).cacheRVEPos((LivingEntity) capturedEntity);
            ((GameRendererExtension) gameRenderer).setupRVE();
        }
        this.renderedEntity = null;
    }

    @Inject(at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;hitResult:Lnet/minecraft/world/phys/HitResult;", ordinal = 1), method = "renderLevel")
    public void interactOutline(PoseStack poseStack, float f, long l, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, CallbackInfo ci) {
        this.level.getProfiler().popPush("outline");
        selR = selG = selB = 1f;
        Vec3 vec3 = camera.getPosition();
        double d = vec3.x();
        double e = vec3.y();
        double g = vec3.z();
        for (int c = 0; c < 2; c++) {
            if (ClientDataHolderVR.getInstance().interactTracker.isInteractActive(c) && (ClientDataHolderVR.getInstance().interactTracker.inBlockHit[c] != null || ClientDataHolderVR.getInstance().interactTracker.bukkit[c])) {
                BlockPos blockpos = ClientDataHolderVR.getInstance().interactTracker.inBlockHit[c] != null ? ClientDataHolderVR.getInstance().interactTracker.inBlockHit[c].getBlockPos() : BlockPos.containing(ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_render.getController(c).getPosition());
                BlockState blockstate = this.level.getBlockState(blockpos);
                this.renderHitOutline(poseStack, this.renderBuffers.bufferSource().getBuffer(RenderType.lines()), camera.getEntity(), d, e, g, blockpos, blockstate);
            }
        }
    }

    @ModifyVariable(at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;hitResult:Lnet/minecraft/world/phys/HitResult;", ordinal = 1), method = "renderLevel", ordinal = 0, argsOnly = true)
    public boolean noBlockoutlineOnInteract(boolean renderBlockOutline) {
        // don't draw the block outline when the interaction outline is active
        return !(ClientDataHolderVR.getInstance().interactTracker.isInteractActive(0) && (ClientDataHolderVR.getInstance().interactTracker.inBlockHit[0] != null || ClientDataHolderVR.getInstance().interactTracker.bukkit[0])) && renderBlockOutline;
    }

    @Inject(at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;applyModelViewMatrix()V", ordinal = 1), method = "renderLevel")
    public void renderBukkake(PoseStack poseStack, float f, long l, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, CallbackInfo ci) {
        this.level.getProfiler().popPush("render bukkake");
    }

    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", ordinal = 13), method = "renderLevel")
    public void blackOutline(ProfilerFiller instance, String s) {
        selR = selG = selB = 0f;
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderStateShard$OutputStateShard;clearRenderState()V", ordinal = 0), method = "renderLevel")
    public void renderFabulous(PoseStack poseStack, float f, long l, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, CallbackInfo ci) {
        boolean menuHandleft = ((GameRendererExtension) gameRenderer).isInMenuRoom() || this.minecraft.screen != null || KeyboardHandler.Showing;
        boolean menuhandright = menuHandleft || ClientDataHolderVR.getInstance().interactTracker.hotbar >= 0 && ClientDataHolderVR.getInstance().vrSettings.vrTouchHotbar;
        ((GameRendererExtension) gameRenderer).renderVRFabulous(f, (LevelRenderer) (Object) this, menuhandright, menuHandleft, poseStack);
    }

    private boolean menuHandleft;
    private boolean menuhandright;

    private boolean guiRendered = false;

    @Inject(at = @At("HEAD"), method = "renderLevel")
    public void resetGuiRendered(PoseStack poseStack, float f, long l, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, CallbackInfo ci) {
        guiRendered = false;
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", shift = Shift.BEFORE, ordinal = 17),
            method = "renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V")
    public void renderFast1(PoseStack poseStack, float f, long l, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, CallbackInfo info) {
        if (RenderPassType.isVanilla()) {
            return;
        }

        menuHandleft = ((GameRendererExtension) gameRenderer).isInMenuRoom() || this.minecraft.screen != null || KeyboardHandler.Showing;
        menuhandright = menuHandleft || ClientDataHolderVR.getInstance().interactTracker.hotbar >= 0 && ClientDataHolderVR.getInstance().vrSettings.vrTouchHotbar;
        ((GameRendererExtension) gameRenderer).renderVrFast(f, false, menuhandright, menuHandleft, poseStack);

        if ((Xplat.isModLoaded("iris") || Xplat.isModLoaded("oculus")) && IrisHelper.isShaderActive() && ClientDataHolderVR.getInstance().vrSettings.shaderGUIRender == VRSettings.ShaderGUIRender.BEFORE_TRANSLUCENT_SOLID) {
            // shaders active, and render gui before translucents
            ((GameRendererExtension) gameRenderer).renderVrFast(f, true, menuhandright, menuHandleft, poseStack);
            guiRendered = true;
        }
    }

    @Inject(at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = Shift.BEFORE, ordinal = 3),
            method = "renderLevel")
    public void renderFast2(PoseStack poseStack, float f, long l, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, CallbackInfo ci) {
        if (RenderPassType.isVanilla()) {
            return;
        }

        if (transparencyChain == null && (!((Xplat.isModLoaded("iris") || Xplat.isModLoaded("oculus")) && IrisHelper.isShaderActive()) || ClientDataHolderVR.getInstance().vrSettings.shaderGUIRender == VRSettings.ShaderGUIRender.AFTER_TRANSLUCENT)) {
            // no shaders, or shaders, and gui after translucents
            ((GameRendererExtension) gameRenderer).renderVrFast(f, true, menuhandright, menuHandleft, poseStack);
            guiRendered = true;
        }
    }

    // if the gui didn't render yet, and something canceled the level renderer, render it now.
    // or if shaders are on, and option AFTER_SHADER is selected
    @Inject(at = @At("RETURN"), method = "renderLevel")
    public void renderFast2Final(PoseStack poseStack, float f, long l, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, CallbackInfo info) {
        if (RenderPassType.isVanilla()) {
            return;
        }

        if (!guiRendered && transparencyChain == null) {
            ((GameRendererExtension) gameRenderer).renderVrFast(f, true, menuhandright, menuHandleft, poseStack);
            guiRendered = true;
        }
    }

    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderShape(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/shapes/VoxelShape;DDDFFFF)V"), method = "renderHitOutline")
    public void colorHitBox(PoseStack poseStack, VertexConsumer vertexConsumer, VoxelShape voxelShape, double d, double e, double f, float g, float h, float i, float j) {
        renderShape(poseStack, vertexConsumer, voxelShape, d, e, f, this.selR, this.selG, this.selB, j);
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;playLocalSound(Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V", ordinal = 11, shift = Shift.BEFORE), method = "levelEvent")
    public void levelEvent1011(int i, BlockPos blockPos, int j, CallbackInfo ci) {
        boolean playerNear = this.minecraft.player != null && this.minecraft.player.isAlive() && this.minecraft.player.blockPosition().distSqr(blockPos) < 25.0D;

        if (playerNear) {
            ClientDataHolderVR.getInstance().vr.triggerHapticPulse(0, 250);
        }
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;playLocalSound(Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V", ordinal = 12, shift = Shift.BEFORE), method = "levelEvent")
    public void levelEvent1012(int i, BlockPos blockPos, int j, CallbackInfo ci) {
        boolean playerNear = this.minecraft.player != null && this.minecraft.player.isAlive() && this.minecraft.player.blockPosition().distSqr(blockPos) < 25.0D;

        if (playerNear) {
            ClientDataHolderVR.getInstance().vr.triggerHapticPulse(0, 250);
        }
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;playLocalSound(Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V", ordinal = 13, shift = Shift.BEFORE), method = "levelEvent")
    public void levelEvent1013(int i, BlockPos blockPos, int j, CallbackInfo ci) {
        boolean playerNear = this.minecraft.player != null && this.minecraft.player.isAlive() && this.minecraft.player.blockPosition().distSqr(blockPos) < 25.0D;

        if (playerNear) {
            ClientDataHolderVR.getInstance().vr.triggerHapticPulse(0, 250);
        }
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;playLocalSound(Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V", ordinal = 14, shift = Shift.BEFORE), method = "levelEvent")
    public void levelEvent1014(int i, BlockPos blockPos, int j, CallbackInfo ci) {
        boolean playerNear = this.minecraft.player != null && this.minecraft.player.isAlive() && this.minecraft.player.blockPosition().distSqr(blockPos) < 25.0D;

        if (playerNear) {
            ClientDataHolderVR.getInstance().vr.triggerHapticPulse(0, 250);
        }
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;playLocalSound(Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V", ordinal = 19, shift = Shift.BEFORE), method = "levelEvent")
    public void levelEvent1019(int i, BlockPos blockPos, int j, CallbackInfo ci) {
        boolean playerNear = this.minecraft.player != null && this.minecraft.player.isAlive() && this.minecraft.player.blockPosition().distSqr(blockPos) < 25.0D;

        if (playerNear) {
            ClientDataHolderVR.getInstance().vr.triggerHapticPulse(0, 750);
            ClientDataHolderVR.getInstance().vr.triggerHapticPulse(1, 750);

        }
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;playLocalSound(Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V", ordinal = 20, shift = Shift.BEFORE), method = "levelEvent")
    public void levelEvent1020(int i, BlockPos blockPos, int j, CallbackInfo ci) {
        boolean playerNear = this.minecraft.player != null && this.minecraft.player.isAlive() && this.minecraft.player.blockPosition().distSqr(blockPos) < 25.0D;

        if (playerNear) {
            ClientDataHolderVR.getInstance().vr.triggerHapticPulse(0, 750);
            ClientDataHolderVR.getInstance().vr.triggerHapticPulse(1, 750);

        }
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;playLocalSound(Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V", ordinal = 21, shift = Shift.BEFORE), method = "levelEvent")
    public void levelEvent1021(int i, BlockPos blockPos, int j, CallbackInfo ci) {
        boolean playerNear = this.minecraft.player != null && this.minecraft.player.isAlive() && this.minecraft.player.blockPosition().distSqr(blockPos) < 25.0D;

        if (playerNear) {
            ClientDataHolderVR.getInstance().vr.triggerHapticPulse(0, 750);
            ClientDataHolderVR.getInstance().vr.triggerHapticPulse(1, 750);
        }
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;playLocalSound(Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V", ordinal = 28, shift = Shift.BEFORE), method = "levelEvent")
    public void levelEvent1030(int i, BlockPos blockPos, int j, CallbackInfo ci) {
        boolean playerNear = this.minecraft.player != null && this.minecraft.player.isAlive() && this.minecraft.player.blockPosition().distSqr(blockPos) < 25.0D;

        if (playerNear) {
            ClientDataHolderVR.getInstance().vr.triggerHapticPulse(0, 500);
        }
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;playLocalSound(Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V", ordinal = 29, shift = Shift.BEFORE), method = "levelEvent")
    public void levelEvent1031(int i, BlockPos blockPos, int j, CallbackInfo ci) {
        boolean playerNear = this.minecraft.player != null && this.minecraft.player.isAlive() && this.minecraft.player.blockPosition().distSqr(blockPos) < 25.0D;

        if (playerNear) {
            ClientDataHolderVR.getInstance().vr.triggerHapticPulse(0, 1250);
            ClientDataHolderVR.getInstance().vr.triggerHapticPulse(1, 1250);
        }
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;playLocalSound(Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V", ordinal = 33, shift = Shift.BEFORE), method = "levelEvent")
    public void levelEvent1036(int i, BlockPos blockPos, int j, CallbackInfo ci) {
        boolean playerNear = this.minecraft.player != null && this.minecraft.player.isAlive() && this.minecraft.player.blockPosition().distSqr(blockPos) < 25.0D;

        if (playerNear) {
            ClientDataHolderVR.getInstance().vr.triggerHapticPulse(0, 250);
        }
    }

    @Inject(at = @At("TAIL"), method = "initOutline")
    public void captureOutlineChain(CallbackInfo ci){
        RenderPassManager.INSTANCE.vanillaOutlineChain = entityEffect;
    }

    @Inject(at = @At("TAIL"), method = "initTransparency")
    public void captureTransparencyChain(CallbackInfo ci){
        RenderPassManager.INSTANCE.vanillaTransparencyChain = transparencyChain;
    }
    @Inject(at = @At("TAIL"), method = "deinitTransparency")
    public void removeTransparencyChain(CallbackInfo ci){
        RenderPassManager.INSTANCE.vanillaTransparencyChain = null;
    }

    @Inject(at = @At("TAIL"), method = "close")
    public void removePostChains(CallbackInfo ci){
        RenderPassManager.INSTANCE.vanillaOutlineChain = null;
        RenderPassManager.INSTANCE.vanillaTransparencyChain = null;
    }

    @Override
    public void restoreVanillaPostChains(){
        transparencyChain = RenderPassManager.INSTANCE.vanillaTransparencyChain;

        if (transparencyChain != null) {
            this.translucentTarget = transparencyChain.getTempTarget("translucent");
            this.itemEntityTarget = transparencyChain.getTempTarget("itemEntity");
            this.particlesTarget = transparencyChain.getTempTarget("particles");
            this.weatherTarget = transparencyChain.getTempTarget("weather");
            this.cloudsTarget = transparencyChain.getTempTarget("clouds");
        } else {
            this.translucentTarget = null;
            this.itemEntityTarget = null;
            this.particlesTarget = null;
            this.weatherTarget = null;
            this.cloudsTarget = null;
        }

        entityEffect = RenderPassManager.INSTANCE.vanillaOutlineChain;
        if (entityEffect != null) {
            this.entityTarget = entityEffect.getTempTarget("final");
        } else {
            this.entityTarget = null;
        }
    }

    public void setShaderGroup() {
        PostChain transparencyChain = RenderPassManager.wrp.transparencyChain;

        if (transparencyChain != null) {
            this.transparencyChain = transparencyChain;
            this.translucentTarget = transparencyChain.getTempTarget("translucent");
            this.itemEntityTarget = transparencyChain.getTempTarget("itemEntity");
            this.particlesTarget = transparencyChain.getTempTarget("particles");
            this.weatherTarget = transparencyChain.getTempTarget("weather");
            this.cloudsTarget = transparencyChain.getTempTarget("clouds");
            this.alphaSortVRHandsFramebuffer = transparencyChain.getTempTarget("vrhands");
            this.alphaSortVROccludedFramebuffer = transparencyChain.getTempTarget("vroccluded");
            this.alphaSortVRUnoccludedFramebuffer = transparencyChain.getTempTarget("vrunoccluded");
        } else {
            this.transparencyChain = null;
            this.translucentTarget = null;
            this.itemEntityTarget = null;
            this.particlesTarget = null;
            this.weatherTarget = null;
            this.cloudsTarget = null;
            this.alphaSortVRHandsFramebuffer = null;
            this.alphaSortVROccludedFramebuffer = null;
            this.alphaSortVRUnoccludedFramebuffer = null;
        }

        PostChain outlineChain = RenderPassManager.wrp.outlineChain;

        if (outlineChain != null) {
            this.entityEffect = outlineChain;
            this.entityTarget = outlineChain.getTempTarget("final");
        } else {
            this.entityEffect = null;
            this.entityTarget = null;
        }
    }

    @Override
    public RenderTarget getAlphaSortVROccludedFramebuffer() {
        return alphaSortVROccludedFramebuffer;
    }

    @Override
    public RenderTarget getAlphaSortVRUnoccludedFramebuffer() {
        return alphaSortVRUnoccludedFramebuffer;
    }

    @Override
    public RenderTarget getAlphaSortVRHandsFramebuffer() {
        return alphaSortVRHandsFramebuffer;
    }
}
package org.vivecraft.client_vr.gameplay.trackers;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import org.vivecraft.client.Xplat;
import org.vivecraft.client.network.ClientNetworking;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.extensions.PlayerExtension;
import org.vivecraft.client_vr.gameplay.VRPlayer;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.common.network.CommonNetworkHelper;
import org.vivecraft.common.utils.math.Vector3;
import org.vivecraft.mod_compat_vr.pehkui.PehkuiHelper;

import java.nio.ByteBuffer;

public class BowTracker extends Tracker {
    private double lastcontrollersDist;
    private double lastcontrollersDot;
    private double controllersDist;
    private double controllersDot;
    private double currentDraw;
    private double lastDraw;
    public boolean isDrawing;
    private boolean pressed;
    private boolean lastpressed;
    private boolean canDraw;
    private boolean lastcanDraw;
    public long startDrawTime;
    private final double notchDotThreshold = 20.0D;
    private double maxDraw;
    private final long maxDrawMillis = 1100L;
    private Vec3 aim;
    float tsNotch = 0.0F;
    int hapcounter = 0;
    int lasthapStep = 0;

    public BowTracker(Minecraft mc, ClientDataHolderVR dh) {
        super(mc, dh);
    }

    public Vec3 getAimVector() {
        return this.aim;
    }

    public float getDrawPercent() {
        return (float) (this.currentDraw / this.maxDraw);
    }

    public boolean isNotched() {
        return this.canDraw || this.isDrawing;
    }

    public static boolean isBow(ItemStack itemStack) {
        if (itemStack == ItemStack.EMPTY) {
            return false;
        } else if (ClientDataHolderVR.getInstance().vrSettings.bowMode == VRSettings.BowMode.OFF) {
            return false;
        } else if (ClientDataHolderVR.getInstance().vrSettings.bowMode == VRSettings.BowMode.VANILLA) {
            return itemStack.getItem() == Items.BOW;
        } else {
            return itemStack.getItem().getUseAnimation(itemStack) == UseAnim.BOW;
        }
    }

    public static boolean isHoldingBow(LivingEntity e, InteractionHand hand) {
        return !ClientDataHolderVR.getInstance().vrSettings.seated && isBow(e.getItemInHand(hand));
    }

    public static boolean isHoldingBowEither(LivingEntity e) {
        return isHoldingBow(e, InteractionHand.MAIN_HAND) || isHoldingBow(e, InteractionHand.OFF_HAND);
    }

    public boolean isActive(LocalPlayer p) {
        if (p == null) {
            return false;
        } else if (this.mc.gameMode == null) {
            return false;
        } else if (!p.isAlive()) {
            return false;
        } else if (p.isSleeping()) {
            return false;
        } else {
            return isHoldingBow(p, InteractionHand.MAIN_HAND) || isHoldingBow(p, InteractionHand.OFF_HAND);
        }
    }

    public boolean isCharged() {
        return Util.getMillis() - this.startDrawTime >= this.maxDrawMillis;
    }

    public void reset(LocalPlayer player) {
        this.isDrawing = false;
    }

    public EntryPoint getEntryPoint() {
        return EntryPoint.SPECIAL_ITEMS;
    }

    public void doProcess(LocalPlayer player) {
        VRData vrdata = this.dh.vrPlayer.vrdata_world_render;

        if (vrdata == null) {
            vrdata = this.dh.vrPlayer.vrdata_world_pre;
        }

        VRPlayer vrplayer = this.dh.vrPlayer;

        if (this.dh.vrSettings.seated) {
            this.aim = vrdata.getController(0).getCustomVector(new Vec3(0.0D, 0.0D, 1.0D));
        } else {
            this.lastcontrollersDist = this.controllersDist;
            this.lastcontrollersDot = this.controllersDot;
            this.lastpressed = this.pressed;
            this.lastDraw = this.currentDraw;
            this.lastcanDraw = this.canDraw;
            this.maxDraw = (double) this.mc.player.getBbHeight() * 0.22D;

            if (Xplat.isModLoaded("pehkui")) {
                // this is meant to be relative to the base Bb height, not the scaled one
                this.maxDraw /= PehkuiHelper.getPlayerBbScale(player, mc.getFrameTime());
            }

            // these are wrong since this is called every frame but should be fine so long as they're only compared to each other.
            Vec3 rightPos = vrdata.getController(0).getPosition();
            Vec3 leftPos = vrdata.getController(1).getPosition();
            //

            this.controllersDist = leftPos.distanceTo(rightPos);

            Vec3 up = new Vec3(0.0D, 1.0D * vrdata.worldScale, 0.0D);

            Vec3 stringPos = vrdata.getHand(1).getCustomVector(up).scale(this.maxDraw * 0.5D).add(leftPos);
            double notchDist = rightPos.distanceTo(stringPos);

            this.aim = rightPos.subtract(leftPos).normalize();

            Vec3 rightaim3 = vrdata.getController(0).getCustomVector(new Vec3(0.0D, 0.0D, -1.0D));

            Vector3 rightAim = new Vector3((float) rightaim3.x, (float) rightaim3.y, (float) rightaim3.z);

            Vec3 leftGripDown = vrdata.getHand(1).getCustomVector(new Vec3(0.0D, -1.0D, 0.0D));

            Vector3 leftAim = new Vector3((float) leftGripDown.x, (float) leftGripDown.y, (float) leftGripDown.z);

            this.controllersDot = (180D / Math.PI) * Math.acos(leftAim.dot(rightAim));

            this.pressed = this.mc.options.keyAttack.isDown();

            float notchDistThreshold = 0.15F * vrdata.worldScale;

            boolean main = isHoldingBow(player, InteractionHand.MAIN_HAND);

            InteractionHand hand = main ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;

            ItemStack ammo = ItemStack.EMPTY;
            ItemStack bow = ItemStack.EMPTY;

            if (main) { // autofind ammo.
                bow = player.getMainHandItem();
                ammo = player.getProjectile(bow);
            } else { // BYOA
                if (player.getMainHandItem().is(ItemTags.ARROWS)) {
                    ammo = player.getMainHandItem();
                }

                bow = player.getOffhandItem();
            }

            int stage0 = bow.getUseDuration();
            int stage1 = bow.getUseDuration() - 15;
            int stage2 = 0;

            if (ammo != ItemStack.EMPTY && notchDist <= (double) notchDistThreshold && this.controllersDot <= 20.0D) {

                // can draw
                if (!this.canDraw) {
                    this.startDrawTime = Util.getMillis();
                }

                this.canDraw = true;
                this.tsNotch = (float) Util.getMillis();

                if (!this.isDrawing) {
                    ((PlayerExtension) player).vivecraft$setItemInUseClient(bow, hand);
                    ((PlayerExtension) player).vivecraft$setItemInUseCountClient(stage0);
                    //Minecraft.getInstance().physicalGuiManager.preClickAction();
                }
            } else if ((float) Util.getMillis() - this.tsNotch > 500.0F) {
                this.canDraw = false;
                ((PlayerExtension) player).vivecraft$setItemInUseClient(ItemStack.EMPTY, hand);
            }

            if (!this.isDrawing && this.canDraw && this.pressed && !this.lastpressed) {
                // draw
                this.isDrawing = true;
                //Minecraft.getInstance().physicalGuiManager.preClickAction();
                this.mc.gameMode.useItem(player, hand); // server
            }

            if (this.isDrawing && !this.pressed && this.lastpressed && (double) this.getDrawPercent() > 0.0D) {
                this.dh.vr.triggerHapticPulse(0, 500);
                this.dh.vr.triggerHapticPulse(1, 3000);
                ServerboundCustomPayloadPacket pack = ClientNetworking.getVivecraftClientPacket(CommonNetworkHelper.PacketDiscriminators.DRAW, ByteBuffer.allocate(4).putFloat(this.getDrawPercent()).array());
                Minecraft.getInstance().getConnection().send(pack);
                this.mc.gameMode.releaseUsingItem(player);
                pack = ClientNetworking.getVivecraftClientPacket(CommonNetworkHelper.PacketDiscriminators.DRAW, ByteBuffer.allocate(4).putFloat(0.0F).array());
                Minecraft.getInstance().getConnection().send(pack);
                this.isDrawing = false;
            }

            if (!this.pressed) {
                this.isDrawing = false;
            }

            if (!this.isDrawing && this.canDraw && !this.lastcanDraw) {
                this.dh.vr.triggerHapticPulse(1, 800);
                this.dh.vr.triggerHapticPulse(0, 800);
                // notch
            }

            if (this.isDrawing) {
                this.currentDraw = (this.controllersDist - (double) notchDistThreshold) / vrdata.worldScale;

                if (this.currentDraw > this.maxDraw) {
                    this.currentDraw = this.maxDraw;
                }

                int hap = 0;

                if (this.getDrawPercent() > 0.0F) {
                    hap = (int) (this.getDrawPercent() * 500.0F) + 700;
                }

                int use = (int) ((float) bow.getUseDuration() - this.getDrawPercent() * (float) this.maxDrawMillis);

                ((PlayerExtension) player).vivecraft$setItemInUseClient(bow, hand); // client draw only

                double drawperc = this.getDrawPercent();
                if (drawperc >= 1.0D) {
                    ((PlayerExtension) player).vivecraft$setItemInUseCountClient(stage2);
                } else if (drawperc > 0.4D) {
                    ((PlayerExtension) player).vivecraft$setItemInUseCountClient(stage1);
                } else {
                    ((PlayerExtension) player).vivecraft$setItemInUseCountClient(stage0);
                }

                int hapstep = (int) (drawperc * 4.0D * 4.0D * 3.0D);
                if (hapstep % 2 == 0 && this.lasthapStep != hapstep) {
                    this.dh.vr.triggerHapticPulse(0, hap);

                    if (drawperc == 1.0D) {
                        this.dh.vr.triggerHapticPulse(1, hap);
                    }
                }

                if (this.isCharged() && this.hapcounter % 4 == 0) {
                    this.dh.vr.triggerHapticPulse(1, 200);
                }

                this.lasthapStep = hapstep;
                ++this.hapcounter;
            } else {
                this.hapcounter = 0;
                this.lasthapStep = 0;
            }
        }
    }
}

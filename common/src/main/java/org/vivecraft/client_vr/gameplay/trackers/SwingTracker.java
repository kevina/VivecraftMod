package org.vivecraft.client_vr.gameplay.trackers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client_vr.BlockTags;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.ItemTags;
import org.vivecraft.client_vr.Vec3History;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.settings.VRSettings;

import java.util.List;

public class SwingTracker extends Tracker {
    private final Vec3[] lastWeaponEndAir = new Vec3[]{new Vec3(0.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 0.0D)};
    private final boolean[] lastWeaponSolid = new boolean[2];
    public Vec3[] miningPoint = new Vec3[2];
    public Vec3[] attackingPoint = new Vec3[2];
    public Vec3History[] tipHistory = new Vec3History[]{new Vec3History(), new Vec3History()};
    public boolean[] canact = new boolean[2];
    public int disableSwing = 3;
    Vec3 forward = new Vec3(0.0D, 0.0D, -1.0D);
    double speedthresh = 3.0D;

    public SwingTracker(Minecraft mc, ClientDataHolderVR dh) {
        super(mc, dh);
    }

    public boolean isActive(LocalPlayer p) {
        if (this.disableSwing > 0) {
            --this.disableSwing;
            return false;
        } else if (this.mc.gameMode == null) {
            return false;
        } else if (p == null) {
            return false;
        } else if (!p.isAlive()) {
            return false;
        } else if (p.isSleeping()) {
            return false;
        } else {
            Minecraft minecraft = Minecraft.getInstance();
            ClientDataHolderVR dataholder = ClientDataHolderVR.getInstance();

            if (minecraft.screen != null) {
                return false;
            } else if (dataholder.vrSettings.weaponCollision == VRSettings.WeaponCollision.OFF) {
                return false;
            } else if (dataholder.vrSettings.weaponCollision == VRSettings.WeaponCollision.AUTO) {
                return !p.isCreative();
            } else if (dataholder.vrSettings.seated) {
                return false;
            } else {
                VRSettings vrsettings = dataholder.vrSettings;

                if (dataholder.vrSettings.vrFreeMoveMode == VRSettings.FreeMove.RUN_IN_PLACE && p.zza > 0.0F) {
                    return false;
                } else if (p.isBlocking()) {
                    return false;
                } else {
                    return !dataholder.jumpTracker.isjumping();
                }
            }
        }
    }

    public static boolean isTool(Item item) {
        return item instanceof DiggerItem || item instanceof ArrowItem || item instanceof FishingRodItem || item instanceof FoodOnAStickItem || item instanceof ShearsItem || item == Items.BONE || item == Items.BLAZE_ROD || item == Items.BAMBOO || item == Items.TORCH || item == Items.REDSTONE_TORCH || item == Items.STICK || item == Items.DEBUG_STICK || item instanceof FlintAndSteelItem || item instanceof BrushItem || item.getDefaultInstance().is(ItemTags.VIVECRAFT_TOOLS);
    }

    public void doProcess(LocalPlayer player) {
        this.speedthresh = 3.0D;

        if (player.isCreative()) {
            this.speedthresh *= 1.5D;
        }

        this.mc.getProfiler().push("updateSwingAttack");

        for (int c = 0; c < 2; ++c) {
            if (!this.dh.climbTracker.isGrabbingLadder(c)) {
                Vec3 handPos = this.dh.vrPlayer.vrdata_world_pre.getController(c).getPosition();
                Vec3 handDirection = this.dh.vrPlayer.vrdata_world_pre.getHand(c).getCustomVector(this.forward);
                ItemStack itemstack = player.getItemInHand(c == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
                Item item = itemstack.getItem();
                boolean tool = false;
                boolean sword = false;

                if (!(item instanceof SwordItem || itemstack.is(ItemTags.VIVECRAFT_SWORDS)) && !(item instanceof TridentItem || itemstack.is(ItemTags.VIVECRAFT_SPEARS))) {
                    if (isTool(item)) {
                        tool = true;
                    }
                } else {
                    sword = true;
                    tool = true;
                }

                float weaponLength;
                float entityReachAdd;

                if (sword) {
                    entityReachAdd = 1.9F;
                    weaponLength = 0.6F;
                    tool = true;
                } else if (tool) {
                    entityReachAdd = 1.2F;
                    weaponLength = 0.35F;
                    tool = true;
                } else if (!itemstack.isEmpty()) {
                    weaponLength = 0.1F;
                    entityReachAdd = 0.3F;
                } else {
                    weaponLength = 0.0F;
                    entityReachAdd = 0.3F;
                }

                weaponLength = weaponLength * this.dh.vrPlayer.vrdata_world_pre.worldScale;

                this.miningPoint[c] = handPos.add(handDirection.scale(weaponLength));

                // do speed calc in actual room coords
                Vec3 vel = this.dh.vrPlayer.vrdata_room_pre.getController(c).getPosition().add(this.dh.vrPlayer.vrdata_room_pre.getHand(c).getCustomVector(this.forward).scale(0.3D));
                this.tipHistory[c].add(vel);
                // at a 0.3m offset on index controllers a speed of 3m/s is a intended smack, 7 m/s is about as high as your arm can go.

                float speed = (float) this.tipHistory[c].averageSpeed(0.33D);
                boolean inAnEntity = false;
                this.canact[c] = (double) speed > this.speedthresh && !this.lastWeaponSolid[c];

                // Check EntityCollisions first
                {
                    boolean entityact = this.canact[c];
                    if (entityact) {
                        BlockHitResult test = this.mc.level.clip(new ClipContext(this.dh.vrPlayer.vrdata_world_pre.hmd.getPosition(), handPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this.mc.player));

                        if (test.getType() != HitResult.Type.MISS) {
                            entityact = false;
                        }
                    }

                    this.attackingPoint[c] = this.constrain(handPos, this.miningPoint[c]);
                    Vec3 extWeapon = handPos.add(handDirection.scale(weaponLength + entityReachAdd));
                    extWeapon = this.constrain(handPos, extWeapon);

                    AABB weaponBB = new AABB(handPos, this.attackingPoint[c]);
                    AABB weaponBBEXT = new AABB(handPos, extWeapon);

                    List<Entity> mobs = this.mc.level.getEntities(this.mc.player, weaponBBEXT);
                    
                    mobs.removeIf((e) ->
                    {
                        return e instanceof Player;
                    });
                    List<Entity> players = this.mc.level.getEntities(this.mc.player, weaponBB);
                    players.removeIf((e) ->
                    {
                        return !(e instanceof Player);
                    });
                    mobs.addAll(players);

                    for (Entity entity : mobs) {
                        if (entity.isPickable() && entity != this.mc.getCameraEntity().getVehicle()) {
                            if (entityact) {
                                //Minecraft.getInstance().physicalGuiManager.preClickAction();
                                this.mc.gameMode.attack(player, entity);
                                this.dh.vr.triggerHapticPulse(c, 1000);
                                this.lastWeaponSolid[c] = true;
                            }

                            inAnEntity = true;
                        }
                    }
                }

                { //block check

                    // dont hit blocks with sword or same time as hitting entity
                    this.canact[c] = this.canact[c] && !sword && !inAnEntity;

                    if (!this.dh.climbTracker.isClimbeyClimb() || (c != 0 || !VivecraftVRMod.INSTANCE.keyClimbeyGrab.isDown(ControllerType.RIGHT)) && tool && (c != 1 || !VivecraftVRMod.INSTANCE.keyClimbeyGrab.isDown(ControllerType.LEFT)) && tool) {
                        BlockPos bp = BlockPos.containing(this.miningPoint[c]);
                        BlockState block = this.mc.level.getBlockState(bp);

                        // every time end of weapon enters a solid for the first time, trace from our previous air position
                        // and damage the block it collides with...
                        BlockHitResult blockHit = this.mc.level.clip(new ClipContext(this.lastWeaponEndAir[c], this.miningPoint[c], ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this.mc.player));

                        if (!block.isAir() && blockHit.getType() == HitResult.Type.BLOCK && this.lastWeaponEndAir[c].length() != 0.0D) {
                            this.lastWeaponSolid[c] = true;
                            boolean flag = blockHit.getBlockPos().equals(bp); // fix ladder
                            boolean protectedBlock = this.dh.vrSettings.realisticClimbEnabled && (block.getBlock() instanceof LadderBlock || block.getBlock() instanceof VineBlock || block.is(BlockTags.VIVECRAFT_CLIMBABLE));
                            //TODO: maybe blacklist right-clickable blocks?

                            if (blockHit.getType() == HitResult.Type.BLOCK && flag && this.canact[c] && !protectedBlock) {
                                int p = 3;

                                if ((item instanceof HoeItem || itemstack.is(ItemTags.VIVECRAFT_HOES) || itemstack.is(ItemTags.VIVECRAFT_SCYTHES)) && (
                                    block.getBlock() instanceof CropBlock
                                        || block.getBlock() instanceof StemBlock
                                        || block.getBlock() instanceof AttachedStemBlock
                                        || block.is(BlockTags.VIVECRAFT_CROPS)
                                        // check if the item can use the block
                                        || item.useOn(new UseOnContext(player, c == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, blockHit)).shouldSwing())) {
                                    // don't try to break crops with hoes
                                    // actually use the item on the block
                                    boolean useSuccessful = this.mc.gameMode.useItemOn(player, c == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, blockHit).shouldSwing();
                                    if (itemstack.is(ItemTags.VIVECRAFT_SCYTHES) && !useSuccessful) {
                                        // some scythes just need to be used
                                        this.mc.gameMode.useItem(player, c == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
                                    }
                                } else if ((item instanceof BrushItem /*|| itemstack.is(ItemTags.VIVECRAFT_BRUSHES*/)) {
                                    ((BrushItem) item).spawnDustParticles(player.level(), blockHit, block, player.getViewVector(0.0F), c == 0 ? player.getMainArm() : player.getMainArm().getOpposite());
                                    player.level().playSound(player, blockHit.getBlockPos(), block.getBlock() instanceof BrushableBlock ? ((BrushableBlock) block.getBlock()).getBrushSound() : SoundEvents.BRUSH_GENERIC, SoundSource.BLOCKS);
                                    this.mc.gameMode.useItemOn(player, c == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, blockHit);
                                } else if (block.getBlock() instanceof NoteBlock || block.is(BlockTags.VIVECRAFT_MUSIC_BLOCKS)) {
                                    this.mc.gameMode.continueDestroyBlock(blockHit.getBlockPos(), blockHit.getDirection());
                                } else { //smack it
                                    p = (int) ((double) p + Math.min((double) speed - this.speedthresh, 4.0D));
                                    //this.mc.physicalGuiManager.preClickAction();

                                    // this will either destroy the block if in creative or set it as the current block.
                                    // does nothing in survival if you are already hitting this block.
                                    this.mc.gameMode.startDestroyBlock(blockHit.getBlockPos(), blockHit.getDirection());

                                    if (this.getIsHittingBlock()) {  //seems to be the only way to tell it didnt insta-broke.
                                        for (int i = 0; i < p; ++i) {
                                            // send multiple ticks worth of 'holding left click' to it.

                                            if (this.mc.gameMode.continueDestroyBlock(blockHit.getBlockPos(), blockHit.getDirection())) {
                                                this.mc.particleEngine.crack(blockHit.getBlockPos(), blockHit.getDirection());
                                            }

                                            this.clearBlockHitDelay();

                                            if (!this.getIsHittingBlock()) { //seems to be the only way to tell it broke.
                                                break;
                                            }
                                        }

                                        Minecraft.getInstance().gameMode.destroyDelay = 0;
                                    }

                                    this.dh.vrPlayer.blockDust(blockHit.getLocation().x, blockHit.getLocation().y, blockHit.getLocation().z, 3 * p, bp, block, 0.6F, 1.0F);
                                }

                                this.dh.vr.triggerHapticPulse(c, 250 * p);
                                //   System.out.println("Hit block speed =" + speed + " mot " + mot + " thresh " + speedthresh) ;
                            }
                        } else {
                            this.lastWeaponEndAir[c] = this.miningPoint[c];
                            this.lastWeaponSolid[c] = false;
                        }
                    }
                }
            }
        }

        this.mc.getProfiler().pop();
    }

    private boolean getIsHittingBlock() {
        return Minecraft.getInstance().gameMode.isDestroying();
    }

    private void clearBlockHitDelay() {
        //MCReflection.PlayerController_blockHitDelay.set(Minecraft.getInstance().gameMode, 0);
        // Minecraft.getInstance().gameMode.blockBreakingCooldown = 1;
    }

    public Vec3 constrain(Vec3 start, Vec3 end) {
        BlockHitResult test = this.mc.level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this.mc.player));
        return test.getType() == HitResult.Type.BLOCK ? test.getLocation() : end;
    }

    // Get the transparency for held items to indicate attack power or sneaking.
    public static float getItemFade(LocalPlayer p, ItemStack is) {
        float fade = p.getAttackStrengthScale(0.0F) * 0.75F + 0.25F;

        if (p.isShiftKeyDown()) {
            fade = 0.75F;
        }

        boolean[] aboolean = ClientDataHolderVR.getInstance().swingTracker.lastWeaponSolid;
        Minecraft.getInstance().getItemRenderer();

        if (aboolean[ClientDataHolderVR.ismainhand ? 0 : 1]) {
            fade -= 0.25F;
        }

        if (is != ItemStack.EMPTY) {
            if (p.isBlocking() && p.getUseItem() != is) {
                fade -= 0.25F;
            }

            if (is.getItem() == Items.SHIELD && !p.isBlocking()) {
                fade -= 0.25F;
            }
        }

        if ((double) fade < 0.1D) {
            fade = 0.1F;
        }

        if (fade > 1.0F) {
            fade = 1.0F;
        }

        return fade;
    }
}

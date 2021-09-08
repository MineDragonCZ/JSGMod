package mrjake.aunis.tileentity.stargate;

import java.util.*;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import mrjake.aunis.Aunis;
import mrjake.aunis.beamer.BeamerLinkingHelper;
import mrjake.aunis.block.AunisBlocks;
import mrjake.aunis.config.AunisConfig;
import mrjake.aunis.gui.container.StargateContainerGuiState;
import mrjake.aunis.gui.container.StargateContainerGuiUpdate;
import mrjake.aunis.item.AunisItems;
import mrjake.aunis.item.notebook.PageNotebookItem;
import mrjake.aunis.packet.AunisPacketHandler;
import mrjake.aunis.packet.StateUpdatePacketToClient;
import mrjake.aunis.renderer.biomes.BiomeOverlayEnum;
import mrjake.aunis.renderer.stargate.StargateClassicRendererState;
import mrjake.aunis.renderer.stargate.StargateClassicRendererState.StargateClassicRendererStateBuilder;
import mrjake.aunis.sound.SoundEventEnum;
import mrjake.aunis.sound.StargateSoundEventEnum;
import mrjake.aunis.sound.StargateSoundPositionedEnum;
import mrjake.aunis.stargate.*;
import mrjake.aunis.stargate.network.*;
import mrjake.aunis.stargate.power.StargateAbstractEnergyStorage;
import mrjake.aunis.stargate.power.StargateClassicEnergyStorage;
import mrjake.aunis.stargate.power.StargateEnergyRequired;
import mrjake.aunis.state.StargateBiomeOverrideState;
import mrjake.aunis.state.StargateRendererActionState;
import mrjake.aunis.state.StargateRendererActionState.EnumGateAction;
import mrjake.aunis.state.StargateSpinState;
import mrjake.aunis.state.State;
import mrjake.aunis.state.StateTypeEnum;
import mrjake.aunis.tileentity.BeamerTile;
import mrjake.aunis.tileentity.util.IUpgradable;
import mrjake.aunis.tileentity.util.ScheduledTask;
import mrjake.aunis.util.AunisAxisAlignedBB;
import mrjake.aunis.util.AunisItemStackHandler;
import mrjake.aunis.util.EnumKeyInterface;
import mrjake.aunis.util.EnumKeyMap;
import mrjake.aunis.util.FacingToRotation;
import mrjake.aunis.util.ItemHandlerHelper;
import mrjake.aunis.util.ItemMetaPair;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.items.CapabilityItemHandler;

import static mrjake.aunis.renderer.stargate.StargateClassicRenderer.PHYSICAL_IRIS_ANIMATION_LENGTH;
import static mrjake.aunis.renderer.stargate.StargateClassicRenderer.SHIELD_IRIS_ANIMATION_LENGTH;

/**
 * This class wraps common behavior for the fully-functional Stargates i.e.
 * all of them (right now) except Orlin's.
 *
 * @author MrJake222
 */
public abstract class StargateClassicBaseTile extends StargateAbstractBaseTile implements IUpgradable {

    // ------------------------------------------------------------------------
    // Stargate state

    protected boolean isFinalActive;

    @Override
    protected void engageGate() {
        super.engageGate();

        for (BlockPos beamerPos : linkedBeamers) {
            ((BeamerTile) world.getTileEntity(beamerPos)).gateEngaged(targetGatePos);
        }
    }

    @Override
    public void closeGate(StargateClosedReasonEnum reason) {
        super.closeGate(reason);

        for (BlockPos beamerPos : linkedBeamers) {
            ((BeamerTile) world.getTileEntity(beamerPos)).gateClosed();
        }
    }

    @Override
    protected void disconnectGate() {
        super.disconnectGate();

        isFinalActive = false;

        updateChevronLight(0, false);
        sendRenderingUpdate(EnumGateAction.CLEAR_CHEVRONS, dialedAddress.size(), isFinalActive);
    }

    @Override
    protected void failGate() {
        super.failGate();

        isFinalActive = false;

        updateChevronLight(0, false);
        sendRenderingUpdate(EnumGateAction.CLEAR_CHEVRONS, dialedAddress.size(), isFinalActive);
    }

    @Override
    public void openGate(StargatePos targetGatePos, boolean isInitiating) {
        super.openGate(targetGatePos, isInitiating);

        this.isFinalActive = true;
    }

    @Override
    public void incomingWormhole(int dialedAddressSize) {
        super.incomingWormhole(dialedAddressSize);

        isFinalActive = true;
        updateChevronLight(dialedAddressSize, isFinalActive);
    }

    @Override
    public void onGateBroken() {
        super.onGateBroken();
        updateChevronLight(0, false);
        isSpinning = false;
        irisState = mrjake.aunis.stargate.EnumIrisState.OPENED;
        irisType = EnumIrisType.NULL;
        currentRingSymbol = getSymbolType().getTopSymbol();
        AunisPacketHandler.INSTANCE.sendToAllTracking(new StateUpdatePacketToClient(pos, StateTypeEnum.SPIN_STATE, new StargateSpinState(currentRingSymbol, spinDirection, true)), targetPoint);

        playPositionedSound(StargateSoundPositionedEnum.GATE_RING_ROLL, false);
        ItemHandlerHelper.dropInventoryItems(world, pos, itemStackHandler);

        for (BlockPos beamerPos : linkedBeamers) {
            BeamerTile beamerTile = (BeamerTile) world.getTileEntity(beamerPos);
            beamerTile.setLinkedGate(null, null);
        }

        linkedBeamers.clear();
    }

    @Override
    protected void onGateMerged() {
        super.onGateMerged();

        BeamerLinkingHelper.findBeamersInFront(world, pos, facing);
        updateBeamers();
        updateIrisType();
    }


    // ------------------------------------------------------------------------
    // Loading and ticking

    @Override
    public void onLoad() {
        super.onLoad();

        lastPos = pos;

        if (!world.isRemote) {
            updateBeamers();
            updatePowerTier();
            updateIrisType();
        }
    }

    protected abstract boolean onGateMergeRequested();

    private BlockPos lastPos = BlockPos.ORIGIN;

    @Override
    public void update() {
        super.update();

        if (!world.isRemote) {
            if (!lastPos.equals(pos)) {
                lastPos = pos;
                generateAddresses(!hasUpgrade(StargateClassicBaseTile.StargateUpgradeEnum.CHEVRON_UPGRADE));

                if (isMerged()) {
                    updateMergeState(onGateMergeRequested(), facing);
                }
            }

            if (givePageTask != null) {
                if (givePageTask.update(world.getTotalWorldTime())) {
                    givePageTask = null;
                }
            }

            if (doPageProgress) {
                if (world.getTotalWorldTime() % 2 == 0) {
                    pageProgress++;

                    if (pageProgress > 18) {
                        pageProgress = 0;
                        doPageProgress = false;
                    }
                }

                if (itemStackHandler.getStackInSlot(pageSlotId).isEmpty()) {
                    lockPage = false;
                    doPageProgress = false;
                    pageProgress = 0;
                    givePageTask = null;
                }
            } else {
                if (lockPage && itemStackHandler.getStackInSlot(pageSlotId).isEmpty()) {
                    lockPage = false;
                }

                if (!lockPage) {
                    for (int i = 7; i < 10; i++) {
                        if (!itemStackHandler.getStackInSlot(i).isEmpty()) {
                            doPageProgress = true;
                            lockPage = true;
                            pageSlotId = i;
                            givePageTask = new ScheduledTask(EnumScheduledTask.STARGATE_GIVE_PAGE, 36);
                            givePageTask.setTaskCreated(world.getTotalWorldTime());
                            givePageTask.setExecutor(this);

                            break;
                        }
                    }
                }
            }

            if (!(isClosed() || isOpened()) && (world.getTotalWorldTime() - irisAnimation) > (isPhysicalIris() ? PHYSICAL_IRIS_ANIMATION_LENGTH : SHIELD_IRIS_ANIMATION_LENGTH)) {
                switch (irisState) {
                    case OPENING:
                        irisState = mrjake.aunis.stargate.EnumIrisState.OPENED;
                        sendRenderingUpdate(EnumGateAction.IRIS_UPDATE, 0, false, irisType, irisState, irisAnimation);
                        break;
                    case CLOSING:
                        irisState = mrjake.aunis.stargate.EnumIrisState.CLOSED;
                        sendRenderingUpdate(EnumGateAction.IRIS_UPDATE, 0, false, irisType, irisState, irisAnimation);
                        break;
                    default:
                        break;
                }
            }
        } else {
            // Client

            // Each 2s check for the biome overlay
            if (world.getTotalWorldTime() % 40 == 0 && rendererStateClient != null && getRendererStateClient().biomeOverride == null) {
                rendererStateClient.setBiomeOverlay(BiomeOverlayEnum.updateBiomeOverlay(world, getMergeHelper().getTopBlock().add(pos), getSupportedOverlays()));
            }
        }
    }

    // Server
    private BiomeOverlayEnum determineBiomeOverride() {
        ItemStack stack = itemStackHandler.getStackInSlot(BIOME_OVERRIDE_SLOT);

        if (stack.isEmpty()) {
            return null;
        }

        BiomeOverlayEnum biomeOverlay = AunisConfig.stargateConfig.getBiomeOverrideItemMetaPairs().get(new ItemMetaPair(stack));

        if (getSupportedOverlays().contains(biomeOverlay)) {
            return biomeOverlay;
        }

        return null;
    }

    @Override
    protected boolean shouldAutoclose() {
        boolean beamerActive = false;

        for (BlockPos beamerPos : linkedBeamers) {
            BeamerTile beamerTile = (BeamerTile) world.getTileEntity(beamerPos);
            beamerActive = beamerTile.isActive();

            if (beamerActive) break;
        }

        return !beamerActive && super.shouldAutoclose();
    }

    // ------------------------------------------------------------------------
    // NBT

    @Override
    protected void setWorldCreate(World world) {
        setWorld(world);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound.setTag("itemHandler", itemStackHandler.serializeNBT());
        compound.setBoolean("isFinalActive", isFinalActive);

        compound.setBoolean("isSpinning", isSpinning);
        compound.setLong("spinStartTime", spinStartTime);
        compound.setInteger("currentRingSymbol", currentRingSymbol.getId());
        compound.setInteger("targetRingSymbol", targetRingSymbol.getId());
        compound.setInteger("spinDirection", spinDirection.id);

        NBTTagList linkedBeamersTagList = new NBTTagList();
        for (BlockPos vect : linkedBeamers)
            linkedBeamersTagList.appendTag(new NBTTagLong(vect.toLong()));
        compound.setTag("linkedBeamers", linkedBeamersTagList);
        compound.setByte("irisState", irisState.id);

        return super.writeToNBT(compound);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        itemStackHandler.deserializeNBT(compound.getCompoundTag("itemHandler"));

        if (compound.getBoolean("hasUpgrade")) {
            itemStackHandler.setStackInSlot(0, new ItemStack(AunisItems.CRYSTAL_GLYPH_STARGATE));
        }

        isFinalActive = compound.getBoolean("isFinalActive");

        isSpinning = compound.getBoolean("isSpinning");
        spinStartTime = compound.getLong("spinStartTime");
        currentRingSymbol = getSymbolType().valueOfSymbol(compound.getInteger("currentRingSymbol"));
        targetRingSymbol = getSymbolType().valueOfSymbol(compound.getInteger("targetRingSymbol"));
        spinDirection = EnumSpinDirection.valueOf(compound.getInteger("spinDirection"));

        for (NBTBase tag : compound.getTagList("linkedBeamers", NBT.TAG_LONG))
            linkedBeamers.add(BlockPos.fromLong(((NBTTagLong) tag).getLong()));

        irisState = mrjake.aunis.stargate.EnumIrisState.getValue(compound.getByte("irisState"));

        super.readFromNBT(compound);
    }


    // ------------------------------------------------------------------------
    // Rendering

    protected void updateChevronLight(int lightUp, boolean isFinalActive) {
        //		Aunis.info("Updating chevron light to: " + lightUp);

        if (isFinalActive) lightUp--;

        for (int i = 0; i < 9; i++) {
            BlockPos chevPos = getMergeHelper().getChevronBlocks().get(i).rotate(FacingToRotation.get(facing)).add(pos);

            if (getMergeHelper().matchMember(world.getBlockState(chevPos))) {
                StargateClassicMemberTile memberTile = (StargateClassicMemberTile) world.getTileEntity(chevPos);
                memberTile.setLitUp(i == 8 ? isFinalActive : lightUp > i);
            }
        }
    }

    @Override
    protected StargateClassicRendererStateBuilder getRendererStateServer() {
        return new StargateClassicRendererStateBuilder(super.getRendererStateServer())
                .setSymbolType(getSymbolType())
                .setActiveChevrons(dialedAddress.size())
                .setFinalActive(isFinalActive)
                .setCurrentRingSymbol(currentRingSymbol)
                .setSpinDirection(spinDirection)
                .setSpinning(isSpinning)
                .setTargetRingSymbol(targetRingSymbol)
                .setSpinStartTime(spinStartTime)
                .setBiomeOverride(determineBiomeOverride())
                .setIrisState(irisState)
                .setIrisType(irisType)
                .setIrisAnimation(irisAnimation);
    }

    @Override
    public StargateClassicRendererState getRendererStateClient() {
        return (StargateClassicRendererState) super.getRendererStateClient();
    }

    public static final AunisAxisAlignedBB RENDER_BOX = new AunisAxisAlignedBB(-5.5, 0, -0.5, 5.5, 10.5, 0.5);

    @Override
    protected AunisAxisAlignedBB getRenderBoundingBoxRaw() {
        return RENDER_BOX;
    }

    protected long getSpinStartOffset() {
        return 0;
    }

    // -----------------------------------------------------------------
    // States

    @Override
    public State getState(StateTypeEnum stateType) {
        switch (stateType) {
            case GUI_STATE:
                return new StargateContainerGuiState(gateAddressMap);

            case GUI_UPDATE:
                return new StargateContainerGuiUpdate(energyStorage.getEnergyStoredInternally(), energyTransferedLastTick, energySecondsToClose);

            default:
                return super.getState(stateType);
        }
    }

    @Override
    public State createState(StateTypeEnum stateType) {
        switch (stateType) {
            case GUI_STATE:
                return new StargateContainerGuiState();

            case GUI_UPDATE:
                return new StargateContainerGuiUpdate();

            case SPIN_STATE:
                return new StargateSpinState();

            case BIOME_OVERRIDE_STATE:
                return new StargateBiomeOverrideState();

            default:
                return super.createState(stateType);
        }
    }

    @Override
    public void setState(StateTypeEnum stateType, State state) {
        switch (stateType) {
            case RENDERER_UPDATE:
                StargateRendererActionState gateActionState = (StargateRendererActionState) state;

                switch (gateActionState.action) {
                    case CHEVRON_ACTIVATE:
                        if (gateActionState.modifyFinal)
                            getRendererStateClient().chevronTextureList.activateFinalChevron(world.getTotalWorldTime());
                        else getRendererStateClient().chevronTextureList.activateNextChevron(world.getTotalWorldTime());

                        break;

                    case CLEAR_CHEVRONS:

                        getRendererStateClient().clearChevrons(world.getTotalWorldTime());
                        break;

                    case LIGHT_UP_CHEVRONS:
                        getRendererStateClient().chevronTextureList.lightUpChevrons(world.getTotalWorldTime(), gateActionState.chevronCount);
                        break;

                    case CHEVRON_ACTIVATE_BOTH:
                        getRendererStateClient().chevronTextureList.activateNextChevron(world.getTotalWorldTime());
                        getRendererStateClient().chevronTextureList.activateFinalChevron(world.getTotalWorldTime());
                        break;

                    case CHEVRON_DIM:
                        getRendererStateClient().chevronTextureList.deactivateFinalChevron(world.getTotalWorldTime());
                        break;

                    case IRIS_UPDATE:
                        System.out.println("client" + getRendererStateClient().irisState.name() + " server " + getRendererStateServer().irisState.name());
                        getRendererStateClient().irisState = gateActionState.irisState;
                        System.out.println("client" + getRendererStateClient().irisState.name() + " server " + getRendererStateServer().irisState.name());
                        getRendererStateClient().irisType = irisType;
                        if (gateActionState.irisState == EnumIrisState.CLOSING || gateActionState.irisState == EnumIrisState.OPENING) {
                            getRendererStateClient().irisAnimation = world.getTotalWorldTime();
                            System.out.println(world.getTotalWorldTime());
                        }
                        break;


                    default:
                        break;
                }

                break;

            case GUI_STATE:
                StargateContainerGuiState guiState = (StargateContainerGuiState) state;
                gateAddressMap = guiState.gateAdddressMap;

                break;

            case GUI_UPDATE:
                StargateContainerGuiUpdate guiUpdate = (StargateContainerGuiUpdate) state;
                energyStorage.setEnergyStoredInternally(guiUpdate.energyStored);
                energyTransferedLastTick = guiUpdate.transferedLastTick;
                energySecondsToClose = guiUpdate.secondsToClose;

                break;

            case SPIN_STATE:
                StargateSpinState spinState = (StargateSpinState) state;
                if (spinState.setOnly) {
                    getRendererStateClient().spinHelper.setIsSpinning(false);
                    getRendererStateClient().spinHelper.setCurrentSymbol(spinState.targetSymbol);
                } else
                    getRendererStateClient().spinHelper.initRotation(world.getTotalWorldTime(), spinState.targetSymbol, spinState.direction, getSpinStartOffset());

                break;

            case BIOME_OVERRIDE_STATE:
                StargateBiomeOverrideState overrideState = (StargateBiomeOverrideState) state;

                if (rendererStateClient != null) {
                    getRendererStateClient().biomeOverride = overrideState.biomeOverride;
                }

                break;

            default:
                break;
        }

        super.setState(stateType, state);
    }


    // -----------------------------------------------------------------
    // Scheduled tasks

    @Override
    public void executeTask(EnumScheduledTask scheduledTask, NBTTagCompound customData) {
        switch (scheduledTask) {
            case STARGATE_SPIN_FINISHED:
                isSpinning = false;
                currentRingSymbol = targetRingSymbol;

                playPositionedSound(StargateSoundPositionedEnum.GATE_RING_ROLL, false);
                playSoundEvent(StargateSoundEventEnum.CHEVRON_SHUT);

                markDirty();
                break;

            case STARGATE_GIVE_PAGE:
                SymbolTypeEnum symbolType = SymbolTypeEnum.valueOf(pageSlotId - 7);
                ItemStack stack = itemStackHandler.getStackInSlot(pageSlotId);

                if (stack.getItem() == AunisItems.UNIVERSE_DIALER) {
                    NBTTagList saved = stack.getTagCompound().getTagList("saved", NBT.TAG_COMPOUND);
                    NBTTagCompound compound = gateAddressMap.get(symbolType).serializeNBT();
                    compound.setBoolean("hasUpgrade", hasUpgrade(StargateUpgradeEnum.CHEVRON_UPGRADE));
                    saved.appendTag(compound);
                } else {
                    Aunis.logger.debug("Giving Notebook page of address " + symbolType);

                    NBTTagCompound compound = PageNotebookItem.getCompoundFromAddress(gateAddressMap.get(symbolType), hasUpgrade(StargateUpgradeEnum.CHEVRON_UPGRADE), PageNotebookItem.getRegistryPathFromWorld(world, pos));

                    stack = new ItemStack(AunisItems.PAGE_NOTEBOOK_ITEM, 1, 1);
                    stack.setTagCompound(compound);
                    itemStackHandler.setStackInSlot(pageSlotId, stack);
                }

                break;

            default:
                super.executeTask(scheduledTask, customData);
        }
    }


    // ------------------------------------------------------------------------
    // Ring spinning

    protected boolean isSpinning;
    protected long spinStartTime;
    protected SymbolInterface currentRingSymbol = getSymbolType().getTopSymbol();
    protected SymbolInterface targetRingSymbol = getSymbolType().getTopSymbol();
    protected EnumSpinDirection spinDirection = EnumSpinDirection.COUNTER_CLOCKWISE;
    protected Object ringSpinContext;

    public void addSymbolToAddressManual(SymbolInterface targetSymbol, @Nullable Object context) {
        targetRingSymbol = targetSymbol;

        boolean moveOnly = targetRingSymbol == currentRingSymbol;

        if (moveOnly) {
            addTask(new ScheduledTask(EnumScheduledTask.STARGATE_SPIN_FINISHED, 0));
        } else {
            spinDirection = spinDirection.opposite();

            float distance = spinDirection.getDistance(currentRingSymbol, targetRingSymbol);
            if (distance < 90 && !AunisConfig.stargateConfig.fasterMWGateDial) {
                spinDirection = spinDirection.opposite();
                distance = spinDirection.getDistance(currentRingSymbol, targetRingSymbol);
            }

            if (distance > 180 && AunisConfig.stargateConfig.fasterMWGateDial) {
                spinDirection = spinDirection.opposite();
                distance = spinDirection.getDistance(currentRingSymbol, targetRingSymbol);
            }

            int duration = StargateClassicSpinHelper.getAnimationDuration(distance);

            Aunis.logger.debug("addSymbolToAddressManual: " + "current:" + currentRingSymbol + ", " + "target:" + targetSymbol + ", " + "direction:" + spinDirection + ", " + "distance:" + distance + ", " + "duration:" + duration + ", " + "moveOnly:" + moveOnly);

            AunisPacketHandler.INSTANCE.sendToAllTracking(new StateUpdatePacketToClient(pos, StateTypeEnum.SPIN_STATE, new StargateSpinState(targetRingSymbol, spinDirection, false)), targetPoint);
            addTask(new ScheduledTask(EnumScheduledTask.STARGATE_SPIN_FINISHED, duration - 5));
            playPositionedSound(StargateSoundPositionedEnum.GATE_RING_ROLL, true);

            isSpinning = true;
            spinStartTime = world.getTotalWorldTime();

            ringSpinContext = context;
            if (context != null)
                sendSignal(context, "stargate_spin_start", new Object[]{dialedAddress.size(), stargateWillLock(targetRingSymbol), targetSymbol.getEnglishName()});
        }

        markDirty();
    }

    // -----------------------------------------------------------------------------
    // Page conversion

    private short pageProgress = 0;
    private int pageSlotId;
    private boolean doPageProgress;
    private ScheduledTask givePageTask;
    private boolean lockPage;

    public short getPageProgress() {
        return pageProgress;
    }

    public void setPageProgress(int pageProgress) {
        this.pageProgress = (short) pageProgress;
    }

    // -----------------------------------------------------------------------------
    // Item handler

    public static final int BIOME_OVERRIDE_SLOT = 10;

    private final AunisItemStackHandler itemStackHandler = new AunisItemStackHandler(12) {

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            Item item = stack.getItem();
            boolean isItemCapacitor = (item == Item.getItemFromBlock(AunisBlocks.CAPACITOR_BLOCK));

            switch (slot) {
                case 0:
                case 1:
                case 2:
                case 3:
                    return StargateUpgradeEnum.contains(item) && !hasUpgrade(item);

                case 4:
                    return isItemCapacitor && getSupportedCapacitors() >= 1;
                case 5:
                    return isItemCapacitor && getSupportedCapacitors() >= 2;
                case 6:
                    return isItemCapacitor && getSupportedCapacitors() >= 3;

                case 7:
                case 8:
                    return item == AunisItems.PAGE_NOTEBOOK_ITEM;

                case 9:
                    return item == AunisItems.PAGE_NOTEBOOK_ITEM || item == AunisItems.UNIVERSE_DIALER;

                case BIOME_OVERRIDE_SLOT:
                    BiomeOverlayEnum override = AunisConfig.stargateConfig.getBiomeOverrideItemMetaPairs().get(new ItemMetaPair(stack));
                    if (override == null) return false;

                    return getSupportedOverlays().contains(override);
                case 11:
                    return StargateIrisUpgradeEnum.contains(item);
                default:
                    return true;
            }
        }

        @Override
        protected int getStackLimit(int slot, ItemStack stack) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);

            switch (slot) {
                case 4:
                case 5:
                case 6:
                    updatePowerTier();
                    break;

                case BIOME_OVERRIDE_SLOT:
                    sendState(StateTypeEnum.BIOME_OVERRIDE_STATE, new StargateBiomeOverrideState(determineBiomeOverride()));
                    break;
                // iris update state
                case 11:
                    updateIrisType();
                    break;
                default:
                    break;
            }

            markDirty();
        }
    };

    public abstract int getSupportedCapacitors();


    public static enum StargateUpgradeEnum implements EnumKeyInterface<Item> {
        MILKYWAY_GLYPHS(AunisItems.CRYSTAL_GLYPH_MILKYWAY),
        PEGASUS_GLYPHS(AunisItems.CRYSTAL_GLYPH_PEGASUS),
        UNIVERSE_GLYPHS(AunisItems.CRYSTAL_GLYPH_UNIVERSE),
        CHEVRON_UPGRADE(AunisItems.CRYSTAL_GLYPH_STARGATE);

        public Item item;

        private StargateUpgradeEnum(Item item) {
            this.item = item;
        }

        @Override
        public Item getKey() {
            return item;
        }

        private static final EnumKeyMap<Item, StargateUpgradeEnum> idMap = new EnumKeyMap<Item, StargateClassicBaseTile.StargateUpgradeEnum>(values());

        public static StargateUpgradeEnum valueOf(Item item) {
            return idMap.valueOf(item);
        }

        public static boolean contains(Item item) {
            return idMap.contains(item);
        }
    }

    // ----------------------------------------------------------
    // IRISES

    private EnumIrisState irisState = mrjake.aunis.stargate.EnumIrisState.OPENED;
    private EnumIrisType irisType = EnumIrisType.NULL;
    private long irisAnimation = 0;
    protected float irisMaxDurability = 0;
    protected float irisDurability = 0;

    public static enum StargateIrisUpgradeEnum implements EnumKeyInterface<Item> {
        IRIS_UPGRADE_CLASSIC(AunisItems.UPGRADE_IRIS),
        IRIS_UPGRADE_TRINIUM(AunisItems.UPGRADE_IRIS_TRINIUM),
        IRIS_UPGRADE_SHIELD(AunisItems.UPGRADE_SHIELD);

        public Item item;

        private StargateIrisUpgradeEnum(Item item) {
            this.item = item;
        }

        @Override
        public Item getKey() {
            return item;
        }

        private static final EnumKeyMap<Item, StargateIrisUpgradeEnum> idMap =
                new EnumKeyMap<Item, StargateClassicBaseTile.StargateIrisUpgradeEnum>(values());

        public static StargateIrisUpgradeEnum valueOf(Item item) {
            return idMap.valueOf(item);
        }

        public static boolean contains(Item item) {
            return idMap.contains(item);
        }
    }

    public void updateIrisType() {
        irisType = EnumIrisType.byItem(itemStackHandler.getStackInSlot(11).getItem());
        irisAnimation = getWorld().getTotalWorldTime();
        // TODO iris durability
        // irisDurability = 0;
        if (irisType == EnumIrisType.NULL)
            irisState = mrjake.aunis.stargate.EnumIrisState.OPENED;

        switch (irisType) {
            case IRIS_TITANIUM:
                irisMaxDurability = AunisConfig.irisConfig.titaniumIrisDurability;
                break;
            case IRIS_TRINIUM:
                irisMaxDurability = AunisConfig.irisConfig.triniumIrisDurability;
                break;
            default:
                irisMaxDurability = 0;
                break;
        }
        sendRenderingUpdate(EnumGateAction.IRIS_UPDATE, 0, false, irisType, irisState, irisAnimation);
        markDirty();
    }

    public EnumIrisType getIrisType() {
        return irisType;
    }

    public EnumIrisState getIrisState() {
        return irisState;
    }

    public boolean isClosed() {
        return irisState == mrjake.aunis.stargate.EnumIrisState.CLOSED;
    }

    public boolean isOpened() {
        return irisState == mrjake.aunis.stargate.EnumIrisState.OPENED;
    }

    public boolean isPhysicalIris() {
        switch (irisType) {
            case IRIS_TITANIUM:
            case IRIS_TRINIUM:
                return true;
            default:
                return false;
        }
    }

    public boolean isShieldIris() {
        return irisType == EnumIrisType.SHIELD;
    }

    protected boolean toggleIris() {
        if (irisType == EnumIrisType.NULL) return false;
        if (isClosed() || isOpened())
            irisAnimation = getWorld().getTotalWorldTime();
        SoundEventEnum openSound;
        SoundEventEnum closeSound;
        if (isPhysicalIris()) {
            openSound = SoundEventEnum.IRIS_OPENING;
            closeSound = SoundEventEnum.IRIS_CLOSING;
        } else {
            openSound = SoundEventEnum.SHIELD_OPENING;
            closeSound = SoundEventEnum.SHIELD_CLOSING;
        }
        switch (irisState) {
            case OPENED:
                irisState = mrjake.aunis.stargate.EnumIrisState.CLOSING;
                sendRenderingUpdate(EnumGateAction.IRIS_UPDATE, 0, true, irisType, irisState, irisAnimation);
                markDirty();
                playSoundEvent(closeSound);
                break;
            case CLOSED:
                irisState = mrjake.aunis.stargate.EnumIrisState.OPENING;
                sendRenderingUpdate(EnumGateAction.IRIS_UPDATE, 0, true, irisType, irisState, irisAnimation);
                markDirty();
                playSoundEvent(openSound);
                break;
            default:
                return false;
        }
        markDirty();
        return true;
    }
    // -----------------------------------------------------------

    @Override
    public Iterator<Integer> getUpgradeSlotsIterator() {
        return IntStream.range(0, 7).iterator();
    }

    // -----------------------------------------------------------------------------
    // Power system

    private final StargateClassicEnergyStorage energyStorage = new StargateClassicEnergyStorage() {

        @Override
        protected void onEnergyChanged() {
            markDirty();
        }
    };

    @Override
    protected StargateAbstractEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    private int currentPowerTier = 1;

    public int getPowerTier() {
        return currentPowerTier;
    }

    private void updatePowerTier() {
        int powerTier = 1;

        for (int i = 4; i < 7; i++) {
            if (!itemStackHandler.getStackInSlot(i).isEmpty()) {
                powerTier++;
            }
        }

        if (powerTier != currentPowerTier) {
            currentPowerTier = powerTier;

            energyStorage.clearStorages();

            for (int i = 4; i < 7; i++) {
                ItemStack stack = itemStackHandler.getStackInSlot(i);

                if (!stack.isEmpty()) {
                    energyStorage.addStorage(stack.getCapability(CapabilityEnergy.ENERGY, null));
                }
            }

            Aunis.logger.debug("Updated to power tier: " + powerTier);
        }
    }


    // -----------------------------------------------------------------------------
    // Capabilities

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(itemStackHandler);

        return super.getCapability(capability, facing);
    }


    // -----------------------------------------------------------------
    // Beamers

    private final List<BlockPos> linkedBeamers = new ArrayList<>();

    public void addLinkedBeamer(BlockPos pos) {
        if (stargateState.engaged()) {
            ((BeamerTile) world.getTileEntity(pos)).gateEngaged(targetGatePos);
        }

        linkedBeamers.add(pos.toImmutable());
        markDirty();
    }

    public void removeLinkedBeamer(BlockPos pos) {
        linkedBeamers.remove(pos);
        markDirty();
    }

    private void updateBeamers() {
        if (stargateState.engaged()) {
            for (BlockPos beamerPos : linkedBeamers) {
                ((BeamerTile) world.getTileEntity(beamerPos)).gateEngaged(targetGatePos);
            }
        }
    }


    // -----------------------------------------------------------------
    // OpenComputers methods

    @Optional.Method(modid = "opencomputers")
    @Callback(doc = "function() -- close/open the iris/shield")
    public Object[] toggleIris(Context context, Arguments args) {
        boolean result = toggleIris();
        markDirty();
        if (!result)
            return new Object[]{null, "stargate_iris_missing", "The stargate does not have iris", "or iris is already closing/opening"};
        return new Object[]{null, "stargate_iris_toggled", "Iris is opening/closing"};
    }

    @Optional.Method(modid = "opencomputers")
    @Callback(doc = "function() -- get info about iris")
    public Object[] getIrisState(Context context, Arguments args) {
        return new Object[]{irisState.toString()};
    }

    @Optional.Method(modid = "opencomputers")
    @Callback(doc = "function() -- get info about iris")
    public Object[] getIrisType(Context context, Arguments args) {
        return new Object[]{irisType.toString()};
    }

    @Optional.Method(modid = "opencomputers")
    @Callback(doc = "function() -- get info about iris")
    public Object[] getIrisDurability(Context context, Arguments args) {
        return new Object[]{irisDurability + "/" + irisMaxDurability};
    }


    @Optional.Method(modid = "opencomputers")
    @Callback(doc = "function(symbolName:string) -- Spins the ring to the given symbol and engages/locks it")
    public Object[] engageSymbol(Context context, Arguments args) {
        if (!isMerged()) return new Object[]{null, "stargate_failure_not_merged", "Stargate is not merged"};

        if (!stargateState.idle()) {
            return new Object[]{null, "stargate_failure_busy", "Stargate is busy, state: " + stargateState.toString()};
        }

        if (dialedAddress.size() == 9) {
            return new Object[]{null, "stargate_failure_full", "Already dialed 9 chevrons"};
        }

        SymbolInterface targetSymbol = getSymbolFromNameIndex(args.checkAny(0));
        addSymbolToAddressManual(targetSymbol, context);
        markDirty();

        return new Object[]{"stargate_spin"};
    }

    @Optional.Method(modid = "opencomputers")
    @Callback(doc = "function() -- Tries to open the gate")
    public Object[] engageGate(Context context, Arguments args) {
        if (!isMerged()) return new Object[]{null, "stargate_failure_not_merged", "Stargate is not merged"};

        if (stargateState.idle()) {
            StargateOpenResult gateState = attemptOpenAndFail();

            if (gateState.ok()) {
                return new Object[]{"stargate_engage"};
            } else {
                sendSignal(null, "stargate_failed", new Object[]{});
                return new Object[]{null, "stargate_failure_opening", "Stargate failed to open", gateState.toString()};
            }
        } else {
            return new Object[]{null, "stargate_failure_busy", "Stargate is busy", stargateState.toString()};
        }
    }

    @Optional.Method(modid = "opencomputers")
    @Callback(doc = "function() -- Tries to close the gate")
    public Object[] disengageGate(Context context, Arguments args) {
        if (!isMerged()) return new Object[]{null, "stargate_failure_not_merged", "Stargate is not merged"};

        if (stargateState.engaged()) {
            if (getStargateState().initiating()) {
                attemptClose(StargateClosedReasonEnum.REQUESTED);
                return new Object[]{"stargate_disengage"};
            } else return new Object[]{null, "stargate_failure_wrong_end", "Unable to close the gate on this end"};
        } else {
            return new Object[]{null, "stargate_failure_not_open", "The gate is closed"};
        }
    }

    @Optional.Method(modid = "opencomputers")
    @Callback
    public Object[] getCapacitorsInstalled(Context context, Arguments args) {
        return new Object[]{isMerged() ? currentPowerTier - 1 : null};
    }

    @Optional.Method(modid = "opencomputers")
    @Callback
    public Object[] getGateType(Context context, Arguments args) {
        return new Object[]{isMerged() ? getSymbolType() : null};
    }

    @Optional.Method(modid = "opencomputers")
    @Callback
    public Object[] getGateStatus(Context context, Arguments args) {
        if (!isMerged()) return new Object[]{"not_merged"};

        if (stargateState.engaged()) return new Object[]{"open", stargateState.initiating()};

        return new Object[]{stargateState.toString().toLowerCase()};
    }

    @SuppressWarnings("unchecked")
    @Optional.Method(modid = "opencomputers")
    @Callback
    public Object[] getEnergyRequiredToDial(Context context, Arguments args) {
        if (!isMerged()) return new Object[]{"not_merged"};

        StargateAddressDynamic stargateAddress = new StargateAddressDynamic(getSymbolType());
        Iterator<Object> iter = null;

        if (args.isTable(0)) {
            iter = args.checkTable(0).values().iterator();
        } else {
            iter = args.iterator();
        }

        while (iter.hasNext()) {
            Object symbolObj = iter.next();

            if (stargateAddress.size() == 9) {
                throw new IllegalArgumentException("Too much glyphs");
            }

            SymbolInterface symbol = getSymbolFromNameIndex(symbolObj);
            if (stargateAddress.contains(symbol)) {
                throw new IllegalArgumentException("Duplicate glyph");
            }

            stargateAddress.addSymbol(symbol);
        }

        if (!stargateAddress.getLast().origin() && stargateAddress.size() < 9) stargateAddress.addOrigin();

        if (!stargateAddress.validate()) return new Object[]{"address_malformed"};

        if (!canDialAddress(stargateAddress)) return new Object[]{"address_malformed"};

        StargateEnergyRequired energyRequired = getEnergyRequiredToDial(network.getStargate(stargateAddress));
        Map<String, Object> energyMap = new HashMap<>(2);

        energyMap.put("open", energyRequired.energyToOpen);
        energyMap.put("keepAlive", energyRequired.keepAlive);
        energyMap.put("canOpen", getEnergyStorage().getEnergyStored() >= energyRequired.energyToOpen);

        return new Object[]{energyMap};
    }
}

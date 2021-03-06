package mrjake.aunis.proxy;

import mrjake.aunis.block.AunisBlocks;
import mrjake.aunis.block.stargate.StargateClassicMemberBlockColor;
import mrjake.aunis.entity.friendly.TokraEntity;
import mrjake.aunis.entity.renderer.TokraRenderer;
import mrjake.aunis.event.InputHandlerClient;
import mrjake.aunis.fluid.AunisBlockFluid;
import mrjake.aunis.fluid.AunisFluids;
import mrjake.aunis.item.AunisItems;
import mrjake.aunis.item.color.PageMysteriousItemColor;
import mrjake.aunis.item.color.PageNotebookItemColor;
import mrjake.aunis.item.renderer.CustomModelItemInterface;
import mrjake.aunis.loader.ReloadListener;
import mrjake.aunis.renderer.BeamerRenderer;
import mrjake.aunis.renderer.dialhomedevice.DHDMilkyWayRenderer;
import mrjake.aunis.renderer.dialhomedevice.DHDPegasusRenderer;
import mrjake.aunis.renderer.stargate.*;
import mrjake.aunis.renderer.transportrings.TRControllerGoauldRenderer;
import mrjake.aunis.renderer.transportrings.TransportRingsGoauldRenderer;
import mrjake.aunis.renderer.transportrings.TransportRingsOriRenderer;
import mrjake.aunis.sound.AunisSoundHelperClient;
import mrjake.aunis.sound.SoundPositionedEnum;
import mrjake.aunis.tileentity.BeamerTile;
import mrjake.aunis.tileentity.dialhomedevice.DHDMilkyWayTile;
import mrjake.aunis.tileentity.dialhomedevice.DHDPegasusTile;
import mrjake.aunis.tileentity.stargate.StargateMilkyWayBaseTile;
import mrjake.aunis.tileentity.stargate.StargateOrlinBaseTile;
import mrjake.aunis.tileentity.stargate.StargatePegasusBaseTile;
import mrjake.aunis.tileentity.stargate.StargateUniverseBaseTile;
import mrjake.aunis.tileentity.transportrings.TRControllerGoauldTile;
import mrjake.aunis.tileentity.transportrings.TransportRingsGoauldTile;
import mrjake.aunis.tileentity.transportrings.TransportRingsOriTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.block.statemap.StateMap;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.obj.OBJLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class ProxyClient implements IProxy {
    public void preInit(FMLPreInitializationEvent event) {
        registerRenderers();
        registerFluidRenderers();

        InputHandlerClient.registerKeybindings();
    }

    public void init(FMLInitializationEvent event) {
        Minecraft.getMinecraft().getItemColors().registerItemColorHandler(new PageMysteriousItemColor(), AunisItems.PAGE_MYSTERIOUS_ITEM);
        Minecraft.getMinecraft().getItemColors().registerItemColorHandler(new PageNotebookItemColor(), AunisItems.PAGE_NOTEBOOK_ITEM);

        Minecraft.getMinecraft().getBlockColors().registerBlockColorHandler(new StargateClassicMemberBlockColor(), AunisBlocks.STARGATE_MILKY_WAY_MEMBER_BLOCK, AunisBlocks.STARGATE_UNIVERSE_MEMBER_BLOCK, AunisBlocks.STARGATE_PEGASUS_MEMBER_BLOCK);
    }

    public void postInit(FMLPostInitializationEvent event) {
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(new ReloadListener());
    }

    public String localize(String unlocalized, Object... args) {
        return I18n.format(unlocalized, args);
    }

    private void registerRenderers() {
        OBJLoader.INSTANCE.addDomain("aunis");

        ClientRegistry.bindTileEntitySpecialRenderer(StargateMilkyWayBaseTile.class, new StargateMilkyWayRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(StargateUniverseBaseTile.class, new StargateUniverseRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(StargateOrlinBaseTile.class, new StargateOrlinRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(StargatePegasusBaseTile.class, new StargatePegasusRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(DHDMilkyWayTile.class, new DHDMilkyWayRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(DHDPegasusTile.class, new DHDPegasusRenderer());

        ClientRegistry.bindTileEntitySpecialRenderer(TransportRingsGoauldTile.class, new TransportRingsGoauldRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(TransportRingsOriTile.class, new TransportRingsOriRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(TRControllerGoauldTile.class, new TRControllerGoauldRenderer());

        ClientRegistry.bindTileEntitySpecialRenderer(BeamerTile.class, new BeamerRenderer());
        registerEntityRenderers();
    }

    private void registerEntityRenderers() {
        RenderingRegistry.registerEntityRenderingHandler(TokraEntity.class, TokraRenderer::new);
    }


    private void registerFluidRenderers() {
        for (AunisBlockFluid blockFluid : AunisFluids.blockFluidMap.values()) {
            ModelLoader.setCustomStateMapper(blockFluid, new StateMap.Builder().ignore(AunisBlockFluid.LEVEL).build());
        }
    }

    @Override
    public EntityPlayer getPlayerInMessageHandler(MessageContext ctx) {
        return Minecraft.getMinecraft().player;
    }

    @Override
    public void setTileEntityItemStackRenderer(Item item) {
        item.setTileEntityItemStackRenderer(((CustomModelItemInterface) item).createTEISR());
    }

    @Override
    public EntityPlayer getPlayerClientSide() {
        return Minecraft.getMinecraft().player;
    }

    @Override
    public void addScheduledTaskClientSide(Runnable runnable) {
        Minecraft.getMinecraft().addScheduledTask(runnable);
    }

    @Override
    public void orlinRendererSpawnParticles(World world, StargateAbstractRendererState rendererState) {
        StargateOrlinRenderer.spawnParticles(world, rendererState);
    }

    @Override
    public void playPositionedSoundClientSide(BlockPos pos, SoundPositionedEnum soundEnum, boolean play) {
        AunisSoundHelperClient.playPositionedSoundClientSide(pos, soundEnum, play);
    }

    @Override
    public void openGui(GuiScreen gui) {
        Minecraft.getMinecraft().displayGuiScreen(gui);
    }
}

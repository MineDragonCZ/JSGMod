package mrjake.aunis.block.dialhomedevice;

import mrjake.aunis.gui.GuiIdEnum;
import mrjake.aunis.tileentity.dialhomedevice.DHDMilkyWayTile;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class DHDBlock extends DHDAbstractBlock {

    public static final String BLOCK_NAME = "dhd_block";

    public DHDBlock() {
        super(BLOCK_NAME);
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new DHDMilkyWayTile();
    }

    @Override
    public Class<? extends TileEntity> getTileEntityClass() {
        return DHDMilkyWayTile.class;
    }

    @Override
    public GuiIdEnum getGui() {
        return GuiIdEnum.GUI_DHD;
    }
}

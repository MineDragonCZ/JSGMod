package mrjake.aunis.block.ore;

import mrjake.aunis.Aunis;
import mrjake.aunis.block.AunisBlock;
import mrjake.aunis.item.AunisItems;
import mrjake.aunis.util.main.loader.AunisCreativeTabsHandler;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

public class NaquadahOreBlock extends AunisBlock {
		
	public NaquadahOreBlock(String blockName) {
		super(Material.ROCK);
		
		setRegistryName(Aunis.MOD_ID + ":" + blockName);
		setUnlocalizedName(Aunis.MOD_ID + "." + blockName);
		
		setSoundType(SoundType.STONE); 
		setCreativeTab(AunisCreativeTabsHandler.aunisOresCreativeTab);
		
		setHardness(4.5f);
		setHarvestLevel("pickaxe", 3);
	}
	
	@Override
	public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
		Random random = world instanceof World ? ((World)world).rand : RANDOM;
		
		int quantity = 5 + random.nextInt(4) + (fortune * random.nextInt(3));
		
		drops.add(new ItemStack(AunisItems.NAQUADAH_SHARD, quantity));
	}
	
	@Override
	public int getExpDrop(IBlockState state, IBlockAccess world, BlockPos pos, int fortune) {
        Random rand = world instanceof World ? ((World)world).rand : new Random();
        return MathHelper.getInt(rand, 5, 10);
	}
	
	@Override
	public BlockRenderLayer getBlockLayer() {
		return BlockRenderLayer.CUTOUT;
	}
}

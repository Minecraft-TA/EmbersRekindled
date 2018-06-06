package teamroots.embers.tileentity;

import java.util.HashSet;
import java.util.Random;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import teamroots.embers.Embers;
import teamroots.embers.EventManager;
import teamroots.embers.SoundManager;
import teamroots.embers.particle.ParticleUtil;
import teamroots.embers.power.DefaultEmberCapability;
import teamroots.embers.power.EmberCapabilityProvider;
import teamroots.embers.power.IEmberCapability;
import teamroots.embers.recipe.ItemMeltingRecipe;
import teamroots.embers.recipe.RecipeRegistry;
import teamroots.embers.util.sound.ISoundController;

public class TileEntityFurnaceBottom extends TileEntity implements ITileEntityBase, ITickable, ISoundController {
	public IEmberCapability capability = new DefaultEmberCapability();
	Random random = new Random();
	int progress = -1;
	public static final double EMBER_COST = 1.0;

	public static final int SOUND_PROCESS = 1;
	public static final int[] SOUND_IDS = new int[]{SOUND_PROCESS};

	HashSet<Integer> soundsPlaying = new HashSet<>();
	boolean isWorking;

	public TileEntityFurnaceBottom(){
		super();
		capability.setEmberCapacity(8000);
	}
	
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tag){
		super.writeToNBT(tag);
		capability.writeToNBT(tag);
		tag.setInteger("progress", progress);
		return tag;
	}
	
	@Override
	public void readFromNBT(NBTTagCompound tag){
		super.readFromNBT(tag);
		capability.readFromNBT(tag);
		if (tag.hasKey("progress")){
			progress = tag.getInteger("progress");
		}
	}

	@Override
	public NBTTagCompound getUpdateTag() {
		return writeToNBT(new NBTTagCompound());
	}

	@Nullable
	@Override
	public SPacketUpdateTileEntity getUpdatePacket() {
		return new SPacketUpdateTileEntity(getPos(), 0, getUpdateTag());
	}

	@Override
	public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
		readFromNBT(pkt.getNbtCompound());
	}

	@Override
	public boolean activate(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
			EnumFacing side, float hitX, float hitY, float hitZ) {
		return false;
	}

	@Override
	public void breakBlock(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
		this.invalidate();
		world.setTileEntity(pos, null);
	}
	
	@Override
	public boolean hasCapability(Capability<?> capability, EnumFacing facing){
		if (capability == EmberCapabilityProvider.emberCapability){
			return true;
		}
		return super.hasCapability(capability, facing);
	}
	
	@Override
	public <T> T getCapability(Capability<T> capability, EnumFacing facing){
		if (capability == EmberCapabilityProvider.emberCapability){
			return (T)this.capability;
		}
		return super.getCapability(capability, facing);
	}
	
	public boolean dirty = false;
	
	@Override
	public void markForUpdate(){
		EventManager.markTEForUpdate(getPos(), this);
	}
	
	@Override
	public void markDirty(){
		markForUpdate();
		super.markDirty();
	}

	@Override
	public void update() {
		if(getWorld().isRemote)
			handleSound();
		TileEntityFurnaceTop top = (TileEntityFurnaceTop) world.getTileEntity(getPos().up());
		if(top != null && !top.inventory.getStackInSlot(0).isEmpty()) {
			if (progress == -1) {
				progress = 200;
				markDirty();
			} else if (capability.getEmber() >= EMBER_COST) {
				capability.removeAmount(EMBER_COST, true);
				if (world.isRemote) {
					if (random.nextInt(20) == 0) {
						ParticleUtil.spawnParticleSpark(world, getPos().getX() + 0.5f + 0.125f * (random.nextFloat() - 0.5f), getPos().getY() + 1.25f, getPos().getZ() + 0.5f + 0.125f * (random.nextFloat() - 0.5f), 0.125f * (random.nextFloat() - 0.5f), 0.125f * (random.nextFloat()), 0.125f * (random.nextFloat() - 0.5f), 255, 64, 16, random.nextFloat() * 0.75f + 0.45f, 80);
					}
					if (random.nextInt(10) == 0) {
						for (int i = 0; i < 12; i++) {
							ParticleUtil.spawnParticleSmoke(world, getPos().getX() + 0.5f + 0.125f * (random.nextFloat() - 0.5f), getPos().getY() + 1.25f, getPos().getZ() + 0.5f + 0.125f * (random.nextFloat() - 0.5f), 0, 0.03125f + 0.03125f * random.nextFloat(), 0, 64, 64, 64, 0.125f, 5.0f + 3.0f * random.nextFloat(), 80);
						}
					}
				}
				isWorking = true;
				progress--;
				markDirty();
				if (progress == 0) {
					ItemStack recipeStack = top.inventory.getStackInSlot(0);
					ItemMeltingRecipe recipe = RecipeRegistry.getMeltingRecipe(recipeStack);
					if (recipe != null && !world.isRemote) {
						FluidStack output = recipe.getResult(this, recipeStack);
						FluidTank tank = top.getTank();
						if (output != null && tank.fill(output, false) >= output.amount) {
							tank.fill(output, true);
							top.markDirty();
							top.inventory.extractItem(0, recipe.getInputConsumed(), false);
							markDirty();
						}
					}
				}
			}
		} else {
			isWorking = false;
			if (progress != -1) {
				progress = -1;
				markDirty();
			}
		}
	}

	@Override
	public void playSound(int id) {
		switch (id) {
			case SOUND_PROCESS:
				Embers.proxy.playMachineSound(this, SOUND_PROCESS, SoundManager.MELTER_LOOP, SoundCategory.BLOCKS, true, 1.0f, 1.0f, (float)pos.getX()+0.5f,(float)pos.getY()+1.0f,(float)pos.getZ()+0.5f);
				break;
		}
		soundsPlaying.add(id);
	}

	@Override
	public void stopSound(int id) {
		soundsPlaying.remove(id);
	}

	@Override
	public boolean isSoundPlaying(int id) {
		return soundsPlaying.contains(id);
	}

	@Override
	public int[] getSoundIDs() {
		return SOUND_IDS;
	}

	@Override
	public boolean shouldPlaySound(int id) {
		return id == SOUND_PROCESS && isWorking;
	}
}

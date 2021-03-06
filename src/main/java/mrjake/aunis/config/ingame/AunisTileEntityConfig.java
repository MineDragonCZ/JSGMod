package mrjake.aunis.config.ingame;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AunisTileEntityConfig {

    private List<AunisConfigOption> options;

    public AunisTileEntityConfig() {
        this.options = new ArrayList<>();
    }

    public AunisTileEntityConfig(ByteBuf buf) {
        this.fromBytes(buf);
    }

    public void addOptions(@Nonnull AunisConfigOption... options){
        this.options.addAll(Arrays.asList(options));
    }

    public void addOption(@Nonnull AunisConfigOption options){
        this.options.add(options);
    }

    public List<AunisConfigOption> getOptions() {
        return options;
    }

    public AunisConfigOption getOption(int id){
        if(id < options.size())
            return options.get(id);
        return new AunisConfigOption(id).setLabel("error while getting option! (" + id + ")").setComment("").setType(AunisConfigOptionTypeEnum.TEXT).setValue("");
    }

    public void clearOptions(){
        options.clear();
    }

    public NBTTagCompound serializeNBT() {
        NBTTagCompound compound = new NBTTagCompound();
        compound.setInteger("Size", options.size());
        for (int i = 0; i < options.size(); i++) {
            compound.setTag("Option" + i, options.get(i).serializeNBT());
        }
        return compound;
    }

    public void deserializeNBT(NBTTagCompound compound) {
        int size = compound.getInteger("Size");
        options.clear();
        for (int i = 0; i < size; i++) {
            options.add(new AunisConfigOption(compound.getCompoundTag("Option" + i)));
        }
    }

    public void toBytes(ByteBuf buf){
        buf.writeInt(options.size());
        for (AunisConfigOption option : options) {
            option.toBytes(buf);
        }
    }

    public void fromBytes(ByteBuf buf) {
        this.options = new ArrayList<>();
        int size = buf.readInt();
        for(int i = 0; i < size; i++){
            options.add(new AunisConfigOption(buf));
        }
    }
}

package mrjake.aunis.stargate.network;

import mrjake.aunis.Aunis;
import mrjake.aunis.datafixer.StargateNetworkReader18;
import mrjake.aunis.stargate.network.internalgates.StargateAddressesEnum;
import mrjake.aunis.stargate.network.internalgates.StargateInternalAddress;
import mrjake.aunis.stargate.network.internalgates.StargateInternalGates;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static mrjake.aunis.stargate.network.internalgates.StargateAddressesEnum.EARTH;

public class StargateNetwork extends WorldSavedData {

    private static final String DATA_NAME = Aunis.MOD_ID + "_StargateNetworkData";
    public final StargateInternalGates INTERNAL_GATES = new StargateInternalGates();
    private Map<SymbolTypeEnum, Map<StargateAddress, StargatePos>> stargateNetworkMap = new HashMap<>();
    private StargateAddress netherGateAddress;

    public StargateNetwork() {
        super(DATA_NAME);
        init();
    }


    // ---------------------------------------------------------------------------------------------------------
    // Stargate Network

    public StargateNetwork(String dataName) {
        super(dataName);
        init();
    }

    public static StargateNetwork get(World world) {
        MapStorage storage = world.getMapStorage();
        StargateNetwork instance = (StargateNetwork) storage.getOrLoadData(StargateNetwork.class, DATA_NAME);

        if (instance == null) {
            instance = new StargateNetwork();
            storage.setData(DATA_NAME, instance);
        }

        return instance;
    }

    private void init() {
        for (SymbolTypeEnum symbolType : SymbolTypeEnum.values())
            stargateNetworkMap.put(symbolType, new ConcurrentHashMap<>());
        INTERNAL_GATES.init();
    }

    private Map<StargateAddress, StargatePos> getMapFromAddress(StargateAddress address) {
        return stargateNetworkMap.get(address.getSymbolType());
    }

    public Map<SymbolTypeEnum, Map<StargateAddress, StargatePos>> getMap() {
        return stargateNetworkMap;
    }

    public boolean isStargateInNetwork(StargateAddress gateAddress) {
        return getMapFromAddress(gateAddress).containsKey(gateAddress);
    }


    // ---------------------------------------------------------------------------------------------------------
    // Nether gate

  /*public static StargateAddressDynamic EARTH_ADDRESS = new StargateAddressDynamic(SymbolTypeEnum.MILKYWAY);

  static {
    EARTH_ADDRESS.addSymbol(SymbolMilkyWayEnum.AURIGA);
    EARTH_ADDRESS.addSymbol(SymbolMilkyWayEnum.CETUS);
    EARTH_ADDRESS.addSymbol(SymbolMilkyWayEnum.CENTAURUS);
    EARTH_ADDRESS.addSymbol(SymbolMilkyWayEnum.CANCER);
    EARTH_ADDRESS.addSymbol(SymbolMilkyWayEnum.SCUTUM);
    EARTH_ADDRESS.addSymbol(SymbolMilkyWayEnum.ERIDANUS);
    EARTH_ADDRESS.addSymbol(SymbolMilkyWayEnum.SERPENSCAPUT);
  }*/

    @Nullable
    public StargatePos getStargate(StargateAddress address) {
        if (address == null)
            return null;

        return getMapFromAddress(address).get(address);
    }

    public void addStargate(StargateAddress gateAddress, StargatePos stargatePos) {
        if (gateAddress == null) return;

        getMapFromAddress(gateAddress).put(gateAddress, stargatePos);

        markDirty();
    }

    public void removeStargate(StargateAddress gateAddress) {
        if (gateAddress == null) return;

        getMapFromAddress(gateAddress).remove(gateAddress);

        markDirty();
    }

    public boolean hasNetherGate() {
        return netherGateAddress != null;
    }

    public void deleteNetherGate() {
        netherGateAddress = null;
        markDirty();
    }

    public StargateAddress getNetherGate() {
        return netherGateAddress;
    }

    public void setNetherGate(StargateAddress address) {
        netherGateAddress = address;
        markDirty();
    }

    public StargateInternalAddress getInternalAddress(int id) {
        return INTERNAL_GATES.map.get(id);
    }

    public void setLastActivatedOrlins(StargateAddress address) {
        getInternalAddress(EARTH.id).addressToReplace.clear();
        getInternalAddress(EARTH.id).addressToReplace.addAll(address);
        markDirty();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Reading and writing

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        if (compound.hasKey("size")) StargateNetworkReader18.readOldMap(compound, this);

        NBTTagList stargateTagList = compound.getTagList("stargates", NBT.TAG_COMPOUND);

        for (NBTBase baseTag : stargateTagList) {
            NBTTagCompound stargateCompound = (NBTTagCompound) baseTag;

            StargateAddress stargateAddress = new StargateAddress(stargateCompound.getCompoundTag("address"));
            StargatePos stargatePos = new StargatePos(stargateAddress.getSymbolType(), stargateCompound.getCompoundTag("pos"));

            getMapFromAddress(stargateAddress).put(stargateAddress, stargatePos);
        }

        if (compound.hasKey("netherGateAddress"))
            netherGateAddress = new StargateAddress(compound.getCompoundTag("netherGateAddress"));

        INTERNAL_GATES.deserializeNBT(compound.getCompoundTag("internalGates"));

        if (compound.hasKey("lastActivatedOrlins")) {
            getInternalAddress(EARTH.id).addressToReplace.clear();
            getInternalAddress(EARTH.id).addressToReplace.addAll(new StargateAddressDynamic(compound.getCompoundTag("lastActivatedOrlins")));
        }
    }


    // ---------------------------------------------------------------------------------------------------------
    // Static

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList stargateTagList = new NBTTagList();

        for (Iterator<Map<StargateAddress, StargatePos>> systemsIterator = stargateNetworkMap.values().iterator(); systemsIterator.hasNext(); ) {
            Map<StargateAddress, StargatePos> stargateMap = systemsIterator.next();

            for (Iterator<Map.Entry<StargateAddress, StargatePos>> perSystemsIterator = stargateMap.entrySet().iterator(); perSystemsIterator.hasNext(); ) {
                Map.Entry<StargateAddress, StargatePos> stargateEntry = perSystemsIterator.next();

                NBTTagCompound stargateCompound = new NBTTagCompound();
                stargateCompound.setTag("address", stargateEntry.getKey().serializeNBT());
                stargateCompound.setTag("pos", stargateEntry.getValue().serializeNBT());
                stargateTagList.appendTag(stargateCompound);
            }
        }

        compound.setTag("stargates", stargateTagList);

        if (netherGateAddress != null) compound.setTag("netherGateAddress", netherGateAddress.serializeNBT());

        compound.setTag("internalGates", INTERNAL_GATES.serializeNBT());

        return compound;
    }
}

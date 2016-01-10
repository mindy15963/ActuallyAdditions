/*
 * This file ("LaserRelayConnectionHandler.java") is part of the Actually Additions Mod for Minecraft.
 * It is created and owned by Ellpeck and distributed
 * under the Actually Additions License to be found at
 * http://ellpeck.de/actaddlicense/
 * View the source code at https://github.com/Ellpeck/ActuallyAdditions
 *
 * © 2016 Ellpeck
 */

package de.ellpeck.actuallyadditions.mod.misc;

import cofh.api.energy.IEnergyReceiver;
import de.ellpeck.actuallyadditions.mod.config.values.ConfigIntValues;
import de.ellpeck.actuallyadditions.mod.tile.TileEntityLaserRelay;
import de.ellpeck.actuallyadditions.mod.util.PosUtil;
import de.ellpeck.actuallyadditions.mod.util.WorldUtil;
import io.netty.util.internal.ConcurrentSet;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

public class LaserRelayConnectionHandler{

    private static LaserRelayConnectionHandler instance;

    /**
     * All of the Networks
     */
    public ConcurrentSet<Network> networks = new ConcurrentSet<Network>();

    public static LaserRelayConnectionHandler getInstance(){
        return instance;
    }

    public static void setInstance(LaserRelayConnectionHandler handler){
        instance = handler;
    }

    public NBTTagCompound writeNetworkToNBT(Network network){
        NBTTagList list = new NBTTagList();
        for(ConnectionPair pair : network.connections){
            list.appendTag(pair.writeToNBT());
        }
        NBTTagCompound compound = new NBTTagCompound();
        compound.setTag("Network", list);
        return compound;
    }

    public Network readNetworkFromNBT(NBTTagCompound tag){
        NBTTagList list = tag.getTagList("Network", 10);
        Network network = new Network();
        for(int i = 0; i < list.tagCount(); i++){
            network.connections.add(ConnectionPair.readFromNBT(list.getCompoundTagAt(i)));
        }
        return network;
    }

    /**
     * Gets all Connections for a Relay
     */
    public ConcurrentSet<ConnectionPair> getConnectionsFor(BlockPos relay){
        ConcurrentSet<ConnectionPair> allPairs = new ConcurrentSet<ConnectionPair>();
        for(Network aNetwork : this.networks){
            for(ConnectionPair pair : aNetwork.connections){
                if(pair.contains(relay)){
                    allPairs.add(pair);
                }
            }
        }
        return allPairs;
    }

    /**
     * Removes a Relay from its Network
     */
    public void removeRelayFromNetwork(BlockPos relay){
        Network network = this.getNetworkFor(relay);
        if(network != null){
            //Setup new network (so that splitting a network will cause it to break into two)
            this.networks.remove(network);
            for(ConnectionPair pair : network.connections){
                if(!pair.contains(relay)){
                    this.addConnection(pair.firstRelay, pair.secondRelay);
                }
            }
            //System.out.println("Removing a Relay from the Network!");
        }
        WorldData.makeDirty();
    }

    /**
     * Gets a Network for a Relay
     */
    public Network getNetworkFor(BlockPos relay){
        for(Network aNetwork : this.networks){
            for(ConnectionPair pair : aNetwork.connections){
                if(pair.contains(relay)){
                    return aNetwork;
                }
            }
        }
        return null;
    }

    /**
     * Adds a new connection between two relays
     * (Puts it into the correct network!)
     */
    public boolean addConnection(BlockPos firstRelay, BlockPos secondRelay){
        int distance = (int)PosUtil.toVec(firstRelay).distanceTo(PosUtil.toVec(secondRelay));
        if(distance > TileEntityLaserRelay.MAX_DISTANCE || PosUtil.areSamePos(firstRelay, secondRelay)){
            return false;
        }

        Network firstNetwork = this.getNetworkFor(firstRelay);
        Network secondNetwork = this.getNetworkFor(secondRelay);

        //No Network exists
        if(firstNetwork == null && secondNetwork == null){
            firstNetwork = new Network();
            this.networks.add(firstNetwork);
            firstNetwork.connections.add(new ConnectionPair(firstRelay, secondRelay));
        }
        //The same Network
        else if(firstNetwork == secondNetwork){
            return false;
        }
        //Both relays have networks
        else if(firstNetwork != null && secondNetwork != null){
            this.mergeNetworks(firstNetwork, secondNetwork);
            firstNetwork.connections.add(new ConnectionPair(firstRelay, secondRelay));
        }
        //Only first network exists
        else if(firstNetwork != null){
            firstNetwork.connections.add(new ConnectionPair(firstRelay, secondRelay));
        }
        //Only second network exists
        else if(secondNetwork != null){
            secondNetwork.connections.add(new ConnectionPair(firstRelay, secondRelay));
        }
        WorldData.makeDirty();
        //System.out.println("Connected "+firstRelay.toString()+" to "+secondRelay.toString());
        //System.out.println(firstNetwork == null ? secondNetwork.toString() : firstNetwork.toString());
        //System.out.println(this.networks);
        return true;
    }

    /**
     * Merges two networks together
     * (Actually puts everything from the second network into the first one and removes the second one)
     */
    public void mergeNetworks(Network firstNetwork, Network secondNetwork){
        for(ConnectionPair secondPair : secondNetwork.connections){
            firstNetwork.connections.add(secondPair);
        }
        this.networks.remove(secondNetwork);
        WorldData.makeDirty();
        //System.out.println("Merged Two Networks!");
    }

    public int transferEnergyToReceiverInNeed(World world, BlockPos energyGottenFrom, Network network, int maxTransfer, boolean simulate){
        int transmitted = 0;
        //Go through all of the connections in the network
        for(ConnectionPair pair : network.connections){
            BlockPos[] relays = new BlockPos[]{pair.firstRelay, pair.secondRelay};
            //Go through both relays in the connection
            for(BlockPos relay : relays){
                if(relay != null){
                    //Get every side of the relay
                    for(int i = 0; i <= 5; i++){
                        EnumFacing side = WorldUtil.getDirectionBySidesInOrder(i);
                        //Get the Position at the side
                        BlockPos pos = WorldUtil.getCoordsFromSide(side, relay, 0);
                        if(!PosUtil.areSamePos(pos, energyGottenFrom)){
                            TileEntity tile = world.getTileEntity(pos);
                            if(tile instanceof IEnergyReceiver && !(tile instanceof TileEntityLaserRelay)){
                                IEnergyReceiver receiver = (IEnergyReceiver)tile;
                                if(receiver.canConnectEnergy(side.getOpposite())){
                                    //Transfer the energy (with the energy loss!)
                                    int theoreticalReceived = ((IEnergyReceiver)tile).receiveEnergy(side.getOpposite(), maxTransfer-transmitted, true);
                                    //The amount of energy lost during a transfer
                                    int deduct = (int)(theoreticalReceived*((double)ConfigIntValues.LASER_RELAY_LOSS.getValue()/100));

                                    transmitted += ((IEnergyReceiver)tile).receiveEnergy(side.getOpposite(), theoreticalReceived-deduct, simulate);
                                    transmitted += deduct;

                                    //If everything that could be transmitted was transmitted
                                    if(transmitted >= maxTransfer){
                                        return transmitted;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return transmitted;
    }

    public static class ConnectionPair{

        public BlockPos firstRelay;
        public BlockPos secondRelay;

        public ConnectionPair(BlockPos firstRelay, BlockPos secondRelay){
            this.firstRelay = firstRelay;
            this.secondRelay = secondRelay;
        }

        public static ConnectionPair readFromNBT(NBTTagCompound compound){
            if(compound != null){
                BlockPos[] pos = new BlockPos[2];
                for(int i = 0; i < pos.length; i++){
                    int anX = compound.getInteger("x"+i);
                    int aY = compound.getInteger("y"+i);
                    int aZ = compound.getInteger("z"+i);
                    pos[i] = new BlockPos(anX, aY, aZ);
                }
                return new ConnectionPair(pos[0], pos[1]);
            }
            return null;
        }

        public boolean contains(BlockPos relay){
            return (this.firstRelay != null && PosUtil.areSamePos(firstRelay, relay)) || (this.secondRelay != null && PosUtil.areSamePos(secondRelay, relay));
        }

        @Override
        public String toString(){
            return (this.firstRelay == null ? "-" : this.firstRelay.toString())+" | "+(this.secondRelay == null ? "-" : this.secondRelay.toString());
        }

        public NBTTagCompound writeToNBT(){
            NBTTagCompound compound = new NBTTagCompound();
            for(int i = 0; i < 2; i++){
                BlockPos relay = i == 0 ? this.firstRelay : this.secondRelay;
                compound.setInteger("x"+i, relay.getX());
                compound.setInteger("y"+i, relay.getY());
                compound.setInteger("z"+i, relay.getZ());
            }
            return compound;
        }
    }

    public static class Network{

        public ConcurrentSet<ConnectionPair> connections = new ConcurrentSet<ConnectionPair>();

        @Override
        public String toString(){
            return this.connections.toString();
        }
    }
}
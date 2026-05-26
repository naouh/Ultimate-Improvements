package com.nao.claimteam.data;

import net.minecraft.nbt.NBTTagCompound;

public class Claim {
    public int chunkX;
    public int chunkZ;
    public int dim;
    public String teamName;
    public boolean chunkload;

    public Claim() {}

    public Claim(int chunkX, int chunkZ, int dim, String teamName, boolean chunkload) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.dim = dim;
        this.teamName = teamName;
        this.chunkload = chunkload;
    }

    public void writeTo(NBTTagCompound nbt) {
        nbt.setInteger("cx", chunkX);
        nbt.setInteger("cz", chunkZ);
        nbt.setInteger("dim", dim);
        nbt.setString("team", teamName == null ? "" : teamName);
        nbt.setBoolean("cl", chunkload);
    }

    public void readFrom(NBTTagCompound nbt) {
        chunkX = nbt.getInteger("cx");
        chunkZ = nbt.getInteger("cz");
        dim = nbt.getInteger("dim");
        teamName = nbt.getString("team");
        chunkload = nbt.getBoolean("cl");
    }
}

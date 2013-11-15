package com.palmergames.bukkit.blockqueue;

import org.bukkit.Material;
import org.bukkit.World;

public class BlockWork {

	private World world;
	private Material type;
	private int x, y, z;
	private byte data;

	public BlockWork(World world, Material type, int x, int y, int z, byte data) {

		this.world = world;
		this.type = type;
		this.x = x;
		this.y = y;
		this.z = z;
		this.data = data;
	}

	public World getWorld() {

		return world;
	}

	public Material getType() {

		return type;
	}

	public int getX() {

		return x;
	}

	public int getY() {

		return y;
	}

	public int getZ() {

		return z;
	}

	public byte getData() {

		return data;
	}
}

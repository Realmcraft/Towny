package com.palmergames.bukkit.towny.listeners;

import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.TownyMessaging;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.TownyTimerHandler;
import com.palmergames.bukkit.towny.event.PlayerChangePlotEvent;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.bukkit.towny.object.PlayerCache.TownBlockStatus;
import com.palmergames.bukkit.towny.permissions.PermissionNodes;
import com.palmergames.bukkit.towny.permissions.TownyPerms;
import com.palmergames.bukkit.towny.regen.TownyRegenAPI;
import com.palmergames.bukkit.towny.regen.block.BlockLocation;
import com.palmergames.bukkit.towny.utils.CombatUtil;
import com.palmergames.bukkit.towny.utils.PlayerCacheUtil;
import com.palmergames.bukkit.towny.war.flagwar.TownyWarConfig;
import com.palmergames.bukkit.util.Colors;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Attachable;
import org.kitteh.tag.PlayerReceiveNameTagEvent;

/**
 * Handle events for all Player related events
 * 
 * @author Shade/ElgarL
 * 
 */
public class TownyPlayerListener implements Listener {

	private final Towny plugin;

	public TownyPlayerListener(Towny instance) {

		plugin = instance;
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerJoin(PlayerJoinEvent event) {

		Player player = event.getPlayer();

		if (plugin.isError()) {
			player.sendMessage(Colors.Rose + "[Towny Error] Locked in Safe mode!");
			return;
		}

		try {
			plugin.getTownyUniverse().onLogin(player);
		} catch (TownyException x) {
			TownyMessaging.sendErrorMsg(player, x.getMessage());
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerQuit(PlayerQuitEvent event) {

		if (plugin.isError()) {
			return;
		}

		plugin.getTownyUniverse().onLogout(event.getPlayer());

		// Remove from teleport queue (if exists)
		try {
			if (TownyTimerHandler.isTeleportWarmupRunning())
				plugin.getTownyUniverse().abortTeleportRequest(TownyUniverse.getDataSource().getResident(event.getPlayer().getName().toLowerCase()));
		} catch (NotRegisteredException e) {
		}

		plugin.deleteCache(event.getPlayer());
		TownyPerms.removeAttachment(event.getPlayer().getName());
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerRespawn(PlayerRespawnEvent event) {

		if (plugin.isError()) {
			return;
		}

		Player player = event.getPlayer();
		TownyMessaging.sendDebugMsg("onPlayerDeath: " + player.getName());

		if (!TownySettings.isTownRespawning())
			return;

		try {
			Location respawn = plugin.getTownyUniverse().getTownSpawnLocation(player);

			// Check if only respawning in the same world as the town's spawn.
			if (TownySettings.isTownRespawningInOtherWorlds() && !player.getWorld().equals(respawn.getWorld()))
				return;

			event.setRespawnLocation(respawn);
		} catch (TownyException e) {
			// Town has not set respawn location. Using default.
		}
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {

		if (plugin.isError()) {
			event.setCancelled(true);
			return;
		}

		// Test against the item in hand as we need to test the bucket contents we are trying to empty.
		event.setCancelled(onPlayerInteract(event.getPlayer(), event.getBlockClicked(), event.getPlayer().getItemInHand()));

		//Test on the resulting empty bucket to see if we have permission to empty a bucket.
		if (!event.isCancelled())
			event.setCancelled(onPlayerInteract(event.getPlayer(), event.getBlockClicked(), event.getItemStack()));

	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onPlayerBucketFill(PlayerBucketFillEvent event) {

		if (plugin.isError()) {
			event.setCancelled(true);
			return;
		}
		//test against the bucket we will finish up with to see if we are allowed to fill this item.
		event.setCancelled(onPlayerInteract(event.getPlayer(), event.getBlockClicked(), event.getItemStack()));

	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPlayerInteract(PlayerInteractEvent event) {

		if (plugin.isError()) {
			event.setCancelled(true);
			return;
		}

		Player player = event.getPlayer();
		Block block = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
		TownyWorld World = null;

		try {
			World = TownyUniverse.getDataSource().getWorld(block.getLocation().getWorld().getName());
			if (!World.isUsingTowny())
				return;

		} catch (NotRegisteredException e) {
			// World not registered with Towny.
			e.printStackTrace();
			return;
		}

		// prevent players trampling crops

		if ((event.getAction() == Action.PHYSICAL)) {

			if ((block.getType() == Material.SOIL) || (block.getType() == Material.CROPS))
				if (World.isDisablePlayerTrample() || !PlayerCacheUtil.getCachePermission(player, block.getLocation(), block.getTypeId(), block.getData(), TownyPermission.ActionType.DESTROY)) {
					event.setCancelled(true);
					return;
				}
		}

		if (event.hasItem()) {

			if (TownySettings.isItemUseId(event.getItem().getTypeId())) {
				event.setCancelled(onPlayerInteract(player, event.getClickedBlock(), event.getItem()));
			}
		}

		if (event.getClickedBlock() != null) {
			// Towny regen
			if (TownySettings.getRegenDelay() > 0) {
				if (event.getClickedBlock().getState().getData() instanceof Attachable) {
					Attachable attachable = (Attachable) event.getClickedBlock().getState().getData();
					BlockLocation attachedToBlock = new BlockLocation(event.getClickedBlock().getRelative(attachable.getAttachedFace()).getLocation());
					// Prevent attached blocks from falling off when interacting
					if (TownyRegenAPI.hasProtectionRegenTask(attachedToBlock)) {
						event.setCancelled(true);
						return;
					}
				}
			}

			if (TownySettings.isSwitchId(event.getClickedBlock().getTypeId()) || event.getAction() == Action.PHYSICAL) {
				onPlayerSwitchEvent(event, null, World);
				return;
			}
		}

	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {

		if (plugin.isError()) {
			event.setCancelled(true);
			return;
		}

		if (event.getRightClicked() != null) {

			TownyWorld World = null;

			try {
				World = TownyUniverse.getDataSource().getWorld(event.getPlayer().getWorld().getName());
				if (!World.isUsingTowny())
					return;

			} catch (NotRegisteredException e) {
				// World not registered with Towny.
				e.printStackTrace();
				return;
			}

			Player player = event.getPlayer();
			boolean bBuild = true;
			int blockID = 0;

			/*
			 * Protect specific entity interactions.
			 */
			switch(event.getRightClicked().getType()) {

			case ITEM_FRAME:

				blockID = 389;
				//Get permissions (updates if none exist)
				bBuild = PlayerCacheUtil.getCachePermission(player, event.getRightClicked().getLocation(), blockID, (byte)0, TownyPermission.ActionType.DESTROY);
				break;

			case PAINTING:

				blockID = 321;
				//Get permissions (updates if none exist)
				bBuild = PlayerCacheUtil.getCachePermission(player, event.getRightClicked().getLocation(), blockID, (byte)0, TownyPermission.ActionType.DESTROY);
				break;

			case MINECART:

				if (event.getRightClicked() instanceof org.bukkit.entity.minecart.StorageMinecart) {

					blockID = 342;

				} else if (event.getRightClicked() instanceof org.bukkit.entity.minecart.RideableMinecart) {

					blockID = 328;

				} else if (event.getRightClicked() instanceof org.bukkit.entity.minecart.PoweredMinecart) {

					blockID = 343;

				} else if (event.getRightClicked() instanceof org.bukkit.entity.minecart.HopperMinecart) {

					blockID = 408;

				} else {

					blockID = 321;
				}

				if ((blockID != 0) && (!TownySettings.isSwitchId(blockID)))
					return;

				//Get permissions (updates if none exist)
				bBuild = PlayerCacheUtil.getCachePermission(player, event.getRightClicked().getLocation(), blockID, (byte)0, TownyPermission.ActionType.SWITCH);
				break;

			}

			if (blockID != 0) {

				// Allow the removal if we are permitted
				if (bBuild)
					return;

				event.setCancelled(true);

				/*
				 * Fetch the players cache
				 */
				PlayerCache cache = plugin.getCache(player);

				if (cache.hasBlockErrMsg())
					TownyMessaging.sendErrorMsg(player, cache.getBlockErrMsg());

				return;
			}


			/*
			 * Item_use protection.
			 */
			if (event.getPlayer().getItemInHand() != null) {

				if (TownySettings.isItemUseId(event.getPlayer().getItemInHand().getTypeId())) {
					event.setCancelled(onPlayerInteract(event.getPlayer(), null, event.getPlayer().getItemInHand()));
					return;
				}
			}
		}

	}



	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPlayerMove(PlayerMoveEvent event) {

		if (plugin.isError()) {
			event.setCancelled(true);
			return;
		}

		/*
		 * Abort if we havn't really moved
		 */
		if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockZ() == event.getTo().getBlockZ() && event.getFrom().getBlockY() == event.getTo().getBlockY()) {
			return;
		}

		Player player = event.getPlayer();
		Location to = event.getTo();
		Location from;
		PlayerCache cache = plugin.getCache(player);

		try {
			from = cache.getLastLocation();
		} catch (NullPointerException e) {
			from = event.getFrom();
		}


		// Prevent fly/double jump cheats
		if (!(event instanceof PlayerTeleportEvent)) {
			if (TownySettings.isUsingCheatProtection() && (player.getGameMode() != GameMode.CREATIVE) && !TownyUniverse.getPermissionSource().has(player, PermissionNodes.CHEAT_BYPASS.getNode())) {
				try {
					if (TownyUniverse.getDataSource().getWorld(player.getWorld().getName()).isUsingTowny())
						if ((from.getBlock().getRelative(BlockFace.DOWN).getType() == Material.AIR) && (player.getFallDistance() == 0) && (player.getVelocity().getY() <= -0.6) && (player.getLocation().getY() > 0)) {
							//plugin.sendErrorMsg(player, "Cheat Detected!");

							Location blockLocation = from;

							//find the first non air block below us
							while ((blockLocation.getBlock().getType() == Material.AIR) && (blockLocation.getY() > 0))
								blockLocation.setY(blockLocation.getY() - 1);

							// set to 1 block up so we are not sunk in the ground
							blockLocation.setY(blockLocation.getY() + 1);

							// Update the cache for this location (same WorldCoord).
							cache.setLastLocation(blockLocation);
							player.teleport(blockLocation);
							return;
						}
				} catch (NotRegisteredException e1) {
					TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_err_not_configured"));
					return;
				}
			}
		}

		try {
			TownyWorld fromWorld = TownyUniverse.getDataSource().getWorld(from.getWorld().getName());
			WorldCoord fromCoord = new WorldCoord(fromWorld.getName(), Coord.parseCoord(from));
			TownyWorld toWorld = TownyUniverse.getDataSource().getWorld(to.getWorld().getName());
			WorldCoord toCoord = new WorldCoord(toWorld.getName(), Coord.parseCoord(to));
			if (!fromCoord.equals(toCoord))
				onPlayerMoveChunk(player, fromCoord, toCoord, from, to, event);
			else {
				//plugin.sendDebugMsg("    From: " + fromCoord);
				//plugin.sendDebugMsg("    To:   " + toCoord);
				//plugin.sendDebugMsg("        " + from.toString());
				//plugin.sendDebugMsg("        " + to.toString());
			}
		} catch (NotRegisteredException e) {
			TownyMessaging.sendErrorMsg(player, e.getMessage());
		}

		// Update the cached players current location
		cache.setLastLocation(to);

		//plugin.updateCache(player);
		//plugin.sendDebugMsg("onBlockMove: " + player.getName() + ": ");
		//plugin.sendDebugMsg("        " + from.toString());
		//plugin.sendDebugMsg("        " + to.toString());
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onPlayerTeleport(PlayerTeleportEvent event) {

		/*
		 * Test to see if Ender pearls are disabled.
		 */
		if (event.getCause() == TeleportCause.ENDER_PEARL) {

			if (TownySettings.isItemUseId(Material.ENDER_PEARL.getId())) {
				if (onPlayerInteract(event.getPlayer(), event.getTo().getBlock(), new ItemStack(Material.ENDER_PEARL))) {
					event.setCancelled(true);
					TownyMessaging.sendErrorMsg(event.getPlayer(), Colors.Red + "Ender Pearls are disabled!");
					return;
				}
			}
		}
		onPlayerMove(event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerChangeWorld(PlayerChangedWorldEvent event) { // has changed worlds

		TownyPerms.assignPermissions(null, event.getPlayer());
	}

	public boolean onPlayerInteract(Player player, Block block, ItemStack item) {


		boolean cancelState = false;
		WorldCoord worldCoord;

		try {
			String worldName = player.getWorld().getName();

			if (block != null)
				worldCoord = new WorldCoord(worldName, Coord.parseCoord(block));
			else
				worldCoord = new WorldCoord(worldName, Coord.parseCoord(player));

			//Get itemUse permissions (updates if none exist)
			boolean bItemUse;

			if (block != null)
				bItemUse = PlayerCacheUtil.getCachePermission(player, block.getLocation(), item.getTypeId(), item.getData().getData(), TownyPermission.ActionType.ITEM_USE);
			else
				bItemUse = PlayerCacheUtil.getCachePermission(player, player.getLocation(), item.getTypeId(), item.getData().getData(), TownyPermission.ActionType.ITEM_USE);

			boolean wildOverride = TownyUniverse.getPermissionSource().hasWildOverride(worldCoord.getTownyWorld(), player, item.getTypeId(), item.getData().getData(), TownyPermission.ActionType.ITEM_USE);

			PlayerCache cache = plugin.getCache(player);
			//cache.updateCoord(worldCoord);
			try {

				TownBlockStatus status = cache.getStatus();
				if (status == TownBlockStatus.UNCLAIMED_ZONE && wildOverride)
					return cancelState;

				// Allow item_use if we have an override
				if (((status == TownBlockStatus.TOWN_RESIDENT) && (TownyUniverse.getPermissionSource().hasOwnTownOverride(player, item.getTypeId(), item.getData().getData(), TownyPermission.ActionType.ITEM_USE))) || (((status == TownBlockStatus.OUTSIDER) || (status == TownBlockStatus.TOWN_ALLY) || (status == TownBlockStatus.ENEMY)) && (TownyUniverse.getPermissionSource().hasAllTownOverride(player, item.getTypeId(), item.getData().getData(), TownyPermission.ActionType.ITEM_USE))))
					return cancelState;

				if (status == TownBlockStatus.WARZONE) {
					if (!TownyWarConfig.isAllowingItemUseInWarZone()) {
						cancelState = true;
						TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_err_warzone_cannot_use_item"));
					}
					return cancelState;
				}
				if (((status == TownBlockStatus.UNCLAIMED_ZONE) && (!wildOverride)) || ((!bItemUse) && (status != TownBlockStatus.UNCLAIMED_ZONE))) {
					//if (status == TownBlockStatus.UNCLAIMED_ZONE)
					//	TownyMessaging.sendErrorMsg(player, String.format(TownySettings.getLangString("msg_err_cannot_perform_action"), world.getUnclaimedZoneName()));

					cancelState = true;
				}

				if ((cache.hasBlockErrMsg())) // && (status != TownBlockStatus.UNCLAIMED_ZONE))
					TownyMessaging.sendErrorMsg(player, cache.getBlockErrMsg());

			} catch (NullPointerException e) {
				System.out.print("NPE generated!");
				System.out.print("Player: " + player.getName());
				System.out.print("Item: " + item.getData().getItemType().name());
				//System.out.print("Block: " + block.getType().toString());
			}

		} catch (NotRegisteredException e1) {
			TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_err_not_configured"));
			cancelState = true;
			return cancelState;
		}

		return cancelState;

	}

	public void onPlayerSwitchEvent(PlayerInteractEvent event, String errMsg, TownyWorld world) {

		Player player = event.getPlayer();
		Block block = event.getClickedBlock();
		event.setCancelled(onPlayerSwitchEvent(player, block, errMsg, world));

	}

	public boolean onPlayerSwitchEvent(Player player, Block block, String errMsg, TownyWorld world) {


		if (!TownySettings.isSwitchId(block.getTypeId()))
			return false;

		//Get switch permissions (updates if none exist)
		boolean bSwitch = PlayerCacheUtil.getCachePermission(player, block.getLocation(), block.getTypeId(), block.getData(), TownyPermission.ActionType.SWITCH);

		// Allow switch if we are permitted
		if (bSwitch)
			return false;

		/*
		 * Fetch the players cache
		 */
		PlayerCache cache = plugin.getCache(player);
		TownBlockStatus status = cache.getStatus();

		/* 
		 * display any error recorded for this plot
		 */
		if (cache.hasBlockErrMsg())
			TownyMessaging.sendErrorMsg(player, cache.getBlockErrMsg());

		/*
		 * Flag war
		 */
		if (status == TownBlockStatus.WARZONE) {
			if (!TownyWarConfig.isAllowingSwitchesInWarZone()) {
				TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_err_warzone_cannot_use_switches"));
				return true;
			}
			return false;
		} else {
			return true;
		}


	}

	public void onPlayerMoveChunk(Player player, WorldCoord from, WorldCoord to, Location fromLoc, Location toLoc, PlayerMoveEvent moveEvent) {

		plugin.getCache(player).setLastLocation(toLoc);
		plugin.getCache(player).updateCoord(to);

		PlayerChangePlotEvent event = new PlayerChangePlotEvent(player, from, to, moveEvent);
		Bukkit.getServer().getPluginManager().callEvent(event);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onNameTag(PlayerReceiveNameTagEvent event) {
		Player player = event.getPlayer();

		Location loc = player.getLocation();

		TownyWorld world;

		try {
			world = TownyUniverse.getDataSource().getWorld(loc.getWorld().getName());
		} catch (NotRegisteredException e) {
			return;
		}

		if (!world.isUsingTowny()){
			return;
		}

		Player namedPlayer = event.getNamedPlayer();

		if (plugin.getWorldGuard() != null){
			Location namedPlayerLocation = namedPlayer.getLocation();

			RegionManager mgr = plugin.getWorldGuard().getGlobalRegionManager().get(namedPlayerLocation.getWorld());

			ApplicableRegionSet set = mgr.getApplicableRegions(namedPlayerLocation);

			for (ProtectedRegion region : set){
				if (region.getId().equalsIgnoreCase("pvparena")){
					return;
				}
			}
		}

		String tag = event.getTag();

		tag = ChatColor.stripColor(tag);

		if (CombatUtil.isAlly(player.getName(), namedPlayer.getName())){
			tag = ChatColor.AQUA + tag;

			event.setTag(tag);
		}else if (CombatUtil.isEnemy(player.getName(), namedPlayer.getName())){
			tag = ChatColor.DARK_RED + tag;

			event.setTag(tag);
		}
	}
}

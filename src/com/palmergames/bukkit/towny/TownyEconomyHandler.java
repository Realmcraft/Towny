package com.palmergames.bukkit.towny;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.World;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Economy handler to interface with Register, Vault or iConomy 5.01 directly.
 * 
 * @author ElgarL
 * 
 */
public class TownyEconomyHandler {

	private static Towny plugin = null;

	private static Economy vaultEconomy = null;

	private static String version = "";

	public static void initialize(Towny plugin) {

		TownyEconomyHandler.plugin = plugin;
	}

	/**
	 * Are we using any economy system?
	 * 
	 * @return true if we found one.
	 */
	public static boolean isActive() {
		return vaultEconomy != null;
	}

	/**
	 * @return The current economy providers version string
	 */
	public static String getVersion() {

		return version;
	}

	/**
	 * Internal function to set the version string.
	 * 
	 * @param version
	 */
	private static void setVersion(String version) {

		TownyEconomyHandler.version = version;
	}

	/**
	 * Find and configure a suitable economy provider
	 * 
	 * @return true if successful.
	 */
	public static Boolean setupEconomy() {

		/*
		 * Attempt to find Vault for Economy handling
		 */
		try {
			RegisteredServiceProvider<Economy> vaultEcoProvider = plugin.getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
			if (vaultEcoProvider != null) {
				/*
				 * Flag as using Vault hooks
				 */
				vaultEconomy = vaultEcoProvider.getProvider();
				setVersion(String.format("%s v%s", "Vault", vaultEcoProvider.getPlugin().getDescription().getVersion()));
				return true;
			}
		} catch (NoClassDefFoundError ex) {
		}

		/*
		 * No compatible Economy system found.
		 */
		return false;
	}

	/**
	 * Attempt to delete the economy account.
	 */
	public static void removeAccount(String accountName) {
		if (vaultEconomy.hasAccount(accountName)){
			vaultEconomy.withdrawPlayer(accountName, (vaultEconomy.getBalance(accountName)));
		}
	}

	/**
	 * Returns the accounts current balance
	 * 
	 * @param accountName
	 * @return double containing the total in the account
	 */
	public static double getBalance(String accountName, World world) {
		//if (!vaultEconomy.hasAccount(accountName)){
		//	vaultEconomy.createPlayerAccount(accountName);
		//}

		return vaultEconomy.getBalance(accountName);
	}

	/**
	 * Returns true if the account has enough money
	 * 
	 * @param accountName
	 * @param amount
	 * @return true if there is enough in the account
	 */
	public static boolean hasEnough(String accountName, Double amount, World world) {

		if (getBalance(accountName, world) >= amount){
			return true;
		}

		return false;
	}

	/**
	 * Attempts to remove an amount from an account
	 * 
	 * @param accountName
	 * @param amount
	 * @return true if successful
	 */
	public static boolean subtract(String accountName, Double amount, World world) {
		if (!vaultEconomy.hasAccount(accountName)){
			vaultEconomy.createPlayerAccount(accountName);
		}

		return vaultEconomy.withdrawPlayer(accountName, amount).type == EconomyResponse.ResponseType.SUCCESS;
	}

	/**
	 * Add funds to an account.
	 * 
	 * @param accountName
	 * @param amount
	 * @param world
	 * @return true if successful
	 */
	public static boolean add(String accountName, Double amount, World world) {
		if (!vaultEconomy.hasAccount(accountName)){
			vaultEconomy.createPlayerAccount(accountName);
		}

		return vaultEconomy.depositPlayer(accountName, amount).type == EconomyResponse.ResponseType.SUCCESS;
	}

	public static boolean setBalance(String accountName, Double amount, World world) {
		if (!vaultEconomy.hasAccount(accountName)){
			vaultEconomy.createPlayerAccount(accountName);
		}

		return vaultEconomy.depositPlayer(accountName, (amount - vaultEconomy.getBalance(accountName))).type == EconomyResponse.ResponseType.SUCCESS;
	}

	/**
	 * Format this balance according to the current economy systems settings.
	 * 
	 * @param balance
	 * @return string containing the formatted balance
	 */
	public static String getFormattedBalance(double balance) {
		return vaultEconomy.format(balance);

	}

}

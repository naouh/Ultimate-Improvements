package com.nao.hdv;

import java.io.File;

import net.minecraftforge.common.Configuration;

/** Server-side tunables, stored in config/HDV.cfg. */
public final class Config {
	private Config() {}

	public static int maxActiveListings = 14;
	public static double listingFee = 0.0;
	public static double saleTaxPercent = 5.0;
	public static double minPrice = 1.0;
	public static double maxPrice = 1000000.0;

	public static void load(File file) {
		Configuration cfg = new Configuration(file);
		cfg.load();
		maxActiveListings = cfg.get("general", "maxActiveListings", 14,
				"Max active listings per player").getInt(14);
		listingFee = cfg.get("general", "listingFee", 0.0,
				"Flat fee charged to the seller when creating a listing").getDouble(0.0);
		saleTaxPercent = cfg.get("general", "saleTaxPercent", 5.0,
				"Percent of the sale taken as tax (money sink). Seller gets total*(1-tax/100)").getDouble(5.0);
		minPrice = cfg.get("general", "minPricePerUnit", 1.0, "Minimum price per item").getDouble(1.0);
		maxPrice = cfg.get("general", "maxPricePerUnit", 1000000.0, "Maximum price per item").getDouble(1000000.0);
		cfg.save();

		if (maxActiveListings < 1) maxActiveListings = 1;
		if (listingFee < 0) listingFee = 0;
		if (saleTaxPercent < 0) saleTaxPercent = 0;
		if (saleTaxPercent > 100) saleTaxPercent = 100;
		if (minPrice < 0) minPrice = 0;
		if (maxPrice < minPrice) maxPrice = minPrice;
	}
}

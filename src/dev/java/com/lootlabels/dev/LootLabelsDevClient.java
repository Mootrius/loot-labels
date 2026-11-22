package com.lootlabels.dev;

import com.lootlabels.LootLabelsPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Development only: starts a developer-mode client with only Loot Labels loaded.
 * Not part of the plugin.
 */
public class LootLabelsDevClient
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(LootLabelsPlugin.class);
		RuneLite.main(args);
	}
}

package com.lootlabels;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.client.util.ImageUtil;

/**
 * The clan rank title icons from the OSRS Wiki's Clan Settings page (Rank Titles), bundled with the plugin in
 * the ranks folder. An icon is identified by its file name without ".png", e.g. "owner".
 */
final class RankIcons
{
	/** Every icon, in the order the wiki lists them. */
	static final String[] IDS = {
		"jmod", "owner", "deputy_owner", "administrator", "guest", "dogsbody",
		"minion", "recruit", "pawn", "private", "corporal", "novice",
		"sergeant", "cadet", "page", "noble", "adept", "legionnaire",
		"lieutenant", "proselyte", "captain", "major", "general", "master",
		"officer", "commander", "colonel", "brigadier", "admiral", "marshal",
		"opal", "jade", "red_topaz", "sapphire", "emerald", "ruby",
		"diamond", "dragonstone", "onyx", "zenyte", "kitten", "bob",
		"wily", "hellcat", "skulled", "goblin", "beast", "imp",
		"gnome_child", "gnome_elder", "short_green_guy", "misthalinian", "karamjan", "asgarnian",
		"kharidian", "morytanian", "wild", "kandarin", "fremennik", "tirannian",
		"brassican", "saradominist", "guthixian", "zamorakian", "serenist", "bandosian",
		"zarosian", "armadylean", "xerician", "air", "mind", "water",
		"earth", "fire", "body", "cosmic", "chaos", "nature",
		"law", "death", "astral", "blood", "soul", "wrath",
		"diseased", "pine", "wintumber", "oak", "willow", "maple",
		"yew", "blisterwood", "magic", "attacker", "enforcer", "defender",
		"ranger", "priest", "magician", "runecrafter", "medic", "athlete",
		"herbologist", "thief", "crafter", "fletcher", "miner", "smith",
		"fisher", "cook", "firemaker", "lumberjack", "slayer", "farmer",
		"constructor", "hunter", "skiller", "competitor", "holy", "unholy",
		"natural", "sage", "destroyer", "mediator", "legend", "myth",
		"tztok", "tzkal", "maxed", "anchor", "apothecary", "merchant",
		"feeder", "harpoon", "carry", "archer", "battlemage", "artillery",
		"infantry", "smiter", "looter", "saviour", "sniper", "crusader",
		"spellcaster", "mentor", "prefect", "leader", "supervisor", "superior",
		"executive", "senator", "monarch", "scavenger", "labourer", "worker",
		"forager", "hoarder", "prospector", "gatherer", "collector", "bronze",
		"iron", "steel", "gold", "mithril", "adamant", "rune",
		"dragon", "protector", "bulwark", "justiciar", "sentry", "guardian",
		"warden", "vanguard", "templar", "squire", "duellist", "striker",
		"ninja", "inquisitor", "expert", "knight", "paladin", "goon",
		"brawler", "bruiser", "scourge", "fighter", "warrior", "barbarian",
		"berserker", "staff", "crew", "helper", "moderator", "sheriff",
		"red", "orange", "yellow", "green", "blue", "purple",
		"pink", "grey", "wizard", "trickster", "illusionist", "summoner",
		"necromancer", "warlock", "witch", "seer", "assassin", "cutpurse",
		"bandit", "scout", "burglar", "rogue", "smuggler", "brigand",
		"oracle", "pure", "champion", "epic", "mystic", "hero",
		"trialist", "defiler", "scholar", "councillor", "recruiter", "learner",
		"scribe", "assistant", "teacher", "coordinator", "walker", "speed_runner",
		"wanderer", "pilgrim", "vagrant", "record_chaser", "racer", "strider",
		"doctor", "nurse", "druid", "healer", "zealot", "cleric",
		"shaman", "therapist", "gamer", "adventurer", "explorer", "achiever",
		"quester", "raider", "completionist", "elite", "firestarter", "specialist",
		"burnt", "pyromancer", "prodigy", "ignitor", "artisan", "legacy"
	};

	private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

	private RankIcons()
	{
	}

	/**
	 * @return the icon, or null if the id is empty or unknown
	 */
	static BufferedImage image(String id)
	{
		if (id == null || id.isEmpty())
		{
			return null;
		}

		BufferedImage image = CACHE.get(id);
		if (image == null)
		{
			try
			{
				image = ImageUtil.loadImageResource(RankIcons.class, "ranks/" + id + ".png");
			}
			catch (RuntimeException e)
			{
				return null;
			}
			CACHE.put(id, image);
		}
		return image;
	}
}

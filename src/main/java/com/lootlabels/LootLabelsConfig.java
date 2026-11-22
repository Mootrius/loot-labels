package com.lootlabels;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(LootLabelsConfig.GROUP)
public interface LootLabelsConfig extends Config
{
	String GROUP = "loot-labels";

	@ConfigSection(
		name = "Display",
		description = "The general appearance of loot labels",
		position = 0
	)
	String DISPLAY_SECTION = "display";

	@ConfigSection(
		name = "Font",
		description = "The font within loot labels",
		position = 1
	)
	String FONT_SECTION = "font";

	@ConfigSection(
		name = "Values",
		description = "Shows an item's worth next to its name",
		position = 2
	)
	String VALUES_SECTION = "values";

	@ConfigSection(
		name = "Despawn",
		description = "Shows how long until an item despawns",
		position = 3
	)
	String DESPAWN_SECTION = "despawn";

	@ConfigSection(
		name = "Ownership",
		description = "Which items get labels",
		position = 4
	)
	String OWNERSHIP_SECTION = "ownership";

	@ConfigSection(
		name = "Menu",
		description = "The behavior of the right-click menu for items",
		position = 5
	)
	String MENU_SECTION = "menu";

	@ConfigSection(
		name = "Hotkey",
		description = "Hold a key of your choice to toggle visibility of these settings",
		position = 6
	)
	String HOTKEY_SECTION = "hotkey";

	enum FontSize
	{
		SMALL("Small", 16f),
		LARGE("Large", 16f);

		private final String label;
		final float points;

		FontSize(String label, float points)
		{
			this.label = label;
			this.points = points;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum FontWeight
	{
		REGULAR("Regular"),
		BOLD("Bold");

		private final String label;

		FontWeight(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum FontStyle
	{
		REGULAR("Regular"),
		ITALICIZED("Italicized");

		private final String label;

		FontStyle(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum FontAccent
	{
		SHADOW("Shadow"),
		OUTLINE("Outline"),
		NONE("None");

		private final String label;

		FontAccent(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum ValueDisplay
	{
		HIGHEST("Highest"),
		GRAND_EXCHANGE("Grand Exchange"),
		HIGH_ALCHEMY("High alchemy"),
		BOTH("Both");

		private final String label;

		ValueDisplay(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum DisplayStyle
	{
		COMPACT("Compact"),
		VERBOSE("Verbose");

		private final String label;

		DisplayStyle(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum DespawnType
	{
		PIE("Pie"),
		TICKS("Ticks"),
		SECONDS("Seconds");

		private final String label;

		DespawnType(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum DespawnStyle
	{
		COLORS("Colors"),
		NONE("None");

		private final String label;

		DespawnStyle(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigItem(
		keyName = "textColor",
		name = "Text color",
		description = "The color of the text in the label",
		section = DISPLAY_SECTION,
		position = 0
	)
	default Color textColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "borderThickness",
		name = "Border thickness",
		description = "How many pixels thick the box around a label is (from 0.0 to 2.0, in increments of 0.1)",
		section = DISPLAY_SECTION,
		position = 1
	)
	default double borderThickness()
	{
		return 1.5;
	}

	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "borderOpacity",
		name = "Border opacity",
		description = "How opaque the box around a label is (from 0 to 100%)",
		section = DISPLAY_SECTION,
		position = 2
	)
	default int borderOpacity()
	{
		return 100;
	}

	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "backgroundOpacity",
		name = "Background opacity",
		description = "How opaque the background fill of a label is (from 0 to 100%)",
		section = DISPLAY_SECTION,
		position = 3
	)
	default int backgroundOpacity()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "fontSize",
		name = "Font size",
		description = "The size of the label text",
		section = FONT_SECTION,
		position = 0
	)
	default FontSize fontSize()
	{
		return FontSize.SMALL;
	}

	@ConfigItem(
		keyName = "fontWeight",
		name = "Font weight",
		description = "The weight of the label text",
		section = FONT_SECTION,
		position = 1
	)
	default FontWeight fontWeight()
	{
		return FontWeight.REGULAR;
	}

	@ConfigItem(
		keyName = "fontStyle",
		name = "Font style",
		description = "The style of the label text",
		section = FONT_SECTION,
		position = 2
	)
	default FontStyle fontStyle()
	{
		return FontStyle.REGULAR;
	}

	@ConfigItem(
		keyName = "fontAccent",
		name = "Font accent",
		description = "The accent of the label text",
		section = FONT_SECTION,
		position = 3
	)
	default FontAccent fontAccent()
	{
		return FontAccent.SHADOW;
	}

	@ConfigItem(
		keyName = "showValue",
		name = "Show value",
		description = "Toggles the value text visibility",
		section = VALUES_SECTION,
		position = 0
	)
	default boolean showValue()
	{
		return true;
	}

	@ConfigItem(
		keyName = "valueDisplay",
		name = "Value display",
		description = "Which value(s) to display",
		section = VALUES_SECTION,
		position = 1
	)
	default ValueDisplay valueDisplay()
	{
		return ValueDisplay.BOTH;
	}

	@ConfigItem(
		keyName = "displayStyle",
		name = "Display style",
		description = "Compact: [6.9K/420]. Verbose: [GE: 6.9K] [HA: 420].",
		section = VALUES_SECTION,
		position = 2
	)
	default DisplayStyle displayStyle()
	{
		return DisplayStyle.VERBOSE;
	}

	@ConfigItem(
		keyName = "showDespawn",
		name = "Show despawn",
		description = "Toggles the despawn timer visibility",
		section = DESPAWN_SECTION,
		position = 0
	)
	default boolean showDespawn()
	{
		return false;
	}

	@ConfigItem(
		keyName = "despawnType",
		name = "Despawn type",
		description = "Pie: A circle that drains over time. Ticks/Seconds: Text that counts down over time.",
		section = DESPAWN_SECTION,
		position = 1
	)
	default DespawnType despawnType()
	{
		return DespawnType.PIE;
	}

	@ConfigItem(
		keyName = "despawnStyle",
		name = "Despawn style",
		description = "Colors: Green when you can see an item, yellow when others can see it, and red when it's about to despawn. None: Match the \"Text color\" setting above.",
		section = DESPAWN_SECTION,
		position = 2
	)
	default DespawnStyle despawnStyle()
	{
		return DespawnStyle.COLORS;
	}

	@ConfigItem(
		keyName = "showWorldSpawns",
		name = "Show world spawns",
		description = "Label items in the world by default, like a knife on a table",
		section = OWNERSHIP_SECTION,
		position = 0
	)
	default boolean showWorldSpawns()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showOthersDrops",
		name = "Show others' drops",
		description = "Label items other people (like people outside your GIM group) have dropped or gotten as a drop",
		section = OWNERSHIP_SECTION,
		position = 1
	)
	default boolean showOthersDrops()
	{
		return true;
	}

	@ConfigItem(
		keyName = "deprioritizeItems",
		name = "Deprioritize items",
		description = "Makes left-clicks on non-labeled items click through them",
		section = MENU_SECTION,
		position = 0
	)
	default boolean deprioritizeItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "collapseEntries",
		name = "Collapse entries",
		description = "Makes it so that several, non-stackable items on one tile show up with one entry rather than multiple",
		section = MENU_SECTION,
		position = 1
	)
	default boolean collapseEntries()
	{
		return true;
	}

	@ConfigItem(
		keyName = "recolorEntries",
		name = "Recolor entries",
		description = "Colors item names to be the same as the group's color",
		section = MENU_SECTION,
		position = 2
	)
	default boolean recolorEntries()
	{
		return true;
	}

	@ConfigItem(
		keyName = "recolorNonLabeled",
		name = "Recolor non-labeled",
		description = "Colors non-labeled item names with the color set below",
		section = MENU_SECTION,
		position = 3
	)
	default boolean recolorNonLabeled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "nonLabeledColor",
		name = "Non-labeled color",
		description = "The color for non-labeled item names to be",
		section = MENU_SECTION,
		position = 4
	)
	default Color nonLabeledColor()
	{
		return new Color(0x808080);
	}

	@ConfigItem(
		keyName = "hotkey",
		name = "Hotkey",
		description = "The key to hold for the below settings to show",
		section = HOTKEY_SECTION,
		position = 0
	)
	default Keybind hotkey()
	{
		return Keybind.ALT;
	}

	@ConfigItem(
		keyName = "hotkeyShowNonLabeled",
		name = "Shows non-labeled items",
		description = "Holding the hotkey shows non-labeled items as simple text with no color/border",
		section = HOTKEY_SECTION,
		position = 1
	)
	default boolean hotkeyShowNonLabeled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hotkeyShowValues",
		name = "Shows item values",
		description = "Holding the hotkey shows item values, as set in \"Values\", even when \"Show value\" is off",
		section = HOTKEY_SECTION,
		position = 2
	)
	default boolean hotkeyShowValues()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hotkeyShowDespawn",
		name = "Shows despawn timers",
		description = "Holding the hotkey shows item despawn timers, as set in \"Despawn\", even when \"Show despawn\" is off",
		section = HOTKEY_SECTION,
		position = 3
	)
	default boolean hotkeyShowDespawn()
	{
		return true;
	}
}

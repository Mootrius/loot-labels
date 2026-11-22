package com.lootlabels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * A named set of item names that can be switched on and off together. Saved as JSON in the plugin's config,
 * so the field names here are part of the saved format.
 */
class LabelGroup
{
	static final int DEFAULT_COLOR = 0x000000;

	@Getter
	@Setter
	private String name = "";

	@Getter
	@Setter
	private boolean enabled = true;

	@Getter
	@Setter
	private boolean collapsed;

	/** The color of this group's labels, as 0xRRGGBB. */
	@Getter
	@Setter
	private int color = DEFAULT_COLOR;

	/** Whether the user has picked a color (as opposed to the default black). */
	@Setter
	private boolean colorChosen;

	/**
	 * The rank icon drawn left of the text in this group's labels (see {@link RankIcons}), or "" for none.
	 */
	@Getter
	@Setter
	private String icon = "";

	/** The lines of this group, top to bottom. */
	@Getter
	private List<ItemEntry> entries = new ArrayList<>();

	/**
	 * Only read from groups saved before entries existed (names, and conditions by name). {@link #migrate()} turns
	 * them into entries and clears them, so they are not saved again.
	 */
	private List<String> items;
	private Map<String, ItemConditions> conditions;

	LabelGroup()
	{
	}

	LabelGroup(String name)
	{
		this.name = name;
	}

	/** Call after reading a group from JSON: upgrades the old format and drops anything unusable. */
	void migrate()
	{
		if (entries == null)
		{
			entries = new ArrayList<>();
		}
		entries.removeIf(entry -> entry == null);

		if (items != null)
		{
			for (String item : items)
			{
				if (item == null)
				{
					continue;
				}
				final ItemEntry entry = new ItemEntry(item);
				final ItemConditions old = conditions == null ? null : conditions.get(LabelGroupManager.normalize(item));
				if (old != null && !old.isEmpty())
				{
					entry.setConditions(old);
				}
				entries.add(entry);
			}
		}
		items = null;
		conditions = null;
	}

	boolean hasChosenColor()
	{
		return colorChosen || color != DEFAULT_COLOR;
	}

	boolean hasChosenIcon()
	{
		return icon != null && !icon.isEmpty();
	}
}

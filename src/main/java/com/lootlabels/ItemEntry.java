package com.lootlabels;

/**
 * One line in a group: an item name (which may use * as a wildcard) and, optionally, the conditions the item has
 * to meet for this line to apply. A group can have several lines for the same name, each with its own conditions.
 * Saved as JSON with the group, so the field names are part of the format.
 */
final class ItemEntry
{
	private String name = "";

	/** Null when the line always applies. */
	private ItemConditions conditions;

	ItemEntry()
	{
	}

	ItemEntry(String name)
	{
		this.name = name;
	}

	String getName()
	{
		return name == null ? "" : name;
	}

	void setName(String name)
	{
		this.name = name;
	}

	ItemConditions getConditions()
	{
		return conditions;
	}

	void setConditions(ItemConditions conditions)
	{
		this.conditions = conditions;
	}
}

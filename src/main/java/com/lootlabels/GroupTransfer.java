package com.lootlabels;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/** Turns groups into shareable JSON, and reads other people's JSON back into groups that are safe to add. */
@Slf4j
final class GroupTransfer
{
	private GroupTransfer()
	{
	}

	static String export(Gson gson, List<LabelGroup> selected)
	{
		final JsonObject root = new JsonObject();
		root.addProperty("format", "loot-labels");
		root.addProperty("version", 1);
		final JsonElement tree = gson.toJsonTree(selected);
		for (JsonElement element : tree.getAsJsonArray())
		{
			// Whether a group is open in the sidebar or switched on is personal, so neither is shared
			element.getAsJsonObject().remove("collapsed");
			element.getAsJsonObject().remove("enabled");
		}
		root.add("groups", tree);
		return gson.toJson(root);
	}

	/**
	 * Reads groups out of text that was exported by {@link #export} (or a bare list of groups, or a single group).
	 * Anything that isn't a group is skipped, and what is kept is cleaned up, so the result is safe to add.
	 */
	static List<LabelGroup> parse(Gson gson, String text)
	{
		final List<LabelGroup> found = new ArrayList<>();
		try
		{
			for (JsonElement element : groupElements(gson.fromJson(text.trim(), JsonElement.class)))
			{
				if (element != null && element.isJsonObject())
				{
					final LabelGroup group = sanitize(gson.fromJson(element, LabelGroup.class));
					if (group != null)
					{
						found.add(group);
					}
				}
			}
		}
		catch (JsonParseException | IllegalStateException | ClassCastException e)
		{
			log.debug("Could not read imported label groups", e);
		}
		return found;
	}

	/** The group objects in an export envelope, a bare array, or a lone group. */
	private static List<JsonElement> groupElements(JsonElement root)
	{
		final List<JsonElement> elements = new ArrayList<>();
		if (root != null && root.isJsonArray())
		{
			root.getAsJsonArray().forEach(elements::add);
		}
		else if (root != null && root.isJsonObject())
		{
			final JsonObject object = root.getAsJsonObject();
			final JsonElement list = object.get("groups");
			if (list != null && list.isJsonArray())
			{
				list.getAsJsonArray().forEach(elements::add);
			}
			else if (object.has("entries") || object.has("items"))
			{
				elements.add(object);
			}
		}
		return elements;
	}

	/** A clean copy of a group read from somebody else's JSON, or null if it isn't usable. */
	private static LabelGroup sanitize(LabelGroup source)
	{
		if (source == null)
		{
			return null;
		}
		source.migrate();

		String name = source.getName() == null ? "" : source.getName().trim();
		if (name.length() > 60)
		{
			name = name.substring(0, 60).trim();
		}

		final LabelGroup group = new LabelGroup(name.isEmpty() ? "Imported" : name);
		group.setEnabled(false);
		group.setCollapsed(true);
		group.setColor(source.getColor() & 0xFFFFFF);
		group.setColorChosen(source.hasChosenColor());
		for (String id : RankIcons.IDS)
		{
			if (id.equals(source.getIcon()))
			{
				group.setIcon(id);
				break;
			}
		}

		for (ItemEntry entry : source.getEntries())
		{
			final String key = LabelGroupManager.normalize(entry.getName());
			if (key.isEmpty())
			{
				continue;
			}
			final ItemEntry copy = new ItemEntry(LabelGroupManager.capitalize(key));
			if (entry.getConditions() != null && !entry.getConditions().isEmpty())
			{
				copy.setConditions(entry.getConditions().copy());
			}
			group.getEntries().add(copy);
		}
		return group;
	}
}

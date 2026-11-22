package com.lootlabels;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Owns the label groups: loads and saves them, and answers "should this item get a label?".
 *
 * Groups are only changed from the sidebar panel (Swing thread), while the overlay asks about visibility every
 * frame on the client thread, so the overlay reads an immutable snapshot of the rules instead of the groups.
 */
@Slf4j
@Singleton
class LabelGroupManager
{
	private static final String CONFIG_KEY = "groups";

	private final ConfigManager configManager;
	private final Gson gson;
	private final List<LabelGroup> groups = new ArrayList<>();
	private volatile Rules rules = new Rules(new ArrayList<>());

	@Inject
	LabelGroupManager(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	/** Item names are compared ignoring case and surrounding spaces. */
	static String normalize(String name)
	{
		return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
	}

	/** Item names are stored with the first letter capitalized and everything else lowercase. */
	static String capitalize(String name)
	{
		final String key = normalize(name);
		return key.isEmpty() ? key : key.substring(0, 1).toUpperCase(Locale.ROOT) + key.substring(1);
	}

	synchronized void load()
	{
		groups.clear();

		final String json = configManager.getConfiguration(LootLabelsConfig.GROUP, CONFIG_KEY);
		if (json != null && !json.isEmpty())
		{
			try
			{
				final LabelGroup[] loaded = gson.fromJson(json, LabelGroup[].class);
				if (loaded != null)
				{
					for (LabelGroup group : loaded)
					{
						if (group != null && group.getName() != null)
						{
							group.migrate();
							groups.add(group);
						}
					}
				}
			}
			catch (JsonParseException e)
			{
				log.warn("Could not read the saved label groups", e);
			}
		}

		rebuildRules();
	}

	synchronized List<LabelGroup> getGroups()
	{
		return new ArrayList<>(groups);
	}

	synchronized LabelGroup addGroup(String name)
	{
		final LabelGroup group = new LabelGroup(name);
		groups.add(group);
		changed();
		return group;
	}

	synchronized void removeGroup(LabelGroup group)
	{
		if (groups.remove(group))
		{
			changed();
		}
	}

	synchronized void renameGroup(LabelGroup group, String name)
	{
		group.setName(name);
		changed();
	}

	/**
	 * Moves a group so it ends up at the given place in the list. The order matters: when an item is in several
	 * enabled groups, the one nearest the top decides how it looks.
	 */
	synchronized void moveGroup(LabelGroup group, int newIndex)
	{
		if (groups.remove(group))
		{
			groups.add(Math.max(0, Math.min(newIndex, groups.size())), group);
			changed();
		}
	}

	synchronized void moveItem(LabelGroup group, ItemEntry entry, int newIndex)
	{
		final List<ItemEntry> entries = group.getEntries();
		if (entries.remove(entry))
		{
			entries.add(Math.max(0, Math.min(newIndex, entries.size())), entry);
			changed();
		}
	}

	/**
	 * Renames a line, keeping its place and its conditions. Other lines may have the same name.
	 *
	 * @return false if the new name was blank
	 */
	synchronized boolean renameItem(LabelGroup group, ItemEntry entry, String name)
	{
		final String key = normalize(name);
		if (key.isEmpty() || !group.getEntries().contains(entry))
		{
			return false;
		}

		entry.setName(capitalize(key));
		changed();
		return true;
	}

	synchronized void setEnabled(LabelGroup group, boolean enabled)
	{
		group.setEnabled(enabled);
		changed();
	}

	/** Requires the line's quantity, GE value or HA value to satisfy a comparison. */
	synchronized void setComparison(ItemEntry entry, ItemConditions.Type type, ItemConditions.Op op, long value)
	{
		final ItemConditions.Comparison comparison = new ItemConditions.Comparison(op, value);
		final ItemConditions existing = entry.getConditions();
		if (existing != null && comparison.sameAs(existing.getComparison(type)))
		{
			return;
		}

		editableConditions(entry).setComparison(type, comparison);
		changed();
	}

	/**
	 * @param stackable true for only stackable items, false for only items that don't stack
	 */
	synchronized void setStackable(ItemEntry entry, boolean stackable)
	{
		final ItemConditions existing = entry.getConditions();
		if (existing != null && existing.getStackable() != null && existing.getStackable() == stackable)
		{
			return;
		}

		editableConditions(entry).setStackable(stackable);
		changed();
	}

	synchronized void clearCondition(ItemEntry entry, ItemConditions.Type type)
	{
		final ItemConditions existing = entry.getConditions();
		if (existing == null || !existing.has(type))
		{
			return;
		}

		existing.clear(type);
		if (existing.isEmpty())
		{
			entry.setConditions(null);
		}
		changed();
	}

	private ItemConditions editableConditions(ItemEntry entry)
	{
		if (entry.getConditions() == null)
		{
			entry.setConditions(new ItemConditions());
		}
		return entry.getConditions();
	}

	/** Changes the color while it is being picked: labels update right away, but nothing is saved yet. */
	synchronized void previewColor(LabelGroup group, int rgb)
	{
		group.setColor(rgb & 0xFFFFFF);
		rebuildRules();
	}

	synchronized void setIcon(LabelGroup group, String icon)
	{
		group.setIcon(icon == null ? "" : icon);
		changed();
	}

	/**
	 * @param chosen whether the user actually picked this color, so the sidebar can show it
	 */
	synchronized void setColor(LabelGroup group, int rgb, boolean chosen)
	{
		group.setColor(rgb & 0xFFFFFF);
		if (chosen)
		{
			group.setColorChosen(true);
		}
		changed();
	}

	/** Collapsing only affects the sidebar, so the label rules don't need rebuilding. */
	synchronized void setCollapsed(LabelGroup group, boolean collapsed)
	{
		group.setCollapsed(collapsed);
		save();
	}

	/**
	 * Adds a line at the bottom of the group. A group may have several lines for the same item.
	 *
	 * @return false if the name was blank
	 */
	synchronized boolean addItem(LabelGroup group, String item)
	{
		final String key = normalize(item);
		if (key.isEmpty())
		{
			return false;
		}

		group.getEntries().add(new ItemEntry(capitalize(key)));
		changed();
		return true;
	}

	synchronized void removeItem(LabelGroup group, ItemEntry entry)
	{
		if (group.getEntries().remove(entry))
		{
			changed();
		}
	}

	/**
	 * Decides how a ground item is labelled. A group's entry for an item only counts if the item meets the entry's
	 * conditions. The item gets the style of the first enabled group (in list order) with an entry that counts. If
	 * it only has entries in disabled groups it is hidden, and if it has no entry that counts it is drawn plain
	 * when showUngrouped is set, or hidden.
	 *
	 * @return the style, or null if the item should not be labelled
	 */
	LabelStyle styleFor(GroundLabel label, boolean showUngrouped)
	{
		return rules.styleFor(label, showUngrouped);
	}

	/** The JSON that gets copied to the clipboard when the given groups are exported. */
	synchronized String exportJson(List<LabelGroup> selected)
	{
		return GroupTransfer.export(gson, selected);
	}

	/** Reads groups out of exported text; empty when the text holds none. See {@link GroupTransfer#parse}. */
	List<LabelGroup> parseImport(String text)
	{
		return GroupTransfer.parse(gson, text);
	}

	/**
	 * Adds groups produced by {@link #parseImport(String)} after the existing ones.
	 */
	synchronized void importGroups(List<LabelGroup> imported)
	{
		if (!imported.isEmpty())
		{
			groups.addAll(imported);
			changed();
		}
	}

	private void changed()
	{
		rebuildRules();
		save();
	}

	private void save()
	{
		configManager.setConfiguration(LootLabelsConfig.GROUP, CONFIG_KEY, gson.toJson(groups));
	}

	private void rebuildRules()
	{
		rules = new Rules(groups);
	}

	/** Immutable lookup built from the groups. Entries containing * are wildcards, e.g. "*bones". */
	private static final class Rules
	{
		private final Map<String, List<Entry>> exactEntries = new HashMap<>();
		private final List<Entry> patternEntries = new ArrayList<>();

		Rules(List<LabelGroup> groups)
		{
			int order = 0;
			for (LabelGroup group : groups)
			{
				final LabelStyle style = LabelStyle.ofGroup(group.getColor(), RankIcons.image(group.getIcon()));
				for (ItemEntry item : group.getEntries())
				{
					final String key = normalize(item.getName());
					if (key.isEmpty())
					{
						continue;
					}

					final ItemConditions conditions = item.getConditions();
					final ItemConditions snapshot = conditions == null ? null : conditions.copy();
					if (key.indexOf('*') >= 0)
					{
						patternEntries.add(new Entry(toPattern(key), style, order, group.isEnabled(), snapshot));
					}
					else
					{
						exactEntries.computeIfAbsent(key, k -> new ArrayList<>())
							.add(new Entry(null, style, order, group.isEnabled(), snapshot));
					}
				}
				order++;
			}
		}

		LabelStyle styleFor(GroundLabel label, boolean showUngrouped)
		{
			final String key = label.getKey();
			Entry best = null;
			boolean listed = false;

			final List<Entry> exact = exactEntries.get(key);
			if (exact != null)
			{
				for (Entry entry : exact)
				{
					if (entry.applies(label))
					{
						listed = true;
						best = better(best, entry);
					}
				}
			}

			for (Entry entry : patternEntries)
			{
				if (entry.pattern.matcher(key).matches() && entry.applies(label))
				{
					listed = true;
					best = better(best, entry);
				}
			}

			if (best != null)
			{
				return best.style;
			}
			return showUngrouped && !listed ? LabelStyle.PLAIN : null;
		}

		/** Of two entries, the enabled one from the earlier group. */
		private static Entry better(Entry best, Entry entry)
		{
			if (!entry.enabled)
			{
				return best;
			}
			return best == null || entry.order < best.order ? entry : best;
		}

		private static Pattern toPattern(String key)
		{
			final StringBuilder regex = new StringBuilder();
			final String[] parts = key.split("\\*", -1);
			for (int i = 0; i < parts.length; i++)
			{
				if (i > 0)
				{
					regex.append(".*");
				}
				if (!parts[i].isEmpty())
				{
					regex.append(Pattern.quote(parts[i]));
				}
			}
			return Pattern.compile(regex.toString());
		}

		private static final class Entry
		{
			private final Pattern pattern; // null for an exact name
			private final LabelStyle style;
			private final int order;
			private final boolean enabled;
			private final ItemConditions conditions;

			Entry(Pattern pattern, LabelStyle style, int order, boolean enabled, ItemConditions conditions)
			{
				this.pattern = pattern;
				this.style = style;
				this.order = order;
				this.enabled = enabled;
				this.conditions = conditions;
			}

			boolean applies(GroundLabel label)
			{
				return conditions == null || conditions.matches(label);
			}
		}
	}
}

package com.lootlabels;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.TileItem;

/** An item lying on the ground along with the text drawn for it. */
class GroundLabel
{
	@Getter
	private final TileItem item;

	/** The item's name as the game shows it. */
	@Getter
	private final String name;

	/** The name in the form used to look it up in the label groups. */
	@Getter
	private final String key;

	/** How many are in the pile. Kept here because it can change while the item is on the ground. */
	@Getter
	@Setter
	private int quantity;

	/** Prices of one item, and whether the item stacks. They don't change, so they are looked up once. */
	private final long gePrice;
	private final long haPrice;

	@Getter
	private final boolean stackable;

	/** The game tick the item was first seen on the ground, for working out how far through its time it is. */
	@Getter
	private final int spawnTick;

	/**
	 * The label whose timer state this one uses: itself, or for a label standing for a pile, the real label of the
	 * item that despawns first. (Pile labels are rebuilt every frame, so they can't keep state of their own.)
	 */
	private GroundLabel source;

	/**
	 * For the despawn timer in seconds: when the item is expected to despawn (System.nanoTime(), 0 until known), and
	 * the game tick that estimate was last adjusted on.
	 */
	@Getter
	@Setter
	private long deadlineNanos;

	@Getter
	@Setter
	private int syncedTick;

	GroundLabel target()
	{
		return source == null ? this : source;
	}

	GroundLabel(TileItem item, String name, long gePrice, long haPrice, boolean stackable, int spawnTick)
	{
		this.spawnTick = spawnTick;
		this.item = item;
		this.name = name;
		this.key = LabelGroupManager.normalize(name);
		this.quantity = item.getQuantity();
		this.gePrice = gePrice;
		this.haPrice = haPrice;
		this.stackable = stackable;
	}

	/** One label standing for several of the same item lying on a tile: same item, the quantities added together. */
	static GroundLabel merged(List<GroundLabel> same)
	{
		// The one that despawns first stands for the pile when showing despawn times
		GroundLabel first = same.get(0);
		int quantity = 0;
		for (GroundLabel label : same)
		{
			quantity += label.quantity;
			if (label.item.getDespawnTime() < first.item.getDespawnTime())
			{
				first = label;
			}
		}

		final GroundLabel merged = new GroundLabel(first.item, first.name, first.gePrice, first.haPrice,
			first.stackable, first.spawnTick);
		merged.quantity = quantity;
		merged.source = first.target();
		return merged;
	}

	/**
	 * The text drawn for the item: its name and the amount when there is more than one, and its value when
	 * that is turned on in the settings.
	 */
	String getText(LootLabelsConfig config, boolean showValue)
	{
		// A stack of a stackable item reads "Name (100)"; several of a non-stackable item read "Name x100"
		final String base = quantity <= 1 ? name : (stackable ? name + " (" + abbreviate(quantity) + ")" : name + " x" + abbreviate(quantity));
		if (!showValue)
		{
			return base;
		}

		final long ge = getGeTotal();
		final long ha = getHaTotal();
		final boolean verbose = config.displayStyle() == LootLabelsConfig.DisplayStyle.VERBOSE;
		switch (config.valueDisplay())
		{
			case GRAND_EXCHANGE:
				return ge > 0 ? base + single("GE", ge, verbose) : base;
			case HIGH_ALCHEMY:
				return ha > 0 ? base + single("HA", ha, verbose) : base;
			case BOTH:
				if (ge <= 0 && ha <= 0)
				{
					return base;
				}
				return verbose
					? base + " [GE: " + abbreviate(ge) + "] [HA: " + abbreviate(ha) + "]"
					: base + " [" + abbreviate(ge) + "/" + abbreviate(ha) + "]";
			default:
				if (Math.max(ge, ha) <= 0)
				{
					return base;
				}
				return ge >= ha ? base + single("GE", ge, verbose) : base + single("HA", ha, verbose);
		}
	}

	private static String single(String kind, long value, boolean verbose)
	{
		return verbose ? " [" + kind + ": " + abbreviate(value) + "]" : " [" + abbreviate(value) + "]";
	}

	/**
	 * For quantities and values. Under 1,000 it is the plain number (800); from 1,000 up it is K, M or B with one
	 * decimal (1.5K, 2M).
	 */
	static String abbreviate(long value)
	{
		if (value < 1000)
		{
			return String.valueOf(value);
		}

		final String[] units = {"K", "M", "B"};
		double scaled = value / 1000.0;
		int unit = 0;
		// 999,950 rounds to 1000.0K, which reads better as 1M
		while (unit < units.length - 1 && Math.round(scaled * 10) / 10.0 >= 1000)
		{
			scaled /= 1000;
			unit++;
		}

		final double rounded = Math.round(scaled * 10) / 10.0;
		final String number = rounded == Math.floor(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
		return number + units[unit];
	}

	/** The Grand Exchange value of the whole pile. */
	long getGeTotal()
	{
		return gePrice * quantity;
	}

	/** The high alchemy value of the whole pile. */
	long getHaTotal()
	{
		return haPrice * quantity;
	}
}

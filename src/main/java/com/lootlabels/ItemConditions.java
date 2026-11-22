package com.lootlabels;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Conditions an item has to meet for a group's style to apply to it: its quantity, its Grand Exchange value, its
 * high alchemy value and whether it stacks. Every condition that is set has to hold. Values are for the whole pile
 * on the ground (price times quantity). Saved as JSON with the group, so the field names are part of the format.
 *
 * Instances are edited only by replacing whole conditions, and the overlay reads copies, never the live object.
 */
final class ItemConditions
{
	enum Type
	{
		QUANTITY, GE_VALUE, HA_VALUE, STACKABLE
	}

	enum Op
	{
		EQ("="), GT(">"), LT("<"), GE(">="), LE("<=");

		private final String symbol;

		Op(String symbol)
		{
			this.symbol = symbol;
		}

		/** Plain ASCII form, used to look up the drawn symbol. */
		String symbol()
		{
			return symbol;
		}

		boolean test(long actual, long expected)
		{
			switch (this)
			{
				case EQ:
					return actual == expected;
				case GT:
					return actual > expected;
				case LT:
					return actual < expected;
				case GE:
					return actual >= expected;
				default:
					return actual <= expected;
			}
		}
	}

	static final class Comparison
	{
		private final Op op;
		private final long value;

		Comparison(Op op, long value)
		{
			this.op = op;
			this.value = value;
		}

		Op getOp()
		{
			return op;
		}

		long getValue()
		{
			return value;
		}

		boolean test(long actual)
		{
			return op.test(actual, value);
		}

		boolean sameAs(Comparison other)
		{
			return other != null && op == other.op && value == other.value;
		}
	}

	private Comparison quantity;
	private Comparison geValue;
	private Comparison haValue;
	private Boolean stackable;

	boolean has(Type type)
	{
		return type == Type.STACKABLE ? stackable != null : getComparison(type) != null;
	}

	boolean isEmpty()
	{
		return quantity == null && geValue == null && haValue == null && stackable == null;
	}

	Comparison getComparison(Type type)
	{
		switch (type)
		{
			case QUANTITY:
				return quantity;
			case GE_VALUE:
				return geValue;
			case HA_VALUE:
				return haValue;
			default:
				return null;
		}
	}

	void setComparison(Type type, Comparison comparison)
	{
		switch (type)
		{
			case QUANTITY:
				quantity = comparison;
				break;
			case GE_VALUE:
				geValue = comparison;
				break;
			case HA_VALUE:
				haValue = comparison;
				break;
			default:
				break;
		}
	}

	Boolean getStackable()
	{
		return stackable;
	}

	void setStackable(Boolean stackable)
	{
		this.stackable = stackable;
	}

	void clear(Type type)
	{
		if (type == Type.STACKABLE)
		{
			stackable = null;
		}
		else
		{
			setComparison(type, null);
		}
	}

	ItemConditions copy()
	{
		final ItemConditions copy = new ItemConditions();
		copy.quantity = quantity;
		copy.geValue = geValue;
		copy.haValue = haValue;
		copy.stackable = stackable;
		return copy;
	}

	boolean matches(GroundLabel label)
	{
		return (quantity == null || quantity.test(label.getQuantity()))
			&& (geValue == null || geValue.test(label.getGeTotal()))
			&& (haValue == null || haValue.test(label.getHaTotal()))
			&& (stackable == null || stackable == label.isStackable());
	}

	/** Reads a whole number, allowing commas and the k, m and b suffixes (e.g. "2.5m"). */
	static OptionalLong parseAmount(String text)
	{
		String t = text.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("_", "").replace(" ", "");
		if (t.isEmpty())
		{
			return OptionalLong.empty();
		}

		long multiplier = 1;
		final char last = t.charAt(t.length() - 1);
		if (last == 'k')
		{
			multiplier = 1_000L;
		}
		else if (last == 'm')
		{
			multiplier = 1_000_000L;
		}
		else if (last == 'b')
		{
			multiplier = 1_000_000_000L;
		}

		try
		{
			if (multiplier == 1)
			{
				final long value = Long.parseLong(t);
				return value >= 0 ? OptionalLong.of(value) : OptionalLong.empty();
			}

			final BigDecimal value = new BigDecimal(t.substring(0, t.length() - 1)).multiply(BigDecimal.valueOf(multiplier));
			return value.signum() >= 0
				? OptionalLong.of(value.setScale(0, RoundingMode.HALF_UP).longValueExact())
				: OptionalLong.empty();
		}
		catch (NumberFormatException | ArithmeticException e)
		{
			return OptionalLong.empty();
		}
	}

	/** The full number with commas: 1500000 is "1,500,000". */
	static String formatAmount(long value)
	{
		return String.format(Locale.US, "%,d", value);
	}
}

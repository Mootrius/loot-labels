package com.lootlabels;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.KeyCode;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Loot Labels",
	description = "Customizable and easy-to-change labels for items on the ground",
	tags = {"loot", "labels", "filters", "ground", "items", "custom", "overlay"}
)
public class LootLabelsPlugin extends Plugin
{
	/** Items that aren't in a group are left unlabelled until this becomes a setting. */
	static final boolean SHOW_UNGROUPED = false;

	@Inject
	private Client client;

	@Inject
	private LootLabelsConfig config;

	@Inject
	private KeyManager keyManager;

	/** Whether the hotkey from the settings is being held. */
	@Getter(AccessLevel.PACKAGE)
	private volatile boolean hotkeyHeld;

	private final HotkeyListener hotkeyListener = new HotkeyListener(() -> config.hotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			hotkeyHeld = true;
		}

		@Override
		public void hotkeyReleased()
		{
			hotkeyHeld = false;
		}
	};

	@Inject
	private ItemManager itemManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private LootLabelsOverlay overlay;

	@Inject
	private LabelGroupManager groups;

	@Inject
	private LootLabelsPanel panel;

	private NavigationButton navButton;

	/**
	 * Ground items currently in the scene, grouped by tile. Events and overlay rendering both
	 * run on the client thread, so this does not need to be synchronized.
	 */
	@Getter(AccessLevel.PACKAGE)
	private final Map<Tile, List<GroundLabel>> labels = new HashMap<>();

	@Override
	protected void startUp()
	{
		groups.load();
		SwingUtilities.invokeLater(panel::rebuild);

		overlayManager.add(overlay);
		keyManager.registerKeyListener(hotkeyListener);

		final BufferedImage icon = ImageUtil.loadImageResource(LootLabelsPlugin.class, "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Loot Labels")
			.icon(icon)
			.priority(5)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(overlay);
		keyManager.unregisterKeyListener(hotkeyListener);
		hotkeyHeld = false;
		labels.clear();
	}

	@Provides
	LootLabelsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LootLabelsConfig.class);
	}

	/** When the last game tick arrived, so the despawn timer in seconds can count down between ticks. */
	@Getter(AccessLevel.PACKAGE)
	private volatile long lastTickNanos;

	/**
	 * The game tick count when {@link #lastTickNanos} was taken.
	 */
	@Getter(AccessLevel.PACKAGE)
	private volatile int lastTickCount;

	@Subscribe
	public void onGameTick(GameTick event)
	{
		lastTickCount = client.getTickCount();
		lastTickNanos = System.nanoTime();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// Ground items are re-announced through ItemSpawned once the new scene has loaded
		if (event.getGameState() == GameState.LOADING
			|| event.getGameState() == GameState.LOGIN_SCREEN
			|| event.getGameState() == GameState.HOPPING)
		{
			labels.clear();
		}
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		final TileItem item = event.getItem();
		final ItemComposition composition = itemManager.getItemComposition(item.getId());
		final String name = composition.getName();

		// A noted item is worth what the item it stands for is worth
		final int priceId = composition.getNote() != -1 ? composition.getLinkedNoteId() : item.getId();
		final long gePrice = itemManager.getItemPrice(priceId);
		final long haPrice = itemManager.getItemComposition(priceId).getHaPrice();

		labels.computeIfAbsent(event.getTile(), t -> new ArrayList<>())
			.add(new GroundLabel(item, name, gePrice, haPrice,
				composition.isStackable(), client.getTickCount()));
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		final List<GroundLabel> onTile = labels.get(event.getTile());
		if (onTile == null)
		{
			return;
		}

		for (Iterator<GroundLabel> it = onTile.iterator(); it.hasNext(); )
		{
			if (it.next().getItem() == event.getItem())
			{
				it.remove();
				break;
			}
		}

		if (onTile.isEmpty())
		{
			labels.remove(event.getTile());
		}
	}

	@Subscribe
	public void onItemQuantityChanged(ItemQuantityChanged event)
	{
		final List<GroundLabel> onTile = labels.get(event.getTile());
		if (onTile == null)
		{
			return;
		}

		for (GroundLabel label : onTile)
		{
			if (label.getItem() == event.getItem())
			{
				label.setQuantity(event.getNewQuantity());
				break;
			}
		}
	}

	/**
	 * Shift + right-click on an item, in the inventory or on the ground, adds "Add to Loot Labels", with a submenu
	 * listing the groups. Clicking a group puts the item in it.
	 */
	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!client.isKeyPressed(KeyCode.KC_SHIFT))
		{
			return;
		}

		final List<LabelGroup> all = groups.getGroups();
		if (all.isEmpty())
		{
			return;
		}

		for (MenuEntry entry : event.getMenuEntries())
		{
			if (entry.getType() == MenuAction.EXAMINE_ITEM_GROUND)
			{
				// One for each item lying on the tile that was clicked
				addGroupMenu(all, entry.getIdentifier(), entry.getTarget());
				continue;
			}

			if (entry.getType() != MenuAction.CC_OP && entry.getType() != MenuAction.CC_OP_LOW_PRIORITY)
			{
				continue;
			}

			final Widget widget = entry.getWidget();
			if (widget != null && widget.getItemId() > 0
				&& WidgetUtil.componentToInterface(widget.getId()) == InterfaceID.INVENTORY)
			{
				addGroupMenu(all, widget.getItemId(), entry.getTarget());
				return; // every entry of an inventory slot is for the same item
			}
		}
	}

	private static boolean isGroundItemOption(MenuAction type)
	{
		return type == MenuAction.GROUND_ITEM_FIRST_OPTION || type == MenuAction.GROUND_ITEM_SECOND_OPTION
			|| type == MenuAction.GROUND_ITEM_THIRD_OPTION || type == MenuAction.GROUND_ITEM_FOURTH_OPTION
			|| type == MenuAction.GROUND_ITEM_FIFTH_OPTION || type == MenuAction.WIDGET_TARGET_ON_GROUND_ITEM;
	}

	/**
	 * Stands in for "no label" in {@link #styleOf}: an item the labels hide (PLAIN is never given out while ungrouped
	 * items are left unlabelled, so it can't be mistaken for a real style).
	 */
	private static final LabelStyle NOT_LABELED = LabelStyle.PLAIN;

	/**
	 * The style the labels give the items with this id on a tile (several count as one pile, as in the labels), or
	 * {@link #NOT_LABELED} when they get no label, or null when the plugin doesn't know of such an item.
	 */
	private LabelStyle styleOf(Tile tile, int itemId)
	{
		final List<GroundLabel> onTile = labels.get(tile);
		if (onTile == null)
		{
			return null;
		}

		final List<GroundLabel> same = new ArrayList<>();
		for (GroundLabel label : onTile)
		{
			if (label.getItem().getId() == itemId)
			{
				same.add(label);
			}
		}
		if (same.isEmpty())
		{
			return null;
		}

		final List<GroundLabel> shown = LootLabelsOverlay.visible(same, config.showWorldSpawns(),
			config.showOthersDrops());
		if (shown.isEmpty())
		{
			return NOT_LABELED;
		}

		final LabelStyle style = groups.styleFor(shown.size() == 1 ? shown.get(0) : GroundLabel.merged(shown),
			SHOW_UNGROUPED);
		return style == null ? NOT_LABELED : style;
	}

	/**
	 * Right-click menu entries for items on the ground: unlabelled ones can be pushed out of the left-click spot, and
	 * item names can take the color of their group (or a color of their own when they have no label).
	 */
	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.deprioritizeItems() && !config.recolorEntries() && !config.recolorNonLabeled())
		{
			return;
		}

		final MenuEntry entry = event.getMenuEntry();
		final MenuAction type = entry.getType();
		final boolean option = isGroundItemOption(type);
		if (!option && type != MenuAction.EXAMINE_ITEM_GROUND)
		{
			return;
		}

		final WorldView worldView = client.getWorldView(entry.getWorldViewId());
		if (worldView == null)
		{
			return;
		}

		final Tile[][][] tiles = worldView.getScene().getTiles();
		final int plane = worldView.getPlane();
		final int sceneX = event.getActionParam0();
		final int sceneY = event.getActionParam1();
		if (sceneX < 0 || sceneY < 0 || sceneX >= tiles[plane].length || sceneY >= tiles[plane][sceneX].length)
		{
			return;
		}

		final LabelStyle style = styleOf(tiles[plane][sceneX][sceneY], event.getIdentifier());
		if (style == null)
		{
			return;
		}

		if (style == NOT_LABELED)
		{
			if (option && config.deprioritizeItems())
			{
				entry.setDeprioritized(true);
			}
			if (config.recolorNonLabeled())
			{
				entry.setTarget(recolor(entry.getTarget(), config.nonLabeledColor()));
			}
		}
		else if (config.recolorEntries() && style.isBoxed())
		{
			entry.setTarget(recolor(entry.getTarget(), style.getBorder()));
		}
	}

	/**
	 * Swaps the color tag in front of the item's name. A menu target looks like "<col=ff9040>Logs", or with a spell
	 * or item in use, "<col=00ff00>Telekinetic Grab</col><col=ffffff> -> <col=ff9040>Logs".
	 */
	private static String recolor(String target, Color color)
	{
		final int tag = target.lastIndexOf("<col=");
		if (tag < 0 || target.length() < tag + 12 || target.charAt(tag + 11) != '>')
		{
			return target;
		}
		return target.substring(0, tag) + ColorUtil.colorTag(color) + target.substring(tag + 12);
	}

	/**
	 * Several of the same item on a tile that aren't a stack have identical menu entries; with "Collapse entries" on,
	 * only one is kept and its name gets "x" and how many there were. A stackable item's name always gets its stack
	 * size in brackets.
	 */
	@Subscribe
	public void onClientTick(ClientTick event)
	{
		final boolean collapse = config.collapseEntries();
		final MenuEntry[] entries = client.getMenuEntries();
		final List<MenuEntry> kept = new ArrayList<>(entries.length);
		final Map<String, MenuEntry> firstOf = new HashMap<>();
		final Map<MenuEntry, Integer> counts = new IdentityHashMap<>();

		// Top of the menu first, so the entry that stays is the one nearest the top
		for (int i = entries.length - 1; i >= 0; i--)
		{
			final MenuEntry entry = entries[i];
			final MenuAction type = entry.getType();
			if (collapse && (isGroundItemOption(type) || type == MenuAction.EXAMINE_ITEM_GROUND))
			{
				final String key = type + "|" + entry.getOption() + "|" + entry.getTarget() + "|"
					+ entry.getIdentifier() + "|" + entry.getParam0() + "|" + entry.getParam1() + "|"
					+ entry.getWorldViewId();
				final MenuEntry existing = firstOf.get(key);
				if (existing != null)
				{
					counts.merge(existing, 1, Integer::sum);
					continue;
				}
				firstOf.put(key, entry);
				counts.put(entry, 1);
			}
			kept.add(entry);
		}

		boolean changed = kept.size() != entries.length;
		for (MenuEntry entry : kept)
		{
			final int count = counts.getOrDefault(entry, 1);
			if (count > 1)
			{
				// Closing the color tag puts the amount back in the menu's own text color, the one words like "Take" and
				// "Talk-to" have, including when the row is hovered. (A fixed color would not change on hover.)
				entry.setTarget(entry.getTarget() + "</col> x" + count);
				changed = true;
			}
			else if (isGroundItemOption(entry.getType()) || entry.getType() == MenuAction.EXAMINE_ITEM_GROUND)
			{
				// A stackable item gets its stack size, e.g. "Take Coins (1.5K)"
				final long quantity = stackQuantity(entry);
				if (quantity > 1)
				{
					final String suffix = "</col> (" + GroundLabel.abbreviate(quantity) + ")";
					if (!entry.getTarget().endsWith(suffix)) // the menu is looked at every tick; add it only once
					{
						entry.setTarget(entry.getTarget() + suffix);
						changed = true;
					}
				}
			}
		}

		if (!changed)
		{
			return;
		}

		Collections.reverse(kept);
		client.setMenuEntries(kept.toArray(new MenuEntry[0]));
	}

	/**
	 * The size of the stack a menu entry is about, or -1 if the item doesn't stack or the stack can't be told apart
	 * (several piles of the same item on one tile).
	 */
	private long stackQuantity(MenuEntry entry)
	{
		final WorldView worldView = client.getWorldView(entry.getWorldViewId());
		if (worldView == null)
		{
			return -1;
		}

		final Tile[][][] tiles = worldView.getScene().getTiles();
		final int plane = worldView.getPlane();
		final int sceneX = entry.getParam0();
		final int sceneY = entry.getParam1();
		if (sceneX < 0 || sceneY < 0 || sceneX >= tiles[plane].length || sceneY >= tiles[plane][sceneX].length)
		{
			return -1;
		}

		final Tile tile = tiles[plane][sceneX][sceneY];
		final List<TileItem> onTile = tile == null ? null : tile.getGroundItems();
		if (onTile == null)
		{
			return -1;
		}

		final int id = entry.getIdentifier();
		long quantity = -1;
		for (TileItem item : onTile)
		{
			if (item.getId() != id)
			{
				continue;
			}
			if (quantity != -1)
			{
				return -1;
			}
			quantity = item.getQuantity();
		}
		return quantity > 0 && itemManager.getItemComposition(id).isStackable() ? quantity : -1;
	}

	private void addGroupMenu(List<LabelGroup> all, int itemId, String target)
	{
		final String name = itemManager.getItemComposition(itemId).getName();
		final MenuEntry parent = client.createMenuEntry(1)
			.setOption("Add to Loot Labels")
			.setTarget(target)
			.setType(MenuAction.RUNELITE);
		final Menu submenu = parent.createSubMenu();
		for (LabelGroup group : all)
		{
			submenu.createMenuEntry(0)
				.setOption(group.getName())
				.setType(MenuAction.RUNELITE)
				.onClick(e ->
				{
					if (groups.addItem(group, name))
					{
						SwingUtilities.invokeLater(panel::rebuild);
					}
				});
		}
	}
}

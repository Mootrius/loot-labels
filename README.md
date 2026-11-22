# Loot Labels

A customizable replacement for RuneLite's Ground Items / Loot Filters plugins.

## Current features

- Draws the name of every item on the ground above its tile, with the amount when there are several: "(100)" for
  a stack of a stackable item and "x100" for several of a non-stackable one.
- Optional despawn timers: a draining pie, or the time left in ticks or seconds, colored green while only you can
  see an item, yellow once others can, and red for the last 30 seconds.
- Menu settings for items on the ground: make unlabelled items need a right-click to pick up, collapse several of
  the same item into one entry, and color item names in the menu by group (or a color of your choice when they
  have no label).
- Stackable items on the ground show their stack size in the right-click menu, like Coins (1.5K).
- A hotkey (set in the settings) that, while held, can also label every unlabelled item as plain text, and show item
  values and despawn timers even when those are turned off.
- Settings to hide world spawns and other players' drops (for example, for ironmen).
- Settings for the text color and for the box around grouped items (border thickness, border opacity and
  background opacity), and for the label font (size, weight, style and a shadow / outline / no accent) and for showing item
  values (Grand Exchange, high alchemy, the higher of the two, or both, in a compact or verbose style, with K/M/B abbreviations).
- Every group has its own color (the color swatch in the sidebar). Labels from a group are drawn in a box with a
  border and a fill of that color (by default a solid 1.5px border and a 50% opaque fill).
- Every group can also show a clan rank title icon (the picture button in the sidebar) left of the text, inside the
  box. The icons are the Rank Titles icons from the OSRS Wiki's Clan Settings page, bundled in `ranks/`.
- Each item in a group can have conditions, set with the icons on its row: quantity (#), Grand Exchange value,
  high alchemy value and stackable / not stackable. Values are for the whole pile on the ground and accept
  `k`, `m` and `b` (e.g. `2.5m`). An item only gets the group's style while all its conditions hold.
- Click a group's name or an item's name to rename it. Groups, and items within a group, can be dragged to
  reorder them; when an item is in several enabled groups, the group nearest the top decides its style.
- A dark, collapsible sidebar panel for making groups of items. Each group has an on/off switch, and you can
  add items by name with the "+ ITEM" button (or remove them with the x) (`*` works as a wildcard, e.g. `*bones`). An item is labelled if it is in at
  least one enabled group; items in no group are not labelled.
  A group can list the same item more than once, each line with its own rules; the item is labelled if any of its lines applies.
- Hold shift and right-click an item (in your inventory or on the ground) and choose "Add to Loot Labels" to put it
  in one of your groups.
- Share your setup with the export and import buttons next to "+ GROUP". Export lets you tick which groups to copy to your
  clipboard as JSON; import reads JSON from your clipboard, lists the groups it finds, and adds the ones you tick (collapsed and switched off).

## Running it

```
./gradlew run
```

This launches a RuneLite developer client with the plugin loaded. To log in with a Jagex account see
https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts

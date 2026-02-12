# Staircased Printer

The Staircased Printer builds arbitrary staircased fullblock maps line by line without any user interaction.
The bot mines all placed blocks again to recycle all used materials.
The printer uses a mapart platform to collect all mined blocks feeds them into an item sorter on the north side of the map.
This module will not work on servers where placing blocks in the air is disabled.

Blocks that require support blocks (e.g., carpets) are not supported by the printer.
The only two colors not supported are two “water colors,” which require different water depths.
One of the three water colors can be supported by using glass and the same depth level in the mapart platform.
The schematic linked below uses the shallow water color.

---

## Platform Setup

A Litematica file with an example map area can be found [here](StaircasedPrinter.litematic).

You can design your own platform as long as it fulfills the following criteria:
- The setup must be north of the map area.
- The platform must be placed in a Mushroom Fields biome to avoid mob spawning.
- To build all possible maps, the main platform must have 128 blocks of empty space above and below.
    - Minimum Y-level: `-60 + 129 = 69`
    - Maximum Y-level: `320 - 129 = 191`
    - You will likely need to dig a perimeter to clear enough space.
- All components listed below must be reachable from each other by walking in a straight line.

---

## Platform Components

### DumpStation
The bot throws all blocks it no longer needs at this position.
Ideally, use a water stream to pipe them back into the sorter.

### Cartography Table
Used to lock created maps.

### FinishedMapChest
The bot puts finished maps here.

### MapMaterialChest
This chest should contain empty maps and glass panes for locking new maps.

### ToolChests
Chests containing the tools required to mine the built map.
Recommended:
- Pickaxe
- Axe
- Shovel

Optional (rarely used):
- Shears
- Hoe

### UsedToolChests
The chest where all used tools are deposited.

### BuildMaterialChests
These chests contain the actual mapart materials used for building.
They are connected to a sorter, which should be efficient and include overflow protection (e.g., a dolphin).

### Bed (Optional)
Used by bots when starting a new map to avoid phantom spawning.

---

## Loading NBT Files

When the module is started for the first time, a `nerv-printer` folder is created in your Minecraft directory.
Place as many `1×1` NBT files as you like into this folder.

The type of staircasing used in the converter (`Valley` / `Classic`) does not matter, as the bot parses and reinterprets the NBT into a new arrangement.
This allows the bot to walk the map line by line starting from the north side.

NBT files are processed in alphabetical order.

---

## Workflow

Follow these four steps:

1. Register important blocks
2. Build map
3. Create map item
4. Mine map

---

### 1. Register Important Blocks

The module prompts you to interact with all special blocks. Chests only need to be selected once, even if the rendered box highlights only half of the chest.

When finished, interact with one of the start blocks specified in the **start-blocks** setting (default: all buttons) to begin printing.
Inventory slots containing nothing or a registered material are marked for future materials.
All other slots are ignored.

---

### 2. Build Map

The bot builds the map line by line.
It calculates the maximum area it can cover with available inventory slots and restocks as needed.

When a material runs out:
1. The bot dumps unnecessary items into the DumpStation.
2. Restocks needed block.
3. Continues building

---

### 3. Create Map Item

When the map is finished:

1. The bot takes an empty map and a glass pane from the MapMaterialChest.
2. It creates a map in the center of the Map Area.
3. It locks the map at the Cartography Table.

---

### 4. Mine Map

All bots mine the entire map from left to right.

When multiple bots are used:
- They request the next leftmost line to mine from the master bot.
- This prevents item loss caused by items falling onto neighboring lines.

After the entire map is mined, the process returns to **Step 2**.

---

## Optional Features

### Save and Load Configurations

To save a configuration:
1. Register blocks as usual.
2. Press **Save Config** in the module settings.
3. Select a file to overwrite or choose a new file name.
It will be a JSON file.

To load a configuration:
1. Press **Load**.
2. Select a config file.
3. Start the print as usual.

---

### Multi-User Printing

The printer can coordinate multiple accounts to print on the same map area.

- One bot acts as the **master**.
- Other bots act as **slaves**.
- Communication occurs via direct messages.

#### Setup

1. Adjust the prefix and suffix in the settings.
Most servers use third-party DM plugins with varying syntax.  
Configure the Multi-User settings accordingly.
Incoming DMs should follow this format: `(prefix)(sender's name)(suffix)(message)`
2. Enable the module and load the configuration on **every** bot.
3. Move all slave bots within render distance of the master.
4. Press **Register** using the master account.
An **Accept** message should appear for each slave.
5. Start the print as usual.



export const howToPlayMarkdown = `
# How to Play

## Chat
You can send chat messages with the chat input on the bottom center of the screen.  
These can be seen by all players/agents in the simulation.

## Character Movement
Cick/tap anywhere in the game to move there.

## Actions
When you stand near another object in the game, you may be able to take an action on that object.  
Some interactions require that you have item equipped (e.g. cutting a tree with an axe).  
Other interactions don't require an item, such as picking up a piece of wood.  
The game will automatically pick an action based on the nearest object to your character that as an action available to you.  
When an action is available, a large button will show up in the bottom center of the screen (above the quick inventory panel).  
If you have keyboard available, you can also use the spacebar to perform actions.

## Chat Input
On the bottom center of the screen, you can input chat messages to communicate with other characters.  
Note that chat messages can only be seen within the fog-of-war viewing distance.
The chat input can also be used for certain developer commands (see Chat commands).

## Quick Inventory Bar / Inventory Panel
The Quick Inventory Bar is directly above the chat input box.  
This bar only shows the first instance of each type of equippable item in your inventory.  
Clicking on items in the bar immediately equips or unequips those items.  
On the right side of Quick Inventory Bar is a button to open the full inventory panel, which includes detailed quantities of items, including non-equippable resources, and has buttons for dropping inventory items.

## Activty Panel
The button to open the activity panel is located above the chat input box, on the left side.  
The activity panel button's icon is two chat bubbles.

## Crafting Panel
The button to open the crafting panel is located above the chat input box, to the right of the activity panel button.  
The crafting panel button's icon is a hammer.

## Character List Header
At the top center of the screen, there is a list of characters in the simulation.  
You can tap on their icons to select the characters.  
Once a character is selected, you can optionally click the \`View Perspective\` button to view the simulation from their perspective, meaning that the fog of war (including activity stream filtering) will represent their perspective.

## Equipping/Dropping Items
After picking up or crafting an item, it will go into your inventory.
Open the inventory panel to view your current inventory.
From here, there will be buttons on each item that you can use to equip (hand icon) or drop items (down arrow icon).

## Camera Movement
Swipe/drag on the screen to move the camera.
Pinch/scroll to zoom the camera.

## Chat commands
There are special / commands you can enter via the chat input

### Change your character's name
\`/name <new name>\`

### Re-roll your character's random appearance
\`/reroll <gender (male/female)> <skin-color (light, amber, olive, bronze, brown, black)> <hair-color (blonde, ash, sandy, black, gray, white)>\`


### Give item
\`/give-item item-id <amount_per_stack=1> <stacks=1>\`

### Spawn item
\`/spawn-item item-id <amount_per_stack=1> <stacks=1>\`

### Spawn agent
\`/spawn-agent <agent-type=script> <name=Agent>\`

### Give item to nearest character
\`/give-item-near item-id <amount_per_stack> <stacks>\`

### Take item (from yourself)
\`/give-item item-id <amount_per_stack> <stacks>\`

`

export const attributionsMarkdown = `
## Attribution
- Liberated Pixel Cup (LPC)
    - The characters animations in this project are from the [Liberated Pixel Cup](https://lpc.opengameart.org/) (LPC) project, which is licensed under CC BY-SA 3.0 and GPLv3.
    - [The full list of attributions for these art assets is available here](https://github.com/jshmrsn/botfarm/blob/master/client/public/assets/liberated-pixel-cup-characters/CREDITS.TXT)
    - This project also uses JSON data derived from the "sheet_definitions" of the [Universal LPC Spritesheet Character Generator](https://github.com/sanderfrenken/Universal-LPC-Spritesheet-Character-Generator) project (also GPLv3).

## License 
This project is licensed under GPLv3 (see the LICENSE file).
If this license is prohibitive for your use case, please open an issue to discuss further.
`
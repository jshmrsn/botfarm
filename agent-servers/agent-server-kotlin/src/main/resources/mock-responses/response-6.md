I want to gather stone to build my house. I will find a boulder and use a pickaxe to break it apart. I will also let Joe know that I am gathering stone now.

```javascript
speak("Mock response 6")
// Crafting a pickaxe
const pickaxeRecipe = getAllCraftingRecipes().find(recipe => recipe.itemTypeId === "pickaxe");
if (pickaxeRecipe && pickaxeRecipe.canCurrentlyAfford) {
  pickaxeRecipe.craft("Crafting a pickaxe to gather stone.");
}

// Equipping the pickaxe
const pickaxe = getCurrentInventoryItemStacks().find(item => item.itemTypeId === "pickaxe");
if (pickaxe && pickaxe.canBeEquipped) {
  pickaxe.equip("Equipping the pickaxe to gather stone.");
}

// Finding a boulder
const boulder = getCurrentNearbyEntities().find(entity => entity.itemOnGround && entity.itemOnGround.itemTypeId === "boulder");
if (boulder && boulder.damageable && boulder.damageable.canBeDamagedByEquippedItemTypeId === "pickaxe") {
  // Moving to the boulder and breaking it apart
  walkTo(boulder.location, "Walking to the boulder to gather stone.");
  boulder.damageable.attackWithEquippedItem("Breaking apart the boulder to gather stone.");
}

// Letting Joe know
speak("Hey Joe, I'm gathering stone now!");
```
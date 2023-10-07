I would like to start crafting my house now that I have gathered enough wood. I will also let Joe know that I am starting to build my house and ask him if he has started building his house yet.

```javascript
speak("Mock response 1")
// Find the crafting recipe for the house
const craftingRecipes = getAllCraftingRecipes();
const houseRecipe = craftingRecipes.find(recipe => recipe.itemTypeId === "house");

// Craft the house
if (houseRecipe && houseRecipe.canCurrentlyAfford) {
  houseRecipe.craft("Building my house.");
  speak("Joe, I've started building my house. Have you started building yours?");
} else {
  speak("It seems I still need more materials to build my house.");
}
```
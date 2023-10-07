I would like to continue gathering stone for my house. I will pick up the stone that I just harvested from the boulder. Then, I will check if I have enough stone and wood to build my house. If I don't have enough, I will find another boulder to break apart. I also want to keep Joe updated about my progress. 

```js
speak("Mock response 7")
// Pick up the stone
stone_entity_0B4B8C78.itemOnGround.pickUp("Gathering stone for building a house.");

// Check if I have enough materials to build a house
let woodAmount = getTotalInventoryAmountForItemTypeId("wood");
let stoneAmount = getTotalInventoryAmountForItemTypeId("stone");
if (woodAmount >= 150 && stoneAmount >= 50) {
    speak("Hey Joe, I have gathered enough materials for my house. I'm going to start building it now!");
} else {
    // Find another boulder to break apart
    let boulders = getCurrentNearbyEntities().filter(entity => entity.itemOnGround && entity.itemOnGround.itemTypeId === "boulder");
    if (boulders.length > 0) {
        walkTo(boulders[0].location, "Walking to the boulder to gather more stone.");
        boulders[0].damageable.attackWithEquippedItem("Breaking apart the boulder to gather stone.");
    }
    speak("Hey Joe, I'm still gathering stone. How's your progress?");
}
```

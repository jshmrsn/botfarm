package botfarmshared.game
import botfarmshared.game.apidata.CraftingRecipe
import botfarmshared.misc.Vector2
import kotlinx.serialization.Serializable

@Serializable
object GameConstants {
   val distanceUnit = "centimeters"
   val peopleSize = 40.0
}

@Serializable
class GameSimulationInfo(
   val worldBounds: Vector2,
   val craftingRecipes: List<CraftingRecipe>
)
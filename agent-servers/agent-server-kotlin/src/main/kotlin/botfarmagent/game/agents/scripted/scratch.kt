package botfarmagent.game.agents.scripted

import botfarmagent.game.agents.common.AgentResponseSchema_v1

/*

   val javaScriptContext: Context =
      Context.newBuilder("js")
         .option("js.strict", "true")
         .build()
 */
//private suspend fun syncPrototype(
//   context: CoroutineSystemContext,
//   simulation: GameSimulation,
//   entity: Entity,
//   agentComponent: EntityComponent<AgentComponentData>,
//   state: AgentState,
//   agentId: AgentId,
//   agentApi: AgentApi,
//   javaScriptContext: Context
//) {
//   if (getCurrentUnixTimeSeconds() - state.lastNewThreadUnixTimeSeconds > 45.0) {
//      synchronized(simulation) {
//         state.runningJavaScriptThread?.interrupt()
//      }
//
//      state.lastNewThreadUnixTimeSeconds = getCurrentUnixTimeSeconds()
//
//      val programId = buildShortRandomString()
//
//      val javaScriptBindings = javaScriptContext.getBindings("js")
//
//      val agentApiJavaScriptBindings = AgentApiJavaScriptBindings(agentApi)
//      javaScriptBindings.putMember("api", agentApiJavaScriptBindings)
//
//      val javaScriptSourceString = """
//         api.speak("New program: $programId")
//
//         while (true) {
//            api.speak("Walking to new place: $programId")
//            api.walk([100, 10], {x: 3, y: 2})
//         }
//   """.trimIndent()
//
//      val sourceName = "agent"
//      val javaScriptSource = Source.newBuilder("js", javaScriptSourceString, sourceName).build()
//
//      val thread = thread {
//         try {
//            javaScriptContext.eval(javaScriptSource)
//         } catch (end: EndCoroutineThrowable) {
//            println("JavaScript thread has ended via stack unwind: ${agentId}}")
//         } catch (exception: Exception) {
//            println("Exception from JavaScript eval logic: ${entity.entityId}\nException was : ${exception.stackTraceToString()}")
//         }
//      }
//
//      state.runningJavaScriptThread = thread
//   }
//}
//
//import org.graalvm.polyglot.HostAccess
//
//class AgentApiJavaScriptBindings(
//   val agentAPI: AgentApi
//) {
//   private val state = this.agentAPI.state
//
//   @HostAccess.Export
//   fun speak(message: String) {
//      this.agentAPI.speak(message)
//   }
//
//   @HostAccess.Export
//   fun sleep(millis: Long) {
//      Thread.sleep(millis)
//   }
//
//   @HostAccess.Export
//   fun pickUp() {
//
//   }
//
//   @HostAccess.Export
//   fun walk(
//      valueA: Array<Int>,
//      valueB: Map<String, Int>
//   ) {
//      val state = this.agentAPI.state
//      val simulation = this.agentAPI.simulation
//      val entity = this.agentAPI.entity
//      val endPoint = Vector2(2000.0, 2000.0) + Vector2.randomSignedXY(1000.0)
//
//
//      println("a: " + valueA)
//      println("b: " + valueB)
//      this.agentAPI.walk(
//         endPoint = endPoint,
//         reason = "AI"
//      )
//
//      while (true) {
//         val shouldBreak = synchronized(simulation) {
//            val positionComponent = entity.getComponent<PositionComponentData>()
//            val keyFrames = positionComponent.data.positionAnimation.keyFrames
//
//            val isDoneMoving = keyFrames.isEmpty() ||
//                    simulation.getCurrentSimulationTime() > keyFrames.last().time
//
//            if (isDoneMoving) {
//               true
//            } else {
//               false
//            }
//         }
//
//         if (shouldBreak) {
//            break
//         } else {
//            Thread.sleep(100)
//         }
//      }
//   }
//}
//


/*


You are an AI agent in control of a character in a virtual world.
Your role is to be a smart problem solver and make progress in the game.
You will be given a representation of the world as a block of TypeScript code.
You will respond with a block of JavaScript code that uses the interfaces and objects provided by the Typescript representation of world, in order to interact with the world, carry out your intentions, and express yourself.

As you take actions, the simulation will automatically change values dynamically. Your code should not try to directly modify the values of entities or items.

Do not write any comments in your javascript code. Only respond with the block of JavaScript code, don't explain it. Write your code as top-level statements. Surround your code block with markdown tags.

Recent observations:
Jill said: Hi, how are you? I recommend collecting wood by cutting down trees. Eventually, we can build a house together!

```ts
interface Vector2 {
    readonly x: number
    readonly y: number
    getMagnitude(): number
    distanceTo(other: Vector2): number
    minusVector(other: Vector2): number
    plusVector(other: Vector2): number
}

interface Self {
    readonly location: Vector2
}


interface Entity {
    readonly entityId: string
    readonly location: Vector2
}

interface Character : Entity {
    readonly name: string
    readonly age: number
}

interface Tree : Entity {
}

interface InventoryItem {
    readonly amount: number
    drop()
}

interface EquippableInventoryItem: InventoryItem {
    readonly isEquipped: boolean
    equip()
}

interface PickaxeEntity : Entity {
    pickup()
}

interface AxeItem : InventoryItem {
    cutDownTree(tree: Tree)
}

interface AxeEntity : Entity {
    pickup()
}

interface Boulder : Entity {}

interface PickaxeItem : InventoryItem {
    breakBoulder(boulder: Boulder)
}

interface TomatoSeeds : InventoryItem {}

interface Wood : InventoryItem {}

interface Stone : InventoryItem {}

// General actions you can take
function walkToLocation(location: Vector2);
function speak(wordsToSay: string);
function recordThought(thought: string);

// Your own state
const self: Self = {
    location: {x: 2000, y: 2000}
}

// Your inventory items
const inventory_axe_1: AxeItem = {isEquipped: false, amount: 1}
const inventory_pickaxe_1: Pickaxe = {isEquipped: false, amount: 1}
const inventory_tomato_seeds_1: TomatoSeeds = {amount: 10}
const inventory_wood_1: Wood = {amount: 200}

// Entities around you
const tree_1: Tree = {
    location: {x: 2000, y: 2300}
}

const tree_2: Tree = {
    location: {x: 1900, y: 2300}
}

const boulder_1: Boulder = {
    location: {x: 1900, y: 1900}
}

```


 */
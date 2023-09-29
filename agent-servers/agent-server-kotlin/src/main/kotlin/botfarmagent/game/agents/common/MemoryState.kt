package botfarm.agentserver.agents.common

import botfarmshared.misc.Vector2

class LongTermMemory(
   val createdTime: Double,
   val id: Int,
   val content: String,
   val importance: Int,
   val createdAtLocation: Vector2
)

class MemoryState {
   var shortTermMemory = ""
   val longTermMemories = mutableListOf<LongTermMemory>()
   val automaticShortTermMemories: MutableList<AutomaticShortTermMemory> = mutableListOf<AutomaticShortTermMemory>()
}

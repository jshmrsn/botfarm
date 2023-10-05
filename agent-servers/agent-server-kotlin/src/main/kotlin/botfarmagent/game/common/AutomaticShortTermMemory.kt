package botfarmagent.game.common

class AutomaticShortTermMemory(
   val time: Double,
   val summary: String,
   val deDuplicationCategory: String? = null,
   val forcePreviousActivity: Boolean = false,
   val isHighPriority: Boolean = false
)

fun deDuplicateOldAutomaticMemories(automaticShortTermMemories: MutableList<AutomaticShortTermMemory>) {
   val deDuplicationCategories = automaticShortTermMemories.mapNotNull { it.deDuplicationCategory }.toSet()

   deDuplicationCategories.forEach { deDuplicationCategory ->
      val newestForCategory = automaticShortTermMemories.findLast {
         it.deDuplicationCategory == deDuplicationCategory
      } ?: throw Exception("newestForCategory not found")

      automaticShortTermMemories.removeIf {
         val shouldRemove = it.deDuplicationCategory == deDuplicationCategory && it != newestForCategory
         if (shouldRemove) {
            println("Removing duplicate memory: ${it.summary}")
         }
         shouldRemove
      }
   }
}

fun addNewAutomaticShortTermMemories(
   automaticShortTermMemories: MutableList<AutomaticShortTermMemory>,
   newAutomaticShortTermMemories: List<AutomaticShortTermMemory>
) {
   automaticShortTermMemories.addAll(newAutomaticShortTermMemories)
   automaticShortTermMemories.sortBy { it.time }
   deDuplicateOldAutomaticMemories(automaticShortTermMemories)
}

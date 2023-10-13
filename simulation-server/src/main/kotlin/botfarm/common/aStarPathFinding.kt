package botfarm.common

import kotlinx.serialization.Serializable

@Serializable
data class IndexPair(val row: Int, val col: Int) {
   fun distanceSquared(other: IndexPair): Int {
      val rowDelta = this.row - other.row
      val colDelta = this.col - other.col
      return rowDelta * rowDelta + colDelta * colDelta
   }
}

data class Node(
   val position: IndexPair,
   val parent: Node? = null,
   val g: Double = 0.0,
   val h: Double = 0.0,
   val f: Double = 0.0
)

fun <COLLISION_FLAG : Any> aStarPathfinding(
   collisionMap: List<List<CollisionMap.Cell<COLLISION_FLAG>>>,
   start: IndexPair,
   end: IndexPair,
   flag: COLLISION_FLAG
): List<IndexPair> {

   val openList = mutableListOf<Node>()
   val closedList = mutableListOf<Node>()
   val directions = listOf(
      IndexPair(-1, 0), IndexPair(1, 0), IndexPair(0, -1), IndexPair(0, 1),
      IndexPair(-1, -1), IndexPair(-1, 1), IndexPair(1, -1), IndexPair(1, 1)
   )

   openList.add(Node(start))

   fun getCollision(row: Int, col: Int): Boolean {
      if (row < 0 || row >= collisionMap.size) {
         return false
      }

      val rowArray = collisionMap[row]

      if (col < 0 || col >= rowArray.size) {
         return false
      }

      return rowArray[col].isOpen(flag)
   }

   while (openList.isNotEmpty()) {
      val currentNode = openList.minByOrNull { it.f } ?: break
      if (currentNode.position == end) {
         var node = currentNode
         val path = mutableListOf<IndexPair>()
         while (node.parent != null) {
            path.add(node.position)
            node = node.parent!!
         }
         return path.reversed()
      }

      openList.remove(currentNode)
      closedList.add(currentNode)

      for (direction in directions) {
         val newRow = currentNode.position.row + direction.row
         val newCol = currentNode.position.col + direction.col

         if (newRow !in 0 until collisionMap.size || newCol !in 0 until collisionMap[0].size) continue
         if (!getCollision(newRow, newCol)) continue

         val isDiagonal = direction.row != 0 && direction.col != 0

         // Avoid obstructed corners
         if (isDiagonal && (
                    !getCollision(currentNode.position.row + direction.row, currentNode.position.col) ||
                            !getCollision(currentNode.position.row, currentNode.position.col + direction.col)
                    )
         ) continue

         val newG = currentNode.g + if (isDiagonal) 1.414 else 1.0 // 1.414 is sqrt(2)
         val newH = (Math.abs(end.row - newRow) + Math.abs(end.col - newCol)).toDouble()
         val newF = newG + newH

         if (closedList.any { it.position == IndexPair(newRow, newCol) && it.f <= newF }) continue

         val existingNode = openList.find { it.position == IndexPair(newRow, newCol) }
         if (existingNode == null || existingNode.f > newF) {
            openList.remove(existingNode)
            openList.add(Node(IndexPair(newRow, newCol), currentNode, newG, newH, newF))
         }
      }
   }

   return emptyList()
}



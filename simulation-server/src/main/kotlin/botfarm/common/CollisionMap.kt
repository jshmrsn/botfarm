package botfarm.common

import botfarmshared.engine.apidata.EntityId
import botfarmshared.misc.Vector2
import kotlin.math.roundToInt


class CollisionMap(
   val rowCount: Int,
   val columnCount: Int,
   val cellWidth: Double,
   val cellHeight: Double
) {
   companion object {
      val defaultMaxSearchOffset = 15
   }

   class OccupyingEntity(
      val occupyingCellsIndices: List<IndexPair>
   )

   class Cell {
      private val occupyingEntityIds = mutableSetOf<EntityId>()

      var isOpen: Boolean = true
         private set

      fun addOccupyingEntityId(entityId: EntityId) {
         this.occupyingEntityIds.add(entityId)
         this.isOpen = false
      }

      fun clearOccupyingEntityId(entityId: EntityId) {
         this.occupyingEntityIds.remove(entityId)
         this.isOpen = this.occupyingEntityIds.isEmpty()
      }
   }

   val bounds = Vector2(
      x = this.columnCount.toDouble() * this.cellWidth,
      y = this.rowCount.toDouble() * this.cellHeight
   )

   private val collisionMapColumnsByRow: List<List<Cell>>
   private val entityCollisionStatesByEntityId = mutableMapOf<EntityId, OccupyingEntity>()

   init {
      val collisionMap = mutableListOf<List<Cell>>()

      for (rowIndex in 0..(this.rowCount - 1)) {
         val row = (0..(this.columnCount - 1)).map { Cell() }
         collisionMap.add(row)
      }

      this.collisionMapColumnsByRow = collisionMap
   }


   fun indexPairToCellCenter(indexPair: IndexPair): Vector2 {
      val tileWidth = this.cellWidth
      val tileHeight = this.cellHeight

      return Vector2(
         x = tileWidth * (indexPair.col + 0.5),
         y = tileHeight * (indexPair.row + 0.5)
      )
   }

   fun indexPairToCellTopLeftCorner(indexPair: IndexPair): Vector2 {
      val tileWidth = this.cellWidth
      val tileHeight = this.cellHeight

      return Vector2(
         x = tileWidth * (indexPair.col),
         y = tileHeight * (indexPair.row)
      )
   }

   fun indexPairToCellBottomRightCorner(indexPair: IndexPair): Vector2 {
      val tileWidth = this.cellWidth
      val tileHeight = this.cellHeight

      return Vector2(
         x = tileWidth * (indexPair.col + 1),
         y = tileHeight * (indexPair.row + 1)
      )
   }

   fun pointToNearestCorner(point: Vector2): Vector2 {
      return Vector2(
         x = (point.x / this.cellWidth).roundToInt() * this.cellWidth,
         y = (point.y / this.cellHeight).roundToInt() * this.cellHeight
      )
   }

   fun pointToNearestCellCenter(point: Vector2): Vector2 {
      return Vector2(
         x = ((point.x / this.cellWidth).toInt() + 0.5) * this.cellWidth,
         y = ((point.y / this.cellHeight).toInt() + 0.5) * this.cellHeight
      )
   }

   fun pointToIndexPair(point: Vector2): IndexPair {
      return IndexPair(
         row = (point.y / this.cellHeight + 0.001).toInt(),
         col = (point.x / this.cellWidth + 0.001).toInt()
      )
   }

   fun clearEntity(entityId: EntityId) {
      val entityState = this.entityCollisionStatesByEntityId[entityId]
      this.entityCollisionStatesByEntityId.remove(entityId)

      if (entityState != null) {
         entityState.occupyingCellsIndices.forEach { occupyingCellsIndex ->
            val cell = this.getCell(
               row = occupyingCellsIndex.row,
               col = occupyingCellsIndex.col
            )

            cell.clearOccupyingEntityId(entityId)
         }
      }
   }

   private fun getCell(row: Int, col: Int): Cell {
      val columnsInRow = this.collisionMapColumnsByRow[row]
      return columnsInRow[col]
   }

   private fun getCellOrNull(row: Int, col: Int): Cell? {
      if (row < 0 || col < 0) {
         return null
      } else if (row >= this.rowCount) {
         return null
      } else if (col >= this.columnCount) {
         return null
      }

      val cellsInColumn = this.collisionMapColumnsByRow[row]
      return cellsInColumn[col]
   }

   fun isCellOpen(row: Int, col: Int): Boolean {
      return this.getCellOrNull(row = row, col = col)?.isOpen ?: false
   }

   fun isCellOpen(indexPair: IndexPair): Boolean {
      return this.isCellOpen(row = indexPair.row, col = indexPair.col)
   }

   fun addEntity(
      entityId: EntityId,
      topLeftRow: Int,
      topLeftCol: Int,
      width: Int,
      height: Int
   ) {
      this.clearEntity(entityId)

      val occupyingCellsIndices = mutableListOf<IndexPair>()

      for (row in (topLeftRow..<topLeftRow + height)) {
         for (col in (topLeftCol..<topLeftCol + width)) {
            val cell = this.getCellOrNull(row = row, col = col)
            cell?.addOccupyingEntityId(entityId)

            if (cell != null) {
               occupyingCellsIndices.add(IndexPair(row = row, col = col))
            }
         }
      }

      this.entityCollisionStatesByEntityId[entityId] = OccupyingEntity(
         occupyingCellsIndices = occupyingCellsIndices
      )
   }


   fun clampPoint(point: Vector2): Vector2 {
      return Vector2(
         x = Math.min(Math.max(point.x, 0.0), this.bounds.x),
         y = Math.min(Math.max(point.y, 0.0), this.bounds.y),
      )
   }

   fun clampIndexPair(indexPair: IndexPair): IndexPair {
      return IndexPair(
         row = Math.min(Math.max(indexPair.row, 0), this.rowCount - 1),
         col = Math.min(Math.max(indexPair.col, 0), this.columnCount - 1),
      )
   }

   fun findOpenCellBetween(startPoint: Vector2, endPoint: Vector2): IndexPair? {
      val attempts = Math.ceil(startPoint.distance(endPoint) / Math.min(this.cellWidth, this.cellHeight)).toInt() + 1

      for (attempt in (0..attempts)) {
         val percent = attempt / attempts.toDouble()
         val point = startPoint.lerp(endPoint, percent)

         val indexPair = this.clampIndexPair(this.pointToIndexPair(point))

         if (this.isCellOpen(indexPair)) {
            return indexPair
         }
      }

      return null
   }

   fun isAreaOpen(
      topLeftCorner: IndexPair,
      width: Int,
      height: Int
   ): Boolean {
      for (checkOffsetRow in 0..<height) {
         for (checkOffsetCol in 0..<width) {
            if (!this.isCellOpen(
                  row = topLeftCorner.row + checkOffsetRow,
                  col = topLeftCorner.col + checkOffsetCol
               )
            ) {
               return false
            }
         }
      }

      return true
   }

   fun findOpenTopLeftCellForShape(
      startIndexPair: IndexPair,
      maxSearchOffset: Int = Companion.defaultMaxSearchOffset,
      fitShapeWidth: Int = 1,
      fitShapeHeight: Int = 1
   ): IndexPair? {
      if (maxSearchOffset < 0) {
         throw Exception("maxSearchOffset must not be negative (was $maxSearchOffset)")
      }

      val clampedStartIndexPair = this.clampIndexPair(startIndexPair)

      fun areCellsUnderShapeOpen(indexPair: IndexPair): Boolean = this.isAreaOpen(
         topLeftCorner = indexPair,
         width = fitShapeWidth,
         height = fitShapeHeight
      )

      if (areCellsUnderShapeOpen(clampedStartIndexPair)) {
         return clampedStartIndexPair
      }

      for (searchOffset in 1..maxSearchOffset) {
         for (rowOffsetToCheck in listOf(-searchOffset, searchOffset)) {
            for (columnOffsetToCheck in -searchOffset..searchOffset) {
               val indexPairToCheck = IndexPair(
                  row = clampedStartIndexPair.row + rowOffsetToCheck,
                  col = clampedStartIndexPair.col + columnOffsetToCheck
               )

               if (areCellsUnderShapeOpen(indexPairToCheck)) {
                  return indexPairToCheck
               }
            }
         }

         for (columnOffsetToCheck in listOf(-searchOffset, searchOffset)) {
            val searchOffsetWithoutTopOrBottomRows = searchOffset - 1

            for (rowOffsetToCheck in -searchOffsetWithoutTopOrBottomRows..searchOffsetWithoutTopOrBottomRows) {
               val indexPairToCheck = IndexPair(
                  row = clampedStartIndexPair.row + rowOffsetToCheck,
                  col = clampedStartIndexPair.col + columnOffsetToCheck
               )

               if (areCellsUnderShapeOpen(indexPairToCheck)) {
                  return indexPairToCheck
               }
            }
         }
      }

      return null
   }

   fun findOpenTopLeftCellForShapeOrFallback(
      startIndexPair: IndexPair,
      maxSearchOffset: Int = Companion.defaultMaxSearchOffset,
      fitShapeWidth: Int = 1,
      fitShapeHeight: Int = 1
   ): IndexPair {
      return this.findOpenTopLeftCellForShape(
         startIndexPair = startIndexPair,
         maxSearchOffset = maxSearchOffset,
         fitShapeWidth = fitShapeWidth,
         fitShapeHeight = fitShapeHeight
      ) ?: this.clampIndexPair(startIndexPair)
   }

   fun findOpenCellCenterAround(
      startPoint: Vector2,
      searchRadius: Int = 6,
      fitShapeWidth: Int = 1,
      fitShapeHeight: Int = 1
   ): Vector2? {
      val startIndexPair = this.pointToIndexPair(startPoint)
      val indexPairResult = this.findOpenTopLeftCellForShape(
         startIndexPair = startIndexPair,
         maxSearchOffset = searchRadius,
         fitShapeWidth = fitShapeWidth,
         fitShapeHeight = fitShapeHeight
      ) ?: return null

      return this.indexPairToCellCenter(indexPairResult)
   }

   fun findOpenCellCenterAroundOrFallback(
      startPoint: Vector2,
      maxSearchOffset: Int = Companion.defaultMaxSearchOffset,
      fitShapeWidth: Int = 1,
      fitShapeHeight: Int = 1
   ): Vector2 {
      val startIndexPair = this.pointToIndexPair(startPoint)
      val indexPairResult = this.findOpenTopLeftCellForShapeOrFallback(
         startIndexPair = startIndexPair,
         maxSearchOffset = maxSearchOffset,
         fitShapeWidth = fitShapeWidth,
         fitShapeHeight = fitShapeHeight
      )

      return this.indexPairToCellCenter(indexPairResult)
   }

   fun findPath(startPoint: Vector2, endPoint: Vector2): List<IndexPair> {
      val startIndexPair = this.findOpenCellBetween(startPoint, endPoint)
      val endIndexPair = this.findOpenCellBetween(endPoint, startPoint)

      if (startIndexPair == null ||
         endIndexPair == null
      ) {
         return listOf()
      }

      if (endIndexPair.row == startIndexPair.row &&
         endIndexPair.col == startIndexPair.col
      ) {
         return listOf(endIndexPair)
      }

      return aStarPathfinding(
         collisionMap = this.collisionMapColumnsByRow,
         start = startIndexPair,
         end = endIndexPair
      )
   }
}
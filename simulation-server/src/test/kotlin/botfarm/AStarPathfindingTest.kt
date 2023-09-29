package botfarm

import botfarm.common.IndexPair
import botfarm.common.aStarPathfinding
import kotlin.test.Test
import kotlin.test.assertEquals

class AStarPathfindingTest {
   @Test
   fun testStraightPath() {
      val collisionMap = listOf(
         listOf(true, true, true),
         listOf(false, false, false),
         listOf(true, true, true)
      )
      val start = IndexPair(2, 0)
      val end = IndexPair(2, 2)
      val expectedPath = listOf(
         IndexPair(2, 1),
         IndexPair(2, 2)
      )
      assertEquals(expectedPath, aStarPathfinding(collisionMap, start, end))
   }

   @Test
   fun testPathWithObstacles() {
      val collisionMap = listOf(
         listOf(true, false, true),
         listOf(true, false, true),
         listOf(true, true, true)
      )
      val start = IndexPair(0, 0)
      val end = IndexPair(2, 2)
      val expectedPath = listOf(
         IndexPair(1, 0),
         IndexPair(2, 0),
         IndexPair(2, 1),
         IndexPair(2, 2)
      )
      assertEquals(expectedPath, aStarPathfinding(collisionMap, start, end))
   }

   @Test
   fun testDiagonalObstruction() {
      val collisionMap = listOf(
         listOf(true, true, true),
         listOf(true, true, true),
         listOf(false, true, true)
      )
      val start = IndexPair(0, 0)
      val end = IndexPair(2, 2)
      val expectedPath = listOf(
         IndexPair(1, 1),
         IndexPair(2, 2)
      )
      assertEquals(expectedPath, aStarPathfinding(collisionMap, start, end))
   }
}
package botfarm.common

import kotlinx.serialization.Serializable

@Serializable
class Tilemap(
   val compressionlevel: Int,
   val height: Int, // 100
   val infinite: Boolean, // false
   val layers: List<Layer>,
   val nextlayerid: Int,
   val nextobjectid: Int,
   val orientation: String, // orthogonal
   val renderorder: String, // right-down
   val tiledversion: String, // 1.8.6
   val tileheight: Int, // 32
   val tilesets: List<Tileset>,
   val tilewidth: Int, // 32
   val type: String, // "map"
   val version: String, // "1.8"
   val width: Int // 140
) {
   @Serializable
   class Layer(
      val data: List<Long>, // 2684369381
      val height: Int, // 100
      val id: Int, // 1
      val name: String, // Bottom Ground
      val opacity: Double,
      val type: String, // tilelayer
      val visible: Boolean,
      val width: Int, // 140 (observed as per-row count)
      val x: Int, // 0
      val y: Int // 0
   )

   @Serializable
   class Tileset(
      val columns: Int, // 16
      val firstgid: Int, // 1, 257, 513, 769, 9053, 9309, 9565, 9821, 10077, 10333, 10589, 15597
      val image: String, // "map_assets\/cute_rpg_word_VXAce\/tilesets\/CuteRPG_Desert_B.png"
      val imageheight: Int,  // 512
      val imagewidth: Int, // 512
      val margin: Int, // 0
      val name: String, // CuteRPG_Desert_B
      val spacing: Int, // 0
      val tilecount: Int, // 256
      val tileheight: Int, // 32
      val tilewidth: Int, // 32
      val transparentcolor: String // "#ff00ff"
   )
}
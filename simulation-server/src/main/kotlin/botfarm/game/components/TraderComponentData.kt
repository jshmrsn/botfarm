package botfarm.game.components

import botfarm.engine.simulation.EntityComponentData
import botfarmshared.engine.apidata.EntityId
import botfarmshared.game.apidata.ItemCollection
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class TradeId(val value: String)

@JvmInline
@Serializable
value class TradeVersion(val value: Int)

data class TradeProposal(
   val version: TradeVersion = TradeVersion(1),
   val startedBySelf: Boolean,
   val targetEntityId: EntityId,
   val tradeId: TradeId,
   val outboundOffering: ItemCollection = ItemCollection(),
   val hasApprovedInboundOffering: Boolean = false
)

data class TraderComponentData(
   val activeTradeProposal: TradeProposal?
) : EntityComponentData()

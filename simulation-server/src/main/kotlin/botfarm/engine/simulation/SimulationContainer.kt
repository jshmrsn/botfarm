package botfarm.engine.simulation

import botfarmshared.engine.apidata.EntityId
import botfarmshared.engine.apidata.SimulationId
import botfarmshared.misc.buildShortRandomString
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import java.util.concurrent.Executors
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

class TickSystemContext(
   val simulation: Simulation,
   val entity: Entity,
   val deltaTime: Double
)

class CoroutineSystemContext(
   val entity: Entity,
   val simulation: Simulation,
   val simulationContainer: SimulationContainer
) {
   fun synchronize(logic: () -> Unit) {
      synchronized(this.simulationContainer) {
         logic()
      }
   }
}

open class RegisteredTickSystem(
   val checkEntity: (Entity) -> ((TickSystemContext) -> Unit)?
)

open class RegisteredCoroutineSystem(
   val checkEntity: (Entity) -> (suspend (CoroutineSystemContext) -> Unit)?
)

class TickSystem(
   val registeredTickSystem: RegisteredTickSystem,
   val simulation: Simulation
) {
   class Entry(
      val entity: Entity,
      val callback: (TickSystemContext) -> Unit
   )

   private val entries = mutableListOf<Entry>()

   fun activateForEntity(
      entity: Entity
   ) {
      val callback = this.registeredTickSystem.checkEntity(entity)

      if (callback != null) {
         this.entries.add(
            Entry(
               entity = entity,
               callback = callback
            )
         )
      }
   }

   fun deactivateForEntity(entity: Entity) {
      this.entries.removeIf { it.entity == entity }
   }

   fun tick(deltaTime: Double) {
      this.entries.forEach {
         val tickContext = TickSystemContext(
            simulation = this.simulation,
            entity = it.entity,
            deltaTime = deltaTime
         )

         try {
            it.callback(tickContext)
         } catch (exception: Exception) {
            println("Exception in tick system: ${exception.stackTraceToString()}")
         }
      }
   }
}

class CoroutineSystem(
   val simulation: Simulation,
   val registeredCoroutineSystem: RegisteredCoroutineSystem
) {
   class Entry(
      val entity: Entity,
      val job: Job
   )

   private val entries = mutableListOf<Entry>()

   fun activateForEntity(
      entity: Entity,
      simulationContainer: SimulationContainer
   ) {
      val callback = this.registeredCoroutineSystem.checkEntity(entity)

      if (callback != null) {
         val simulation = this.simulation

         val job = CoroutineScope(simulationContainer.coroutineDispatcher).launch {
            try {
               callback(
                  CoroutineSystemContext(
                     entity = entity,
                     simulation = simulation,
                     simulationContainer = simulationContainer
                  )
               )
            } catch (exception: Exception) {
               println("Exception in coroutine system logic for entity: ${entity.entityId}\nException was : $exception")
            }
         }

         this.entries.add(
            Entry(
               entity = entity,
               job = job
            )
         )
      }
   }

   fun handleTermination() {
      this.entries.forEach {
         it.job.cancel()
      }

      this.entries.clear()
   }

   fun deactivateForEntity(entity: Entity) {
      this.entries.removeIf {
         if (it.entity == entity) {
            it.job.cancel()
            true
         } else {
            false
         }
      }
   }
}

class Systems {
   companion object {
      val default = Systems()
   }

   private val mutableRegisteredTickSystems = mutableListOf<RegisteredTickSystem>()
   private val mutableRegisteredCoroutineSystems = mutableListOf<RegisteredCoroutineSystem>()

   val registeredTickSystems: List<RegisteredTickSystem> = this.mutableRegisteredTickSystems
   val registeredCoroutineSystems: List<RegisteredCoroutineSystem> = this.mutableRegisteredCoroutineSystems

   fun <COMPONENT_1 : EntityComponentData> registerCoroutineSystemInternal(
      component1Type: KClass<COMPONENT_1>,
      coroutineLogic: suspend (
         tickContext: CoroutineSystemContext,
         component1: EntityComponent<COMPONENT_1>
      ) -> Unit
   ) {
      this.mutableRegisteredCoroutineSystems.add(RegisteredCoroutineSystem { entity ->
         val component1 = entity.getComponentOrNull(component1Type)

         if (component1 != null) {
            { tickContext -> coroutineLogic(tickContext, component1) }
         } else null
      })
   }

   fun <COMPONENT_1 : EntityComponentData, COMPONENT_2 : EntityComponentData> registerCoroutineSystemInternal(
      component1Type: KClass<COMPONENT_1>,
      component2Type: KClass<COMPONENT_2>,
      coroutineLogic: suspend (
         tickContext: CoroutineSystemContext,
         component1: EntityComponent<COMPONENT_1>,
         component2: EntityComponent<COMPONENT_2>
      ) -> Unit
   ) {
      this.mutableRegisteredCoroutineSystems.add(RegisteredCoroutineSystem { entity ->
         val component1 = entity.getComponentOrNull(component1Type)
         val component2 = entity.getComponentOrNull(component2Type)

         if (component1 != null && component2 != null) {
            { tickContext -> coroutineLogic(tickContext, component1, component2) }
         } else null
      })
   }

   fun <COMPONENT_1 : EntityComponentData, COMPONENT_2 : EntityComponentData, COMPONENT_3 : EntityComponentData> registerCoroutineSystemInternal(
      component1Type: KClass<COMPONENT_1>,
      component2Type: KClass<COMPONENT_2>,
      component3Type: KClass<COMPONENT_3>,
      coroutineLogic: suspend (
         tickContext: CoroutineSystemContext,
         component1: EntityComponent<COMPONENT_1>,
         component2: EntityComponent<COMPONENT_2>,
         component3: EntityComponent<COMPONENT_3>
      ) -> Unit
   ) {
      this.mutableRegisteredCoroutineSystems.add(RegisteredCoroutineSystem { entity ->
         val component1 = entity.getComponentOrNull(component1Type)
         val component2 = entity.getComponentOrNull(component2Type)
         val component3 = entity.getComponentOrNull(component3Type)

         if (component1 != null && component2 != null && component3 != null) {
            { tickContext -> coroutineLogic(tickContext, component1, component2, component3) }
         } else null
      })
   }

   inline fun <reified COMPONENT_1 : EntityComponentData> registerCoroutineSystem(
      noinline coroutineLogic: suspend (
         tickContext: CoroutineSystemContext,
         component1: EntityComponent<COMPONENT_1>
      ) -> Unit
   ) = this.registerCoroutineSystemInternal(COMPONENT_1::class, coroutineLogic)

   inline fun <reified COMPONENT_1 : EntityComponentData, reified COMPONENT_2 : EntityComponentData>
           registerCoroutineSystem2(
      noinline coroutineLogic: (
         tickContext: CoroutineSystemContext,
         component1: EntityComponent<COMPONENT_1>,
         component2: EntityComponent<COMPONENT_2>
      ) -> Unit
   ) = this.registerCoroutineSystemInternal(COMPONENT_1::class, COMPONENT_2::class, coroutineLogic)

   inline fun <reified COMPONENT_1 : EntityComponentData, reified COMPONENT_2 : EntityComponentData, reified COMPONENT_3 : EntityComponentData>
           registerCoroutineSystem3(
      noinline coroutineLogic: (
         tickContext: CoroutineSystemContext,
         component1: EntityComponent<COMPONENT_1>,
         component2: EntityComponent<COMPONENT_2>,
         component3: EntityComponent<COMPONENT_3>
      ) -> Unit
   ) = this.registerCoroutineSystemInternal(COMPONENT_1::class, COMPONENT_2::class, COMPONENT_3::class, coroutineLogic)

   fun <COMPONENT_1 : EntityComponentData> registerTickSystemInternal(
      component1Type: KClass<COMPONENT_1>,
      tickLogic: (
         tickContext: TickSystemContext,
         component1: EntityComponent<COMPONENT_1>
      ) -> Unit
   ) {
      this.mutableRegisteredTickSystems.add(RegisteredTickSystem { entity ->
         val component1 = entity.getComponentOrNull(component1Type)

         if (component1 != null) {
            { tickContext -> tickLogic(tickContext, component1) }
         } else null
      })
   }

   fun <COMPONENT_1 : EntityComponentData, COMPONENT_2 : EntityComponentData> registerTickSystemInternal(
      component1Type: KClass<COMPONENT_1>,
      component2Type: KClass<COMPONENT_2>,
      tickLogic: (
         tickContext: TickSystemContext,
         component1: EntityComponent<COMPONENT_1>,
         component2: EntityComponent<COMPONENT_2>
      ) -> Unit
   ) {
      this.mutableRegisteredTickSystems.add(RegisteredTickSystem { entity ->
         val component1 = entity.getComponentOrNull(component1Type)
         val component2 = entity.getComponentOrNull(component2Type)

         if (component1 != null && component2 != null) {
            { tickContext -> tickLogic(tickContext, component1, component2) }
         } else null
      })
   }


   inline fun <reified COMPONENT_1 : EntityComponentData> registerTickSystem(
      noinline tickLogic: (
         context: TickSystemContext,
         component1: EntityComponent<COMPONENT_1>
      ) -> Unit
   ) = this.registerTickSystemInternal(component1Type = COMPONENT_1::class, tickLogic)

   inline fun <reified COMPONENT_1 : EntityComponentData, reified COMPONENT_2 : EntityComponentData> registerTickSystem2(
      noinline tickLogic: (
         tickContext: TickSystemContext,
         component1: EntityComponent<COMPONENT_1>,
         component2: EntityComponent<COMPONENT_2>
      ) -> Unit
   ) = this.registerTickSystemInternal(COMPONENT_1::class, COMPONENT_2::class, tickLogic)
}

@Serializable
abstract class EntityComponentData

@Serializable
data class EntityData(
   val entityId: EntityId = EntityId(buildShortRandomString()),
   val components: List<EntityComponentData>
) {
   fun <T : EntityComponentData> getComponentOrNull(componentType: KClass<T>): T? {
      val untypedResult = this.components.find {
         it::class.isSubclassOf(componentType)
      }

      if (untypedResult != null) {
         @Suppress("UNCHECKED_CAST")
         return untypedResult as T
      } else {
         return null
      }
   }

   inline fun <reified T : EntityComponentData> getComponentOrNull(): T? {
      return this.getComponentOrNull(T::class)
   }

   fun <T : EntityComponentData> getComponent(componentType: KClass<T>): T {
      return this.getComponentOrNull(componentType)
         ?: throw Exception("Entity data does not have component for type: $componentType")
   }

   inline fun <reified T : EntityComponentData> getComponent(): T {
      return this.getComponent<T>(T::class)
   }
}


data class ClientSimulationData(
   val simulationId: SimulationId,
   val configs: List<Config>,
   val entities: List<EntityData>,
   val simulationTime: Double
)

class StartContext(
   val simulationContainer: SimulationContainer
)

class SimulationContainer {
   val threadPool = Executors.newCachedThreadPool()
   val coroutineDispatcher: ExecutorCoroutineDispatcher = this.threadPool.asCoroutineDispatcher()

   private val simulations = mutableListOf<Simulation>()

   @Serializable
   class CreateSimulationResult(
      val simulationId: SimulationId
   )

   fun addSimulation(simulation: Simulation): CreateSimulationResult {
      val simulationId = simulation.simulationId

      this.simulations.add(simulation)

      val startContext = StartContext(
         simulationContainer = this
      )

      simulation.start(startContext)
      return CreateSimulationResult(
         simulationId = simulationId
      )
   }

   fun tick() {
      this.simulations.filter {
         val tickResult = it.tick()

         tickResult.shouldKeep
      }
   }


   @Serializable
   class ListSimulationsResult(
      val simulations: List<Entry>
   ) {
      @Serializable
      class Entry(
         val simulationId: SimulationId
      )
   }

   fun listSimulations(): ListSimulationsResult {
      return ListSimulationsResult(
         simulations = this.simulations.map {
            ListSimulationsResult.Entry(
               simulationId = it.simulationId
            )
         }
      )
   }

   fun getSimulation(simulationId: SimulationId): Simulation? {
      return this.simulations.find { it.simulationId == simulationId }
   }

   @Serializable
   class TerminateSimulationResult(
      val success: Boolean
   )

   fun terminateSimulation(simulationId: SimulationId): TerminateSimulationResult {
      val simulation = this.getSimulation(simulationId)

      if (simulation != null) {
         simulation.handleTermination()
         this.simulations.remove(simulation)

         return TerminateSimulationResult(success = true)
      } else {
         return TerminateSimulationResult(success = false)
      }
   }
}

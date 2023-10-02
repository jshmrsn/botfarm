package botfarm.game

import botfarm.engine.luautils.setJvmFunction
import org.classdump.luna.StateContext
import org.classdump.luna.Table
import org.classdump.luna.Variable
import org.classdump.luna.compiler.CompilerChunkLoader
import org.classdump.luna.env.RuntimeEnvironments
import org.classdump.luna.exec.CallException
import org.classdump.luna.exec.CallPausedException
import org.classdump.luna.exec.DirectCallExecutor
import org.classdump.luna.impl.StateContexts
import org.classdump.luna.lib.StandardLibrary
import org.classdump.luna.load.ChunkLoader
import org.classdump.luna.runtime.LuaFunction

fun luaDemo() {
   val program = """
      while true do
         local result = javaversion('hello', null, {0, 0, 0})
         print( 'Got result from JVM: ' .. tostring(result))
      end
      """.trimIndent()

   val state: StateContext = StateContexts.newDefaultInstance()
   val env: Table = StandardLibrary.`in`(RuntimeEnvironments.system()).installInto(state)

   env.setJvmFunction("javaversion") {
      it.getString(0).length.toString() + " " + it.getTable(2).rawlen()
   }

   val loader: ChunkLoader = CompilerChunkLoader.of("hello_world")
   val main: LuaFunction = loader.loadTextChunk(Variable(env), "hello", program)

   val executor = DirectCallExecutor.newExecutorWithTickLimit(10000)

   try {
      executor.call(state, main)
   } catch (callException: CallException) {
      println("call exec error: " + callException.cause?.stackTraceToString())
   } catch (callPausedException: CallPausedException) {
      println("Call was paused: " + callPausedException.continuation)
   } catch (exception: Exception) {
      println("error")
   }
}
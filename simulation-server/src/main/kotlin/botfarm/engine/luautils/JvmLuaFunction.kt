package botfarm.engine.luautils

import org.classdump.luna.Table
import org.classdump.luna.impl.NonsuspendableFunctionException
import org.classdump.luna.runtime.ExecutionContext
import org.classdump.luna.runtime.LuaFunction

class JvmLuaFunction(
   val implementation: (JvmLuaInputs) -> Any?
) : LuaFunction() {
   override fun invoke(context: ExecutionContext, arg1: Any?) {
      this.dispatchImplementation(context, arrayOf(arg1))
   }

   override fun invoke(context: ExecutionContext, arg1: Any?, arg2: Any?) {
      this.dispatchImplementation(context, arrayOf(arg1, arg2))
   }

   override fun invoke(context: ExecutionContext, arg1: Any?, arg2: Any?, arg3: Any?) {
      this.dispatchImplementation(context, arrayOf(arg1, arg2, arg3))
   }

   override fun invoke(context: ExecutionContext, arg1: Any?, arg2: Any?, arg3: Any?, arg4: Any?) {
      this.dispatchImplementation(context, arrayOf(arg1, arg2, arg3, arg4))
   }

   override fun invoke(context: ExecutionContext, arg1: Any?, arg2: Any?, arg3: Any?, arg4: Any?, arg5: Any?) {
      this.dispatchImplementation(context, arrayOf(arg1, arg2, arg3, arg4, arg5))
   }

   fun dispatchImplementation(context: ExecutionContext, args: Array<Any?>) {
      val result = this.implementation(JvmLuaInputs(args))

      if (result != null && result !is Unit) {
         context.returnBuffer.setTo(result)
      } else {
         context.returnBuffer.setTo()
      }
   }

   override fun invoke(context: ExecutionContext) {
      this.dispatchImplementation(context, arrayOf())
   }

   override fun invoke(context: ExecutionContext, args: Array<Any?>) {
      this.dispatchImplementation(context, args)
   }

   override fun resume(context: ExecutionContext, suspendedState: Any?) {
      throw NonsuspendableFunctionException()
   }
}


class JvmLuaInputs(
   val args: Array<Any?>
) {
   inline fun <reified T> getOrNull(index: Int): T? {
      if (index < 0) {
         throw Exception("Index should not be negative")
      }

      val result = this.args.getOrNull(index) ?: return null
      return result as? T ?: throw Exception("Failed to cast result from Lua at index $index from ${result::class.qualifiedName} to ${T::class.qualifiedName} ($result)")
   }

   inline fun <reified T> get(index: Int): T {
      if (index < 0) {
         throw Exception("Index should not be negative")
      }

      if (index >= this.args.size) {
         throw Exception("No input from Lua at index $index")
      }

      val result = this.args.getOrNull(index) ?: throw Exception("Input from Lua at index $index was nil")
      return result as? T ?: throw Exception("Failed to cast result from Lua at index $index from ${result::class.qualifiedName} to ${T::class.qualifiedName} ($result)")
   }

   fun getString(index: Int): String {
      // Got org.classdump.luna.ArrayByteString
      return this.get<Any>(index).toString()
   }

   fun getStringOrNull(index: Int): String? {
      return this.getOrNull<String>(index)
   }

   fun getLong(index: Int): Long {
      return this.get<Number>(index).toLong()
   }

   fun getInt(index: Int): Int {
      return this.get<Number>(index).toInt()
   }

   fun getDouble(index: Int): Double {
      return this.get<Number>(index).toDouble()
   }

   fun getDoubleOrNull(index: Int): Double? {
      return this.getOrNull<Number>(index)?.toDouble()
   }

   fun getBoolean(index: Int): Boolean {
      return this.get<Boolean>(index)
   }

   fun getTable(index: Int): Table {
      return this.get<Table>(index)
   }
}

fun jvmLuaFunction(implementation: (JvmLuaInputs) -> Any?): JvmLuaFunction {
   return JvmLuaFunction(
      implementation = implementation
   )
}

fun Table.setJvmFunction(name: String, implementation: (JvmLuaInputs) -> Any?) {
   this.rawset(name, jvmLuaFunction(
      implementation = implementation
   ))
}

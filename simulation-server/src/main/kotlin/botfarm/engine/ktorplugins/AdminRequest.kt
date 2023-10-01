package botfarm.engine.ktorplugins

import botfarmshared.misc.getCurrentUnixTimeSeconds
import botfarmshared.misc.sha256
import kotlinx.serialization.Serializable
import kotlin.math.absoluteValue
import kotlin.math.roundToInt


@Serializable
class AdminRequest(
   val serverAdminSecret: String
) {
   companion object {
      private val serverAdminSecret: String? = System.getenv("BOTFARM_ADMIN_SECRET")?.trim()

      val serverHasAdminSecret = !serverAdminSecret.isNullOrBlank()

      // jshmrsn: Very basic security to avoid the public demo from burning through funded OpenAI accounts
      fun shouldGiveRequestAdminCapabilities(adminRequest: AdminRequest?): Boolean {
         val serverAdminSecret = this.serverAdminSecret

         if (serverAdminSecret.isNullOrBlank()) {
            return true
         }

         if (adminRequest == null) {
            return false
         }

         if (serverAdminSecret == adminRequest.serverAdminSecret) {
            return true
         } else {
            throw Exception("Admin signature did not match expected")
         }
      }
   }
}
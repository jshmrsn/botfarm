package botfarm.engine.ktorplugins

import kotlinx.serialization.Serializable

@Serializable
class AdminRequest(
   val serverAdminSecret: String
) {
   companion object {
      val serverAdminSecret: String? = ServerEnvironmentGlobals.adminSecret
      val serverHasAdminSecret = ServerEnvironmentGlobals.hasAdminSecret

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
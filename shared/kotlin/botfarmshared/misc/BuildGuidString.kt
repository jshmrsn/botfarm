package botfarmshared.misc

import java.util.*

fun buildShortRandomIdentifier(): String = UUID.randomUUID().toString().substring(0, 8).uppercase()
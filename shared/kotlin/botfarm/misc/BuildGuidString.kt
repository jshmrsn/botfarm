package botfarm.misc

import java.util.*

fun buildShortRandomString(): String = UUID.randomUUID().toString().substring(0, 8).uppercase()
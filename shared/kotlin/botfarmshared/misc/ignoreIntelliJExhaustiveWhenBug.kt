package botfarmshared.misc

fun ignoreIntelliJExhaustiveWhenBug(): Nothing = throw Exception("IntelliJ 2023.2.1 seems to have a bug where sometimes it'll think when statements are not exhaustive unless they have an else statement")
package coreMindustry.lib

import arc.util.CommandHandler
import arc.util.Log
import arc.util.Strings
import cf.wayzer.scriptAgent.Config
import cf.wayzer.scriptAgent.util.DSLBuilder
import coreLibrary.lib.*
import mindustry.Vars.netServer
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Iconc
import mindustry.gen.Player

object ContentHelper {
    fun logToConsole(text: String) = logToConsole(text.with())
    fun logToConsole(text: PlaceHoldString) {
        val parsed = "{text}".with(
            "text" to text,
            "receiver" to "console", "receiver.colorHandler" to Color::convertToAnsiCode
        ).toString()
        Log.info(Strings.stripColors(ColorApi.handle(parsed, ColorApi::consoleColorHandler)))
    }

    fun mindustryColorHandler(color: ColorApi.Color): String {
        if (color is ConsoleColor) {
            return when (color) {
                ConsoleColor.LIGHT_YELLOW -> "[gold]"
                ConsoleColor.LIGHT_PURPLE -> "[magenta]"
                ConsoleColor.LIGHT_RED -> "[scarlet]"
                ConsoleColor.LIGHT_CYAN -> "[cyan]"
                ConsoleColor.LIGHT_GREEN -> "[acid]"
                else -> "[${color.name}]"
            }
        }
        return ""
    }
}

enum class MsgType { Message, InfoMessage, InfoToast, WarningToast, Announce }

fun broadcast(
    text: PlaceHoldString,
    type: MsgType = MsgType.Message,
    time: Float = 10f,
    quite: Boolean = false,
    players: Iterable<Player> = Groups.player
) {
    if (!quite) ContentHelper.logToConsole(text)
    MindustryDispatcher.runInMain {
        players.forEach {
            if (it.con != null)
                it.sendMessage(text, type, time)
        }
    }
}

fun Player?.sendMessage(text: PlaceHoldString, type: MsgType = MsgType.Message, time: Float = 10f) {
    if (this == null) ContentHelper.logToConsole(text.toString())
    else {
        if (con == null) return
        MindustryDispatcher.runInMain {
            val msg = text.toPlayer(this)
            when (type) {
                MsgType.Message -> Call.sendMessage(this.con, msg, null, null)
                MsgType.InfoMessage -> Call.infoMessage(this.con, msg)
                MsgType.InfoToast -> Call.infoToast(this.con, msg, time)
                MsgType.WarningToast -> Call.warningToast(this.con, Iconc.warning.code, msg)
                MsgType.Announce -> Call.announce(this.con, msg)
            }
        }
    }
}

fun PlaceHoldString.toPlayer(player: Player): String = ColorApi.handle(
    "{text}".with("text" to this, "player" to player, "receiver" to player).toString(),
    ContentHelper::mindustryColorHandler
)

fun Player?.sendMessage(text: String, type: MsgType = MsgType.Message, time: Float = 10f) =
    sendMessage(text.with(), type, time)

val Config.clientCommands by DSLBuilder.dataKeyWithDefault {
    netServer?.clientCommands ?: CommandHandler("/")
}
val Config.serverCommands by DSLBuilder.dataKeyWithDefault {
    println("serverCommands Not exists")
    CommandHandler("")
}
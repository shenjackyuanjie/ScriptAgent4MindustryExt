package coreMindustry.lib

import cf.wayzer.scriptAgent.define.Script
import cf.wayzer.scriptAgent.define.ScriptDsl
import coreLibrary.lib.CommandHandler
import coreLibrary.lib.CommandInfo
import mindustry.gen.Player

/**
 * 注册指令
 * 所有body将在 Dispatchers.game下调用, 费时操作请注意launch并切换Dispatcher
 */
@ScriptDsl
fun Script.command(name: String, description: String, init: CommandInfo.() -> Unit) {
    onEnable {
        RootCommands += CommandInfo(this, name, description, init)
    }
}

@Deprecated("use new command api", ReplaceWith("command(name,description){init\nbody(handler)}"))
fun Script.command(name: String, description: String, init: CommandInfo.() -> Unit, handler: CommandHandler) {
    command(name, description) {
        init()
        body(handler)
    }
}

//常见拼写错误，但不报错
@Suppress("unused")
@Deprecated(
    "请检查变量是否使用正确, Vars.player 为null",
    ReplaceWith("error(\"服务器中不允许使用该变量\")"),
    DeprecationLevel.ERROR
)
val Script.player: Player
    get() = error("服务器中不允许使用该变量")
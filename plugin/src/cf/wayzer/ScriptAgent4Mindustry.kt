package cf.wayzer

import arc.ApplicationListener
import arc.Core
import arc.util.CommandHandler
import arc.util.Log
import cf.wayzer.ConfigExt.clientCommands
import cf.wayzer.ConfigExt.serverCommands
import cf.wayzer.scriptAgent.*
import cf.wayzer.scriptAgent.define.LoaderApi
import kotlinx.coroutines.runBlocking
import mindustry.Vars
import mindustry.plugin.Plugin

class ScriptAgent4Mindustry : Plugin() {
    init {
        if (System.getProperty("java.util.logging.SimpleFormatter.format") == null)
            System.setProperty(
                "java.util.logging.SimpleFormatter.format",
                "[%1\$tF | %1\$tT | %4\$s] [%3\$s] %5\$s%6\$s%n"
            )
        @OptIn(LoaderApi::class)
        ScriptAgent.load()
    }

    override fun registerClientCommands(handler: CommandHandler) {
        Config.clientCommands = handler
    }

    override fun registerServerCommands(handler: CommandHandler) {
        Config.serverCommands = handler
    }

    @OptIn(LoaderApi::class)
    override fun init() {
        Config.rootDir = Vars.dataDirectory.child("scripts").file()
        ScriptRegistry.registries.add(JarScriptRegistry)
        ScriptRegistry.scanRoot()
        runBlocking {
            System.getenv("SAMain")?.let { id ->
                Log.info("发现环境变量SAMain=$id")
                val script = ScriptRegistry.getScriptInfo(id)
                    ?: error("未找到脚本$id")
                Log.info("发现脚本$id,开始加载")
                ScriptManager.transaction {
                    add(script)
                    load();enable()
                }
            } ?: let {
                ScriptManager.transaction {
                    addAll()
                    load();enable()
                }
            }
        }
        Core.app.addListener(object : ApplicationListener {
            override fun pause() {
                if (Vars.headless)
                    exit()
            }

            override fun exit() {
                runBlocking {
                    ScriptManager.disableAll()
                }
            }
        })
        Log.info("&y===========================")
        Log.info("&lm&fb     ScriptAgent          ")
        Log.info("&b           By &cWayZer    ")
        Log.info("&b插件官网: https://git.io/SA4Mindustry")
        Log.info("&bQQ交流群: 1033116078")
        val all = ScriptRegistry.allScripts { true }
        if (all.isEmpty())
            Log.warn("&c未在config/scripts下发现脚本,请下载安装脚本包,以发挥本插件功能")
        else
            Log.info("&b共找到${all.size}脚本,加载成功${all.count { it.scriptState.loaded }},启用成功${all.count { it.scriptState.enabled }},出错${all.count { it.failReason != null }}")
        Log.info("&y===========================")
    }
}
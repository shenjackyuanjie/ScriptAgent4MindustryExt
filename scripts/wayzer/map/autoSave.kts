@file:Depends("coreMindustry/utilNext", "调用菜单")

package wayzer.map

import arc.Core
import arc.files.Fi
import arc.struct.ObjectIntMap
import arc.struct.StringMap
import coreLibrary.lib.CommandContext
import coreLibrary.lib.util.loop
import coreLibrary.lib.with
import coreMindustry.lib.RootCommands
import coreMindustry.lib.broadcast
import coreMindustry.lib.command
import coreMindustry.lib.player
import mindustry.Vars.state
import mindustry.core.GameState
import mindustry.gen.Player
import mindustry.io.SaveIO
import java.text.DecimalFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.logging.Level


name = "自动存档"

val autoSaveRange = 100 until 106
val autoSaveFindRange = 1 until 106
val menu = contextScript<coreMindustry.UtilNext>()
val playerpage: ObjectIntMap<String> = ObjectIntMap()//玩家个人翻页数
fun Player.getplayerpage(): Int {
    return playerpage.get(uuid())
}

fun Player.previousPage() {
    return playerpage.put(uuid(), playerpage.get(uuid()) - 6)
}

fun Player.nextPage() {
    return playerpage.put(uuid(), playerpage.get(uuid()) + 6)
}

command("slots", "列出保存的存档") {
    body {

        this.player?.Menu(this.player!!, this)
    }
}

fun slotInfo(page: Int): String {
    val list = autoSaveFindRange.map { it to SaveIO.fileFor(it) }.filter { it.second.exists() }.map { (id, file) ->
        "[red]{id}[]: [yellow]保存日期:{date:MM/dd HH:mm}".with("id" to id, "date" to file.lastModified().let(::Date))
    }
    val info: String = if (page >= list.size) {
        ""
    } else {
        list[page].toString()
    }
    return info
}

fun maxSlot(): Int {
    val list = autoSaveFindRange.map { it to SaveIO.fileFor(it) }.filter { it.second.exists() }.map { (id, file) ->
        "[red]{id}[]: [yellow]保存日期:{date:MM/dd HH:mm}".with("id" to id, "date" to file.lastModified().let(::Date))
    }
    val last = list.size
    return last
}

fun maxPage(): Int {
    val add = (maxSlot() % 6)
    val maxPage = if(add == 0){
        maxSlot() / 6
    }else{
        (maxSlot() / 6) + 1
    }
    return maxPage
}


suspend fun Player.Menu(player: Player, context: CommandContext) {
    menu.sendMenuBuilder<Unit>(
        this, 30_000, "[yellow]存档列表",
        """
            [sky]查询服务器存档列表
            [acid]点击即可发起回档投票
        """.trimIndent()
    ) {
        for (page in 0 + getplayerpage()..5 + getplayerpage()) {
            this += listOf(slotInfo(page) to {
                if (slotInfo(page) == "") {
                    Menu(player, context)
                } else {
                    moreMenu(player, page, context)
                }
            })
        }
        this += listOf(
            "[acid][上一页]" to {
                if (getplayerpage() >= 6) {
                    previousPage()
                    Menu(player, context)
                } else {
                    sendMessage("[red]没有上一页了")
                    Menu(player, context)
                }
            },
            "[acid]${(getplayerpage() + 6) / 6}/${maxPage()}" to { Menu(player, context) },
            "[acid][下一页]" to {
                if (getplayerpage() <= maxSlot() - 7) {
                    nextPage()
                    Menu(player, context)
                } else {
                    sendMessage("[red]没有下一页了")
                    Menu(player, context)
                }
            }
        )
        this += listOf(
            "[scarlet][关闭]" to {},
            "[forest][回到首页]" to {
                playerpage.put(uuid(), 0)
                Menu(player, context)
            }
        )
    }
}

suspend fun Player.moreMenu(player: Player, page: Int, context: CommandContext) {
    menu.sendMenuBuilder<Unit>(
        this, 30_000, "[yellow]存档详情",
        """
            [sky]存档信息:
            [yellow]${slotInfo(page)}
            [scarlet]确定要回档吗？
        """.trimIndent()
    ) {
        this += listOf(
            "[red]返回" to {
                Menu(player, context)
            },
            "[green]投票回档:${page}" to {
                RootCommands.getSubCommands(context).values.toSet().filter {
                    (it.name == "vote")
                }.forEach {
                    context.arg = listOf("rollback", (page + 1).toString())
                    it.invoke(context)
                }
            }
        )
    }
}


val nextSaveTime: Date
    get() {//Every 10 minutes
        val t = Calendar.getInstance()
        t.set(Calendar.SECOND, 0)
        val mNow = t.get(Calendar.MINUTE)
        t.add(Calendar.MINUTE, (mNow + 10) / 10 * 10 - mNow)
        return t.time
    }

onEnable {
    loop {
        val nextTime = nextSaveTime.time
        delay(nextTime - System.currentTimeMillis())
        if (state.`is`(GameState.State.playing)) {
            val minute = ((nextTime / TimeUnit.MINUTES.toMillis(1)) % 60).toInt() //Get the minute
            Core.app.post {
                val id = autoSaveRange.first + minute / 10
                val tmp = Fi.tempFile("save")
                try {
                    val extTag = StringMap.of(
                        "name", "[回档$id]" + state.map.name(),
                        "description", state.map.description(),
                        "author", state.map.author(),
                    )
                    SaveIO.write(tmp, extTag)
                    tmp.moveTo(SaveIO.fileFor(id))
                } catch (e: Exception) {
                    logger.log(Level.SEVERE, "存档存档出错", e)
                    tmp.delete()
                }
                broadcast("[green]自动存档完成(整10分钟一次),存档号 [red]{id}".with("id" to id))
            }
        }
    }
}
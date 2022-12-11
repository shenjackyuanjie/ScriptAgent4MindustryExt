@file:Depends("coreMindustry")
@file:Depends("coreMindustry/utilNext", "调用菜单")
@file:Depends("wayzer/user/userService")
@file:Depends("wayzer/voteService")
@file:Depends("wayzer/maps", "怎么能不依赖一下呢")

package wayzer

import arc.struct.ObjectIntMap
import arc.util.serialization.Jval
import cf.wayzer.placehold.DynamicVar
import coreLibrary.lib.util.loop
import java.net.URL


name = "地图站列表获取"

val menu = contextScript<coreMindustry.UtilNext>()
val playerpage: ObjectIntMap<String> = ObjectIntMap()//玩家个人翻页数
val usedtimes: ObjectIntMap<String> = ObjectIntMap()//玩家使用频率
val maxusedtime by config.key(200, "每小时最大申请允许次数")
fun Player.getusedtimes():Int{
    return usedtimes.get(uuid())
}
fun Player.getplayerpage():Int {
    return playerpage.get(uuid())
}
fun Player.previousPage(){
    return playerpage.put(uuid(),playerpage.get(uuid()) - 6)
}
fun Player.nextPage(){
    return playerpage.put(uuid(),playerpage.get(uuid()) + 6)
}
var json: Jval.JsonArray? = null
var serverpage: Int = 0

fun modes(mode:String): String {
    when (mode){
        "Attack§warning" -> {return "攻击"}
        "Sandbox§warning" -> {return "沙盒"}
        "Survive§warning" -> {return "生存"}
        "Pvp§warning" -> {return "PVP"}
    }
    return "生存"
}

fun map(page:Int){
    try {
        val text = URL("https://mdt.wayzer.top/api/maps/list?begin=${(page.toString())}").readText()
        json = Jval.read(text).asArray()
        ///broadcast("进行一次请求$page${json?.size}".with())///请求cs用
    }catch (e: Throwable){
        broadcast("wz站拒绝了你的请求".with())
        broadcast(e.toString().with())
    }
}//防止高强度请求
fun mapinfo(page:Int):String{
    if(page !in serverpage..serverpage+15){ serverpage = (page/15)*15 ;map(serverpage) }
    val page2 = page%15
    val json = json?.get(page2)
    val name = json?.get("name").toString()
    val id = json?.get("id").toString()
    var mode = json?.get("tags")?.asArray()?.get(1).toString()
    mode = if (mode.endsWith("§warning")){
        mode
    }else{
        json?.get("tags")?.asArray()?.get(1).toString()
    }
    return "[sky]地图id:$id[cyan]\n[yellow]$name[green]-${modes(mode)}"
}
fun moremapinfo(page:Int):String{
    val page2 = page % 15
    val json = json?.get(page2)
    val name = json?.get("name").toString()
    val id = json?.get("id").toString()
    val tag = json?.get("tags")?.asArray().toString()
    var mode = json?.get("tags")?.asArray()?.get(1).toString()
    mode = if (mode.endsWith("§warning")){
        mode
    }else{
        json?.get("tags")?.asArray()?.get(1).toString()
    }
    val desc = json?.get("desc").toString()
    var version = json?.get("tags")?.asArray()?.get(2).toString()
    val size:String
    if (version.startsWith("v")){
        version = version.toString()
        size = json?.get("tags")?.asArray()?.get(3).toString()
    }else{
        version = json?.get("tags")?.asArray()?.get(1).toString()
        size = json?.get("tags")?.asArray()?.get(2).toString()
    }
    return """
        |[sky]地图id:$id
        |[yellow]$name[green]-${modes(mode)}
        |[yellow]$desc
        |[sky]游戏版本:$version
        |[cyan]地图大小:$size
        |""".trimMargin()
}
fun mapid(page: Int): String {
    val page2 = page % 15
    val json = json?.get(page2)
    return json?.get("id").toString()
}
command("maps", "查看网络图库") {
    usage = "[可选说明]"
    type = CommandType.Client
    aliases = listOf("查看地图")
    permission = "LookMaps"
    body{
        if(!(this.player?.admin())!!) {
            if ((this.player?.getusedtimes() ?: 0) >= maxusedtime) {
                returnReply("你在一个小时中使用了${this.player?.getusedtimes() ?: 0}次请在一个小时后再试".with())
            }
        }
        if ((this.player?.getusedtimes() ?: 0) >= maxusedtime) {
            this.player?.preMenu(this.player!!,this)
        }else {
            this.player?.Menu(this.player!!, this)
        }
        usedtimes.put(this.player?.uuid(),usedtimes.get(this.player?.uuid())+1)
    }
}
suspend fun Player.preMenu(player: Player, context: CommandContext) {
    menu.sendMenuBuilder<Unit>(
        this, 30_000, "[red]注意不要频繁使用",
        """
            wz站有几率拒绝你的高强度请求
            每小时每人只允许查看$maxusedtime 次
            在一小时中你已经查看了${getusedtimes()}次
        """.trimIndent()
    ) {
        add(listOf("[red]关闭" to {},
                   "[acid]确定" to {Menu(player,context)}))
    }
}
suspend fun Player.Menu(player: Player, context: CommandContext) {
    menu.sendMenuBuilder<Unit>(
        this, 30_000, "[yellow]资源站地图列表",
        """
            [sky]快捷查询资源站地图列表
            [acid]点击即可查看简介等更多信息!
        """.trimIndent()
    ) {
        for(page in 0+getplayerpage()..5+getplayerpage()){
            this += listOf(
                mapinfo(page) to {
                    moreMenu(player,page,context)
                }
            )
        }
        this += listOf(
            "[acid][上一页]" to {
                if(getplayerpage() >= 6) {
                    previousPage()
                    Menu(player,context)
                    usedtimes.put(uuid(),usedtimes.get(uuid())+1)
                } else {
                    sendMessage("[red]没有上一页了")
                    Menu(player,context)
                }
            },"[acid]第${(getplayerpage() + 6) / 6}页" to {
                Menu(player,context)
                usedtimes.put(uuid(),usedtimes.get(uuid())+1)
            },
            "[acid][下一页]" to {
                nextPage()
                Menu(player,context)
                usedtimes.put(uuid(),usedtimes.get(uuid())+1)
            }

        )
        this += listOf(
            "[scarlet][关闭]" to {},
            "[forest][回到首页]" to {
                playerpage.put(uuid(),0)
                Menu(player,context)
                usedtimes.put(uuid(),usedtimes.get(uuid())+1)
            },
            "[cyan]打开资源站\n查看更多地图" to {
                Call.openURI(player.con,"https://mdt.wayzer.top/v2/map")
            }
        )
    }
}

suspend fun Player.moreMenu(player: Player, page:Int,context: CommandContext) {
    menu.sendMenuBuilder<Unit>(
        this, 30_000, "[yellow]地图详情",
        """
            ${moremapinfo(page)}
        """.trimIndent()
    ){
        this += listOf(
            "[green]投票换图${mapid(page)}" to {
                RootCommands.getSubCommands(context).values.toSet().filter {
                    (it.name == "vote")
                }.forEach {
                    context.arg = listOf("map", mapid(page))
                    it.invoke(context)
                }
            },"[red]返回" to {
                Menu(player,context)
            }
        )
    }
}
registerVar("scoreBroad.ext.contentsVersion", "提示", DynamicVar.v {
    "[sky]输入\"/maps\"可以以ui直接查看资源站地图啦[]"
})
onEnable{
    launch{
        loop(Dispatchers.game){
            map(0)
            playerpage.clear()
            usedtimes.clear()
            delay(60_000*60)
        }//每小时进行一次重置
    }
}
///服务器重启数据没了咋办？谁会在意。反正我不在意
PermissionApi.registerDefault("LookMaps")

@file:Depends("wayzer/maps", "监测投票换图")
@file:Depends("wayzer/user/userService")

package wayzer.user.ext

import cf.wayzer.placehold.DynamicVar
import cf.wayzer.placehold.PlaceHoldApi
import coreLibrary.lib.util.loop
import mindustry.game.Gamemode
import mindustry.game.Team
import mindustry.gen.Groups
import mindustry.world.Block
import wayzer.MapChangeEvent
import wayzer.user.UserService
import java.io.Serializable
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.min

data class StatisticsData(
    var playedTime: Int = 0,
    var idleTime: Int = 0,
    var buildScore: Float = 0f,
    var breakBlock: Int = 0,
    @Transient var pvpTeam: Team = Team.sharded
) : Serializable {
    val win get() = state.rules.pvp && pvpTeam == teamWin

    //加权比分
    val score
        get() = playedTime - 0.8 * idleTime +
                0.6 * min(buildScore, 0.75f * playedTime) +
                if (win) 600 * (1 - idleTime / playedTime) else 0

    //结算经验计算
    val exp get() = min(ceil(score * 15 / 3600 * rate).toInt(), (60 * rate).toInt())//3600点积分为15,40封顶

    companion object {
        lateinit var teamWin: Team
        var rate = 1.0
    }
}

val Block.buildScore: Float
    get() {
        //如果有更好的建筑积分规则，请修改此处
        return buildCost / 60f //建筑时间(单位秒)
    }
val Player.active
    get() = depends("wayzer/user/ext/activeCheck")
        ?.import<(Player) -> Int>("inactiveTime")
        ?.let { it(this) < 5000 } ?: true

val userService = contextScript<UserService>()

data class Activity(val name: String, val rate: Double, val endTime: Long = 0)

val activity by config.key(Activity("无", 1.0), "活动设置")

@Savable
val statisticsData = mutableMapOf<String, StatisticsData>()
customLoad(::statisticsData) { statisticsData += it }
val Player.data get() = statisticsData.getOrPut(uuid()) { StatisticsData() }


registerVarForType<StatisticsData>().apply {
    registerChild("playedTime", "本局在线时间", DynamicVar.obj { Duration.ofSeconds(it.playedTime.toLong()) })
    registerChild("idleTime", "本局在线时间", DynamicVar.obj { Duration.ofSeconds(it.idleTime.toLong()) })
    registerChild("buildScore", "建筑积分") { _, obj, p ->
        if (!p.isNullOrBlank()) p.format(obj.buildScore)
        else obj.buildScore
    }
    registerChild("score", "综合得分", DynamicVar.obj { it.score })
    registerChild("breakBlock", "破坏方块数", DynamicVar.obj { it.breakBlock })
}
registerVarForType<Player>().apply {
    registerChild("statistics", "游戏统计数据", DynamicVar.obj { it.data })
}
onDisable {
    PlaceHoldApi.resetTypeBinder(StatisticsData::class.java)//局部类，防止泄漏
}

listen<EventType.ResetEvent> {
    statisticsData.clear()
}
listen<EventType.PlayerJoin> {
    it.player.data.pvpTeam = it.player.team()
}
listen<EventType.PlayEvent> {
    launch {
        delay(5000)
        Groups.player.forEach { p ->
            p.data.pvpTeam = p.team()
        }
    }
}

onEnable {
    loop {
        delay(1000)
        runCatching {
            Groups.player.forEach {
                it.data.playedTime++
                if (it.dead() || !it.active)
                    it.data.idleTime++
            }
        }
    }
}
listen<EventType.BlockBuildEndEvent> {
    it.unit?.player?.data?.apply {
        if (it.breaking)
            breakBlock++
        else
            buildScore += it.tile.block().buildScore
    }
}

listen<EventType.GameOverEvent> { event ->
    onGameOver(event.winner)
}

fun onGameOver(winner: Team) {
    val gameTime by PlaceHold.reference<Duration>("state.gameTime")
    if (state.rules.infiniteResources || state.rules.editor) {
        return broadcast(
            """
            [yellow]本局游戏时长: {gameTime:分钟}
            [yellow]沙盒或编辑器模式,不计算贡献
        """.trimIndent().with("gameTime" to gameTime)
        )
    }

    StatisticsData.teamWin = if (state.rules.mode() != Gamemode.survival) winner else Team.sharded
    var totalTime = 0
    val sortedData = statisticsData.filterValues { it.playedTime > 60 }
        .mapKeys { netServer.admins.getInfo(it.key) }
        .toList()
        .sortedByDescending { it.second.score }
    val list = sortedData.map { (player, data) ->
        totalTime += data.playedTime - data.idleTime
        "[white]{pvpState}{player.name}[white]({statistics.playedTime:分钟}/{statistics.idleTime:分钟}/{statistics.buildScore:%.1f})".with(
            "player" to player, "statistics" to data, "pvpState" to if (data.win) "[green][胜][]" else ""
        )
    }
    broadcast(
        """
        [yellow]本局游戏时长: {gameTime:分钟}
        [yellow]有效总贡献时长: {totalTime:分钟}
        [yellow]贡献排行榜(时长/挂机/建筑): {list}
    """.trimIndent().with("gameTime" to gameTime, "totalTime" to Duration.ofSeconds(totalTime.toLong()), "list" to list)
    )
    StatisticsData.rate = 1.0
    if (sortedData.isNotEmpty() && gameTime > Duration.ofMinutes(15)) {
        if (activity.endTime > System.currentTimeMillis()) {
            StatisticsData.rate = activity.rate
            broadcast(
                "[gold]{name} [green]活动加成 [gold]x{rate:%.1f}[],还剩{left:小时}".with(
                    "name" to activity.name, "rate" to activity.rate,
                    "left" to Duration.between(Instant.now(), Instant.ofEpochMilli(activity.endTime))
                )
            )
        }
        val map = mutableMapOf<PlayerProfile, StatisticsData>()
        sortedData.groupBy { PlayerData.findById(it.first.id)?.profile }.forEach { (key, value) ->
            if (key == null || value.isEmpty()) return@forEach
            map[key] = value.maxByOrNull { it.second.score }!!.second
        }
        launch(Dispatchers.IO) {
            map.forEach { (profile, data) ->
                userService.updateExp(profile, data.exp, "游戏结算")
            }
        }
        depends("wayzer/user/ext/rank")
            ?.import<(Map<PlayerProfile, Pair<Int, Boolean>>) -> Unit>("onGameOver")
            ?.invoke(map.mapValues { it.value.playedTime to it.value.win })
    }
    statisticsData.clear()
}
export(::onGameOver)//Need in Dispatchers.game
listenTo<MapChangeEvent>(Event.Priority.Before) {
    onGameOver(Team.derelict)
}
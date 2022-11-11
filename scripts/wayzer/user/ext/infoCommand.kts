package wayzer.user.ext

val template by config.key(
    """
    | [#DEA82A] {player.name} [#DEA82A]个人信息[]
    | [#2B60DE]=======================================[]
    | [green]用户名[]:{player.name}
    | [green]代表3位ID[white]:{player.shortID}
    | [green]最早进服时间[]:{player.ext.firstJoin:YYYY-MM-dd}
    | {profileInfo}
    | [#2B60DE]=======================================[]
""".trimMargin(), "个人信息模板"
)
val profileTemplate by config.key(
    """
    | [green]当前绑定账号[]:{profile.id}
    | [green]总在线时间[]:{profile.onlineTime:分钟}
    | [green]当前等级[]:{profile.levelIcon}{profile.level}
    | [green]当前经验(下一级所需经验)[]:{profile.totalExp}({profile.nextLevel})
    | [green]注册时间[]:{profile.registerTime:YYYY-MM-dd}
""".trimMargin(), "统一账号信息介绍"
)

command("info", "获取当前个人信息") {
    type = CommandType.Client
    aliases = listOf("个人信息")
    body {
        val profile = PlayerData[player!!.uuid()].profile
        val profileInfo = profile?.let {
            profileTemplate.with("profile" to it)
        } ?: """
        [yellow]当前未绑定账号,请私聊群机器人"绑定"进行绑定
        [yellow]绑定成功后,才能获取经验和使用更多功能
    """.trimIndent()
        reply(template.with("player" to player!!, "profileInfo" to profileInfo), MsgType.InfoMessage)
    }
}

command("mInfo", "获取用户信息") {
    usage = "[uid]"
    permission = "wayzer.info.other"
    body {
        if (arg.isEmpty()) returnReply("[red]请输入玩家uuid".with())
        val player = netServer.admins.getInfo(arg[0]) ?: returnReply("[red]玩家未找到".with())

        val data = PlayerData.findById(player.id) ?: returnReply("[red]玩家未找到".with())
        val profileInfo = data.profile?.let {
            profileTemplate.with("profile" to it)
        } ?: """
        [yellow]玩家未绑定账号
    """.trimIndent()
        reply(template.with("player" to player, "player.ext" to data, "profileInfo" to profileInfo))
    }
}
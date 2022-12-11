package wayzer.ext

import coreLibrary.lib.config
import coreLibrary.lib.with
import coreMindustry.lib.MsgType
import coreMindustry.lib.broadcast
import coreMindustry.lib.game
import java.time.Duration

val type by config.key(MsgType.InfoMessage, "发送方式")
val time by config.key(Duration.ofMinutes(10)!!,"公告间隔")
val list by config.key(emptyList<String>(),"公告列表,支持颜色和变量")

var i = 0
fun broadcast(){
    if(list.isEmpty())return
    i %= list.size
    broadcast(list[i].with(),type,15f)
    i++
}

onEnable{
    launch(Dispatchers.game) {
        while (true) {
            delay(time.toMillis())
            broadcast()
        }
    }
}
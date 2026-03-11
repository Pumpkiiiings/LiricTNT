package liric.tnt.managers

import liric.tnt.LiricTNTPlugin
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import java.io.File

class PlayerManager(private val plugin: LiricTNTPlugin) {
    private val disqualified = mutableSetOf<String>()
    private val file = File(plugin.dataFolder, "disqualified.txt")

    init {
        if (!file.exists()) file.createNewFile()
        disqualified.addAll(file.readLines())
    }

    fun disqualify(player: Player) {
        disqualified.add(player.name)
        file.appendText("${player.name}\n")
        player.kick(Component.text("§cHas sido descalificado permanentemente del evento."))
    }

    fun isDisqualified(player: Player): Boolean = disqualified.contains(player.name)

    fun revive(player: Player) {
        val arena = plugin.arenaManager.getArena(player) ?: return
        arena.spectators.remove(player)
        arena.alivePlayers.add(player)
        player.gameMode = org.bukkit.GameMode.ADVENTURE
        player.teleportAsync(arena.spawns.random())
    }
}

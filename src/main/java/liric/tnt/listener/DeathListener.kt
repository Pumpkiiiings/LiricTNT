package liric.tnt.listener

import liric.tnt.LiricTNTPlugin
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent

class DeathListener(private val plugin: LiricTNTPlugin) : Listener {

    // Colores Sólidos Resaltantes
    private val cRed = "<#FF0000>"
    private val cWhite = "<#FFFFFF>"
    private val cAqua = "<#00FFFF>"

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onDeath(e: PlayerDeathEvent) {
        val player = e.player

        // 1. ELIMINAR MENSAJE VANILLA (Silencio total)
        e.deathMessage(null)

        // 2. MENSAJE CUSTOM VIBRANTE
        // Nota: El respawn ocurre solo por la GameRule que activamos en el onEnable
        val deathMsg = plugin.messageManager.parse(
            player,
            "☠ <bold>$cRed MUERTE $cWhite</bold> » $cAqua<player> $cWhite ha muerto.",
            Placeholder.component("player", player.name())
        )

        plugin.server.broadcast(deathMsg)

        // 3. SONIDO DE IMPACTO
        player.world.playSound(player.location, Sound.ENTITY_WITHER_SPAWN, 0.5f, 2f)
    }
}

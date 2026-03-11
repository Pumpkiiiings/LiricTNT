package liric.tnt.listener

import liric.tnt.LiricTNTPlugin
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class WorldProtectionListener(private val plugin: LiricTNTPlugin) : Listener {

    /**
     * Quitar los mensajes de Join/Quit por completo
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onJoin(e: PlayerJoinEvent) {
        e.joinMessage(null) // Adiós al "Player joined the game"
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onQuit(e: PlayerQuitEvent) {
        e.quitMessage(null) // Adiós al "Player left the game"
    }

    /**
     * Bloquear romper bloques (Con permiso de bypass)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBreak(e: BlockBreakEvent) {
        if (!e.player.hasPermission("tnt.admin.build")) {
            e.isCancelled = true
        }
    }

    /**
     * Bloquear poner bloques (Con permiso de bypass)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlace(e: BlockPlaceEvent) {
        if (!e.player.hasPermission("tnt.admin.build")) {
            e.isCancelled = true
        }
    }

    /**
     * Quitar el daño de caída en TODOS los mundos
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onFallDamage(e: EntityDamageEvent) {
        if (e.cause == EntityDamageEvent.DamageCause.FALL) {
            // Cancelamos cualquier daño que sea por caída
            e.isCancelled = true
        }
    }
}

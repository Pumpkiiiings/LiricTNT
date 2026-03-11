package liric.tnt.spectator

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import liric.tnt.game.Arena
import net.kyori.adventure.text.Component
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

object SpectatorManager {
    fun setSpectator(player: Player, arena: Arena) {
        player.gameMode = GameMode.SPECTATOR
        player.inventory.clear()

        val compass = ItemStack(Material.COMPASS)
        val meta = compass.itemMeta
        meta.displayName(Component.text("§aEspectar Jugadores (Click Derecho)"))
        // CORRECCIÓN: Usar setItemMeta() porque Kotlin toma 'itemMeta' como val
        compass.setItemMeta(meta)

        player.inventory.addItem(compass)
    }

    fun openGui(player: Player, arena: Arena) {
        val gui = Gui.gui()
            .title(Component.text("§cJugadores Vivos"))
            .rows(4)
            .create()

        arena.alivePlayers.forEach { p ->
            // CORRECCIÓN: API correcta de Triumph GUI para Skulls + Tipo explícito de Evento
            gui.addItem(ItemBuilder.skull()
                .owner(p)
                .name(Component.text("§e${p.name}"))
                .asGuiItem { event: InventoryClickEvent ->
                    player.teleportAsync(p.location)
                    player.sendMessage("§aTeletransportado a ${p.name}")
                    gui.close(player)
                })
        }
        gui.open(player)
    }
}

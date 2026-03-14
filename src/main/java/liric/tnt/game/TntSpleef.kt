package liric.tnt.game

import liric.tnt.LiricTNTPlugin
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class TntSpleef(plugin: LiricTNTPlugin, instanceName: String, displayName: String) : Arena(plugin, instanceName, displayName, "spleef") {

    var isPreparation = false
    var timer = 10

    override fun getAvailableBoosters(): List<BoosterType> {
        return listOf(BoosterType.SPEED_1, BoosterType.SPEED_2, BoosterType.JUMP_1, BoosterType.JUMP_2, BoosterType.MIX, BoosterType.PEARL)
    }

    override fun startTasks() {
        state = ArenaState.INGAME
        isPreparation = true
        timer = 10

        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (state != ArenaState.INGAME) {
                task.cancel()
                return@runAtFixedRate
            }

            // --- FASE DE PREPARACIÓN ---
            if (isPreparation) {
                timer--
                val bar = plugin.mm.deserialize("${cWhite}El Spleef comienza en: ${cAqua}<bold>$timer</bold>s")
                alivePlayers.forEach {
                    it.sendActionBar(bar)
                    it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1f)
                }

                if (timer <= 0) {
                    isPreparation = false
                    startBoosterTask()

                    // Armamento de Spleef
                    val bow = ItemStack(Material.BOW)
                    val bowMeta = bow.itemMeta
                    bowMeta.displayName(plugin.mm.deserialize("${cRed}<b>Arco Ignitor</b>"))
                    bowMeta.addEnchant(Enchantment.INFINITY, 1, true)
                    bowMeta.isUnbreakable = true
                    bow.itemMeta = bowMeta

                    val arrow = ItemStack(Material.ARROW)

                    alivePlayers.forEach {
                        it.inventory.setItem(0, bow)
                        it.inventory.setItem(9, arrow) // En el inventario interno para que Infinity funcione
                        it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1f)
                        it.sendMessage(plugin.mm.deserialize("<newline>${cRed}<b>¡A DISPARAR!</b> ${cWhite}Dispara a la TNT para hacer caer a tus rivales.<newline>"))
                    }
                }
                return@runAtFixedRate
            }

            // --- CHEQUEO DE ELIMINACIÓN (Caída al vacío) ---
            val toEliminate = alivePlayers.filter { it.location.y <= it.world.minHeight + 2 }
            toEliminate.forEach { eliminate(it) }

        }, 20L, 20L)
    }
}

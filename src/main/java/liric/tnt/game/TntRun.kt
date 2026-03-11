package liric.tnt.game

import liric.tnt.LiricTNTPlugin
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class TntRun(plugin: LiricTNTPlugin, instanceName: String, displayName: String) : Arena(plugin, instanceName, displayName, "run") {

    var isPreparation = false
    var timer = 10

    // Variables para el sistema de Borde (Muerte Súbita)
    var borderStarted = false
    var borderTimer = 30

    override fun getAvailableBoosters(): List<BoosterType> {
        return listOf(BoosterType.SPEED_1, BoosterType.SPEED_2, BoosterType.SPEED_3, BoosterType.JUMP_1, BoosterType.JUMP_2, BoosterType.JUMP_3, BoosterType.MIX, BoosterType.PEARL)
    }

    override fun startTasks() {
        state = ArenaState.INGAME
        isPreparation = true
        timer = 10
        borderStarted = false
        borderTimer = 30

        // 1. Bucle de Preparación, Check de Muertes y Borde (Cada segundo / 20 ticks)
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (state != ArenaState.INGAME) {
                task.cancel()
                return@runAtFixedRate
            }

            // --- FASE DE PREPARACIÓN ---
            if (isPreparation) {
                timer--
                val bar = plugin.mm.deserialize("${cWhite}El suelo caerá en: ${cAqua}<bold>$timer</bold>s")
                alivePlayers.forEach {
                    it.sendActionBar(bar)
                    it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1f)
                }

                if (timer <= 0) {
                    isPreparation = false
                    startBoosterTask()

                    // Crear el ítem de Vuelo (Pluma con 3 usos y Cooldown de 15s)
                    val feather = ItemStack(Material.FEATHER, 3)
                    val meta = feather.itemMeta
                    meta.displayName(plugin.mm.deserialize("${cAqua}<b>PLUMA DE SALTO</b>"))
                    meta.lore(listOf(
                        plugin.mm.deserialize("${cWhite}¡Impúlsate hacia el cielo!"),
                        plugin.mm.deserialize("${cRed}⚠ Cooldown: 5 segundos")
                    ))

                    val key = NamespacedKey(plugin, "tnt_item")
                    meta.persistentDataContainer.set(key, PersistentDataType.STRING, "feather")
                    feather.itemMeta = meta

                    alivePlayers.forEach {
                        it.inventory.setItem(4, feather)
                        it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1f)
                        it.sendActionBar(plugin.mm.deserialize("${cRed}<b>¡CORRE!</b> ${cWhite}El suelo está desapareciendo"))
                    }
                }
                return@runAtFixedRate
            }

            // --- CHEQUEO DE ELIMINACIÓN (Caída al vacío) ---
            val toEliminate = alivePlayers.filter { it.location.y <= it.world.minHeight + 2 }
            toEliminate.forEach { eliminate(it) }

            // --- SISTEMA DE BORDE (Muerte Súbita) ---
            if (alivePlayers.size <= 8 && alivePlayers.size > 1) {
                val world = spawns.firstOrNull()?.world
                if (world != null) {
                    if (!borderStarted) {
                        borderStarted = true
                        val border = world.worldBorder
                        border.center = spawns.first()
                        border.size = 80.0
                        border.damageAmount = 2.0

                        alivePlayers.forEach {
                            it.sendMessage(plugin.mm.deserialize("<newline>${cRed}<b>¡MUERTE SÚBITA!</b> ${cWhite}El borde se cerrará cada 30s.<newline>"))
                            it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f)
                        }
                    } else {
                        borderTimer--

                        // Notificación de tiempo de borde por ActionBar (últimos 10s)
                        if (borderTimer <= 10 && borderTimer > 0) {
                            val borderBar = plugin.mm.deserialize("${cRed}<b>⚠ CIERRE DE BORDE:</b> ${cYellow}$borderTimer")
                            alivePlayers.forEach { it.sendActionBar(borderBar) }
                        }

                        if (borderTimer <= 0) {
                            borderTimer = 30
                            val border = world.worldBorder
                            val newSize = (border.size - 15.0).coerceAtLeast(10.0)
                            border.setSize(newSize, 5L)

                            alivePlayers.forEach {
                                it.sendActionBar(plugin.mm.deserialize("${cRed}<b>¡EL BORDE SE HA REDUCIDO!</b>"))
                                it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f)
                            }
                        }
                    }
                }
            }

        }, 20L, 20L)

        // 2. Bucle de Detección de Bloques (Súper rápido)
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (state != ArenaState.INGAME || isPreparation) {
                if (state != ArenaState.INGAME) task.cancel()
                return@runAtFixedRate
            }

            for (player in alivePlayers) {
                scheduleBlockBreak(player)
            }
        }, 10L, 2L)
    }

    private fun scheduleBlockBreak(player: Player) {
        val box = player.boundingBox
        val world = player.world
        val y = box.minY - 0.05

        val corners = listOf(
            Location(world, box.minX, y, box.minZ),
            Location(world, box.maxX, y, box.minZ),
            Location(world, box.minX, y, box.maxZ),
            Location(world, box.maxX, y, box.maxZ)
        )

        val blocksToBreak = corners.map { it.block }.toSet().filter {
            it.type == Material.SAND || it.type == Material.RED_SAND || it.type == Material.GRAVEL || it.type == Material.TNT
        }

        for (block in blocksToBreak) {
            plugin.server.regionScheduler.runDelayed(plugin, block.location, { _ ->
                if (state != ArenaState.INGAME) return@runDelayed

                if (block.type != Material.AIR) {
                    block.setType(Material.AIR, false)
                    val blockBelow = block.getRelative(BlockFace.DOWN)
                    if (blockBelow.type == Material.TNT) {
                        blockBelow.setType(Material.AIR, false)
                    }
                }
            }, 6L)
        }
    }
}

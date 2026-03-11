package liric.tnt.game

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.tnt.LiricTNTPlugin
import liric.tnt.spectator.SpectatorManager
import liric.tnt.utils.CraftEngineUtils
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.joml.Vector3f
import java.util.concurrent.ConcurrentLinkedQueue

enum class ArenaState { WAITING, INGAME, ENDING }

// Boosters con colores sólidos. A mayor nivel, menor duración.
enum class BoosterType(
    val material: Material,
    val displayName: String,
    val color: String
) {
    SPEED_1(Material.FEATHER, "Velocidad I", "<#00FF00>") {
        override fun applyEffect(player: Player, plugin: LiricTNTPlugin) {
            // Amplificador 0 = Nivel 1. Duración: 10 segundos (200 ticks)
            player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 200, 0))
        }
    },
    SPEED_2(Material.FEATHER, "Velocidad II", "<#00FF00>") {
        override fun applyEffect(player: Player, plugin: LiricTNTPlugin) {
            // Amplificador 1 = Nivel 2. Duración: 6 segundos (120 ticks)
            player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 120, 1))
        }
    },
    SPEED_3(Material.FEATHER, "Velocidad III", "<#00FF00>") {
        override fun applyEffect(player: Player, plugin: LiricTNTPlugin) {
            // Amplificador 2 = Nivel 3. Duración: 3.5 segundos (70 ticks)
            player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 70, 2))
        }
    },
    JUMP_1(Material.RABBIT_FOOT, "Súper Salto I", "<#FFFF00>") {
        override fun applyEffect(player: Player, plugin: LiricTNTPlugin) {
            // Amplificador 0 = Nivel 1. Duración: 10 segundos (200 ticks)
            player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 200, 0))
        }
    },
    JUMP_2(Material.RABBIT_FOOT, "Súper Salto II", "<#FFFF00>") {
        override fun applyEffect(player: Player, plugin: LiricTNTPlugin) {
            // Amplificador 1 = Nivel 2. Duración: 6 segundos (120 ticks)
            player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 120, 1))
        }
    },
    JUMP_3(Material.RABBIT_FOOT, "Súper Salto III", "<#FFFF00>") {
        override fun applyEffect(player: Player, plugin: LiricTNTPlugin) {
            // Amplificador 2 = Nivel 3. Duración: 3.5 segundos (70 ticks)
            player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 70, 2))
        }
    },
    MIX(Material.BLAZE_POWDER, "Mix Poder", "<#FF00FF>") { // Magenta
        override fun applyEffect(player: Player, plugin: LiricTNTPlugin) {
            // Mezcla de nivel 2 pero con corta duración (5 segundos)
            player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 100, 1))
            player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 100, 1))
        }
    },
    PEARL(Material.ENDER_PEARL, "Perla Salvación", "<#00FFFF>") { // Cyan / Aqua
        override fun applyEffect(player: Player, plugin: LiricTNTPlugin) {
            val item = ItemStack(Material.ENDER_PEARL)
            val meta = item.itemMeta
            meta.displayName(plugin.mm.deserialize("<#00FFFF><b>Perla de Salvación</b>"))

            val key = NamespacedKey(plugin, "tnt_item")
            meta.persistentDataContainer.set(key, PersistentDataType.STRING, "pearl")

            item.itemMeta = meta
            player.inventory.addItem(item)
        }
    };

    abstract fun applyEffect(player: Player, plugin: LiricTNTPlugin)
}

data class ActiveBooster(
    val item: ItemDisplay,
    val text: TextDisplay,
    var task: ScheduledTask? = null
)

abstract class Arena(val plugin: LiricTNTPlugin, val name: String, val type: String) {
    val mm = MiniMessage.miniMessage()

    // Colores Resaltantes Sólidos
    val cRed = "<#FF0000>"
    val cGreen = "<#00FF00>"
    val cAqua = "<#00FFFF>"
    val cYellow = "<#FFFF00>"
    val cPurple = "<#FF00FF>"
    val cWhite = "<#FFFFFF>"

    var state = ArenaState.WAITING
    val spawns = mutableListOf<Location>()
    val alivePlayers = mutableListOf<Player>()
    val spectators = mutableListOf<Player>()

    private val activeBoosters = ConcurrentLinkedQueue<ActiveBooster>()
    private var boosterTask: ScheduledTask? = null

    open fun getAvailableBoosters(): List<BoosterType> {
        return if (type.lowercase() == "run") {
            listOf(
                BoosterType.SPEED_1, BoosterType.SPEED_2, BoosterType.SPEED_3,
                BoosterType.JUMP_1, BoosterType.JUMP_2, BoosterType.JUMP_3,
                BoosterType.MIX, BoosterType.PEARL
            )
        } else {
            listOf(
                BoosterType.SPEED_1, BoosterType.SPEED_2, BoosterType.SPEED_3,
                BoosterType.JUMP_1, BoosterType.JUMP_2, BoosterType.JUMP_3,
                BoosterType.MIX
            )
        }
    }

    fun join(player: Player) {
        if (plugin.playerManager.isDisqualified(player)) {
            player.sendMessage(mm.deserialize("$cRed<b>!</b> ${cWhite}Estás descalificado permanentemente."))
            return
        }

        if (spawns.isEmpty()) {
            player.sendMessage(mm.deserialize("$cRed<b>!</b> ${cWhite}La arena no tiene spawns configurados."))
            return
        }

        alivePlayers.add(player)
        player.teleportAsync(spawns.random())
        player.sendMessage(mm.deserialize("$cGreen<b>+</b> ${cWhite}Entraste a la arena: $cAqua$name"))
    }

    fun eliminate(player: Player) {
        alivePlayers.remove(player)
        spectators.add(player)

        player.showTitle(Title.title(
            mm.deserialize("$cRed<b>ELIMINADO</b>"),
            mm.deserialize("${cWhite}Ahora eres espectador")
        ))
        player.playSound(player.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 2f)

        SpectatorManager.setSpectator(player, this)

        if (alivePlayers.size <= 1 && state == ArenaState.INGAME) {
            checkWinner()
        }
    }

    private fun checkWinner() {
        state = ArenaState.ENDING
        val winner = alivePlayers.firstOrNull()

        if (winner != null) {
            plugin.server.broadcast(mm.deserialize("<newline>$cYellow<b>EVENTO</b> ${cWhite}» $cPurple${winner.name} ${cWhite}ha ganado en <b>$name</b>!<newline>"))
            winner.playSound(winner.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
        }

        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ -> stop() }, 200L)
    }

    fun stop() {
        state = ArenaState.ENDING
        boosterTask?.cancel()

        activeBoosters.forEach { booster ->
            booster.task?.cancel()
            plugin.server.regionScheduler.run(plugin, booster.item.location) { _ ->
                if (!booster.item.isDead) booster.item.remove()
                if (!booster.text.isDead) booster.text.remove()
            }
        }
        activeBoosters.clear()

        val allPlayers = alivePlayers + spectators
        val exitLoc = plugin.arenaManager.waitingSpawn ?: plugin.arenaManager.mainLobby

        allPlayers.forEach { p ->
            p.inventory.clear()
            p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
            p.gameMode = org.bukkit.GameMode.ADVENTURE

            CraftEngineUtils.stopAllMusic(p)

            if (exitLoc != null) p.teleportAsync(exitLoc)
        }

        alivePlayers.clear()
        spectators.clear()
        state = ArenaState.WAITING
        plugin.server.broadcast(mm.deserialize("$cRed<b>!</b> ${cWhite}La arena $cAqua$name ${cWhite}ha sido reiniciada."))
    }

    abstract fun startTasks()

    fun startBoosterTask() {
        boosterTask = plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (state != ArenaState.INGAME) { task.cancel(); return@runAtFixedRate }

            if (Math.random() < 0.30 && activeBoosters.size < 8 && alivePlayers.isNotEmpty()) {
                spawnBooster()
            }
        }, 60L, 40L)
    }

    private fun spawnBooster() {
        val availableBoosters = getAvailableBoosters()
        if (availableBoosters.isEmpty()) return
        val type = availableBoosters.random()

        val targetPlayer = alivePlayers.randomOrNull() ?: return
        val centerLoc = targetPlayer.location

        val offsetX = (Math.random() * 24 - 12)
        val offsetZ = (Math.random() * 24 - 12)
        val searchLoc = centerLoc.clone().add(offsetX, 0.0, offsetZ)

        plugin.server.regionScheduler.run(plugin, searchLoc) { _ ->
            val world = searchLoc.world
            var highestY = searchLoc.blockY
            var foundSolid = false

            for (y in (searchLoc.blockY + 15) downTo (searchLoc.blockY - 20)) {
                val b = world.getBlockAt(searchLoc.blockX, y, searchLoc.blockZ)
                if (b.type.isSolid) {
                    highestY = y
                    foundSolid = true
                    break
                }
            }

            val finalLoc = if (foundSolid) {
                Location(world, searchLoc.x, highestY + 1.2, searchLoc.z)
            } else {
                centerLoc.clone().add(targetPlayer.location.direction.multiply(3)).add(0.0, 1.2, 0.0)
            }

            val itemDisplay = world.spawn(finalLoc, ItemDisplay::class.java)
            itemDisplay.setItemStack(ItemStack(type.material))

            val transform = itemDisplay.transformation
            itemDisplay.transformation = Transformation(
                transform.translation, transform.leftRotation, Vector3f(1.5f, 1.5f, 1.5f), transform.rightRotation
            )

            val textLoc = finalLoc.clone().add(0.0, 0.8, 0.0)
            val textDisplay = world.spawn(textLoc, TextDisplay::class.java)
            textDisplay.text(mm.deserialize("${type.color}<b>${type.displayName.uppercase()}</b>"))
            textDisplay.billboard = org.bukkit.entity.Display.Billboard.CENTER
            textDisplay.backgroundColor = org.bukkit.Color.fromARGB(0, 0, 0, 0)
            textDisplay.isShadowed = true
            textDisplay.alignment = TextDisplay.TextAlignment.CENTER

            val activeBooster = ActiveBooster(itemDisplay, textDisplay, null)
            activeBoosters.add(activeBooster)

            var tick = 0
            val animTask = plugin.server.regionScheduler.runAtFixedRate(plugin, finalLoc, { task ->
                if (state != ArenaState.INGAME || itemDisplay.isDead) {
                    itemDisplay.remove()
                    textDisplay.remove()
                    activeBoosters.remove(activeBooster)
                    task.cancel()
                    return@runAtFixedRate
                }

                tick++
                val currentLoc = itemDisplay.location
                val blockBelow = world.getBlockAt(currentLoc.blockX, currentLoc.blockY - 1, currentLoc.blockZ)

                if (!blockBelow.type.isSolid && blockBelow.type != Material.WATER) {
                    itemDisplay.teleport(currentLoc.clone().subtract(0.0, 0.4, 0.0))
                    textDisplay.teleport(textDisplay.location.clone().subtract(0.0, 0.4, 0.0))

                    if (currentLoc.y < world.minHeight) {
                        itemDisplay.remove()
                        textDisplay.remove()
                        activeBoosters.remove(activeBooster)
                        task.cancel()
                        return@runAtFixedRate
                    }
                } else {
                    itemDisplay.interpolationDuration = 1
                    val currentTransform = itemDisplay.transformation
                    currentTransform.rightRotation.rotateY(0.1f)
                    val floatOffset = (Math.sin(tick / 10.0) * 0.15).toFloat()
                    currentTransform.translation.set(0f, floatOffset, 0f)
                    itemDisplay.transformation = currentTransform
                }

                val picker = currentLoc.getNearbyPlayers(1.5).firstOrNull { alivePlayers.contains(it) }

                if (picker != null) {
                    picker.scheduler.run(plugin, { _ ->
                        type.applyEffect(picker, plugin)
                        picker.playSound(picker.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f)
                        picker.sendMessage(mm.deserialize("$cPurple<b>!</b> ${cWhite}Has recogido un potenciador: ${type.color}<b>${type.displayName}</b>"))
                    }, null)

                    world.spawnParticle(org.bukkit.Particle.FIREWORK, currentLoc, 15, 0.2, 0.2, 0.2, 0.1)
                    itemDisplay.remove()
                    textDisplay.remove()
                    activeBoosters.remove(activeBooster)
                    task.cancel()
                }
            }, 1L, 1L)

            activeBooster.task = animTask
        }
    }
}

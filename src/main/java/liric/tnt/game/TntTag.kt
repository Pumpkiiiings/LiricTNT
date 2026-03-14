package liric.tnt.game

import liric.tnt.LiricTNTPlugin
import net.kyori.adventure.title.Title
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.concurrent.TimeUnit

class TntTag(plugin: LiricTNTPlugin, instanceName: String, displayName: String) : Arena(plugin, instanceName, displayName, "tag") {

    val itPlayers = mutableSetOf<Player>()
    var timer = 30
    var isPreparation = false
    var intenseMusicPlayed = false

    // Variables de Borde
    var borderStarted = false
    var borderTimer = 30

    override fun startTasks() {
        state = ArenaState.INGAME
        isPreparation = true
        timer = 10
        itPlayers.clear()
        intenseMusicPlayed = false
        borderStarted = false
        borderTimer = 30

        // Reseteo inicial del borde
        val spawnLoc = spawns.firstOrNull()
        if (spawnLoc != null) {
            plugin.server.regionScheduler.run(plugin, spawnLoc) { _ ->
                val border = spawnLoc.world.worldBorder
                border.center = spawnLoc
                border.size = 150.0 // Empieza grande
                border.damageAmount = 2.0
            }
        }

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            startBoosterTask()
        }

        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (state != ArenaState.INGAME) {
                task.cancel()
                return@runAtFixedRate
            }

            timer--

            // --- FASE DE PREPARACIÓN ---
            if (isPreparation) {
                val bar = plugin.mm.deserialize("$cWhite La TNT se repartirá en: $cAqua<bold>$timer</bold>s")
                alivePlayers.forEach { p ->
                    p.sendActionBar(bar)
                    p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1f)
                }

                if (timer <= 0) {
                    isPreparation = false
                    timer = 30
                    plugin.server.globalRegionScheduler.run(plugin) { _ ->
                        selectNewIts()
                        giveAbilities()
                    }
                }
                return@runAtFixedRate
            }

            // --- EVENTOS DE MUERTE SÚBITA (6 JUGADORES O MENOS) ---
            if (alivePlayers.size <= 6 && alivePlayers.size > 1) {

                // Música intensa una sola vez
                if (!intenseMusicPlayed) {
                    intenseMusicPlayed = true
                    val customSound = net.kyori.adventure.sound.Sound.sound(
                        net.kyori.adventure.key.Key.key("mistaken", "lms"),
                        net.kyori.adventure.sound.Sound.Source.MASTER,
                        1f, 1f
                    )
                    (alivePlayers + spectators).forEach { it.playSound(customSound) }
                }

                // Brillo a todos
                alivePlayers.forEach { p ->
                    p.scheduler.run(plugin, { _ ->
                        p.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false, false))
                    }, null)
                }

                // Lógica del Borde (Igual que en TNT Run)
                val world = spawns.firstOrNull()?.world
                if (world != null) {
                    if (!borderStarted) {
                        borderStarted = true

                        alivePlayers.forEach {
                            it.sendMessage(plugin.mm.deserialize("<newline>$cRed<b>¡MUERTE SÚBITA!</b> $cWhite El borde se cerrará cada 30s.<newline>"))
                            it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f)
                        }
                    } else {
                        borderTimer--

                        if (borderTimer <= 10 && borderTimer > 0) {
                            val borderBar = plugin.mm.deserialize("${cRed}<b>⚠ CIERRE DE BORDE:</b> ${cYellow}$borderTimer")
                            alivePlayers.forEach { it.sendActionBar(borderBar) }
                        }

                        if (borderTimer <= 0) {
                            borderTimer = 30
                            val border = world.worldBorder
                            // Reduce 15 bloques de golpe cada 30s
                            val newSize = (border.size - 25.0).coerceAtLeast(10.0)
                            border.setSize(newSize, 5L)

                            alivePlayers.forEach {
                                it.sendActionBar(plugin.mm.deserialize("${cRed}<b>¡EL BORDE SE HA REDUCIDO!</b>"))
                                it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f)
                            }
                        }
                    }
                }
            }

            // Efectos de los IT
            itPlayers.forEach { p ->
                p.scheduler.run(plugin, { _ ->
                    p.world.spawnParticle(org.bukkit.Particle.SMALL_FLAME, p.location.add(0.0, 2.2, 0.0), 3, 0.1, 0.1, 0.1, 0.02)
                }, null)
                p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f)
            }

            // Barra de explosión
            if (borderTimer > 10 || alivePlayers.size > 6) { // Para no solapar con el aviso del borde
                val bar = plugin.mm.deserialize("$cWhite Explosión en: $cRed<bold>$timer</bold>s")
                alivePlayers.forEach { it.sendActionBar(bar) }
            }

            if (timer <= 0) {
                val victims = itPlayers.toList()
                victims.forEach { p ->
                    p.scheduler.run(plugin, { _ ->
                        p.world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, p.location, 1)
                        p.world.playSound(p.location, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f)

                        p.inventory.helmet = null
                        eliminate(p)
                    }, null)
                }

                if (alivePlayers.size > victims.size) {
                    timer = 30
                    plugin.server.globalRegionScheduler.runDelayed(plugin, { _ -> selectNewIts() }, 2L)
                } else {
                    state = ArenaState.ENDING
                    task.cancel()
                }
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun giveAbilities() {
        val key = NamespacedKey(plugin, "tnt_item")

        val dash = ItemStack(Material.SUGAR)
        val dashMeta = dash.itemMeta
        dashMeta.displayName(plugin.mm.deserialize("$cAqua<b>DASH EVASIVO</b>"))
        dashMeta.persistentDataContainer.set(key, PersistentDataType.STRING, "dash")
        dash.itemMeta = dashMeta

        val jump = ItemStack(Material.RABBIT_FOOT)
        val jumpMeta = jump.itemMeta
        jumpMeta.displayName(plugin.mm.deserialize("$cGreen<b>DOBLE SALTO</b>"))
        jumpMeta.persistentDataContainer.set(key, PersistentDataType.STRING, "doublejump")
        jump.itemMeta = jumpMeta

        alivePlayers.forEach { p ->
            p.inventory.setItem(1, dash)
            p.inventory.setItem(2, jump)
        }
    }

    fun handlePunch(attacker: Player, victim: Player) {
        if (isPreparation) return

        if (!itPlayers.contains(attacker)) return
        if (itPlayers.contains(victim)) return
        if (!alivePlayers.contains(victim)) return

        itPlayers.remove(attacker)
        itPlayers.add(victim)

        attacker.inventory.helmet = null
        victim.inventory.helmet = ItemStack(Material.TNT)

        attacker.sendMessage(plugin.mm.deserialize("$cGreen<b>!</b> $cWhite ¡Te has librado de la TNT!"))
        victim.sendMessage(plugin.mm.deserialize("$cRed<b>!</b> $cWhite ¡Ahora la tienes tú, pásala!"))

        attacker.playSound(attacker.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
        victim.playSound(victim.location, Sound.ENTITY_CREEPER_PRIMED, 1f, 1.2f)

        victim.world.spawnParticle(org.bukkit.Particle.LAVA, victim.location.add(0.0, 1.0, 0.0), 5)
    }

    private fun selectNewIts() {
        itPlayers.clear()

        val amount = (alivePlayers.size / 4).coerceAtLeast(1)

        alivePlayers.shuffled().take(amount).forEach { p ->
            itPlayers.add(p)
            p.inventory.helmet = ItemStack(Material.TNT)

            p.showTitle(Title.title(
                plugin.mm.deserialize("$cRed<b>¡TIENES LA TNT!</b>"),
                plugin.mm.deserialize("$cWhite Pásala rápido golpeando a alguien")
            ))
            p.playSound(p.location, Sound.ENTITY_TNT_PRIMED, 1f, 1f)
        }

        val itNames = itPlayers.joinToString("$cWhite, $cAqua") { it.name }
        val msg = plugin.mm.deserialize("<newline>$cRed<b>¡CUIDADO!</b> $cWhite La TNT ha sido entregada a: $cAqua$itNames<newline>")

        alivePlayers.forEach { it.sendMessage(msg) }
        spectators.forEach { it.sendMessage(msg) }
    }
}

package liric.tnt.game.entities

import liric.tnt.LiricTNTPlugin
import liric.tnt.game.Arena
import liric.tnt.game.ArenaState
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

class GeoffreyEXE(private val plugin: LiricTNTPlugin, private val arena: Arena) {

    private val parts = mutableListOf<BlockDisplay>()
    private var isRunning = true
    private var currentTarget: Player? = null
    private var lastVictimUUID: UUID? = null

    private val teamWhite = "GeoffreyGlow"
    private val teamRed = "GeoffreyAngry"
    private var consecutiveMisses = 0

    private enum class State { BUSCANDO, SALTANDO, MISIL, AEREO, FURIA }
    private var currentState = State.BUSCANDO

    fun spawn(startLoc: Location) {
        plugin.server.globalRegionScheduler.run(plugin) {
            try {
                val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
                scoreboard.getTeam(teamWhite) ?: scoreboard.registerNewTeam(teamWhite).apply { color(NamedTextColor.WHITE) }
                scoreboard.getTeam(teamRed) ?: scoreboard.registerNewTeam(teamRed).apply { color(NamedTextColor.RED) }

                // --- CONSTRUCCIÓN ---
                val body = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(3f, 3f, 3f), Vector3f(-1.5f, 0f, -1.5f))
                val leftEye = createPart(startLoc, Material.RED_CONCRETE, Vector3f(0.5f, 0.2f, 0.1f), Vector3f(-1.0f, 1.8f, 1.51f))
                val rightEye = createPart(startLoc, Material.RED_CONCRETE, Vector3f(0.5f, 0.2f, 0.1f), Vector3f(0.5f, 1.8f, 1.51f))
                val leftHand = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.8f, 0.8f, 0.8f), Vector3f(-2.8f, 0.5f, 0.5f))
                val rightHand = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.8f, 0.8f, 0.8f), Vector3f(2.0f, 0.5f, 0.5f))

                parts.addAll(listOf(body, leftEye, rightEye, leftHand, rightHand))

                setGlowColor(NamedTextColor.WHITE)
                plugin.server.broadcast(plugin.mm.deserialize("<red><b>[!]</b> <dark_red>ANOMALÍA DETECTADA: <b>GEOFFREY 3.0</b> HA DESPERTADO."))

                iniciarIANativa()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createPart(loc: Location, mat: Material, scale: Vector3f, translation: Vector3f): BlockDisplay {
        return loc.world.spawn(loc, BlockDisplay::class.java) { bd ->
            bd.block = mat.createBlockData()
            bd.transformation = Transformation(translation, Quaternionf(), scale, Quaternionf())
            bd.isPersistent = false
            bd.interpolationDuration = 1
            bd.teleportDuration = 1
            bd.brightness = Display.Brightness(15, 15)
            bd.isGlowing = true
        }
    }

    private fun setGlowColor(color: NamedTextColor) {
        val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        val targetTeam = if (color == NamedTextColor.RED) teamRed else teamWhite
        val team = scoreboard.getTeam(targetTeam) ?: return

        parts.forEach {
            val id = it.uniqueId.toString()
            if (!team.hasEntry(id)) team.addEntry(id)
        }
    }

    private fun iniciarIANativa() {
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning || arena.state != ArenaState.INGAME || parts.isEmpty() || !parts[0].isValid) {
                explodeAndRemove()
                task.cancel()
                return@runAtFixedRate
            }

            val bodyLoc = parts[0].location
            // Solo targetea a jugadores vivos de la arena actual
            currentTarget = arena.alivePlayers
                .filter { it.uniqueId != lastVictimUUID }
                .minByOrNull { it.location.distanceSquared(bodyLoc) }
                ?: arena.alivePlayers.firstOrNull()

            if (currentTarget == null) return@runAtFixedRate

            if (currentState == State.BUSCANDO) {
                if (consecutiveMisses >= 3) {
                    currentState = State.FURIA
                    ejecutarModoFuria(currentTarget!!)
                } else {
                    currentState = State.SALTANDO
                    ejecutarSaltos(currentTarget!!)
                }
            }
        }, 1L, 20L)
    }

    private fun ejecutarSaltos(target: Player) {
        var saltos = 0
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning || !target.isOnline || saltos >= 4 || arena.state != ArenaState.INGAME) {
                task.cancel()
                if (isRunning) {
                    currentState = if (Math.random() < 0.3) State.AEREO else State.MISIL
                    if (currentState == State.AEREO) ejecutarAtaqueAereo(target) else ejecutarAtaqueMisil(target)
                }
                return@runAtFixedRate
            }

            val bodyLoc = parts[0].location
            val nextLoc = bodyLoc.add(target.location.toVector().subtract(bodyLoc.toVector()).normalize().multiply(3.5))
            moverTodo(nextLoc, lookAtTarget = true)
            target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 1f, 0.7f)
            saltos++
        }, 1L, 15L)
    }

    private fun ejecutarAtaqueMisil(target: Player) {
        target.showTitle(Title.title(plugin.mm.deserialize("<red><b>GEOFFREY VIENE"), plugin.mm.deserialize("<dark_red>¡CORRE!")))

        plugin.server.globalRegionScheduler.runDelayed(plugin, {
            if (!isRunning || !target.isOnline) return@runDelayed

            val dir = target.location.add(0.0, 1.0, 0.0).toVector().subtract(parts[0].location.toVector()).normalize()
            var step = 0

            plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
                if (!isRunning || step >= 15 || arena.state != ArenaState.INGAME) {
                    task.cancel()
                    currentState = State.BUSCANDO
                    consecutiveMisses++
                    return@runAtFixedRate
                }

                val nextLoc = parts[0].location.add(dir.clone().multiply(3.0))
                moverTodo(nextLoc, false)
                nextLoc.world.spawnParticle(org.bukkit.Particle.FLAME, nextLoc, 5, 0.2, 0.2, 0.2, 0.1)

                val hit = arena.alivePlayers.find { it.location.distanceSquared(nextLoc) < 5.0 }
                if (hit != null) {
                    ejecutarMuerte(hit)
                    consecutiveMisses = 0
                    task.cancel()
                    currentState = State.BUSCANDO
                }
                step++
            }, 1L, 2L)
        }, 20L)
    }

    private fun ejecutarAtaqueAereo(target: Player) {
        var subida = 0
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (subida >= 8) {
                task.cancel()
                plugin.server.globalRegionScheduler.runDelayed(plugin, {
                    val dropLoc = target.location
                    moverTodo(dropLoc, false)
                    dropLoc.world.createExplosion(dropLoc, 0f, false, false)
                    arena.alivePlayers.filter { it.location.distanceSquared(dropLoc) < 16.0 }.forEach { ejecutarMuerte(it) }
                    currentState = State.BUSCANDO
                }, 10L)
                return@runAtFixedRate
            }
            moverTodo(parts[0].location.add(0.0, 2.0, 0.0), false)
            subida++
        }, 1L, 2L)
    }

    private fun ejecutarModoFuria(target: Player) {
        setGlowColor(NamedTextColor.RED)
        var ticks = 0
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (ticks > 100 || arena.state != ArenaState.INGAME || !target.isOnline) {
                task.cancel()
                setGlowColor(NamedTextColor.WHITE)
                currentState = State.BUSCANDO
                consecutiveMisses = 0
                return@runAtFixedRate
            }
            val dir = target.location.toVector().subtract(parts[0].location.toVector()).normalize()
            moverTodo(parts[0].location.add(dir.multiply(1.2)), true)

            if (parts[0].location.distanceSquared(target.location) < 3.0) {
                ejecutarMuerte(target)
                task.cancel()
                setGlowColor(NamedTextColor.WHITE)
                currentState = State.BUSCANDO
            }
            ticks++
        }, 1L, 1L)
    }

    private fun ejecutarMuerte(victim: Player) {
        lastVictimUUID = victim.uniqueId
        victim.world.createExplosion(victim.location, 0f, false, false)
        victim.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 2f, 0.5f)

        // Lo eliminamos de la arena de TNT
        arena.eliminate(victim)

        plugin.server.broadcast(plugin.mm.deserialize("<red><b>[!]</b> <white>${victim.name} fue triturado por <dark_red>GEOFFREY 3.0"))
    }

    private fun moverTodo(baseLoc: Location, lookAtTarget: Boolean) {
        if (parts.isEmpty()) return
        val newLoc = baseLoc.clone()
        if (lookAtTarget && currentTarget != null) {
            newLoc.setDirection(currentTarget!!.location.toVector().subtract(baseLoc.toVector()))
        }
        parts.forEach { it.teleport(newLoc) }
    }

    fun explodeAndRemove() {
        if (parts.isNotEmpty()) {
            val loc = parts[0].location
            loc.world.createExplosion(loc, 0f, false, false)
        }
        remove()
    }

    fun remove() {
        isRunning = false
        parts.forEach { it.remove() }
        parts.clear()
    }
}

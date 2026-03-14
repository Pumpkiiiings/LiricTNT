package liric.tnt.game

import liric.tnt.LiricTNTPlugin
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.FallingBlock
import org.bukkit.util.Vector
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sin

private val mm = MiniMessage.miniMessage()
private const val cRed = "<#FFB7B2>"
private const val cWhite = "<#F8F8F8>"
private const val cYellow = "<#FDFD96>"
private const val cAqua = "<#B2E2F2>"
private const val cGray = "<#A9A9A9>"

/**
 * EVENTO 1: EL SUELO ES LAVA
 */
fun Arena.triggerLava(plugin: LiricTNTPlugin) {
    val title = Title.title(
        mm.deserialize("<#FF0000><b>¡EL SUELO ES LAVA!</b>"),
        mm.deserialize("$cWhite¡No te quedes quieto o te quemarás!")
    )

    val msg = mm.deserialize("<newline><#FF0000><b>¡EVENTO!</b> $cWhite El suelo comenzará a derretirse.<newline>")

    alivePlayers.forEach {
        it.showTitle(title)
        it.sendMessage(msg)
        it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.5f)
    }

    var ticks = 0
    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
        if (state != ArenaState.INGAME || ticks >= 300) { // 15 segundos
            task.cancel()
            return@runAtFixedRate
        }
        ticks += 5

        alivePlayers.forEach { p ->
            val blockLoc = p.location.clone().subtract(0.0, 0.1, 0.0)
            if (blockLoc.block.type.isSolid && blockLoc.block.type != Material.BEDROCK) {
                plugin.server.regionScheduler.runDelayed(plugin, blockLoc, { _ ->
                    if (blockLoc.block.type != Material.BEDROCK && blockLoc.block.type != Material.AIR) {
                        blockLoc.block.type = Material.LAVA
                    }
                }, 10L)
            }
        }
    }, 1L, 5L)
}

/**
 * EVENTO 2: TORNADO DESTRUCTIVO (Optimizado y Visual)
 * Ahora usa un embudo de humo ligero y se mueve fluidamente.
 */
fun Arena.triggerTornado(plugin: LiricTNTPlugin) {
    val title = Title.title(
        mm.deserialize("<#00FFFF><b>¡TORNADO!</b>"),
        mm.deserialize("$cWhite ¡Huye de la corriente de aire!")
    )

    val msg = mm.deserialize("<newline><#00FFFF><b>¡EVENTO!</b> $cWhite Un tornado nivel F5 se aproxima.<newline>")

    alivePlayers.forEach {
        it.showTitle(title)
        it.sendMessage(msg)
        it.playSound(it.location, Sound.ITEM_TRIDENT_THUNDER, 1f, 0.5f)
    }

    val tornadoCenter = spawns.randomOrNull()?.clone() ?: return
    var ticks = 0
    var angle = 0.0
    // Vector de movimiento constante (para que no tiemble)
    val direction = Vector(Math.random() - 0.5, 0.0, Math.random() - 0.5).normalize().multiply(0.4)

    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
        if (state != ArenaState.INGAME || ticks >= 300) { // 15 segundos
            task.cancel()
            return@runAtFixedRate
        }
        ticks += 2 // Ejecutar cada 2 ticks para no laguear

        // Mover el tornado de forma fluida y cambiar su giro ligeramente
        tornadoCenter.add(direction)
        direction.rotateAroundY(0.1 * (Math.random() - 0.5))

        plugin.server.regionScheduler.run(plugin, tornadoCenter) { _ ->
            val world = tornadoCenter.world

            // Renderizar un embudo en espiral (Visualmente hermoso y ligero)
            for (y in 0..15 step 2) {
                val radius = 0.5 + (y * 0.2) // Se ensancha arriba
                val offsetAngle = angle + y
                val cx = tornadoCenter.x + radius * cos(offsetAngle)
                val cz = tornadoCenter.z + radius * sin(offsetAngle)
                val particleLoc = Location(world, cx, tornadoCenter.y + y, cz)

                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, particleLoc, 2, 0.2, 0.2, 0.2, 0.02)
            }
            angle += 0.6 // Girar el tornado

            // Destruir únicamente el bloque de la base (Más ligero para el servidor)
            val baseBlock = tornadoCenter.block
            if (baseBlock.type != Material.AIR && baseBlock.type != Material.BEDROCK) {
                world.spawnParticle(Particle.BLOCK, tornadoCenter, 10, baseBlock.blockData)
                baseBlock.type = Material.AIR
            }

            // Físicas: Si estás muy cerca te tira para arriba, si estás en el borde te empuja lejos
            alivePlayers.forEach { p ->
                val dist = p.location.distanceSquared(tornadoCenter)
                if (dist < 49) { // Radio de 7 bloques
                    val flingDir = p.location.toVector().subtract(tornadoCenter.toVector()).normalize()

                    if (dist < 9) {
                        // En el centro del tornado: Vuelas alto
                        p.velocity = Vector(0.0, 1.5, 0.0)
                    } else {
                        // En los bordes: Te avienta lejos
                        p.velocity = flingDir.multiply(1.5).setY(0.5)
                    }
                    if (ticks % 10 == 0) p.playSound(p.location, Sound.ENTITY_BAT_TAKEOFF, 1f, 0.8f)
                }
            }
        }
    }, 1L, 2L)
}

/**
 * EVENTO 3: TORMENTA ELÉCTRICA (Telegrafiada y Equilibrada)
 * Ahora avisa en el piso con un círculo de fuego antes de que caiga el rayo.
 */
fun Arena.triggerStorm(plugin: LiricTNTPlugin) {
    val title = Title.title(
        mm.deserialize("<#FFFF00><b>¡TORMENTA ELÉCTRICA!</b>"),
        mm.deserialize("$cWhite ¡Esquiva las marcas de fuego en el suelo!")
    )

    val msg = mm.deserialize("<newline><#FFFF00><b>¡EVENTO!</b> $cWhite Mira el piso y esquiva los rayos.<newline>")

    alivePlayers.forEach {
        it.showTitle(title)
        it.sendMessage(msg)
        it.playSound(it.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 0.5f)
    }

    var ticks = 0
    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
        if (state != ArenaState.INGAME || ticks >= 400) { // 20 segundos
            task.cancel()
            return@runAtFixedRate
        }
        ticks += 15 // Cae un rayo cada 0.75 segundos

        val target = alivePlayers.randomOrNull() ?: return@runAtFixedRate
        val strikeLoc = target.location.clone().add((Math.random() - 0.5) * 6, 0.0, (Math.random() - 0.5) * 6)

        // Ajustar al suelo
        strikeLoc.y = strikeLoc.world.getHighestBlockYAt(strikeLoc.blockX, strikeLoc.blockZ).toDouble() + 1

        plugin.server.regionScheduler.run(plugin, strikeLoc) { _ ->
            val world = strikeLoc.world

            // 1. Advertencia Visual (Telegrafiar el ataque)
            for (i in 0..360 step 30) {
                val rad = Math.toRadians(i.toDouble())
                world.spawnParticle(Particle.FLAME, strikeLoc.clone().add(cos(rad) * 1.5, 0.1, sin(rad) * 1.5), 1, 0.0, 0.0, 0.0, 0.0)
            }
            world.playSound(strikeLoc, Sound.ENTITY_CREEPER_PRIMED, 1f, 0.5f)

            // 2. Caída del rayo 1 segundo después (20 ticks)
            plugin.server.regionScheduler.runDelayed(plugin, strikeLoc, { _ ->
                if (state == ArenaState.INGAME) {
                    world.strikeLightning(strikeLoc)
                }
            }, 20L)
        }
    }, 10L, 15L)
}

/**
 * EVENTO 4: LLUVIA DE YUNQUES (NUEVO)
 * Caen yunques del cielo. Avisan con humo negro en el piso donde van a caer.
 */
fun Arena.triggerAnvils(plugin: LiricTNTPlugin) {
    val title = Title.title(
        mm.deserialize("$cGray<b>¡LLUVIA DE YUNQUES!</b>"),
        mm.deserialize("$cWhite ¡Mira hacia arriba!")
    )
    val msg = mm.deserialize("<newline>$cGray<b>¡EVENTO!</b> $cWhite Cuidado con la cabeza...<newline>")

    alivePlayers.forEach {
        it.showTitle(title)
        it.sendMessage(msg)
        it.playSound(it.location, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f)
    }

    var ticks = 0
    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
        if (state != ArenaState.INGAME || ticks >= 300) { // 15 segundos
            task.cancel()
            return@runAtFixedRate
        }
        ticks += 10 // Cae un yunque cada 0.5s

        val target = alivePlayers.randomOrNull() ?: return@runAtFixedRate
        // El yunque spawnea 15 bloques por encima de ellos
        val spawnLoc = target.location.clone().add((Math.random() - 0.5) * 5, 15.0, (Math.random() - 0.5) * 5)

        plugin.server.regionScheduler.run(plugin, spawnLoc) { _ ->
            val world = spawnLoc.world

            // Calcular dónde va a caer (suelo)
            val groundY = world.getHighestBlockYAt(spawnLoc.blockX, spawnLoc.blockZ).toDouble() + 1
            val shadowLoc = Location(world, spawnLoc.x, groundY, spawnLoc.z)

            // Partículas de "Sombra" en el suelo para que los jugadores lo esquiven
            world.spawnParticle(Particle.SMOKE, shadowLoc, 20, 0.5, 0.1, 0.5, 0.0)
            world.playSound(shadowLoc, Sound.ENTITY_TNT_PRIMED, 0.5f, 2f)

            // Spawnear el Yunque real
            val anvil: FallingBlock = world.spawnFallingBlock(spawnLoc, Material.DAMAGED_ANVIL.createBlockData())
            anvil.dropItem = false
            anvil.setHurtEntities(true) // Daño letal nativo de Minecraft

            // Borrar el yunque después de 3 segundos para que no ensucie la arena
            plugin.server.regionScheduler.runDelayed(plugin, spawnLoc, { _ ->
                if (anvil.isValid && !anvil.isDead) anvil.remove()

                // Limpiar el bloque si ya aterrizó
                val landedBlock = world.getBlockAt(shadowLoc)
                if (landedBlock.type.name.contains("ANVIL")) {
                    landedBlock.type = Material.AIR
                    world.spawnParticle(Particle.BLOCK, shadowLoc, 15, Material.DAMAGED_ANVIL.createBlockData())
                    world.playSound(shadowLoc, Sound.BLOCK_ANVIL_DESTROY, 1f, 1f)
                }
            }, 60L)
        }
    }, 10L, 10L)
}

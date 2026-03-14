package liric.tnt.listener

import liric.tnt.LiricTNTPlugin
import liric.tnt.game.ArenaState
import liric.tnt.game.TntSpleef
import liric.tnt.game.TntTag
import liric.tnt.spectator.SpectatorManager
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Arrow
import org.bukkit.entity.Fireball
import org.bukkit.entity.Player
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.persistence.PersistentDataType

class GameListener(private val plugin: LiricTNTPlugin) : Listener {

    private val mm = MiniMessage.miniMessage()

    /**
     * Lógica de TNT TAG: Pasar la TNT al golpear
     * Al NO cancelar el evento, Minecraft aplica su Knockback Vanilla.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPunch(e: EntityDamageByEntityEvent) {
        val attacker = e.damager as? Player ?: return
        val victim = e.entity as? Player ?: return

        val arena = plugin.arenaManager.getArena(attacker)
        if (arena is TntTag && arena.state == ArenaState.INGAME) {

            e.damage = 0.001

            arena.handlePunch(attacker, victim)
        }
    }

    /**
     * Interacción con Ítems (Espectador y Habilidades)
     */
    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (!event.action.isRightClick) return

        val player = event.player
        val item = event.item ?: return
        val arena = plugin.arenaManager.getArena(player) ?: return

        // 1. Brújula de Espectador
        if (item.type == Material.COMPASS && arena.spectators.contains(player)) {
            SpectatorManager.openGui(player, arena)
            event.isCancelled = true
            return
        }

        // 2. Ítems de Juego
        if (arena.state != ArenaState.INGAME) return

        val key = NamespacedKey(plugin, "tnt_item")
        val type = item.itemMeta?.persistentDataContainer?.get(key, PersistentDataType.STRING) ?: return

        when (type) {
            "fireball" -> {
                event.isCancelled = true
                item.subtract(1)
                player.launchProjectile(Fireball::class.java).apply {
                    yield = 0f
                    setIsIncendiary(false)
                }
                player.playSound(player.location, Sound.ENTITY_GHAST_SHOOT, 1f, 1f)
            }
            "feather" -> {
                // Cooldown de 5 segundos (100 ticks)
                if (player.hasCooldown(Material.FEATHER)) {
                    player.sendActionBar(mm.deserialize("<#FF0000><b>✘</b> <#FFFFFF>¡Espera 5 segundos para volver a usar la habilidad!"))
                    event.isCancelled = true
                    return
                }

                event.isCancelled = true
                item.subtract(1) // La pluma de TntRun tiene usos limitados
                player.setCooldown(Material.FEATHER, 100)

                player.velocity = player.location.direction.multiply(1.2).setY(0.8)
                player.playSound(player.location, Sound.ENTITY_BAT_TAKEOFF, 1f, 1.2f)
                player.sendActionBar(mm.deserialize("<#00FFFF><b>⚡</b> <#FFFFFF>¡SÚPER SALTO ACTIVADO!"))
            }
            "dash" -> {
                // Cooldown de 30 segundos (600 ticks)
                if (player.hasCooldown(Material.SUGAR)) {
                    player.sendActionBar(mm.deserialize("<#FF0000><b>✘</b> <#FFFFFF>Habilidad en recarga..."))
                    event.isCancelled = true
                    return
                }

                event.isCancelled = true
                player.setCooldown(Material.SUGAR, 600)

                // Vector de Dash hacia adelante
                val dashVector = player.location.direction.normalize().multiply(1.8).setY(0.3)
                player.velocity = dashVector

                player.playSound(player.location, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.5f)
                player.world.spawnParticle(org.bukkit.Particle.CLOUD, player.location, 20, 0.5, 0.5, 0.5, 0.1)
                player.sendActionBar(mm.deserialize("<#00FFFF><b>💨</b> <#FFFFFF>¡DASH EVASIVO!"))
            }
            "doublejump" -> {
                // Cooldown de 30 segundos (600 ticks)
                if (player.hasCooldown(Material.RABBIT_FOOT)) {
                    player.sendActionBar(mm.deserialize("<#FF0000><b>✘</b> <#FFFFFF>Habilidad en recarga..."))
                    event.isCancelled = true
                    return
                }

                event.isCancelled = true
                player.setCooldown(Material.RABBIT_FOOT, 600)

                // Vector de Doble Salto (hacia arriba)
                val jumpVector = player.location.direction.multiply(0.5).setY(1.3)
                player.velocity = jumpVector

                player.playSound(player.location, Sound.ENTITY_BAT_TAKEOFF, 1f, 1f)
                player.world.spawnParticle(org.bukkit.Particle.POOF, player.location, 15, 0.2, 0.1, 0.2, 0.05)
                player.sendActionBar(mm.deserialize("<#00FF00><b>⚡</b> <#FFFFFF>¡DOBLE SALTO!"))
            }
            "pearl" -> {
                player.playSound(player.location, Sound.ENTITY_ENDER_PEARL_THROW, 1f, 1f)
            }
        }
    }

    /**
     * EVITAR DROPEAR ITEMS DE JUEGO
     */
    @EventHandler
    fun onDrop(e: PlayerDropItemEvent) {
        val item = e.itemDrop.itemStack
        val meta = item.itemMeta ?: return
        val key = NamespacedKey(plugin, "tnt_item")

        if (meta.persistentDataContainer.has(key, PersistentDataType.STRING)) {
            e.isCancelled = true
        }
    }

    /**
     * EVITAR MOVER ITEMS EN EL INVENTARIO
     */
    @EventHandler
    fun onClick(e: InventoryClickEvent) {
        val item = e.currentItem ?: return
        val meta = item.itemMeta ?: return
        val key = NamespacedKey(plugin, "tnt_item")

        if (meta.persistentDataContainer.has(key, PersistentDataType.STRING)) {
            e.isCancelled = true
        }
    }

    /**
     * EFECTOS DE PROYECTILES (Fireball y Flechas de Spleef)
     */
    @EventHandler
    fun onHit(event: ProjectileHitEvent) {
        val proj = event.entity
        val shooter = proj.shooter as? Player ?: return
        val arena = plugin.arenaManager.getArena(shooter)

        // 1. Destrucción 3x3 de la Fireball
        if (proj is Fireball) {
            val loc = event.hitBlock?.location ?: event.hitEntity?.location ?: return

            plugin.server.regionScheduler.run(plugin, loc) { _ ->
                val world = loc.world
                val blockX = loc.blockX
                val blockY = loc.blockY
                val blockZ = loc.blockZ

                for (x in -1..1) {
                    for (z in -1..1) {
                        val target = world.getBlockAt(blockX + x, blockY, blockZ + z)
                        if (target.type != Material.AIR && target.type != Material.BEDROCK) {
                            target.type = Material.AIR
                        }
                    }
                }
                world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, loc, 1)
                world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.5f)
            }
        }

        // 2. Encendido de TNT con el Arco en Tnt Spleef
        else if (proj is Arrow) {
            val block = event.hitBlock ?: return

            if (arena is TntSpleef && arena.state == ArenaState.INGAME) {
                if (block.type == Material.TNT) {
                    plugin.server.regionScheduler.run(plugin, block.location) { _ ->
                        block.type = Material.AIR
                        // Generamos la TNT encendida
                        val tnt = block.world.spawn(block.location.add(0.5, 0.0, 0.5), TNTPrimed::class.java)
                        tnt.fuseTicks = 20 // ¡Explota en 1 segundo!
                        proj.remove()
                    }
                }
            }
        }
    }

    /**
     * PROTEGER CONTRA EL DAÑO DE EXPLOSIÓN EN SPLEEF
     * Queremos que la TNT los empuje, pero no que los mate a daño (solo mueren al caer).
     */
    @EventHandler(priority = EventPriority.HIGH)
    fun onExplosionDamage(e: EntityDamageEvent) {
        val player = e.entity as? Player ?: return
        val arena = plugin.arenaManager.getArena(player) ?: return

        if (arena is TntSpleef && arena.state == ArenaState.INGAME) {
            if (e.cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION || e.cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
                e.damage = 0.001 // Cero daño letal, pero aplica el empuje (Knockback)
            }
        }
    }

    /**
     * PREVENIR MUERTE POR EXTRAS (Lava, Rayos, Caídas de Tornado)
     * En lugar de morir, se curan y pasan a espectador automáticamente.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onLethalDamage(e: EntityDamageEvent) {
        val player = e.entity as? Player ?: return
        val arena = plugin.arenaManager.getArena(player) ?: return

        if (arena.state == ArenaState.INGAME) {
            // Ignoramos el Spleef aquí porque queremos que el vacío sí los mate y pase por este filtro.
            // Si el daño los mataría...
            if (player.health - e.finalDamage <= 0) {
                e.isCancelled = true
                player.health = 20.0
                player.fireTicks = 0

                // Efecto de muerte controlado
                player.world.spawnParticle(org.bukkit.Particle.SOUL, player.location, 20, 0.5, 1.0, 0.5, 0.1)
                arena.eliminate(player)
            }
        }
    }
}

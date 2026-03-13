package liric.tnt.listener

import liric.tnt.LiricTNTPlugin
import liric.tnt.game.ArenaState
import liric.tnt.game.TntTag
import liric.tnt.spectator.SpectatorManager
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Fireball
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
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
     * Usamos HIGHEST para que actúe por encima de protecciones (WorldGuard, etc.)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onPunch(e: EntityDamageByEntityEvent) {
        val attacker = e.damager as? Player ?: return
        val victim = e.entity as? Player ?: return

        val arena = plugin.arenaManager.getArena(attacker)
        if (arena is TntTag && arena.state == ArenaState.INGAME) {
            e.isCancelled = true // Evitar daño a la vida real

            // 1. Eliminar los ticks de invulnerabilidad para que se pueda spamear el click
            victim.noDamageTicks = 0

            // 2. Empuje (Knockback) manual
            val direction = attacker.location.direction.setY(0.0).normalize()
            victim.velocity = direction.multiply(0.5).setY(0.25)

            // 3. Llamamos a la lógica de la arena
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
                // Cooldown de 15 segundos (300 ticks)
                if (player.hasCooldown(Material.FEATHER)) {
                    player.sendActionBar(mm.deserialize("<#FF0000><b>✘</b> <#FFFFFF>¡Espera 15 segundos para volver a usar la habilidad!"))
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

    @EventHandler
    fun onDrop(e: PlayerDropItemEvent) {
        val item = e.itemDrop.itemStack
        val meta = item.itemMeta ?: return
        val key = NamespacedKey(plugin, "tnt_item")

        // Si tiene el tag de item del minijuego, no se puede tirar
        if (meta.persistentDataContainer.has(key, PersistentDataType.STRING)) {
            e.isCancelled = true
        }
    }

    // --- EVITAR MOVER ITEMS EN EL INVENTARIO ---
    @EventHandler
    fun onClick(e: InventoryClickEvent) {
        val item = e.currentItem ?: return
        val meta = item.itemMeta ?: return
        val key = NamespacedKey(plugin, "tnt_item")

        // Si intentan mover un item del minijuego en el inventario, se cancela
        if (meta.persistentDataContainer.has(key, PersistentDataType.STRING)) {
            e.isCancelled = true
        }
    }

    /**
     * Efecto de la Fireball: Destrucción 3x3
     */
    @EventHandler
    fun onHit(event: ProjectileHitEvent) {
        val proj = event.entity
        if (proj is Fireball && proj.shooter is Player) {
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
    }
}

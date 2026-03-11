package liric.tnt.utils

import liric.tnt.LiricTNTPlugin
import net.kyori.adventure.sound.SoundStop
import net.kyori.adventure.key.Key as AdventureKey
import net.kyori.adventure.sound.Sound as AdventureSound
import net.momirealms.craftengine.bukkit.item.BukkitItemManager
import net.momirealms.craftengine.core.util.Key as CraftEngineKey
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 *[LIRIC-TNT 2.0]
 * CraftEngineUtils: El puente definitivo para ítems y MÚSICA custom.
 * Optimizado para Paper/Folia 1.21.4 con Adventure API.
 */
object CraftEngineUtils {

    private lateinit var plugin: LiricTNTPlugin

    /**
     * Inicializa el motor (Llámalo en el onEnable de tu LiricTNTPlugin)
     * Ej: CraftEngineUtils.init(this)
     */
    fun init(plugin: LiricTNTPlugin) {
        this.plugin = plugin
    }

    /**
     * Checa si el motor de CraftEngine está activo en el servidor.
     */
    fun isAvailable(): Boolean = Bukkit.getPluginManager().isPluginEnabled("CraftEngine")

    // ==========================================
    // SISTEMA DE ÍTEMS CUSTOM
    // ==========================================

    /**
     * Resuelve el ítem buscando primero en CraftEngine y luego en Vanilla.
     */
    fun getCustomItem(property: String?): ItemStack? {
        if (property.isNullOrBlank() || property.equals("none", ignoreCase = true)) {
            return null
        }

        // 1. Intento por CraftEngine (si tiene el formato namespace:id)
        if (property.contains(":") && isAvailable()) {
            try {
                val split = property.split(":", limit = 2)
                if (split.size == 2) {
                    val key = CraftEngineKey.of(split[0], split[1])
                    val optionalItem = BukkitItemManager.instance().getCustomItem(key)

                    if (optionalItem.isPresent) {
                        return optionalItem.get().buildItemStack()
                    }
                }
            } catch (e: Exception) {
                plugin.componentLogger.warn(plugin.mm.deserialize("<red>Fallo crítico al pedir ítem a CraftEngine: <white>$property"))
            }
        }

        // 2. Fallback Vanilla
        val mat = Material.matchMaterial(property.uppercase())

        return if (mat != null && mat != Material.AIR) {
            ItemStack(mat)
        } else {
            if (!property.contains(":")) {
                plugin.componentLogger.warn(plugin.mm.deserialize("<yellow>¡Aviso! No se encontró el material vanilla: <white>$property"))
            }
            null
        }
    }

    /**
     * Versión segura: Si no encuentra el ítem, te da una barrera holográfica con el error.
     */
    fun getCustomItemSafe(property: String?): ItemStack {
        val item = getCustomItem(property)
        if (item != null) return item

        return ItemStack(Material.BARRIER).apply {
            editMeta { meta ->
                meta.displayName(plugin.mm.deserialize("<red><bold>ERROR DE ÍTEM</bold>"))
                meta.lore(listOf(
                    plugin.mm.deserialize("<gray>No se encontró: <white>$property"),
                    plugin.mm.deserialize("<gray>Revisa la config del minijuego.")
                ))
            }
        }
    }

    // ==========================================
    // SISTEMA DE MÚSICA Y SONIDOS CUSTOM
    // ==========================================

    /**
     * Reproduce un sonido/música. Si tiene ':', lo reproduce como custom del ResourcePack.
     * Si no tiene ':', intenta buscarlo en los sonidos Vanilla.
     */
    fun playSound(player: Player, soundName: String?, volume: Float = 1f, pitch: Float = 1f) {
        if (soundName.isNullOrBlank() || soundName.equals("none", ignoreCase = true)) return

        if (soundName.contains(":")) {
            // Audio Custom (Adventure API)
            try {
                val split = soundName.split(":", limit = 2)
                val key = AdventureKey.key(split[0], split[1])
                val customSound = AdventureSound.sound(key, AdventureSound.Source.MUSIC, volume, pitch)
                player.playSound(customSound)
            } catch (e: Exception) {
                plugin.componentLogger.warn(plugin.mm.deserialize("<red>No se pudo reproducir el sonido custom: <white>$soundName"))
            }
        } else {
            // Audio Vanilla
            try {
                val vanillaSound = org.bukkit.Sound.valueOf(soundName.uppercase())
                // USAMOS LA CATEGORÍA 'MUSIC' PARA PODER DETENERLO LUEGO CON stopAllMusic()
                player.playSound(player.location, vanillaSound, SoundCategory.MUSIC, volume, pitch)
            } catch (e: Exception) {
                plugin.componentLogger.warn(plugin.mm.deserialize("<yellow>Sonido vanilla no encontrado: <white>$soundName"))
            }
        }
    }

    /**
     * DETIENE una pista de música específica para un jugador.
     * Espectacular para apagar la música de tensión cuando termina la ronda.
     */
    fun stopSound(player: Player, soundName: String?) {
        if (soundName.isNullOrBlank() || soundName.equals("none", ignoreCase = true)) return

        if (soundName.contains(":")) {
            // Detener Audio Custom
            try {
                val split = soundName.split(":", limit = 2)
                val key = AdventureKey.key(split[0], split[1])
                player.stopSound(SoundStop.named(key))
            } catch (e: Exception) {
                // Ignorar si el formato es inválido al detener
            }
        } else {
            // Detener Audio Vanilla
            try {
                val vanillaSound = org.bukkit.Sound.valueOf(soundName.uppercase())
                // MÁS SEGURO: Pasamos el Enum directamente con la categoría, ¡cero warnings de deprecación!
                player.stopSound(vanillaSound, SoundCategory.MUSIC)
            } catch (e: Exception) { }
        }
    }

    /**
     * Detiene TODA la música y sonidos personalizados (Source.MUSIC) sonando actualmente.
     */
    fun stopAllMusic(player: Player) {
        player.stopSound(SoundStop.source(AdventureSound.Source.MUSIC))
    }
}

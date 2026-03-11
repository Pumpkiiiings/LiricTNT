package liric.tnt.managers

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI
import com.infernalsuite.asp.api.loaders.SlimeLoader
import com.infernalsuite.asp.api.world.properties.SlimeProperties
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap
import com.infernalsuite.asp.loaders.file.FileLoader
import liric.tnt.LiricTNTPlugin
import liric.tnt.game.Arena
import liric.tnt.game.TntRun
import liric.tnt.game.TntTag
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class ArenaManager(private val plugin: LiricTNTPlugin) {

    private val asp = AdvancedSlimePaperAPI.instance()
    private val fileLoader: SlimeLoader
    private val mm = MiniMessage.miniMessage()

    private val pBlue = "#B2E2F2"
    private val pGreen = "#00FF00"
    private val pPurple = "#FF00FF"
    private val pRed = "#FF0000"

    private val arenasFile = File(plugin.dataFolder, "arenas.yml")
    private var arenasConfig = YamlConfiguration.loadConfiguration(arenasFile)

    // 🔥 EL SECRETO DEL ÉXITO: Separar las plantillas de los juegos activos
    private val templates = ConcurrentHashMap<String, ArenaTemplate>()
    val activeArenas = mutableMapOf<String, Arena>() // Aquí van las instanciadas

    var mainLobby: Location? = null
    var waitingSpawn: Location? = null

    // Clase de datos para guardar la configuración en memoria sin cargar mundos
    data class ArenaTemplate(val name: String, var type: String, val spawns: MutableList<Location> = mutableListOf())

    init {
        val slimeFolder = File(plugin.dataFolder, "slime_worlds")
        if (!slimeFolder.exists()) slimeFolder.mkdirs()
        this.fileLoader = FileLoader(slimeFolder)

        loadGlobalLocations()
    }

    /**
     * Paso 1: Cargar PLANTILLAS desde el YAML sin instanciar mundos todavía.
     */
    fun loadStoredArenas() {
        val section = arenasConfig.getConfigurationSection("arenas") ?: return
        val keys = section.getKeys(false)

        plugin.componentLogger.info(mm.deserialize("<$pBlue>[ArenaManager] Cargando <white>${keys.size}</white> plantillas base..."))

        for (name in keys) {
            val type = arenasConfig.getString("arenas.$name.type") ?: "tag"
            val template = ArenaTemplate(name, type)

            // Cargamos los spawns con la Magia Anti-Leaks (Mundo Null)
            val spawnsSection = arenasConfig.getConfigurationSection("arenas.$name.spawns")
            if (spawnsSection != null) {
                for (key in spawnsSection.getKeys(false)) {
                    val x = spawnsSection.getDouble("$key.x")
                    val y = spawnsSection.getDouble("$key.y")
                    val z = spawnsSection.getDouble("$key.z")
                    val yaw = spawnsSection.getDouble("$key.yaw").toFloat()
                    val pitch = spawnsSection.getDouble("$key.pitch").toFloat()
                    template.spawns.add(Location(null, x, y, z, yaw, pitch))
                }
            }
            templates[name] = template
            plugin.componentLogger.info(mm.deserialize("<$pGreen>[ArenaManager] Plantilla <white>$name</white> lista en memoria."))
        }
    }

    /**
     * Guarda una plantilla en el archivo YAML (Se usa al crear o añadir spawns).
     */
    private fun saveTemplateToConfig(template: ArenaTemplate) {
        val path = "arenas.${template.name}"
        arenasConfig.set("$path.type", template.type)

        arenasConfig.set("$path.spawns", null) // Limpiar viejos
        template.spawns.forEachIndexed { index, loc ->
            val spawnPath = "$path.spawns.$index"
            arenasConfig.set("$spawnPath.x", loc.x)
            arenasConfig.set("$spawnPath.y", loc.y)
            arenasConfig.set("$spawnPath.z", loc.z)
            arenasConfig.set("$spawnPath.yaw", loc.yaw.toDouble())
            arenasConfig.set("$spawnPath.pitch", loc.pitch.toDouble())
        }
        arenasConfig.save(arenasFile)
    }

    /**
     * Paso 2: Crear un mapa e instanciar la Arena real basándose en la Plantilla.
     */
    fun setupArena(templateName: String, type: String? = null): CompletableFuture<Arena?> {
        val future = CompletableFuture<Arena?>()

        // 1. Verificar si la plantilla existe
        val template = templates[templateName] ?: run {
            // Si no existe, la creamos (ej: desde el comando /tnt arena create)
            val newTemplate = ArenaTemplate(templateName, type ?: "tag")
            templates[templateName] = newTemplate
            saveTemplateToConfig(newTemplate)
            newTemplate
        }

        val instanceName = "${templateName}_game_${System.currentTimeMillis()}"

        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            try {
                if (!fileLoader.worldExists(templateName)) {
                    plugin.componentLogger.error(mm.deserialize("<$pRed>[ArenaManager] Archivo .slime '$templateName' no encontrado en la carpeta slime_worlds."))
                    future.complete(null)
                    return@runNow
                }

                val props = SlimePropertyMap().apply {
                    setValue(SlimeProperties.ALLOW_ANIMALS, false)
                    setValue(SlimeProperties.ALLOW_MONSTERS, false)
                    setValue(SlimeProperties.PVP, true)
                    setValue(SlimeProperties.WORLD_TYPE, "flat")
                }

                val slimeTemplate = asp.readWorld(fileLoader, templateName, true, props)
                val worldInstance = slimeTemplate.clone(instanceName)

                plugin.server.globalRegionScheduler.execute(plugin) {
                    try {
                        val instance = asp.loadWorld(worldInstance, false)
                        val bukkitWorld = instance.bukkitWorld

                        if (bukkitWorld == null) {
                            future.complete(null)
                            return@execute
                        }

                        bukkitWorld.apply {
                            isAutoSave = false
                            setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
                            setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
                            setGameRule(GameRule.DO_WEATHER_CYCLE, false)
                            setGameRule(GameRule.FALL_DAMAGE, false)
                            setGameRule(GameRule.DO_MOB_SPAWNING, false)
                            setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false)
                            setGameRule(GameRule.DO_FIRE_TICK, false)
                        }

                        val arena: Arena = when (template.type.lowercase()) {
                            "tag" -> TntTag(plugin, instanceName) // Usar instanceName, no templateName
                            "run" -> TntRun(plugin, instanceName)
                            else -> TntTag(plugin, instanceName)
                        }

                        // INYECCIÓN: Pasamos las locations de la plantilla a la arena activa y les asignamos el mundo clonado
                        template.spawns.forEach { loc ->
                            arena.spawns.add(Location(bukkitWorld, loc.x, loc.y, loc.z, loc.yaw, loc.pitch))
                        }

                        activeArenas[instanceName] = arena // Registramos la instancia, no la plantilla

                        plugin.componentLogger.info(mm.deserialize("<$pGreen>[ArenaManager] Instancia creada: <$pBlue>${bukkitWorld.name}</$pBlue> <$pPurple>[${template.type.uppercase()}]</$pPurple>"))
                        future.complete(arena)

                    } catch (e: Exception) {
                        e.printStackTrace()
                        future.complete(null)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                future.complete(null)
            }
        }
        return future
    }

    /**
     * Añade un spawn a una PLANTILLA.
     * Importante: Pasarle el templateName (ej: "Mapa1"), no la instancia activa.
     */
    fun addSpawn(templateName: String, location: Location) {
        val template = templates[templateName]
        if (template != null) {
            val cleanLoc = Location(null, location.x, location.y, location.z, location.yaw, location.pitch)
            template.spawns.add(cleanLoc)
            saveTemplateToConfig(template)
        } else {
            plugin.componentLogger.warn(mm.deserialize("<$pRed>[ArenaManager] No se pudo añadir spawn: Plantilla $templateName no encontrada."))
        }
    }

    /**
     * Descarga y elimina por completo una instancia de arena (Regeneración)
     */
    fun unloadArena(instanceName: String) {
        val arena = activeArenas.remove(instanceName) ?: return
        val world = arena.spawns.firstOrNull()?.world ?: Bukkit.getWorld(instanceName)

        if (world != null) {
            plugin.server.globalRegionScheduler.execute(plugin) {
                Bukkit.unloadWorld(world, false) // false = NO GUARDAR CAMBIOS (Regeneración)
                plugin.componentLogger.info(mm.deserialize("<$pBlue>[ArenaManager] Mundo $instanceName destruido (Se regenerará limpio)."))
            }
        }
    }

    // --- MANEJO DEL LOBBY ---

    fun setMainLobbyLoc(loc: Location) {
        this.mainLobby = loc
        arenasConfig.set("global.mainLobby", loc)
        arenasConfig.save(arenasFile)
    }

    fun setWaitingSpawnLoc(loc: Location) {
        this.waitingSpawn = loc
        arenasConfig.set("global.waitingSpawn", loc)
        arenasConfig.save(arenasFile)
    }

    private fun loadGlobalLocations() {
        val mainWorld = plugin.server.worlds.firstOrNull()
        (arenasConfig.get("global.mainLobby") as? Location)?.let {
            it.world = mainWorld
            mainLobby = it
        }
        (arenasConfig.get("global.waitingSpawn") as? Location)?.let {
            it.world = mainWorld
            waitingSpawn = it
        }
    }

    /**
     * Obtiene una arena ACTIVA (para comandos de detener, etc.)
     */
    fun getArenaByName(instanceName: String): Arena? = activeArenas[instanceName]

    /**
     * Obtiene en qué arena ACTIVA está jugando un jugador.
     */
    fun getArena(player: Player): Arena? = activeArenas.values.find {
        it.alivePlayers.contains(player) || it.spectators.contains(player)
    }
}

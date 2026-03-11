package liric.tnt

import com.github.retrooper.packetevents.PacketEvents
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import liric.tnt.command.TntCommand
import liric.tnt.command.GamemodeCommand
import liric.tnt.managers.ArenaManager
import liric.tnt.managers.PlayerManager
import liric.tnt.managers.ChatFormatManager
import liric.tnt.managers.MessageManager // <-- Nuevo Manager
import liric.tnt.board.BoardManager
import liric.tnt.listener.GameListener
import liric.tnt.listener.ChatListener
import liric.tnt.listener.WorldProtectionListener
import liric.tnt.listener.DeathListener
import liric.tnt.utils.CraftEngineUtils
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.plugin.java.JavaPlugin

class LiricTNTPlugin : JavaPlugin() {

    companion object {
        lateinit var instance: LiricTNTPlugin
            private set
    }

    lateinit var arenaManager: ArenaManager
    lateinit var playerManager: PlayerManager
    lateinit var boardManager: BoardManager
    lateinit var chatFormatManager: ChatFormatManager
    lateinit var messageManager: MessageManager // <-- Declaración añadida
    val mm = MiniMessage.miniMessage()

    override fun onLoad() {
        instance = this
        // Setup de PacketEvents
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this))
        PacketEvents.getAPI().settings.reEncodeByDefault(false).bStats(true)
        PacketEvents.getAPI().load()
    }

    override fun onEnable() {
        PacketEvents.getAPI().init()
        saveDefaultConfig()

        server.worlds.forEach { world ->
            world.setGameRule(org.bukkit.GameRule.DO_IMMEDIATE_RESPAWN, true)
        }

        // 👉 Inicialización del Motor de Mensajes (Colores + PAPI) 👈
        messageManager = MessageManager()

        // Inicialización del Motor de Ítems y Música Custom
        CraftEngineUtils.init(this)

        // Inicialización de Managers
        chatFormatManager = ChatFormatManager(this)
        arenaManager = ArenaManager(this)
        playerManager = PlayerManager(this)
        boardManager = BoardManager(this)

        // LLAMADA AL ARENAS.YML
        arenaManager.loadStoredArenas()

        // Registrar Comandos Brigadier (Nueva API Paper 1.21+)
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            TntCommand.register(event.registrar(), this)
            GamemodeCommand.register(event.registrar(), this) // <--- ¡AQUÍ!
        }

        // Registrar Eventos
        val pm = server.pluginManager
        pm.registerEvents(GameListener(this), this)
        pm.registerEvents(ChatListener(this), this)
        pm.registerEvents(WorldProtectionListener(this), this)
        pm.registerEvents(DeathListener(this), this)

        // Tareas de Scoreboard/Tab
        boardManager.startTasks()

        // Mensaje de arranque FOSFOROLOCO
        sendBrandingMessage()
    }

    override fun onDisable() {
        PacketEvents.getAPI().terminate()
    }

    private fun sendBrandingMessage() {
        val console = server.consoleSender
        val branding = """
            <gray>  » <green>Creado por: <white>Pumpkingz</white></gray>
            <gray>  » <green>Versión: <white>${description.version}</white></gray>
            <gray>  » <green>Estado: <aqua>Folia-Ready & Paper 1.21.4+</aqua></gray>
            <gray>  » <yellow>¡El evento de TNT está listo para explotar!</yellow>
            <newline>
        """.trimIndent()

        console.sendMessage(mm.deserialize(branding))
    }
}
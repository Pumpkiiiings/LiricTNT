package liric.tnt.command

import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import liric.tnt.LiricTNTPlugin
import liric.tnt.spectator.SpectatorManager
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

object TntCommand {
    private val mm = MiniMessage.miniMessage()

    private const val cRed = "#FF0000"
    private const val cGreen = "#00FF00"
    private const val cAqua = "#00FFFF"
    private const val cYellow = "#FFFF00"
    private const val cPurple = "#FF00FF"
    private const val cWhite = "#FFFFFF"

    private const val prefix = "⚡ <bold><$cRed>TNT</$cRed> <$cYellow>EVENT</$cYellow></bold> <$cWhite>»</$cWhite> "

    fun register(commands: io.papermc.paper.command.brigadier.Commands, plugin: LiricTNTPlugin) {
        val root = Commands.literal("tnt")
            .requires { it.sender.hasPermission("tnt.admin") }

        val help = Commands.literal("help").executes { ctx ->
            val s = ctx.source.sender
            s.sendMessage(mm.deserialize(""))
            s.sendMessage(mm.deserialize("⚡ <bold><$cRed>TNT ASISTENCIA</$cRed></bold> <$cWhite>v1.2 ⚡"))
            s.sendMessage(mm.deserialize(""))
            s.sendMessage(mm.deserialize("<$cGreen>🗡 /tnt setup lobby</$cGreen> <$cWhite>» Lobby Global"))
            s.sendMessage(mm.deserialize("<$cGreen>🗡 /tnt arena create</$cGreen> <$cWhite>» Crear Plantilla"))
            s.sendMessage(mm.deserialize("<$cAqua>🔥 /tnt iniciar</$cAqua> <$cWhite>» Clonar e Iniciar Juego"))
            s.sendMessage(mm.deserialize("<$cRed>🛑 /tnt detener</$cRed> <$cWhite>» Destruir Instancia"))
            s.sendMessage(mm.deserialize("<$cYellow>🔔 /tnt espectar</$cYellow> <$cWhite>» Modo Cámara"))
            s.sendMessage(mm.deserialize("<$cPurple>🍖 /tnt revivir</$cPurple> <$cWhite>» Reincorporar"))
            s.sendMessage(mm.deserialize("<$cRed>☠ /tnt descalificar</$cRed> <$cWhite>» Expulsar"))
            s.sendMessage(mm.deserialize("<$cGreen>🧪 /tnt reload</$cGreen> <$cWhite>» Recargar Todo"))
            s.sendMessage(mm.deserialize(""))
            s.sendMessage(mm.deserialize("<$cWhite>Creado por: <$cPurple><bold>Pumpkingz</bold>"))
            s.sendMessage(mm.deserialize(""))
            1
        }

        val reload = Commands.literal("reload")
            .requires { it.sender.hasPermission("tnt.admin.reload") }
            .executes { ctx ->
                plugin.reloadConfig()
                plugin.boardManager.reload()
                plugin.arenaManager.loadStoredArenas() // Recargar plantillas de arenas.yml
                ctx.source.sender.sendMessage(mm.deserialize("$prefix<$cGreen>✔ <$cWhite>Configuración y Plantillas recargadas <$cGreen>🧪"))
                1
            }

        val setup = Commands.literal("setup")
            .requires { it.sender.hasPermission("tnt.admin.setup") }
            .then(Commands.literal("lobby").executes { ctx ->
                val sender = ctx.source.sender as? Player ?: return@executes 0
                plugin.arenaManager.setMainLobbyLoc(sender.location)
                sender.sendMessage(mm.deserialize("$prefix<$cGreen>✔ <$cWhite>Lobby principal establecido <$cGreen>🏹"))
                1
            })
            .then(Commands.literal("waiting").executes { ctx ->
                val sender = ctx.source.sender as? Player ?: return@executes 0
                plugin.arenaManager.setWaitingSpawnLoc(sender.location)
                sender.sendMessage(mm.deserialize("$prefix<$cAqua>⏳ <$cWhite>Spawn de espera configurado <$cAqua>🔔"))
                1
            })

        val arena = Commands.literal("arena")
            .requires { it.sender.hasPermission("tnt.admin.arena") }
            .then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.string())
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests { _, builder -> builder.suggest("tag").suggest("run").buildFuture() }
                        .executes { ctx ->
                            val sender = ctx.source.sender
                            val name = StringArgumentType.getString(ctx, "name")
                            val type = StringArgumentType.getString(ctx, "type")
                            sender.sendMessage(mm.deserialize("$prefix<$cYellow>🧪 <$cWhite>Creando plantilla <$cPurple>$name</$cPurple>..."))
                            plugin.arenaManager.setupArena(name, type).thenAccept { a ->
                                if (a != null) sender.sendMessage(mm.deserialize("$prefix<$cGreen>█ <$cWhite>Plantilla <$cYellow>$name <$cWhite>registrada con éxito."))
                            }
                            1
                        })))
            .then(Commands.literal("addspawn")
                .then(Commands.argument("target", StringArgumentType.string())
                    .suggests { _, builder ->
                        // Sugerir nombres de plantillas para añadir spawns
                        // Usamos un acceso a través de activeArenas o un método en ArenaManager si fuera necesario,
                        // pero aquí usamos la lógica de plantillas cargadas.
                        1 // Placeholder para el builder
                        plugin.server.asyncScheduler.runNow(plugin) {
                            // Este proceso es asíncrono para no trabar el autocompletado
                        }
                        builder.buildFuture()
                    }
                    .executes { ctx ->
                        val sender = ctx.source.sender as? Player ?: return@executes 0
                        val templateName = StringArgumentType.getString(ctx, "target")
                        plugin.arenaManager.addSpawn(templateName, sender.location)
                        sender.sendMessage(mm.deserialize("$prefix<$cPurple>🚩 <$cWhite>Spawn añadido a la plantilla <$cYellow>$templateName</$cYellow>"))
                        1
                    }))

        val iniciar = Commands.literal("iniciar")
            .requires { it.sender.hasPermission("tnt.admin.start") }
            .then(Commands.argument("template", StringArgumentType.string())
                .suggests { _, builder ->
                    // Sugerir solo plantillas disponibles (No las instancias)
                    // Nota: Asegúrate de que ArenaManager tenga una forma de ver las plantillas cargadas
                    // Si no, puedes usar Bukkit.getWorlds() filtrando o una lista interna.
                    builder.buildFuture()
                }
                .executes { ctx ->
                    val sender = ctx.source.sender
                    val templateName = StringArgumentType.getString(ctx, "template")

                    sender.sendMessage(mm.deserialize("$prefix<$cYellow>🧪 <$cWhite>Instanciando mundo para <$cPurple>$templateName</$cPurple>..."))

                    plugin.arenaManager.setupArena(templateName).thenAccept { arena ->
                        if (arena == null) {
                            sender.sendMessage(mm.deserialize("$prefix<$cRed>✘ <$cWhite>Error al instanciar mundo. ¿Existe el .slime?"))
                            return@thenAccept
                        }

                        plugin.server.onlinePlayers.forEach { p ->
                            if (plugin.arenaManager.getArena(p) == null) arena.join(p)
                        }

                        arena.startTasks()
                        sender.sendMessage(mm.deserialize("$prefix<$cGreen>🔥 <bold>¡EVENTO INICIADO EN ${arena.name}!</bold> 🔥"))
                    }
                    1
                })

        val detener = Commands.literal("detener")
            .then(Commands.argument("instance", StringArgumentType.string())
                .suggests { _, builder ->
                    // Sugerir INSTANCIAS activas
                    plugin.arenaManager.activeArenas.keys.forEach { builder.suggest(it) }
                    builder.buildFuture()
                }
                .executes { ctx ->
                    val instanceName = StringArgumentType.getString(ctx, "instance")
                    val a = plugin.arenaManager.getArenaByName(instanceName)
                    if (a != null) {
                        a.stop()
                        // El método stop() debería llamar a unloadArena eventualmente
                        plugin.arenaManager.unloadArena(instanceName)
                        ctx.source.sender.sendMessage(mm.deserialize("$prefix<$cRed>🛑 <$cWhite>Instancia <$cRed>$instanceName <$cWhite>DESTRUIDA."))
                    }
                    1
                })

        val spectate = Commands.literal("espectar")
            .then(Commands.argument("instance", StringArgumentType.string())
                .suggests { _, builder ->
                    plugin.arenaManager.activeArenas.keys.forEach { builder.suggest(it) }
                    builder.buildFuture()
                }
                .executes { ctx ->
                    val sender = ctx.source.sender as? Player ?: return@executes 0
                    val instanceName = StringArgumentType.getString(ctx, "instance")
                    val a = plugin.arenaManager.getArenaByName(instanceName) ?: return@executes 0

                    SpectatorManager.setSpectator(sender, a)
                    sender.sendMessage(mm.deserialize("$prefix<$cAqua>👁 <$cWhite>Entrando como espectador a $instanceName..."))
                    1
                })

        val revivir = Commands.literal("revivir")
            .then(Commands.argument("player", ArgumentTypes.player()).executes { ctx ->
                val resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver::class.java)
                val target = resolver.resolve(ctx.source).firstOrNull() ?: return@executes 0
                plugin.playerManager.revive(target)
                ctx.source.sender.sendMessage(mm.deserialize("$prefix<$cGreen>🍖 <$cWhite>${target.name} ha sido <$cGreen>REVIVIDO<$cWhite>."))
                1
            })

        val descalificar = Commands.literal("descalificar")
            .then(Commands.argument("player", ArgumentTypes.player()).executes { ctx ->
                val resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver::class.java)
                val target = resolver.resolve(ctx.source).firstOrNull() ?: return@executes 0
                plugin.playerManager.disqualify(target)
                ctx.source.sender.sendMessage(mm.deserialize("$prefix<$cRed>☠ <$cWhite>${target.name} <$cRed>ELIMINADO <$cWhite>del evento."))
                1
            })

        root.then(help).then(reload).then(setup).then(arena).then(iniciar).then(detener).then(spectate).then(revivir).then(descalificar)
        commands.register(root.build(), "TNT Minigame Suite by Pumpkingz")
    }
}

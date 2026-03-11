package liric.tnt.command

import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import liric.tnt.LiricTNTPlugin
import org.bukkit.GameMode
import org.bukkit.entity.Player

object GamemodeCommand {

    private const val cRed = "#FF0000"
    private const val cGreen = "#00FF00"
    private const val cAqua = "#00FFFF"
    private const val cYellow = "#FFFF00"
    private const val cWhite = "#FFFFFF"
    private const val prefix = "<bold><$cRed>EVENTOS</$cRed></bold> <gray>»</gray> "

    fun register(commands: Commands, plugin: LiricTNTPlugin) {
        val root = Commands.literal("gm")
            .requires { it.sender.hasPermission("tnt.gamemode") }

        // Mapeo de modos para facilitar la lógica
        val modes = mapOf(
            "0" to GameMode.SURVIVAL, "survival" to GameMode.SURVIVAL, "s" to GameMode.SURVIVAL,
            "1" to GameMode.CREATIVE, "creative" to GameMode.CREATIVE, "c" to GameMode.CREATIVE,
            "2" to GameMode.ADVENTURE, "adventure" to GameMode.ADVENTURE, "a" to GameMode.ADVENTURE,
            "3" to GameMode.SPECTATOR, "spectator" to GameMode.SPECTATOR, "sp" to GameMode.SPECTATOR
        )

        // Argumento del modo de juego
        val modeArgument = Commands.argument("mode", StringArgumentType.word())
            .suggests { _, builder ->
                listOf("survival", "creative", "adventure", "spectator", "0", "1", "2", "3").forEach { builder.suggest(it) }
                builder.buildFuture()
            }
            .executes { ctx ->
                val sender = ctx.source.sender as? Player ?: return@executes 0
                val modeStr = StringArgumentType.getString(ctx, "mode").lowercase()
                val targetMode = modes[modeStr] ?: return@executes 0

                changeGamemode(sender, sender, targetMode, plugin)
                1
            }
            // Sub-argumento para otros jugadores
            .then(Commands.argument("target", ArgumentTypes.player())
                .executes { ctx ->
                    val sender = ctx.source.sender
                    val modeStr = StringArgumentType.getString(ctx, "mode").lowercase()
                    val targetMode = modes[modeStr] ?: return@executes 0

                    val resolver = ctx.getArgument("target", PlayerSelectorArgumentResolver::class.java)
                    val target = resolver.resolve(ctx.source).firstOrNull()

                    if (target == null) {
                        sender.sendMessage(plugin.messageManager.parse("<$cRed>✘ <$cWhite>Jugador no encontrado."))
                        return@executes 0
                    }

                    changeGamemode(sender as? Player, target, targetMode, plugin)
                    1
                })

        commands.register(root.then(modeArgument).build(), "Simplistic Gamemode Command by Pumpkingz", listOf("gamemode"))
    }

    private fun changeGamemode(sender: Player?, target: Player, mode: GameMode, plugin: LiricTNTPlugin) {
        // Ejecutar el cambio en el hilo del jugador (Folia Safe)
        target.scheduler.run(plugin, { _ ->
            target.gameMode = mode

            val modeName = mode.name.uppercase()
            val msgTarget = "$prefix <$cWhite>Tu modo de juego ha sido cambiado a <$cAqua>$modeName"
            target.sendMessage(plugin.messageManager.parse(target, msgTarget))

            if (sender != null && sender != target) {
                val msgSender = "$prefix <$cWhite>Has cambiado el modo de <$cYellow>${target.name} <$cWhite>a <$cGreen>$modeName"
                sender.sendMessage(plugin.messageManager.parse(sender, msgSender))
            }

            target.playSound(target.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 2f)
        }, null)
    }
}

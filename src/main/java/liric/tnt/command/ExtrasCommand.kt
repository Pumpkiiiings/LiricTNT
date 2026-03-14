package liric.tnt.command

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.Commands
import liric.tnt.LiricTNTPlugin
import liric.tnt.game.ArenaState
import liric.tnt.game.triggerLava
import liric.tnt.game.triggerStorm
import liric.tnt.game.triggerTornado
import liric.tnt.game.triggerAnvils
import liric.tnt.game.entities.GeoffreyEXE
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

object ExtrasCommand {
    private val mm = MiniMessage.miniMessage()
    private const val prefix = "⚡ <bold><#FF00FF>EXTRAS</#FF00FF></bold> <#FFFFFF>»</#FFFFFF> "

    fun register(commands: io.papermc.paper.command.brigadier.Commands, plugin: LiricTNTPlugin) {

        // NODO RAÍZ: Si escriben solo "/extras"
        val root = Commands.literal("extras")
            .requires { it.sender.hasPermission("tnt.admin.extras") }
            .executes { ctx ->
                ctx.source.sender.sendMessage(mm.deserialize("$prefix<#FF0000>Uso incorrecto. <#FFFFFF>Opciones: <#FFFF00>/extras <efecto | evento>"))
                1
            }

        // ==========================================
        // EFECTOS (POCIONES)
        // ==========================================
        // Si escriben solo "/extras efecto"
        val efecto = Commands.literal("efecto")
            .executes { ctx ->
                ctx.source.sender.sendMessage(mm.deserialize("$prefix<#FF0000>Falta el tipo de efecto. <#FFFFFF>Uso: <#FFFF00>/extras efecto <tipo> <nivel>"))
                1
            }
            .then(Commands.argument("tipo", StringArgumentType.word())
                .suggests { _, builder ->
                    listOf("Speed", "Slowness", "Jump", "Blindness", "Levitation").forEach { builder.suggest(it) }
                    builder.buildFuture()
                }
                // Si escriben solo "/extras efecto <tipo>" pero les falta el nivel
                .executes { ctx ->
                    ctx.source.sender.sendMessage(mm.deserialize("$prefix<#FF0000>Falta el nivel. <#FFFFFF>Uso: <#FFFF00>/extras efecto <tipo> <nivel>"))
                    1
                }
                .then(Commands.argument("nivel", IntegerArgumentType.integer(1, 10))
                    // Ejecución correcta completa
                    .executes { ctx ->
                        val sender = ctx.source.sender as? Player ?: return@executes 0
                        val tipoStr = StringArgumentType.getString(ctx, "tipo").uppercase()
                        val nivel = IntegerArgumentType.getInteger(ctx, "nivel")
                        val arena = plugin.arenaManager.getArena(sender)

                        if (arena == null || arena.state != ArenaState.INGAME) {
                            sender.sendMessage(mm.deserialize("$prefix<#FF0000>Debes estar espectando/jugando en una arena activa."))
                            return@executes 0
                        }

                        val potionType = getPotionType(tipoStr)
                        if (potionType == null) {
                            sender.sendMessage(mm.deserialize("$prefix<#FF0000>El efecto '$tipoStr' no existe."))
                            return@executes 0
                        }

                        val title = Title.title(
                            mm.deserialize("<#FF00FF><b>¡MAGIA APLICADA!</b>"),
                            mm.deserialize("<#FFFFFF>Todos reciben $tipoStr $nivel")
                        )

                        arena.alivePlayers.forEach { p ->
                            p.scheduler.run(plugin, { _ -> p.addPotionEffect(PotionEffect(potionType, 200, nivel - 1)) }, null)
                            p.showTitle(title)
                            p.playSound(p.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f)
                            p.sendMessage(mm.deserialize("<newline><#FF00FF><b>¡MAGIA!</b> <#FFFFFF>Todos han recibido <#FFFF00>$tipoStr $nivel<newline>"))
                        }
                        1
                    }))

        // ==========================================
        // EVENTOS DE ARENA (Lava, Tornado, Tormenta)
        // ==========================================
        // Si escriben solo "/extras evento"
        val evento = Commands.literal("evento")
            .executes { ctx ->
                ctx.source.sender.sendMessage(mm.deserialize("$prefix<#FF0000>Falta el tipo de evento. <#FFFFFF>Uso: <#FFFF00>/extras evento <tipo>"))
                1
            }
            .then(Commands.argument("tipo", StringArgumentType.word())
                .suggests { _, builder ->
                    listOf("Lava", "Tornado", "Tormenta", "Yunques", "Geoffrey").forEach { builder.suggest(it) }
                    builder.buildFuture()
                }
                // Ejecución correcta completa
                .executes { ctx ->
                    val sender = ctx.source.sender as? Player ?: return@executes 0
                    val tipoStr = StringArgumentType.getString(ctx, "tipo").uppercase()
                    val arena = plugin.arenaManager.getArena(sender)

                    if (arena == null || arena.state != ArenaState.INGAME) {
                        sender.sendMessage(mm.deserialize("$prefix<#FF0000>Debes estar espectando una arena activa para lanzar un evento."))
                        return@executes 0
                    }

                    when (tipoStr) {
                        "LAVA" -> arena.triggerLava(plugin)
                        "TORNADO" -> arena.triggerTornado(plugin)
                        "TORMENTA" -> arena.triggerStorm(plugin)
                        "YUNQUES" -> arena.triggerAnvils(plugin)
                        "GEOFFREY" -> {
                            val geoffrey = GeoffreyEXE(plugin, arena)
                            geoffrey.spawn(sender.location.add(0.0, 2.0, 0.0))
                            arena.setGeoffrey(geoffrey)
                        }
                        else -> sender.sendMessage(mm.deserialize("$prefix<#FF0000>Evento '$tipoStr' desconocido. Opciones: Lava, Tornado, Tormenta, Yunques, Geoffrey"))
                    }
                    1
                })

        root.then(efecto).then(evento)
        commands.register(root.build(), "Eventos en vivo")
    }

    private fun getPotionType(name: String): PotionEffectType? {
        return when (name) {
            "SPEED" -> PotionEffectType.SPEED
            "SLOWNESS" -> PotionEffectType.SLOWNESS
            "JUMP" -> PotionEffectType.JUMP_BOOST
            "BLINDNESS" -> PotionEffectType.BLINDNESS
            "LEVITATION" -> PotionEffectType.LEVITATION
            else -> null
        }
    }
}

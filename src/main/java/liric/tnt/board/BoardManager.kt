package liric.tnt.board

import liric.tnt.LiricTNTPlugin
import liric.tnt.game.Arena
import liric.tnt.game.TntTag
import liric.tnt.hooks.LuckPermsHook
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import java.util.*
import java.util.concurrent.TimeUnit

class BoardManager(private val plugin: LiricTNTPlugin) {

    private val playerBossBars = mutableMapOf<UUID, BossBar>()
    private val lp = LuckPermsHook()

    private var currentTitle: String = ""
    private var tabFormat: String = ""

    fun startTasks() {
        reload() // Carga variables iniciales

        plugin.server.asyncScheduler.runAtFixedRate(plugin, { _ ->
            val onlinePlayers = Bukkit.getOnlinePlayers().toList()
            if (onlinePlayers.isEmpty()) return@runAtFixedRate

            val playersCountStr = onlinePlayers.size.toString()

            // 1. PRE-CALCULAR DATOS (O(N) - Súper Optimizado)
            // Calculamos todo asíncronamente una sola vez en lugar de 2500 veces.
            val playerTabNames = mutableMapOf<Player, Component>()
            val playerSortingTeams = mutableMapOf<String, String>()

            for (target in onlinePlayers) {
                val prefix = lp.getPrefix(target)
                val suffix = lp.getSuffix(target)
                val weight = 1000 - lp.getWeight(target)

                val formatStr = tabFormat
                    .replace("<prefix>", prefix)
                    .replace("<suffix>", suffix)
                    .replace("<player>", target.name)

                playerTabNames[target] = plugin.messageManager.parse(target, formatStr)
                playerSortingTeams[target.name] = String.format("%04d_%s", weight, target.name)
            }

            // 2. ACTUALIZAR JUGADORES (O(N))
            for (player in onlinePlayers) {
                val arena = plugin.arenaManager.getArena(player)
                val isTag = arena is TntTag
                val timerStr = if (isTag) (arena as TntTag).timer.toString() else "0"

                val arenaName = arena?.displayName?.uppercase() ?: "LOBBY"
                val modeName = arena?.type?.uppercase() ?: "NINGUNO"
                val aliveStr = arena?.alivePlayers?.size?.toString() ?: "0"
                val deadStr = arena?.spectators?.size?.toString() ?: "0"

                // Función rápida para reemplazar placeholders básicos
                val replaceVars = { text: String ->
                    text.replace("%players%", playersCountStr)
                        .replace("%arena%", arenaName)
                        .replace("%mode%", modeName)
                        .replace("%alive%", aliveStr)
                        .replace("%dead%", deadStr)
                        .replace("%timer%", timerStr)
                }

                // --- TABLIST HEADER & FOOTER (Asíncrono = Seguro y Rápido) ---
                val headerStr = plugin.config.getString("tab.header") ?: ""
                val footerStr = plugin.config.getString("tab.footer") ?: ""
                player.sendPlayerListHeaderAndFooter(
                    plugin.messageManager.parse(player, replaceVars(headerStr)),
                    plugin.messageManager.parse(player, replaceVars(footerStr))
                )

                // --- BOSSBAR ---
                updateBossBar(player, arena, replaceVars)

                // --- SCOREBOARD Y TABLIST NAME (Requiere hilo de la región) ---
                val lines = if (arena == null) plugin.config.getStringList("scoreboard.waiting")
                else plugin.config.getStringList("scoreboard.ingame")

                // Parseamos las líneas asíncronamente para no pesar en el hilo principal
                val parsedLines = lines.map { plugin.messageManager.parse(player, replaceVars(it)) }
                val titleComp = plugin.messageManager.parse(player, currentTitle)
                val myTabName = playerTabNames[player]

                player.scheduler.run(plugin, { _ ->
                    // Setear nombre de tab
                    if (myTabName != null) {
                        player.playerListName(myTabName)
                    }

                    // Scoreboard
                    var board = player.scoreboard
                    if (board == Bukkit.getScoreboardManager().mainScoreboard) {
                        board = Bukkit.getScoreboardManager().newScoreboard
                        player.scoreboard = board
                    }

                    val objective = board.getObjective("tnt_board") ?: board.registerNewObjective("tnt_board", Criteria.DUMMY, titleComp).apply {
                        displaySlot = DisplaySlot.SIDEBAR
                    }
                    objective.displayName(titleComp)

                    val entryIds = arrayOf("§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9", "§a", "§b", "§c", "§d", "§e", "§f")
                    board.entries.forEach { if (!entryIds.contains(it)) board.resetScores(it) }

                    var scoreIndex = parsedLines.size
                    for (i in parsedLines.indices) {
                        if (i >= entryIds.size) break
                        val entryId = entryIds[i]
                        val team = board.getTeam("line_$i") ?: board.registerNewTeam("line_$i").apply { addEntry(entryId) }
                        team.prefix(parsedLines[i])
                        objective.getScore(entryId).score = scoreIndex
                        scoreIndex--
                    }

                    // Teams de Ordenamiento (Se aplican rápido desde el mapa cacheado)
                    playerSortingTeams.forEach { (targetName, teamName) ->
                        val sortingTeam = board.getTeam(teamName) ?: board.registerNewTeam(teamName)
                        if (!sortingTeam.hasEntry(targetName)) {
                            sortingTeam.addEntry(targetName)
                        }
                    }

                }, null)
            }
        }, 0, 1, TimeUnit.SECONDS)
    }

    fun reload() {
        currentTitle = plugin.config.getString("scoreboard.title") ?: "<#FF0000><b>TNT EVENT</b>"
        tabFormat = plugin.config.getString("tab.player-format") ?: "<prefix><player>"

        playerBossBars.forEach { (uuid, bar) ->
            Bukkit.getPlayer(uuid)?.hideBossBar(bar)
        }
        playerBossBars.clear()
    }

    private fun updateBossBar(player: Player, arena: Arena?, replaceVars: (String) -> String) {
        if (arena == null) {
            playerBossBars.remove(player.uniqueId)?.let { player.hideBossBar(it) }
            return
        }

        val configText = plugin.config.getString("bossbar.text") ?: ""
        val colorStr = plugin.config.getString("bossbar.color") ?: "RED"
        val styleStr = plugin.config.getString("bossbar.style") ?: "PROGRESS"

        val alive = arena.alivePlayers.size
        val dead = arena.spectators.size
        val total = (alive + dead).coerceAtLeast(1)
        val progress = (alive.toFloat() / total.toFloat()).coerceIn(0f, 1f)

        val finalContent = replaceVars(configText)
        val parsedContent = plugin.messageManager.parse(player, finalContent)

        val bar = playerBossBars.getOrPut(player.uniqueId) {
            val newBar = BossBar.bossBar(
                parsedContent,
                progress,
                BossBar.Color.valueOf(colorStr),
                BossBar.Overlay.valueOf(styleStr)
            )
            player.showBossBar(newBar)
            newBar
        }

        bar.name(parsedContent)
        bar.progress(progress)
    }
}

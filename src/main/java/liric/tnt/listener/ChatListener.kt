package liric.tnt.listener

import io.papermc.paper.event.player.AsyncChatEvent
import liric.tnt.LiricTNTPlugin
import liric.tnt.hooks.LuckPermsHook
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class ChatListener(private val plugin: LiricTNTPlugin) : Listener {
    private val lp = LuckPermsHook()
    private val formatManager = plugin.chatFormatManager

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        val group = lp.getPrimaryGroup(player)

        // 1. Obtener datos del rango desde la config
        val rawFormat = formatManager.getFormat(group)
        val clickCmd = formatManager.getClickAction(group).replace("<player>", player.name)

        // Unimos las líneas del hover con <newline> para que MiniMessage las entienda
        val hoverLinesText = formatManager.getHover(group).joinToString("<newline>") { it.replace("<player>", player.name) }

        // 2. PROCESAR PREFIJOS Y SUFIJOS CON EL MESSAGE MANAGER
        // Esto es lo que soluciona el &l, porque el MessageManager lo traduce a <bold>
        val prefixComp = plugin.messageManager.parse(player, lp.getPrefix(player))
        val suffixComp = plugin.messageManager.parse(player, lp.getSuffix(player))
        val playerComp = Component.text(player.name)

        // 3. Procesar el mensaje del jugador (SEGURO)
        // Lo convertimos a texto plano para que los jugadores no puedan usar códigos como <red> o &c en su chat
        val messageText = PlainTextComponentSerializer.plainText().serialize(event.message())
        val messageComp = Component.text(messageText)

        // 4. Crear el componente final inyectando las variables de forma segura
        val chatComponent = plugin.messageManager.parse(
            player,
            rawFormat,
            Placeholder.component("prefix", prefixComp),
            Placeholder.component("suffix", suffixComp),
            Placeholder.component("player", playerComp),
            Placeholder.component("message", messageComp)
        )

        // 5. Parsear el Hover para que también soporte colores Legacy y Hex
        val hoverComponent = plugin.messageManager.parse(player, hoverLinesText)

        // 6. Aplicar interactividad (Clic y Hover)
        val interactiveComponent = chatComponent
            .clickEvent(ClickEvent.suggestCommand(clickCmd))
            .hoverEvent(HoverEvent.showText(hoverComponent))

        // 7. Enviar el chat reemplazando el renderizador nativo
        event.renderer { _, _, _, _ -> interactiveComponent }
    }
}

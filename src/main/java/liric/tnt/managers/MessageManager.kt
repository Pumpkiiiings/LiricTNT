package liric.tnt.managers

import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.regex.Pattern

class MessageManager {

    private val mm = MiniMessage.miniMessage()
    private val hasPAPI: Boolean = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")

    // Regex ultra rápido para atrapar los códigos antiguos sin importar si son mayúsculas o minúsculas
    private val legacyRegex = Regex("[&§]([0-9a-fk-or])", RegexOption.IGNORE_CASE)

    // Mapa de códigos a etiquetas MiniMessage
    private val legacyCodes = mapOf(
        "0" to "black", "1" to "dark_blue", "2" to "dark_green", "3" to "dark_aqua",
        "4" to "dark_red", "5" to "dark_purple", "6" to "gold", "7" to "gray",
        "8" to "dark_gray", "9" to "blue", "a" to "green", "b" to "aqua",
        "c" to "red", "d" to "light_purple", "e" to "yellow", "f" to "white",
        "k" to "obfuscated", "l" to "bold", "m" to "strikethrough",
        "n" to "underlined", "o" to "italic", "r" to "reset"
    )

    /**
     * MOTOR DE PARSEO: PAPI + HEX + LEGACY + MINIMESSAGE
     */
    fun parse(player: Player?, text: String?, vararg resolvers: TagResolver): Component {
        if (text.isNullOrEmpty()) return Component.empty()

        var msg = text

        // 1. Aplicar PlaceholderAPI (si está instalado y hay un jugador)
        if (hasPAPI && player != null) {
            msg = PlaceholderAPI.setPlaceholders(player, msg)
        }

        // 2. Traducir colores antiguos/hex a formato MiniMessage y deserializar
        return mm.deserialize(preProcess(msg), *resolvers)
    }

    // Sobrecarga para parsear texto sin necesidad de un jugador
    fun parse(text: String?, vararg resolvers: TagResolver) = parse(null, text, *resolvers)

    /**
     * PROCESADOR INTERNO:
     * Convierte formatos Legacy y Hex al estándar de MiniMessage.
     */
    private fun preProcess(text: String?): String {
        if (text.isNullOrEmpty()) return ""

        var processed = text

        // 1. Traducir Hex Vanilla/Bungee (&x&f&f&0&0&0&0) a <#RRGGBB>
        val vanillaHex = Pattern.compile("[&§]x[&§]([A-Fa-f0-9])[&§]([A-Fa-f0-9])[&§]([A-Fa-f0-9])[&§]([A-Fa-f0-9])[&§]([A-Fa-f0-9])[&§]([A-Fa-f0-9])")
        processed = vanillaHex.matcher(processed).replaceAll("<#$1$2$3$4$5$6>")

        // 2. Traducir Hex Simple (&#RRGGBB o §#RRGGBB) a <#RRGGBB>
        val simpleHex = Pattern.compile("[&§]#([A-Fa-f0-9]{6})")
        val matcher = simpleHex.matcher(processed)
        val sb = StringBuilder()
        while (matcher.find()) {
            matcher.appendReplacement(sb, "<#" + matcher.group(1) + ">")
        }
        matcher.appendTail(sb)
        processed = sb.toString()

        // 3. Traducir códigos Legacy clásicos
        return translateLegacy(processed)
    }

    /**
     * Traduce &l a <bold>, &c a <red>, etc.
     */
    private fun translateLegacy(text: String): String {
        return legacyRegex.replace(text) { matchResult ->
            // Capturamos la letra (ej: 'l', 'L', 'c', 'C') y la pasamos a minúscula para buscarla en el mapa
            val code = matchResult.groupValues[1].lowercase()
            val tag = legacyCodes[code]

            // Si existe en el mapa, devolvemos la etiqueta MiniMessage, si no, lo dejamos igual
            if (tag != null) "<$tag>" else matchResult.value
        }
    }
}

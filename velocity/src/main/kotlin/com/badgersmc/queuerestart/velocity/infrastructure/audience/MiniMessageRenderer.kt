package com.badgersmc.queuerestart.velocity.infrastructure.audience

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver

/**
 * Wraps Adventure MiniMessage with placeholder substitution. The
 * countdown templates use `<server>`, `<time>`, `<hub>` — substituted
 * via [Placeholder.parsed] so the values stay safe when they contain
 * characters that MiniMessage would otherwise interpret as tags.
 */
class MiniMessageRenderer {

    private val mm = MiniMessage.miniMessage()

    fun render(template: String, placeholders: Map<String, String>): Component {
        val resolvers = placeholders.map { (k, v) -> Placeholder.parsed(k, v) }
        return mm.deserialize(template, TagResolver.resolver(resolvers))
    }
}

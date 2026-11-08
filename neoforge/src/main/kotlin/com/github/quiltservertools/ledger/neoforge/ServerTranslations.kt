package com.github.quiltservertools.ledger.neoforge

import com.google.gson.Gson
import com.google.gson.JsonObject
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.contents.TranslatableContents
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Vanilla clients don't have Ledger's language files, so its translatable text is rendered on the server.
 * On Fabric, server-translations-api does this job.
 */
object ServerTranslations {
    private const val DEFAULT_LANGUAGE = "en_us"
    private val FORMAT: Pattern = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)")
    private val languages = ConcurrentHashMap<String, Map<String, String>>()

    /** Returns the component with Ledger's text resolved for the language, or the same instance if nothing changed. */
    @JvmStatic
    fun translate(component: Component, language: String): Component {
        val strings = strings(language)
        val fallback = strings(DEFAULT_LANGUAGE)
        return resolve(component) { key -> strings[key] ?: fallback[key] }
    }

    private fun strings(language: String): Map<String, String> = languages.computeIfAbsent(language.lowercase()) {
        val stream = ServerTranslations::class.java.getResourceAsStream("/data/ledger/lang/$it.json")
            ?: return@computeIfAbsent emptyMap()
        stream.bufferedReader().use { reader ->
            Gson().fromJson(reader, JsonObject::class.java).entrySet().associate { (key, value) ->
                key to value.asString
            }
        }
    }

    private fun resolve(component: Component, lookup: (String) -> String?): Component {
        val contents = component.contents
        val template = (contents as? TranslatableContents)?.let { lookup(it.key) }
        val args = (contents as? TranslatableContents)?.args?.map { arg ->
            if (arg is Component) resolve(arg, lookup) else arg
        }
        val style = resolveStyle(component.style, lookup)
        val siblings = component.siblings.map { resolve(it, lookup) }

        val argsChanged = args != null && args.indices.any {
            args[it] !== (contents as TranslatableContents).args[it]
        }
        val siblingsChanged = siblings.indices.any { siblings[it] !== component.siblings[it] }
        if (template == null && !argsChanged && !siblingsChanged && style === component.style) return component

        val result: MutableComponent = when {
            template != null -> format(template, args.orEmpty())
            contents is TranslatableContents && argsChanged ->
                Component.translatableWithFallback(contents.key, contents.fallback, *args.orEmpty().toTypedArray())
            else -> MutableComponent.create(contents)
        }
        result.withStyle(style)
        siblings.forEach { result.append(it) }
        return result
    }

    private fun resolveStyle(style: Style, lookup: (String) -> String?): Style {
        val hover = style.hoverEvent as? HoverEvent.ShowText ?: return style
        val value = resolve(hover.value, lookup)
        return if (value === hover.value) style else style.withHoverEvent(HoverEvent.ShowText(value))
    }

    private fun format(template: String, args: List<Any?>): MutableComponent {
        val result = Component.empty()
        val matcher = FORMAT.matcher(template)
        var position = 0
        var nextArg = 0
        while (matcher.find()) {
            if (matcher.start() > position) {
                result.append(Component.literal(template.substring(position, matcher.start())))
            }
            position = matcher.end()
            when (matcher.group(2)) {
                "%" -> result.append(Component.literal("%"))
                "s" -> {
                    val index = matcher.group(1)?.let { it.toInt() - 1 } ?: nextArg++
                    result.append(argument(args.getOrNull(index)))
                }
                else -> result.append(Component.literal(matcher.group()))
            }
        }
        if (position < template.length) {
            result.append(Component.literal(template.substring(position)))
        }
        return result
    }

    private fun argument(arg: Any?): Component = when (arg) {
        is Component -> arg
        null -> Component.empty()
        else -> Component.literal(arg.toString())
    }
}

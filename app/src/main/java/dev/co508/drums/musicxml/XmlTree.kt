package dev.co508.drums.musicxml

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.InputStream

/** A parsed XML element: just the parts the importer reads. */
class XmlElement(
    val name: String,
    val attributes: Map<String, String>,
    val children: List<XmlElement>,
    /** Concatenated text directly inside this element, trimmed. */
    val text: String,
) {
    fun child(name: String): XmlElement? = children.firstOrNull { it.name == name }

    fun children(name: String): List<XmlElement> = children.filter { it.name == name }

    fun has(name: String): Boolean = child(name) != null

    /** All descendants named [name], depth first. */
    fun descendants(name: String): List<XmlElement> =
        children.flatMap { c -> (if (c.name == name) listOf(c) else emptyList()) + c.descendants(name) }

    fun attribute(name: String): String? = attributes[name]
}

class XmlSecurityException(
    message: String,
) : Exception(message)

/**
 * Reads XML into an [XmlElement] tree, safely for untrusted files.
 *
 * Nothing outside the file is ever read: DTDs and external entities are never
 * fetched or resolved (DECISIONS.md, "XML: Pull Parser, No DTDs, No
 * Entities"). MusicXML's `<!DOCTYPE … "http://www.musicxml.org/dtds/…">` is
 * skipped, unread. A DOCTYPE with an internal subset (`[ … ]`, where entities
 * are declared) is rejected outright, and so is any reference to an entity
 * other than XML's five predefined ones and character references. That rules
 * out external-entity (XXE) and entity-expansion ("billion laughs") attacks
 * whatever the parser underneath supports.
 */
object XmlTree {
    private val predefined = setOf("amp", "lt", "gt", "apos", "quot")

    fun parse(
        parser: XmlPullParser,
        input: InputStream,
    ): XmlElement {
        // Processing the DOCTYPE is what would declare or fetch anything. Every
        // XmlPullParser feature is off by default (and KXml won't even accept
        // setting this one), so check rather than set it.
        check(!parser.getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL)) { "the XML parser processes DOCTYPEs" }
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        val stack = ArrayDeque<Builder>()
        var root: XmlElement? = null
        while (true) {
            when (parser.nextToken()) {
                XmlPullParser.DOCDECL -> {
                    if (parser.text.orEmpty().contains('[')) {
                        throw XmlSecurityException("The file declares its own DTD, which isn't allowed.")
                    }
                }
                XmlPullParser.ENTITY_REF -> {
                    val name = parser.name.orEmpty()
                    if (name !in predefined && !name.startsWith("#")) {
                        throw XmlSecurityException("The file uses the entity &$name;, which isn't allowed.")
                    }
                    stack.lastOrNull()?.text?.append(parser.text.orEmpty())
                }
                XmlPullParser.START_TAG -> {
                    val attributes =
                        (0 until parser.attributeCount).associate {
                            parser.getAttributeName(it) to
                                parser.getAttributeValue(it)
                        }
                    stack.addLast(Builder(parser.name, attributes))
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> stack.lastOrNull()?.text?.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val element = stack.removeLast().build()
                    if (stack.isEmpty()) root = element else stack.last().children += element
                }
                XmlPullParser.END_DOCUMENT ->
                    return root
                        ?: throw XmlPullParserException("The file has no root element.")
                else -> Unit // Comments, processing instructions, whitespace.
            }
        }
    }

    private class Builder(
        val name: String,
        val attributes: Map<String, String>,
    ) {
        val children = mutableListOf<XmlElement>()
        val text = StringBuilder()

        fun build() = XmlElement(name, attributes, children, text.toString().trim())
    }
}

package dev.co508.drums.musicxml

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
 * skipped, unread.
 *
 * Before any parser sees the file, its raw text is checked: a DOCTYPE with
 * an internal subset (`[ … ]`, where entities are declared) or any
 * `<!ENTITY` is rejected outright. So is any reference to an entity other
 * than XML's five predefined ones and character references. That rules out
 * external-entity (XXE) and entity-expansion ("billion laughs") attacks
 * whatever the parser underneath does with DTDs. Android's parser, for one,
 * processes them by default.
 */
object XmlTree {
    private val predefined = setOf("amp", "lt", "gt", "apos", "quot")

    /** Lesson files are a few kilobytes; anything this big isn't one. */
    const val MAX_BYTES = 8 * 1024 * 1024

    fun parse(
        parser: XmlPullParser,
        input: InputStream,
    ): XmlElement {
        val bytes = readAtMost(input, MAX_BYTES + 1)
        if (bytes.size > MAX_BYTES) throw XmlSecurityException("The file is too large to be a lesson.")
        checkProlog(bytes)

        // Processing the DOCTYPE is what would declare or fetch anything. Android's
        // parser turns it on and lets us turn it off; kxml2 has it off and refuses
        // the call.
        if (parser.getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL)) {
            try {
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            } catch (e: XmlPullParserException) {
                throw XmlSecurityException("The XML parser can't be stopped from processing DTDs.")
            }
        }
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(bytes), null)

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

    /** Rejects entity declarations and internal DTD subsets in the raw text, before a parser can act on them. */
    private fun checkProlog(bytes: ByteArray) {
        val bigEndian = bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()
        val littleEndian = bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()
        // Latin-1 maps every byte to one char, which is enough to find ASCII markup in UTF-8.
        val text = String(bytes, if (bigEndian || littleEndian) Charsets.UTF_16 else Charsets.ISO_8859_1)
        val ownDtd = XmlSecurityException("The file declares its own DTD, which isn't allowed.")
        if (text.contains("<!ENTITY", ignoreCase = true)) throw ownDtd
        val doctype = text.indexOf("<!DOCTYPE", ignoreCase = true)
        if (doctype >= 0) {
            val end = text.indexOf('>', doctype)
            if ('[' in (if (end < 0) text.substring(doctype) else text.substring(doctype, end))) throw ownDtd
        }
    }

    /** Up to [limit] bytes of [input] (InputStream.readNBytes needs API 33). */
    private fun readAtMost(
        input: InputStream,
        limit: Int,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val n = input.read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
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

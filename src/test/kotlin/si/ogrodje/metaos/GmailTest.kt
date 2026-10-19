package si.ogrodje.metaos

import com.google.api.services.gmail.model.Message
import com.google.api.services.gmail.model.MessagePart
import com.google.api.services.gmail.model.MessagePartBody
import com.google.api.services.gmail.model.MessagePartHeader
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GmailTest {

    private fun part(mimeType: String, text: String? = null, charset: String = "UTF-8", filename: String? = null, vararg parts: MessagePart) =
        MessagePart().setMimeType(mimeType).setFilename(filename ?: "")
            .setHeaders(listOf(MessagePartHeader().setName("Content-Type").setValue("$mimeType; charset=\"$charset\"")))
            .setBody(MessagePartBody().apply { text?.let { encodeData(it.toByteArray(charset(charset))) } })
            .setParts(parts.toList().ifEmpty { null })

    private fun headers(mime: ByteArray) =
        mime.toString(Charsets.US_ASCII).substringBefore("\r\n\r\n").replace("\r\n ", " ").split("\r\n")
            .associate { it.substringBefore(": ") to it.substringAfter(": ") }

    @Test
    fun `writes plain text emails, with what isn't ASCII encoded`() {
        val original = Email(
            id = "m1", threadId = "t1", from = "ana@example.com", to = null, cc = null, replyTo = null,
            subject = "Snemanje", date = null, unread = false, snippet = null,
            messageId = "<b@example.com>", references = "<a@example.com>",
        )
        val mime = OutgoingEmail(
            to = listOf("Ana Novak <ana@example.com>", "boris@example.com"),
            cc = listOf("Žiga Čuk <ziga@example.com>"),
            subject = "Re: Snemanje v četrtek – potrditev termina za epizodo S05E08 z gostom",
            text = "Živjo,\n\nse vidimo v četrtek.",
            inReplyTo = original,
        ).toMime()
        val headers = headers(mime)

        assertEquals("\"Ana Novak\" <ana@example.com>, boris@example.com", headers["To"])
        assertEquals("Žiga Čuk <ziga@example.com>", decodeHeader(headers.getValue("Cc")))
        assertEquals("Re: Snemanje v četrtek – potrditev termina za epizodo S05E08 z gostom", decodeHeader(headers.getValue("Subject")))
        assertEquals("<b@example.com>", headers["In-Reply-To"])
        assertEquals("<a@example.com> <b@example.com>", headers["References"])
        assertEquals("text/plain; charset=UTF-8", headers["Content-Type"])
        assertTrue(mime.toString(Charsets.US_ASCII).split("\r\n").all { it.length <= 998 })

        val body = mime.toString(Charsets.US_ASCII).substringAfter("\r\n\r\n")
        assertEquals("Živjo,\n\nse vidimo v četrtek.", Base64.getMimeDecoder().decode(body).toString(Charsets.UTF_8))
    }

    @Test
    fun `refuses what would break the email's headers`() {
        assertFailsWith<IllegalArgumentException> { OutgoingEmail(listOf("not an address"), "Hi", "Hi").toMime() }
        assertFailsWith<IllegalArgumentException> { OutgoingEmail(emptyList(), "Hi", "Hi").toMime() }
        assertFailsWith<IllegalArgumentException> {
            OutgoingEmail(listOf("ana@example.com"), "Hi\r\nBcc: eve@example.com", "Hi").toMime()
        }
    }

    @Test
    fun `reads headers, text and attachments from Gmail's messages`() {
        assertEquals("Žiga Čuk", decodeHeader("=?UTF-8?B?xb1pZ2E=?= =?UTF-8?Q?_=C4=8Cuk?="))
        assertEquals("plain", decodeHeader("plain"))
        assertEquals("Ana & Boris <3", unescapeHtml("Ana &amp; Boris &lt;3"))
        assertEquals("Živjo\n\nNaslednji teden\nsnemamo", htmlToText("<style>p{}</style><p>Živjo</p><p>Naslednji teden<br>snemamo</p>"))

        val message = Message().setId("m1").setThreadId("t1").setLabelIds(listOf("INBOX", "UNREAD")).setSnippet("Živjo &amp; hvala")
            .setPayload(
                part(
                    "multipart/mixed", null, "UTF-8", null,
                    part("multipart/alternative", null, "UTF-8", null,
                        part("text/plain", "Živjo, hvala za povabilo.", "ISO-8859-2"),
                        part("text/html", "<p>Živjo, hvala za povabilo.</p>"),
                    ),
                    part("application/pdf", "%PDF", filename = "pogodba.pdf"),
                ).setHeaders(listOf(MessagePartHeader().setName("Subject").setValue("=?UTF-8?B?UG92YWJpbG8gbmEgc25lbWFuamU=?=")))
            )

        val email = message.toEmail(withText = true)
        assertEquals("Povabilo na snemanje", email.subject)
        assertEquals("Živjo, hvala za povabilo.", email.text)
        assertEquals(listOf("pogodba.pdf"), email.attachments)
        assertEquals("Živjo & hvala", email.snippet)
        assertTrue(email.unread)
        assertEquals(null, message.toEmail().text)

        val htmlOnly = Message().setId("m2").setThreadId("t1").setPayload(part("text/html", "<div>Samo <b>HTML</b></div>"))
        assertEquals("Samo HTML", htmlOnly.toEmail(withText = true).text)
    }

    /** Against the bot's real mailbox - only with its credentials in the environment (.envrc). Sends nothing. */
    @Test
    @EnabledIfEnvironmentVariable(named = "GOOGLE_GMAIL_REFRESH_TOKEN", matches = ".+")
    fun `reads the bot's mailbox and keeps a draft as written`() {
        val mailbox = GoogleMailbox(
            clientId = System.getenv("GOOGLE_CLIENT_ID"),
            clientSecret = System.getenv("GOOGLE_CLIENT_SECRET"),
            refreshToken = System.getenv("GOOGLE_GMAIL_REFRESH_TOKEN"),
        )
        val address = mailbox.address()
        System.getenv("GOOGLE_BOT_EMAIL")?.let { assertEquals(it, address) }
        mailbox.search("in:anywhere", limit = 1)

        val draft = mailbox.createDraft(
            OutgoingEmail(to = listOf("MetaOS Test <$address>"), subject = "MetaOS test – čšž", text = "Živjo,\nčšž ČŠŽ")
        )
        try {
            assertEquals("MetaOS test – čšž", draft.email.subject)
            assertEquals("Živjo,\nčšž ČŠŽ", draft.email.text?.replace("\r\n", "\n")?.trim())
            assertEquals(draft.email.subject, mailbox.draft(draft.id).email.subject)
        } finally {
            mailbox.deleteDraft(draft.id)
        }
    }
}

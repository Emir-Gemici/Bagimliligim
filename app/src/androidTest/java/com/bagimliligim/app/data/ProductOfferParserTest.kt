package com.bagimliligim.app.data

import com.bagimliligim.app.data.remote.ProductOfferParser
import com.bagimliligim.app.data.remote.TobaccoPriceParser
import org.junit.Assert.*
import org.junit.Test

class ProductOfferParserTest {
    @Test fun tobaccoPricesMapExactVariantsAndDoNotBackdateObservation() {
        val latest = "https://www.medyaradar.net/guncel-sigara-fiyatlari-haberi-2245484"
        assertEquals(listOf(latest), TobaccoPriceParser.articleUrls("""<a href="$latest">Liste</a><a href="https://www.medyaradar.net.evil.test/sigara-haberi-2">X</a>"""))
        val now = java.time.Instant.parse("2026-10-17T12:00:00Z").toEpochMilli()
        val html = """<script>{"dateModified":"2026-10-16T10:00:00+03:00"}</script>
            <table><tr><td>Winston Dark Blue</td><td>120 TL</td></tr>
            <tr><td>Tekel 2000 Mavi</td><td>115 TL</td></tr>
            <tr><td>Parliament Reserve</td><td>130 TL</td></tr></table>"""
        val rows = TobaccoPriceParser.parse(html, java.time.LocalDate.of(2026, 10, 17), now)
        assertEquals(3, rows.size)
        assertEquals(11500L, rows.first { it.brandId == "tekel-2000-blue" }.packPriceKurus)
        assertTrue(rows.all { it.validFrom == "2026-10-17" })
        assertTrue(TobaccoPriceParser.parse(html.replace("2026-10-16", "2026-08-01"), java.time.LocalDate.of(2026, 10, 17), now).isEmpty())
        val conflicting = html + "<table><tr><td>Winston Dark Blue</td><td>150 TL</td></tr></table>"
        assertTrue(TobaccoPriceParser.parse(conflicting, java.time.LocalDate.of(2026, 10, 17), now).isEmpty())
    }
    private val offer = """<script type="application/ld+json">{"@type":"Product","name":"Cam kupa","offers":{"@type":"Offer","priceCurrency":"TRY","price":"149","availability":"https://schema.org/InStock"}}</script>"""
    @Test fun acceptsExplicitInStockTryOffersOnly() {
        val parsed = ProductOfferParser.parse(offer, "a", "Kahve", "https://www.ikea.com.tr/urun/test", 1000)!!
        assertEquals(14900L, parsed.priceKurus)
        assertEquals(1000L, parsed.checkedAt)
        val book = ProductOfferParser.parse(offer, "book", "Kitap", "https://www.kitapyurdu.com/kitap/test/1.html", 1000)!!
        assertEquals("Kitapyurdu", book.source)
        assertNull(ProductOfferParser.parse(offer, "a", "Kitap", "https://www.kitapyurdu.com.evil.test/kitap/test/1.html", 1000))
        assertNull(ProductOfferParser.parse(offer.replace("InStock", "OutOfStock"), "a", "Kahve", "https://www.ikea.com.tr/urun/test", 1000))
        assertNull(ProductOfferParser.parse(offer.replace("TRY", "EUR"), "a", "Kahve", "https://www.ikea.com.tr/urun/test", 1000))
        assertNull(ProductOfferParser.parse("<html>Access denied</html>", "a", "Kahve", "https://www.ikea.com.tr/urun/test", 1000))
    }
}

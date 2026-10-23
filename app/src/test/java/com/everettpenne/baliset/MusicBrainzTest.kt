package com.everettpenne.baliset

import com.everettpenne.baliset.data.musicbrainz.MbRelease
import com.everettpenne.baliset.data.musicbrainz.LocalAlbumQuery
import com.everettpenne.baliset.data.musicbrainz.CoverMatch
import com.everettpenne.baliset.data.musicbrainz.parseCaaImages
import com.everettpenne.baliset.data.musicbrainz.parseReleaseSearch
import com.everettpenne.baliset.data.musicbrainz.pickCoverUrl
import com.everettpenne.baliset.data.musicbrainz.releaseQuery
import com.everettpenne.baliset.data.musicbrainz.upgradeHttps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicBrainzTest {
    private val searchJson = """
        {"count":2,"releases":[
          {"id":"release-1","score":100,"title":"Kind of Blue","date":"1987-01-01","status":"Official","track-count":5,
           "artist-credit":[{"name":"Miles Davis","joinphrase":""}],
           "release-group":{"id":"group-1","primary-type":"Album"},
           "label-info":[{"label":{"name":"Columbia"}}]},
          {"id":"release-2","score":98,"title":"Kind of Blue","date":"2004-03-23","status":"Official","track-count":19,
           "artist-credit":[{"name":"Miles Davis","joinphrase":""}],
           "release-group":{"id":"group-1","primary-type":"Album"},
           "label-info":[{"label":{"name":"Columbia"}}]}
        ]}
    """.trimIndent()

    @Test fun parsesTheFieldsTheMatcherNeeds() {
        val releases = parseReleaseSearch(searchJson)
        assertEquals(2, releases.size)
        val first = releases.first()
        assertEquals("release-1", first.id)
        assertEquals("Kind of Blue", first.title)
        assertEquals("Miles Davis", first.artist)
        assertEquals(5, first.trackCount)
        assertEquals(1987, first.year)
        assertEquals("Official", first.status)
        assertEquals("group-1", first.releaseGroupId)
        assertEquals("Columbia", first.label)
    }

    @Test fun artistCreditsJoinNamesAndJoinPhrases() {
        val json = """{"releases":[{"id":"x","title":"T","score":1,"artist-credit":[{"name":"A","joinphrase":" & "},{"name":"B","joinphrase":""}]}]}"""
        assertEquals("A & B", parseReleaseSearch(json).first().artist)
    }

    @Test fun buildsAQuotedQueryAndLeavesTheUnknownOut() {
        assertEquals("release:\"Kind of Blue\" AND artist:\"Miles Davis\"", releaseQuery("Kind of Blue", "Miles Davis"))
        assertEquals("release:\"Solo\"", releaseQuery("Solo", "Unknown artist"))
        assertNull(releaseQuery("Unknown album", "Someone"))
        assertNull(releaseQuery("   ", "Someone"))
    }

    @Test fun escapesQuotesAndBackslashes() {
        assertEquals("release:\"He said \\\"Hi\\\"\" AND artist:\"A\\\\B\"", releaseQuery("He said \"Hi\"", "A\\B"))
    }

    @Test fun picksTheFrontApprovedCoverAtTheRequestedSize() {
        val json = """
            {"images":[
              {"front":true,"approved":true,"types":["Front"],"image":"http://ca.org/release/x/1.jpg",
               "thumbnails":{"250":"http://ca.org/release/x/1-250.jpg","500":"http://ca.org/release/x/1-500.jpg"}},
              {"front":false,"approved":true,"types":["Back"],"image":"http://ca.org/release/x/2.jpg","thumbnails":{}},
              {"front":true,"approved":false,"types":["Front"],"image":"http://ca.org/release/x/3.jpg","thumbnails":{}}
            ]}
        """.trimIndent()
        assertEquals("https://ca.org/release/x/1-500.jpg", pickCoverUrl(json, "500"))
        assertEquals("https://ca.org/release/x/1-250.jpg", pickCoverUrl(json, "250"))
    }

    @Test fun fallsBackToTheOriginalImageWhenThereAreNoThumbnails() {
        val json = """{"images":[{"front":true,"approved":true,"types":["Front"],"image":"http://ca.org/release/x/1.png","thumbnails":{}}]}"""
        assertEquals("https://ca.org/release/x/1.png", pickCoverUrl(json, "500"))
    }

    @Test fun ignoresUnapprovedImagesAndEmptyListings() {
        val json = """{"images":[{"front":true,"approved":false,"types":["Front"],"image":"http://ca.org/x.jpg","thumbnails":{}}]}"""
        assertNull(pickCoverUrl(json, "500"))
        assertNull(pickCoverUrl("{}", "500"))
        assertNull(pickCoverUrl("not json", "500"))
    }

    @Test fun parsesTypesAndThumbnails() {
        val images = parseCaaImages("""{"images":[{"front":true,"approved":true,"types":["Front","Other"],"image":"x","thumbnails":{"500":"y"}}]}""")
        assertEquals(listOf("Front", "Other"), images.first().types)
        assertEquals("y", images.first().thumbnails["500"])
    }

    @Test fun upgradesHttpOnly() {
        assertEquals("https://a/b.jpg", upgradeHttps("http://a/b.jpg"))
        assertEquals("https://a/b.jpg", upgradeHttps("https://a/b.jpg"))
        assertEquals("ftp://a/b.jpg", upgradeHttps("ftp://a/b.jpg"))
    }

    private fun release(id: String, title: String, artist: String, tracks: Int, year: Int, score: Int = 100, status: String = "Official", group: String = "g-$id") =
        MbRelease(id, title, artist, score, year, tracks, status, group, "Album", "Label")

    @Test fun theEditionWithTheMatchingTrackCountWins() {
        val local = LocalAlbumQuery("Kind of Blue", "Miles Davis", 5, 1959)
        val five = release("a", "Kind of Blue", "Miles Davis", 5, 1987)
        val nineteen = release("b", "Kind of Blue", "Miles Davis", 19, 2004)
        val ranked = CoverMatch.rank(local, listOf(nineteen, five))
        assertEquals("a", ranked.first().release.id)
        assertTrue(ranked.first().confidence > ranked.last().confidence)
        assertTrue(ranked.first().confidence >= CoverMatch.AUTO_APPLY)
    }

    @Test fun aDifferentTitleIsNotACandidate() {
        val local = LocalAlbumQuery("Kind of Blue", "Miles Davis", 5, 1959)
        val wrong = release("x", "Bitches Brew", "Miles Davis", 5, 1970)
        assertTrue(CoverMatch.rank(local, listOf(wrong)).isEmpty())
    }

    @Test fun oneCandidatePerReleaseGroup() {
        val local = LocalAlbumQuery("Kind of Blue", "Miles Davis", 5, 1959)
        val a = release("a", "Kind of Blue", "Miles Davis", 5, 1987, group = "same")
        val b = release("b", "Kind of Blue", "Miles Davis", 6, 1997, group = "same")
        assertEquals(1, CoverMatch.rank(local, listOf(a, b)).size)
    }

    @Test fun bootlegsRankBelowOfficialReleases() {
        val local = LocalAlbumQuery("Kind of Blue", "Miles Davis", 5, 1959)
        val official = release("a", "Kind of Blue", "Miles Davis", 5, 1987, status = "Official", group = "g1")
        val bootleg = release("b", "Kind of Blue", "Miles Davis", 5, 1987, status = "Bootleg", group = "g2")
        val ranked = CoverMatch.rank(local, listOf(bootleg, official))
        assertEquals("a", ranked.first().release.id)
    }

    @Test fun unknownArtistDoesNotStopATitleMatch() {
        val local = LocalAlbumQuery("Kind of Blue", "Unknown artist", 5, 0)
        val candidate = release("a", "Kind of Blue", "Miles Davis", 5, 1987)
        assertTrue(CoverMatch.confidence(local, candidate) >= CoverMatch.AUTO_APPLY)
    }
}

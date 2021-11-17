package no.nav.k9punsj.rest.server

import io.mockk.junit5.MockKExtension
import no.nav.k9punsj.TestSetup
import no.nav.k9punsj.awaitExchangeBlocking
import no.nav.k9punsj.wiremock.k9SakToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension


@ExtendWith(SpringExtension::class, MockKExtension::class)
class JournalpostInfoRoutesTest{

    private val client = TestSetup.client
    private val k9sakToken = "Bearer ${no.nav.helse.dusseldorf.testsupport.jws.NaisSts.k9SakToken()}"


    @Test
    suspend fun `Får en liste med journalpostIder som ikke er ferdig behandlet av punsj`() {
        val res = client.get().uri {
            it.pathSegment("api", "journalpost", "uferdig", "1000000000000").build()
        }.header(HttpHeaders.AUTHORIZATION, k9sakToken)

        val clientResponse = res.awaitExchangeBlocking()
        assertEquals(HttpStatus.OK, clientResponse.statusCode())
    }
}

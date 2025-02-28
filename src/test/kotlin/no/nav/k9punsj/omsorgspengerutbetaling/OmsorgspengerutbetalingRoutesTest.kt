package no.nav.k9punsj.omsorgspengerutbetaling

import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import no.nav.helse.dusseldorf.testsupport.jws.Azure
import no.nav.k9punsj.TestSetup
import no.nav.k9punsj.felles.IdentOgJournalpost
import no.nav.k9punsj.felles.dto.ArbeidsgiverMedArbeidsforholdId
import no.nav.k9punsj.felles.dto.MatchFagsakMedPeriode
import no.nav.k9punsj.felles.dto.PeriodeDto
import no.nav.k9punsj.felles.dto.SendSøknad
import no.nav.k9punsj.openapi.OasSoknadsfeil
import no.nav.k9punsj.util.DatabaseUtil
import no.nav.k9punsj.util.IdGenerator
import no.nav.k9punsj.util.LesFraFilUtil
import no.nav.k9punsj.util.SøknadJson
import no.nav.k9punsj.util.TestUtils.hentSøknadId
import no.nav.k9punsj.util.WebClientUtils.getAndAssert
import no.nav.k9punsj.util.WebClientUtils.postAndAssert
import no.nav.k9punsj.util.WebClientUtils.postAndAssertAwaitWithStatusAndBody
import no.nav.k9punsj.util.WebClientUtils.putAndAssert
import no.nav.k9punsj.wiremock.saksbehandlerAccessToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.reactive.function.BodyInserters
import java.net.URI
import java.time.LocalDate
import java.util.*
import kotlin.math.abs
import kotlin.random.Random

@ExtendWith(SpringExtension::class, MockKExtension::class)
class OmsorgspengerutbetalingRoutesTest {

    private val client = TestSetup.client
    private val api = "api"
    private val søknadTypeUri = "omsorgspengerutbetaling-soknad"
    private val saksbehandlerAuthorizationHeader = "Bearer ${Azure.V2_0.saksbehandlerAccessToken()}"
    private val journalpostRepository = DatabaseUtil.getJournalpostRepo()

    @AfterEach
    internal fun tearDown() {
        DatabaseUtil.cleanDB()
    }

    @Test
    fun `Får tom liste når personen ikke har en eksisterende mappe`(): Unit = runBlocking {
        val norskIdent = "01110050053"
        val body = client.getAndAssert<SvarOmsUtDto>(
            norskIdent = norskIdent,
            authorizationHeader = saksbehandlerAuthorizationHeader,
            assertStatus = HttpStatus.OK,
            api,
            søknadTypeUri,
            "mappe"
        )

        Assertions.assertTrue(body.søknader!!.isEmpty())
    }

    @Test
    fun `Opprette ny mappe på person`(): Unit = runBlocking {
        val norskIdent = "01010050053"
        val opprettNySøknad = opprettSøknad(norskIdent, UUID.randomUUID().toString())

        client.postAndAssert(
            authorizationHeader = saksbehandlerAuthorizationHeader,
            assertStatus = HttpStatus.CREATED,
            requestBody = BodyInserters.fromValue(opprettNySøknad),
            api,
            søknadTypeUri
        )
    }

    @Test
    fun `Hente eksisterende mappe på person`(): Unit = runBlocking {
        val norskIdent = "02020050163"
        val journalpostId = UUID.randomUUID().toString()
        val opprettNySøknad = opprettSøknad(norskIdent, journalpostId)

        client.postAndAssert(
            authorizationHeader = saksbehandlerAuthorizationHeader,
            assertStatus = HttpStatus.CREATED,
            requestBody = BodyInserters.fromValue(opprettNySøknad),
            api,
            søknadTypeUri
        )

        val body = client.getAndAssert<SvarOmsUtDto>(
            norskIdent = norskIdent,
            authorizationHeader = saksbehandlerAuthorizationHeader,
            assertStatus = HttpStatus.OK,
            api,
            søknadTypeUri,
            "mappe"
        )

        val journalposterDto = body.søknader?.first()?.journalposter
        Assertions.assertEquals(journalpostId, journalposterDto?.first())
    }

    @Test
    fun `Hent en søknad`(): Unit = runBlocking {
        val søknad = LesFraFilUtil.søknadFraFrontend()
        val norskIdent = "02030050163"
        val journalpostid = abs(Random(2224).nextInt()).toString()
        tilpasserSøknadsMalTilTesten(søknad, norskIdent, journalpostid)

        val opprettNySøknad = opprettSøknad(norskIdent, journalpostid)

        val resPost = client.postAndAssert<IdentOgJournalpost>(
            authorizationHeader = saksbehandlerAuthorizationHeader,
            assertStatus = HttpStatus.CREATED,
            requestBody = BodyInserters.fromValue(opprettNySøknad),
            api,
            søknadTypeUri
        )

        val location = resPost.headers().asHttpHeaders().location
        Assertions.assertNotNull(location)

        val søknadViaGet = client.getAndAssert<OmsorgspengerutbetalingSøknadDto>(
            norskIdent = norskIdent,
            authorizationHeader = saksbehandlerAuthorizationHeader,
            assertStatus = HttpStatus.OK,
            api,
            søknadTypeUri,
            "mappe",
            hentSøknadId(location)!!
        )

        Assertions.assertNotNull(søknadViaGet)
        Assertions.assertEquals(journalpostid, søknadViaGet.journalposter?.first())
    }

    @Test
    fun `Oppdaterer en søknad`(): Unit = runBlocking {
        val søknadFraFrontend = LesFraFilUtil.søknadFraFrontendOmsUt()
        val norskIdent = "02030050163"
        val journalpostid = abs(Random(1234).nextInt()).toString()
        tilpasserSøknadsMalTilTesten(søknadFraFrontend, norskIdent, journalpostid)

        val opprettNySøknad = opprettSøknad(norskIdent, journalpostid)

        val resPost = client.postAndAssert<IdentOgJournalpost>(
            authorizationHeader = saksbehandlerAuthorizationHeader,
            assertStatus = HttpStatus.CREATED,
            requestBody = BodyInserters.fromValue(opprettNySøknad),
            api,
            søknadTypeUri
        )

        val location = resPost.headers().asHttpHeaders().location
        Assertions.assertNotNull(location)

        leggerPåNySøknadId(søknadFraFrontend, location)

        val body = client.putAndAssert<MutableMap<String, Any?>, OmsorgspengerutbetalingSøknadDto>(
            norskIdent = null,
            authorizationHeader = saksbehandlerAuthorizationHeader,
            assertStatus = HttpStatus.OK,
            requestBody = BodyInserters.fromValue(søknadFraFrontend),
            api,
            søknadTypeUri,
            "oppdater"
        )

        Assertions.assertNotNull(body)
        Assertions.assertEquals(norskIdent, body.soekerId)
    }

    @Test
    fun `Oppdaterer en søknad med metadata`(): Unit = runBlocking {
        val søknadFraFrontend = LesFraFilUtil.søknadFraFrontendOmsUt()
        val norskIdent = "02030050163"
        val journalpostid = abs(Random(1234).nextInt()).toString()
        tilpasserSøknadsMalTilTesten(søknadFraFrontend, norskIdent, journalpostid)

        val opprettNySøknad = opprettSøknad(norskIdent, journalpostid)

        val resPost = client.postAndAssert<IdentOgJournalpost>(
            authorizationHeader = saksbehandlerAuthorizationHeader,
            assertStatus = HttpStatus.CREATED,
            requestBody = BodyInserters.fromValue(opprettNySøknad),
            api,
            søknadTypeUri
        )

        val location = resPost.headers().asHttpHeaders().location
        Assertions.assertNotNull(location)

        leggerPåNySøknadId(søknadFraFrontend, location)

        val body = client.putAndAssert<MutableMap<String, Any?>, OmsorgspengerutbetalingSøknadDto>(
            norskIdent = null,
            authorizationHeader = saksbehandlerAuthorizationHeader,
            assertStatus = HttpStatus.OK,
            requestBody = BodyInserters.fromValue(søknadFraFrontend),
            api,
            søknadTypeUri,
            "oppdater"
        )

        Assertions.assertNotNull(body)
        Assertions.assertEquals(norskIdent, body.soekerId)

        val søknadViaGet = client.getAndAssert<OmsorgspengerutbetalingSøknadDto>(
            norskIdent = norskIdent,
            authorizationHeader = saksbehandlerAuthorizationHeader,
            assertStatus = HttpStatus.OK,
            api,
            søknadTypeUri,
            "mappe",
            hentSøknadId(location)!!
        )

        Assertions.assertNotNull(søknadViaGet)
        assertThat(body.metadata).isEqualTo(søknadViaGet.metadata)
    }

    @Test
    fun `Prøver å sende søknaden til Kafka når den er gyldig`(): Unit = runBlocking {
        val norskIdent = "02020050123"
        val gyldigSoeknad: SøknadJson = LesFraFilUtil.søknadFraFrontendOmsUt()
        val journalpostid = abs(Random(56234).nextInt()).toString()
        tilpasserSøknadsMalTilTesten(gyldigSoeknad, norskIdent, journalpostid)

        val body = opprettOgSendInnSoeknad(soeknadJson = gyldigSoeknad, ident = norskIdent, journalpostid)
        assertThat(body.feil).isNull()
        assertThat(journalpostRepository.kanSendeInn(listOf(journalpostid))).isFalse
    }

    @Test
    fun `Skal fordele trekk av dager på enkelt dager slik at det validere ok`(): Unit = runBlocking {
        val norskIdent = "02020050123"
        val gyldigSoeknad: SøknadJson = LesFraFilUtil.søknadFraFrontendOmsUtTrekk()
        val journalpostid = abs(Random(2234).nextInt()).toString()
        tilpasserSøknadsMalTilTesten(gyldigSoeknad, norskIdent, journalpostid)

        val body = opprettOgSendInnSoeknad(soeknadJson = gyldigSoeknad, ident = norskIdent, journalpostid)
        assertThat(body.feil).isNull()
        assertThat(journalpostRepository.kanSendeInn(listOf(journalpostid))).isFalse
    }

    @Test
    fun `Skal verifisere at søknad er ok`(): Unit = runBlocking {
        val norskIdent = "02022352122"
        val soeknad: SøknadJson = LesFraFilUtil.søknadFraFrontendOmsUt()
        val journalpostid = abs(Random(234234).nextInt()).toString()
        tilpasserSøknadsMalTilTesten(soeknad, norskIdent, journalpostid)
        opprettOgLagreSoeknad(soeknadJson = soeknad, ident = norskIdent, journalpostid)

        val body = client.postAndAssertAwaitWithStatusAndBody<SøknadJson, OasSoknadsfeil>(
            authorizationHeader = saksbehandlerAuthorizationHeader,
            navNorskIdentHeader = null,
            assertStatus = HttpStatus.ACCEPTED,
            requestBody = BodyInserters.fromValue(soeknad),
            api,
            søknadTypeUri,
            "valider"
        )

        assertThat(body.feil).isNull()
    }

    @Test
    fun `Korrigering OMP UT med fraværsperioder fra tidiger år validerer riktigt år`() = runBlocking {
        // 03011939596 på OMS har två perioder i k9sak fra december 2022.
        // OmsUtKorrigering fjerner første perioden i 2022.
        val norskIdent = "03011939596"
        val soeknad: SøknadJson = LesFraFilUtil.søknadFraFrontendOmsUtKorrigering()
        val journalpostid = abs(Random(234234).nextInt()).toString()
        tilpasserSøknadsMalTilTesten(soeknad, norskIdent, journalpostid)
        opprettOgLagreSoeknad(soeknadJson = soeknad, ident = norskIdent, journalpostid)

        val body = client.postAndAssertAwaitWithStatusAndBody<SøknadJson, OasSoknadsfeil>(
            authorizationHeader = saksbehandlerAuthorizationHeader,
            navNorskIdentHeader = null,
            assertStatus = HttpStatus.ACCEPTED,
            requestBody = BodyInserters.fromValue(soeknad),
            api,
            søknadTypeUri,
            "valider"
        )

        assertThat(body.feil).isNull()
    }

    @Test
    fun `skal få feil hvis mottattDato ikke er fylt ut`(): Unit = runBlocking {
        val norskIdent = "02022352122"
        val soeknad: SøknadJson = LesFraFilUtil.søknadFraFrontendOmsUtFeil()
        val journalpostid = abs(Random(234234).nextInt()).toString()
        tilpasserSøknadsMalTilTesten(soeknad, norskIdent, journalpostid)
        opprettOgLagreSoeknad(soeknadJson = soeknad, ident = norskIdent, journalpostid)

        val body = client.postAndAssertAwaitWithStatusAndBody<SøknadJson, OasSoknadsfeil>(
            authorizationHeader = saksbehandlerAuthorizationHeader,
            navNorskIdentHeader = null,
            assertStatus = HttpStatus.BAD_REQUEST,
            requestBody = BodyInserters.fromValue(soeknad),
            api,
            søknadTypeUri,
            "valider"
        )

        assertThat(body.feil?.get(0)?.feilkode).isEqualTo("mottattDato")
    }

    @Test
    fun `Skal fordele trekk av dager på enkelt dager slik at det validere ok - kompleks versjon`(): Unit = runBlocking {
        val norskIdent = "02020050123"
        val gyldigSoeknad: SøknadJson = LesFraFilUtil.søknadFraFrontendOmsUtTrekkKompleks()
        val journalpostid = abs(Random(2256234).nextInt()).toString()
        tilpasserSøknadsMalTilTesten(gyldigSoeknad, norskIdent, journalpostid)

        val body = opprettOgSendInnSoeknad(soeknadJson = gyldigSoeknad, ident = norskIdent, journalpostid)
        assertThat(body.feil).isNull()
        assertThat(journalpostRepository.kanSendeInn(listOf(journalpostid))).isFalse
    }

    @Test
    fun `Skal hente arbeidsforholdIder fra k9-sak`(): Unit = runBlocking {
        val norskIdent = "02020050123"
        val dtoSpørring =
            MatchFagsakMedPeriode(
                norskIdent,
                PeriodeDto(LocalDate.now(), LocalDate.now().plusDays(1))
            )

        val oppdatertSoeknadDto =
            client.postAndAssertAwaitWithStatusAndBody<MatchFagsakMedPeriode, List<ArbeidsgiverMedArbeidsforholdId>>(
                authorizationHeader = saksbehandlerAuthorizationHeader,
                navNorskIdentHeader = null,
                assertStatus = HttpStatus.OK,
                requestBody = BodyInserters.fromValue(dtoSpørring),
                api,
                søknadTypeUri,
                "k9sak",
                "arbeidsforholdIder"
            )

        Assertions.assertEquals("randomArbeidsforholdId", oppdatertSoeknadDto[0].arbeidsforholdId[0])
    }

    private fun opprettSøknad(
        personnummer: String,
        journalpostId: String
    ): IdentOgJournalpost {
        return IdentOgJournalpost(personnummer, journalpostId)
    }

    private fun tilpasserSøknadsMalTilTesten(
        søknad: MutableMap<String, Any?>,
        norskIdent: String,
        journalpostId: String? = null
    ) {
        søknad.replace("soekerId", norskIdent)
        if (journalpostId != null) søknad.replace("journalposter", arrayOf(journalpostId))
    }

    private fun leggerPåNySøknadId(søknadFraFrontend: MutableMap<String, Any?>, location: URI?) {
        val path = location?.path
        val søknadId = path?.substring(path.lastIndexOf('/'))
        val trim = søknadId?.trim('/')
        søknadFraFrontend.replace("soeknadId", trim)
    }

    private fun lagSendSøknad(
        norskIdent: String,
        søknadId: String
    ): SendSøknad {
        return SendSøknad(norskIdent, søknadId)
    }

    private suspend fun opprettOgSendInnSoeknad(
        soeknadJson: SøknadJson,
        ident: String,
        journalpostid: String = IdGenerator.nesteId()
    ): OasSoknadsfeil {
        val innsendingForOpprettelseAvMappe = opprettSøknad(ident, journalpostid)

        // oppretter en søknad
        val response = client.postAndAssert(
            authorizationHeader = saksbehandlerAuthorizationHeader,
            assertStatus = HttpStatus.CREATED,
            requestBody = BodyInserters.fromValue(innsendingForOpprettelseAvMappe),
            api,
            søknadTypeUri
        )

        val location = response.headers().asHttpHeaders().location
        Assertions.assertEquals(HttpStatus.CREATED, response.statusCode())
        Assertions.assertNotNull(location)

        leggerPåNySøknadId(soeknadJson, location)

        // fyller ut en søknad
        val søknadDtoFyltUt: OmsorgspengerutbetalingSøknadDto = client.putAndAssert(
            norskIdent = null,
            authorizationHeader = saksbehandlerAuthorizationHeader,
            assertStatus = HttpStatus.OK,
            requestBody = BodyInserters.fromValue(soeknadJson),
            api,
            søknadTypeUri,
            "oppdater"
        )

        Assertions.assertNotNull(søknadDtoFyltUt.soekerId)

        val søknadId = søknadDtoFyltUt.soeknadId
        val sendSøknad = lagSendSøknad(norskIdent = ident, søknadId = søknadId)

        val journalposter = søknadDtoFyltUt.journalposter!!

        val kanSendeInn = journalpostRepository.kanSendeInn(journalposter)
        assertThat(kanSendeInn).isTrue

        // sender en søknad
        val body = client.postAndAssertAwaitWithStatusAndBody<SendSøknad, OasSoknadsfeil>(
            authorizationHeader = saksbehandlerAuthorizationHeader,
            navNorskIdentHeader = null,
            assertStatus = HttpStatus.ACCEPTED,
            requestBody = BodyInserters.fromValue(sendSøknad),
            api,
            søknadTypeUri,
            "send"
        )

        return body
    }

    private suspend fun opprettOgLagreSoeknad(
        soeknadJson: SøknadJson,
        ident: String,
        journalpostid: String = IdGenerator.nesteId()
    ): OmsorgspengerutbetalingSøknadDto {
        val innsendingForOpprettelseAvMappe = opprettSøknad(ident, journalpostid)

        // oppretter en søknad
        val resPost = client.postAndAssert(
            authorizationHeader = saksbehandlerAuthorizationHeader,
            assertStatus = HttpStatus.CREATED,
            requestBody = BodyInserters.fromValue(innsendingForOpprettelseAvMappe),
            api,
            søknadTypeUri
        )

        val location = resPost.headers().asHttpHeaders().location
        Assertions.assertNotNull(location)

        leggerPåNySøknadId(soeknadJson, location)

        // fyller ut en søknad
        val søknadDtoFyltUt = client.putAndAssert<SøknadJson, OmsorgspengerutbetalingSøknadDto>(
            norskIdent = null,
            authorizationHeader = saksbehandlerAuthorizationHeader,
            assertStatus = HttpStatus.OK,
            requestBody = BodyInserters.fromValue(soeknadJson),
            api,
            søknadTypeUri,
            "oppdater"
        )

        Assertions.assertNotNull(søknadDtoFyltUt.soekerId)
        return søknadDtoFyltUt
    }
}

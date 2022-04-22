package no.nav.k9punsj.openapi

import no.nav.k9punsj.journalpost.JournalpostInfoRoutes
import no.nav.k9punsj.felles.SøkUferdigJournalposter
import org.springframework.web.bind.annotation.*

@RestController
internal class JournalpostInfoContoller {
    @GetMapping(JournalpostInfoRoutes.Urls.HentÅpneJournalposter, produces = ["application/json"])
    fun HentÅpneJournalposter(@PathVariable("aktor_id") aktørId: String) {
    }

    @PostMapping(JournalpostInfoRoutes.Urls.HentÅpneJournalposterPost, produces = ["application/json"], consumes = ["application/json"])
    fun HentÅpneJournalposterPost(@RequestBody dto: SøkUferdigJournalposter) {
    }
}

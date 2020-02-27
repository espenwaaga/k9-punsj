package no.nav.k9.mappe

import no.nav.k9.*
import org.springframework.stereotype.Service
import java.util.*

typealias MappeId = String

data class Mappe(
        val mappeId: MappeId,
        val søknadType: SøknadType,
        val person: MutableMap<NorskIdent, Person>
)

data class Person(
        val innsendinger: MutableSet<JournalpostId>,
        val soeknad: SøknadJson
)

internal fun Mappe.dto(personMangler: Map<NorskIdent, Set<Mangel>>) : MappeSvarDTO {
    val personer = mutableMapOf<NorskIdent, PersonDTO<SøknadJson>>()
    personMangler.forEach { (norskIdent, mangler) ->
        personer[norskIdent] = PersonDTO(
                innsendinger = person[norskIdent]!!.innsendinger,
                soeknad = person[norskIdent]!!.soeknad,
                mangler = mangler
        )
    }

    return MappeSvarDTO(
            mappeId = mappeId,
            personer = personer
        )
}


private fun JournalpostInnhold<SøknadJson>.leggIUndermappe(
        person: Person?
) : Person {
    soeknad["id"] = UUID.randomUUID().toString()
    return Person(
            innsendinger = person?.innsendinger?.leggTil(journalpostId) ?: mutableSetOf(journalpostId),
            soeknad = person?.soeknad?.merge(soeknad) ?: soeknad
    )
}

internal fun Innsending.leggIMappe(
        mappe: Mappe?,
        søknadType: SøknadType? = null
) : Mappe {
    val personligInnholdUndermapper = mappe?.person?: mutableMapOf()
    personer?.forEach { (norskIdent, journalpostInnhold) ->
        personligInnholdUndermapper[norskIdent] = journalpostInnhold.leggIUndermappe(person = mappe?.person?.get(norskIdent))
    }

    return Mappe(
            mappeId = mappe?.mappeId ?: UUID.randomUUID().toString(),
            søknadType = mappe?.søknadType ?: søknadType!!,
            person = personligInnholdUndermapper
    )
}

private fun <E> MutableSet<E>.leggTil(item: E): MutableSet<E> {
    add(item)
    return this
}

@Service
internal class MappeService {
    private val map = mutableMapOf<MappeId, Mappe>()

    internal suspend fun hent(
            norskeIdenter: Set<NorskIdent>,
            søknadType: SøknadType
    ) = map.filterValues { it.person.containsKeys(norskeIdenter) }.map { (_, mappe) ->
        mappe
    }.toSet()

    internal suspend fun førsteInnsending(
            søknadType: SøknadType,
            innsending: Innsending
    ) : Mappe {
        val opprettetMappe = innsending.leggIMappe(mappe = null, søknadType = søknadType);

        map[opprettetMappe.mappeId] = opprettetMappe

        return opprettetMappe
    }

    internal suspend fun utfyllendeInnsending(
            mappeId: MappeId,
            søknadType: SøknadType,
            innsending: Innsending
    ) : Mappe? {
        val eksisterendeMappe = map[mappeId]?: return null
        val oppdatertMappe = innsending.leggIMappe(mappe = eksisterendeMappe)

        map[mappeId] = oppdatertMappe

        return oppdatertMappe
    }

    internal suspend fun hent(
            mappeId: MappeId
    ) = map[mappeId]


    internal suspend fun fjern(
            mappeId: MappeId,
            norskIdent: NorskIdent
    ) {
        val mappe = hent(mappeId)?:return
        if (mappe.person.containsKey(norskIdent)) {
            if (mappe.person.size == 1) {
                map.remove(mappeId)
            } else {
                mappe.person.remove(norskIdent)
                map[mappeId] = mappe
            }
        }
    }
}

private fun <K, V> Map<K, V>.containsKeys(keys: Set<K>): Boolean {
    keys.forEach { key ->
        if (!containsKey(key)) {
            return false
        }
    }
    return true
}

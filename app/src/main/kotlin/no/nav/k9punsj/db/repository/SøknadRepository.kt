package no.nav.k9punsj.db.repository

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9punsj.db.datamodell.BunkeId
import no.nav.k9punsj.db.datamodell.SøknadEntitet
import no.nav.k9punsj.db.datamodell.SøknadId
import no.nav.k9punsj.objectMapper
import org.springframework.stereotype.Repository
import java.util.UUID
import javax.sql.DataSource

@Repository
class SøknadRepository(private val dataSource: DataSource) {

    suspend fun opprettSøknad(søknad: SøknadEntitet): SøknadEntitet {
        return using(sessionOf(dataSource)) {
            return@using it.transaction { tx ->
                //language=PostgreSQL
                tx.run(
                    queryOf(
                        """
                    insert into soknad as k (soknad_id, id_bunke, id_person, id_person_barn, barn_fodselsdato, soknad, journalposter)
                    values (:soknad_id, :id_bunke, :id_person, :id_person_barn, :barn_fodselsdato, :soknad :: jsonb, :journalposter :: jsonb)
                    """, mapOf(
                            "soknad_id" to UUID.fromString(søknad.søknadId),
                            "id_bunke" to UUID.fromString(søknad.bunkeId),
                            "id_person" to UUID.fromString(søknad.søkerId),
                            "id_person_barn" to if (søknad.barnId != null) UUID.fromString(søknad.barnId) else null,
                            "barn_fodselsdato" to søknad.barnFødselsdato,
                            "soknad" to objectMapper().writeValueAsString(søknad.søknad),
                            "journalposter" to objectMapper().writeValueAsString(søknad.journalposter))
                    ).asUpdate
                )
                return@transaction søknad
            }
        }
    }

    fun hentAlleSøknaderForBunke(bunkerId: BunkeId): List<SøknadEntitet> {
        return using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        "select soknad_id, id_bunke, id_person, id_person_barn, barn_fodselsdato, soknad, journalposter, sendt_inn, saksnummer from soknad where id_bunke = :id_bunke",
                        mapOf("id_bunke" to UUID.fromString(bunkerId))
                    )
                        .map { row ->
                            søknadEntitet(row)
                        }.asList
                )
            }
        }
    }

    fun oppdaterSøknad(søknad: SøknadEntitet) {
        return using(sessionOf(dataSource)) {
            return@using it.transaction { tx ->
                tx.run(
                    queryOf(
                        """
                    update soknad
                    set soknad = :soknad :: jsonb,
                    journalposter = :journalposter :: jsonb,
                    endret_tid = now(),
                    endret_av = :endret_av,
                    saksnummer = :saksnummer
                    where soknad_id = :soknad_id
                    """,
                        mapOf(
                            "soknad_id" to UUID.fromString(søknad.søknadId),
                            "soknad" to objectMapper().writeValueAsString(søknad.søknad),
                            "journalposter" to objectMapper().writeValueAsString(søknad.journalposter),
                            "endret_av" to objectMapper().writeValueAsString(søknad.endret_av),
                            "saksnummer" to søknad.saksnummerK9Sak),
                    ).asUpdate
                )
            }
        }
    }

    suspend fun hentSøknad(søknadId: SøknadId): SøknadEntitet? {
        return using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        "select soknad_id, id_bunke, id_person, id_person_barn, barn_fodselsdato, soknad, journalposter, sendt_inn, saksnummer from soknad where soknad_id = :soknad_id",
                        mapOf("soknad_id" to UUID.fromString(søknadId))
                    )
                        .map { row ->
                            søknadEntitet(row)
                        }.asSingle
                )
            }
        }
    }

    private fun søknadEntitet(row: Row) = SøknadEntitet(
        søknadId = row.string("soknad_id"),
        bunkeId = row.string("id_bunke"),
        søkerId = row.string("id_person"),
        barnId = row.stringOrNull("id_person_barn"),
        barnFødselsdato = row.localDateOrNull("barn_fodselsdato"),
        søknad = objectMapper().readValue(row.string("soknad")),
        journalposter = objectMapper().readValue(row.string("journalposter")),
        sendtInn = row.boolean("sendt_inn"),
        saksnummerK9Sak = row.stringOrNull("saksnummer")
    )

    suspend fun markerSomSendtInn(søknadId: SøknadId) {
        return using(sessionOf(dataSource)) {
            return@using it.transaction { tx ->
                tx.run(
                    queryOf(
                        """
                    update soknad
                    set SENDT_INN = true
                    where soknad_id = :soknad_id
                    """, mapOf(
                            "soknad_id" to UUID.fromString(søknadId))
                    ).asUpdate
                )
            }
        }
    }
}

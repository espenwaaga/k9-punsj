package no.nav.k9punsj.wiremock

import com.github.tomakehurst.wiremock.core.Options
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder

fun initWireMock(
    port: Int,
    rootDirectory: String = "src/test/resources"
) = WireMockBuilder()
    .withPort(port)
    .withAzureSupport()
    .withNaisStsSupport()
    .wireMockConfiguration {
        it.withRootDirectory(rootDirectory)
        it.useChunkedTransferEncoding(Options.ChunkedEncodingPolicy.NEVER)
    }
    .build()
    .stubSaf()
    .stubPdl()
    .stubAccessTokens()
    .stubGosys()
    .stubAareg()
    .stubEreg()
    .stubDokarkiv()

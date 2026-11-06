package org.opliterature.lnr.source.explore

import io.nightfish.lightnovelreader.api.web.explore.AbstractDefaultExplorePageProvider
import org.opliterature.lnr.data.OpLiteratureApi

class OpLiteratureExplorePageProvider(
    api: OpLiteratureApi
) : AbstractDefaultExplorePageProvider() {

    init {
        registerTapPage(OpLiteratureShelfPageDataSource(api))
    }
}

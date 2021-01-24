package example

import com.jillesvangurp.eskotlinwrapper.AsyncSearchResults
import kotlinx.coroutines.flow.collect

// BEGIN search_response
data class SearchResponse<T : Any>(val totalHits: Long, val items: List<T>)

suspend fun <T : Any> AsyncSearchResults<T>
.toSearchResponse(): SearchResponse<T> {
    val collectedHits = mutableListOf<T>()
    this.mappedHits.collect {
        collectedHits.add(it)
    }
    return SearchResponse(this.total, collectedHits)
}
// END search_response

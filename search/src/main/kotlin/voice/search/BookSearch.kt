package voice.search

import kotlinx.coroutines.flow.Flow
import voice.data.Book
import voice.data.repo.BookRepository
import voice.data.repo.internals.dao.BookContentDao
import voice.search.repository.DiscoveryResult
import voice.search.repository.SearchRepository
import javax.inject.Inject

class BookSearch
@Inject constructor(
  private val dao: BookContentDao,
  private val repo: BookRepository,
  private val searchRepository: SearchRepository,
) {
  // Returns local results immediately, discovery results come through Flow
  suspend fun search(query: String): Pair<List<Book>, Flow<Result<List<DiscoveryResult>>>> {
    val localResults = searchLocal(query)
    val discoveryResultsFlow = searchRepository.search(query)
    return Pair(localResults, discoveryResultsFlow)
  }

  // Existing local search functionality
  private suspend fun searchLocal(query: String): List<Book> {
    return dao.search(
      buildString {
        append("\"")
        append('*')
        append(query.trim().replace("\"", "\"\""))
        append('*')
        append("\"")
      },
    ).mapNotNull { repo.get(it) }
  }
}

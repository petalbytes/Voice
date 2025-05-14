package voice.search.error

sealed class SearchError(message: String, cause: Throwable? = null) : Exception(message, cause) {
  class NetworkError(message: String, cause: Throwable? = null) : 
    SearchError(message, cause)
  
  class ApiError(val code: Int, message: String) : 
    SearchError("$message (code: $code)")
  
  class ParseError(message: String, cause: Throwable? = null) : 
    SearchError(message, cause)
  
  class UnknownError(message: String, cause: Throwable? = null) : 
    SearchError(message, cause)
} 
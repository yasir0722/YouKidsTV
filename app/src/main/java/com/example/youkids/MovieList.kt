package com.example.youkids

import java.util.Locale

object MovieList {
    @Volatile
    private var cachedMovies: List<Movie> = emptyList()

    val list: List<Movie>
        get() = cachedMovies

    fun replace(movies: List<Movie>) {
        cachedMovies = movies.toList()
    }

    fun relatedMoviesFor(movie: Movie): List<Movie> =
        cachedMovies.filterNot { it.id == movie.id }

    fun tagsFor(movies: List<Movie>): List<VideoTag> =
        movies.mapNotNull { movie ->
            movieTags(movie).ifEmpty { null }
        }
            .flatten()
            .groupBy { it.lowercase(Locale.ROOT) }
            .map { (_, tags) -> VideoTag(tags.first(), tags.size) }
            .sortedBy { it.name.lowercase(Locale.ROOT) }

    fun videosForTag(movies: List<Movie>, tag: String): List<Movie> =
        movies.filter { movie ->
            movieTags(movie).any { it.equals(tag.trim(), ignoreCase = true) }
        }

    private fun movieTags(movie: Movie): List<String> =
        movie.tag
            ?.split(',', '|', '\n')
            ?.map { it.trim() }
            ?.filter(String::isNotEmpty)
            .orEmpty()
}

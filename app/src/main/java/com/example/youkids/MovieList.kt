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
            movie.tag?.trim()?.takeIf(String::isNotEmpty)
        }
            .groupBy { it.lowercase(Locale.ROOT) }
            .values
            .map { tags -> VideoTag(tags.first(), tags.size) }
            .sortedBy { it.name.lowercase(Locale.ROOT) }

    fun videosForTag(movies: List<Movie>, tag: String): List<Movie> =
        movies.filter { it.tag?.trim()?.equals(tag.trim(), ignoreCase = true) == true }
}

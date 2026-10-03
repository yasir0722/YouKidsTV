package com.example.youkids

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MovieListTest {

    @org.junit.Before
    fun setUp() {
        MovieList.replace(
            listOf(
                Movie(id = 1, title = "One"),
                Movie(id = 2, title = "Two"),
                Movie(id = 3, title = "Three")
            )
        )
    }

    @org.junit.After
    fun tearDown() {
        MovieList.replace(emptyList())
    }

    @Test
    fun relatedMoviesExcludeSelectedMovieAndPreserveCatalogueOrder() {
        val catalogue = MovieList.list
        val selectedMovie = catalogue[2]

        val relatedMovies = MovieList.relatedMoviesFor(selectedMovie)

        assertEquals(catalogue.filterNot { it.id == selectedMovie.id }, relatedMovies)
        assertFalse(relatedMovies.any { it.id == selectedMovie.id })
    }

    @Test
    fun catalogueOrderAndIdsAreStable() {
        val firstRead = MovieList.list.toList()
        val secondRead = MovieList.list.toList()

        assertEquals(firstRead.map { it.id to it.title }, secondRead.map { it.id to it.title })
        assertEquals(listOf(1L, 2L, 3L), firstRead.map { it.id })
    }

    @Test
    fun tagsAreGroupedCaseInsensitivelyAndVideosCanBeFilteredByTag() {
        val movies = listOf(
            Movie(id = 1, title = "One", tag = "Bing"),
            Movie(id = 2, title = "Two", tag = "bing"),
            Movie(id = 3, title = "Three", tag = "Upin Ipin"),
            Movie(id = 4, title = "Four"),
        )

        assertEquals(
            listOf(VideoTag("Bing", 2), VideoTag("Upin Ipin", 1)),
            MovieList.tagsFor(movies),
        )
        assertEquals(listOf(movies[0], movies[1]), MovieList.videosForTag(movies, "BING"))
    }
}

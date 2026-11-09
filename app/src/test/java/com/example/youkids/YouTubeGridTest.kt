package com.example.youkids

import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeGridTest {
    @Test
    fun gridUsesAtMostFourRowsAndPreservesEdgeVideos() {
        val movies = (1L..10L).map { Movie(id = it) }

        val rows = YouTubeGrid(movies).rows

        assertEquals(4, rows.size)
        assertEquals(listOf(1L, 2L, 3L), rows.first().map(Movie::id))
        assertEquals(listOf(10L), rows.last().map(Movie::id))
        assertEquals(listOf(3, 3, 3, 1), rows.map { it.size })
        assertEquals(movies, rows.flatten())
    }

    @Test
    fun gridUsesOneRowPerVideoWhenThereAreFewerThanFourVideos() {
        val movies = listOf(Movie(id = 1), Movie(id = 2))

        val rows = YouTubeGrid(movies).rows

        assertEquals(2, rows.size)
        assertEquals(listOf(movies[0]), rows[0])
        assertEquals(listOf(movies[1]), rows[1])
    }

    @Test
    fun emptyGridHasNoRows() {
        assertEquals(emptyList<List<Movie>>(), YouTubeGrid(emptyList()).rows)
    }
}

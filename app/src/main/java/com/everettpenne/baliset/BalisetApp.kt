package com.everettpenne.baliset

import android.app.Application
import android.content.Context

/** One process, one library. Built here so every screen reaches the same instances. */
class BalisetApp : Application() {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
    }
}

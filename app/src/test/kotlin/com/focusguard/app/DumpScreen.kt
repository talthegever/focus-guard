package com.focusguard.app

import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** A [Screen] read from a dump file written by the debug build (see tools/dump_screen.py). */
class DumpScreen private constructor(
    override val appPackage: String,
    private val viewsById: Map<String, List<ViewState>>,
) : Screen {
    override fun views(viewId: String): List<ViewState> = viewsById[viewId].orEmpty()

    companion object {
        fun load(name: String): DumpScreen {
            val path = "/screens/$name.xml"
            val stream = DumpScreen::class.java.getResourceAsStream(path)
                ?: error("Missing screen dump: src/test/resources$path")
            val root = stream.use {
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it).documentElement
            }
            val views = mutableMapOf<String, MutableList<ViewState>>()
            val nodes = root.getElementsByTagName("node")
            for (i in 0 until nodes.length) {
                val node = nodes.item(i) as Element
                val id = node.getAttribute("id").substringAfter(":id/", missingDelimiterValue = "")
                if (id.isEmpty()) continue
                views.getOrPut(id) { mutableListOf() } += ViewState(
                    selected = node.getAttribute("selected") == "true",
                    visible = node.getAttribute("visible") != "false",
                )
            }
            return DumpScreen(root.getAttribute("package"), views)
        }
    }
}

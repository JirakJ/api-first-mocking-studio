package com.jakumjirak

import com.google.gson.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class JsonTreeModelBuilder {
    fun build(jsonStr: String): DefaultTreeModel {
        val root = DefaultMutableTreeNode("Response")
        try {
            val element = JsonParser.parseString(jsonStr)
            buildNodes(root, element)
        } catch (e: Exception) {
            root.add(DefaultMutableTreeNode("Error parsing JSON: ${e.message}"))
        }
        return DefaultTreeModel(root)
    }

    private fun buildNodes(node: DefaultMutableTreeNode, element: JsonElement, name: String? = null) {
        val label = if (name != null) "$name: " else ""
        
        when {
            element.isJsonObject -> {
                val objNode = if (name != null) DefaultMutableTreeNode(name) else node
                if (name != null) node.add(objNode)
                element.asJsonObject.entrySet().forEach { (key, value) ->
                    buildNodes(objNode, value, key)
                }
            }
            element.isJsonArray -> {
                val arrayNode = if (name != null) DefaultMutableTreeNode("$name []") else node
                if (name != null) node.add(arrayNode)
                element.asJsonArray.forEachIndexed { index, value ->
                    buildNodes(arrayNode, value, "[$index]")
                }
            }
            element.isJsonPrimitive -> {
                node.add(DefaultMutableTreeNode("$label${element.asJsonPrimitive}"))
            }
            element.isJsonNull -> {
                node.add(DefaultMutableTreeNode("$label null"))
            }
        }
    }
}

package com.jakumjirak

import io.swagger.v3.oas.models.OpenAPI
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Transforms OpenAPI model into a TreeModel for JBTree.
 */
class ApiTreeModelBuilder {
    
    fun buildModel(openApi: OpenAPI): DefaultTreeModel {
        val root = DefaultMutableTreeNode(openApi.info?.title ?: "API")
        
        openApi.paths?.forEach { (path, pathItem) ->
            val pathNode = DefaultMutableTreeNode(path)
            
            pathItem.readOperationsMap().forEach { (method, operation) ->
                val operationNode = DefaultMutableTreeNode("${method.name}: ${operation.summary ?: operation.operationId ?: "No Summary"}")
                pathNode.add(operationNode)
            }
            
            root.add(pathNode)
        }
        
        return DefaultTreeModel(root)
    }
}

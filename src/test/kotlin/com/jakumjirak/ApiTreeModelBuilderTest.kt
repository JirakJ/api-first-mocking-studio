package com.jakumjirak

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.info.Info
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.swing.tree.DefaultMutableTreeNode

class ApiTreeModelBuilderTest {
    
    private val builder = ApiTreeModelBuilder()
    
    @Test
    fun `should build tree model from openapi`() {
        val openApi = OpenAPI().apply {
            info = Info().title("Test API")
            path("/users", PathItem().apply {
                get(Operation().summary("Get users"))
            })
        }
        
        val model = builder.buildModel(openApi)
        val root = model.root as DefaultMutableTreeNode
        
        assertEquals("Test API", root.userObject)
        assertEquals(1, root.childCount)
        
        val pathNode = root.getChildAt(0) as DefaultMutableTreeNode
        assertEquals("/users", pathNode.userObject)
        
        val operationNode = pathNode.getChildAt(0) as DefaultMutableTreeNode
        assertTrue(operationNode.userObject.toString().contains("GET: Get users"))
    }
}

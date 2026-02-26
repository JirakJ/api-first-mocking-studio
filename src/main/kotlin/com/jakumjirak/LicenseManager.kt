package com.jakumjirak

import com.intellij.openapi.components.Service
import com.intellij.ui.LicensingFacade

@Service(Service.Level.PROJECT)
class LicenseManager {

    /**
     * Checks if the user has a valid license for premium features.
     */
    fun isPremium(): Boolean {
        val facade = LicensingFacade.getInstance() ?: return false
        
        // Check if the product is licensed.
        // For Freemium on Marketplace, the IDE handles license retrieval.
        // We just check if the facade says we are licensed and not in expired evaluation.
        return try {
            val metadata = facade.metadata
            metadata != null && !facade.isEvaluationLicense
        } catch (e: Exception) {
            // Fallback or log
            false
        }
    }
}

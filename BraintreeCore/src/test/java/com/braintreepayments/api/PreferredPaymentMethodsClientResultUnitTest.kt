package com.braintreepayments.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferredPaymentMethodsClientResultUnitTest {
    @Test
    fun fromJson_whenApiDetectsPayPalPreferred_setsPayPalPreferredToTrue() {
        val json =
            //language=json
            """{
                  "data": {
                    "preferredPaymentMethods": {
                      "paypalPreferred": true
                    }
                  }
            }"""
            //
        val result = PreferredPaymentMethodsResult.fromJSON(json, false)
        assertTrue(result.isPayPalPreferred())
    }

    @Test
    fun fromJson_whenApiDetectsPayPalNotPreferred_setsPayPalPreferredToFalse() {
        val json =
            //language=json
            """{
                  "data": {
                    "preferredPaymentMethods": {
                      "paypalPreferred": false
                    }
                  }
            }"""
            //
        val result = PreferredPaymentMethodsResult.fromJSON(json, false)
        assertFalse(result.isPayPalPreferred())
    }

    @Test
    fun fromJson_whenVenmoAppIsInstalled_setsVenmoPreferredToTrue() {
        val result = PreferredPaymentMethodsResult.fromJSON("json", true)
        assertTrue(result.isVenmoPreferred())
    }

    @Test
    fun fromJson_whenVenmoAppIsNotInstalled_setsVenmoPreferredToFalse() {
        val result = PreferredPaymentMethodsResult.fromJSON("json", false)
        assertFalse(result.isVenmoPreferred())
    }

    @Test
    fun fromJson_whenJsonIsInvalid_setsIsPayPalPreferredToFalse() {
        val result = PreferredPaymentMethodsResult.fromJSON("invalid-response", false)
        assertFalse(result.isPayPalPreferred())
        assertFalse(result.isVenmoPreferred())
    }
}
package com.ai.phoneagent.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DimensAuthClientTest {
    @Test
    fun parseTokenExchangeResponseReturnsDimensTokens() {
        val raw =
            """
            {
              "code": 1000,
              "message": "success",
              "data": {
                "expire": 604800,
                "token": "dimens_access_token",
                "refreshExpire": 2592000,
                "refreshToken": "dimens_refresh_token",
                "teamIds": ["TEAM1", "TEAM2"]
              }
            }
            """.trimIndent()

        val result = DimensAuthClient.parseTokenExchangeResponse(raw, 200)

        assertTrue(result.success)
        assertEquals("dimens_access_token", result.token)
        assertEquals("dimens_refresh_token", result.refreshToken)
        assertEquals(listOf("TEAM1", "TEAM2"), result.teamIds)
        assertEquals(604800, result.expire)
        assertEquals(2592000, result.refreshExpire)
    }

    @Test
    fun parseTokenExchangeResponseKeepsFailureMessage() {
        val raw =
            """
            {
              "code": 4001,
              "message": "Casdoor token invalid",
              "data": null
            }
            """.trimIndent()

        val result = DimensAuthClient.parseTokenExchangeResponse(raw, 401)

        assertFalse(result.success)
        assertEquals("Casdoor token invalid", result.message)
    }
}

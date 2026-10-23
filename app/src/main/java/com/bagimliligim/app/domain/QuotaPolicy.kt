package com.bagimliligim.app.domain

object QuotaPolicy {
    fun threshold(swipes: Int, quota: Int): Int? = when {
        quota <= 0 -> null
        swipes >= quota -> 100
        swipes.toLong() * 100 >= quota.toLong() * 80 -> 80
        else -> null
    }
}

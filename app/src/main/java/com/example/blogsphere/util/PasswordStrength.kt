package com.example.blogsphere.util

object PasswordStrength {

    data class Rule(val text: String, val passed: Boolean)
    data class Strength(val score: Int, val label: String, val rules: List<Rule>)

    fun evaluate(p: String): Strength {
        val rules = listOf(
            Rule("At least 8 characters", p.length >= 8),
            Rule("One lowercase letter", p.any { it.isLowerCase() }),
            Rule("One uppercase letter", p.any { it.isUpperCase() }),
            Rule("One number", p.any { it.isDigit() })
        )
        val score = rules.count { it.passed }
        val label = when (score) {
            0, 1 -> "Weak"
            2, 3 -> "Medium"
            else -> "Strong"
        }
        return Strength(score, label, rules)
    }
}

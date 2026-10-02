package uz.sergak.core

enum class RiskLevel { SAFE, SUSPICIOUS, DANGEROUS }

/** Bitta aniqlangan belgi: nima topildi, nega xavfli, nima qilish kerak. */
data class Finding(
    val id: String,
    val title: String,
    val explanation: String,
    val advice: String,
    val weight: Int,
)

data class Verdict(
    val score: Int,
    val findings: List<Finding>,
) {
    val level: RiskLevel = when {
        score >= DANGER_THRESHOLD -> RiskLevel.DANGEROUS
        score >= SUSPICIOUS_THRESHOLD -> RiskLevel.SUSPICIOUS
        else -> RiskLevel.SAFE
    }

    val headline: String
        get() = when (level) {
            RiskLevel.DANGEROUS -> "Xavfli! Bu firibgarlik bo'lishi ehtimoli juda yuqori"
            RiskLevel.SUSPICIOUS -> "Shubhali. Ehtiyot bo'ling"
            RiskLevel.SAFE -> "Aniq xavf belgilari topilmadi"
        }

    companion object {
        const val DANGER_THRESHOLD = 60
        const val SUSPICIOUS_THRESHOLD = 25

        fun of(findings: List<Finding>): Verdict {
            val unique = findings.distinctBy { it.id }.sortedByDescending { it.weight }
            return Verdict(unique.sumOf { it.weight }.coerceAtMost(100), unique)
        }
    }
}

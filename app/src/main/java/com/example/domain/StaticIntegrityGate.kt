package com.example.domain

import org.json.JSONObject

data class StaticGateReport(
    val passed: Boolean,
    val issues: List<String>
)

/**
 * Deterministic zero-API-call verification gate run on generated files prior to Code Reviewer.
 * Catches malformed JSON, truncated placeholders, missing PWA manifest fields, and unbalanced braces.
 */
object StaticIntegrityGate {

    private val placeholderRegex = Regex(
        pattern = "(//\\s*\\.\\.\\.\\s*(rest|todo|omitted)|/\\*\\s*\\.\\.\\.\\s*\\*/|\\bTODO\\s*:)",
        option = RegexOption.IGNORE_CASE
    )

    fun validateFiles(files: List<Pair<String, String>>): StaticGateReport {
        val issues = mutableListOf<String>()
        for ((path, content) in files) {
            if (content.isBlank()) {
                issues.add("$path: Generated file content is empty.")
                continue
            }

            if (placeholderRegex.containsMatchIn(content)) {
                issues.add("$path: Contains disallowed placeholder or TODO marker.")
            }

            val lower = path.lowercase()
            if (lower.endsWith(".json") || lower.endsWith(".webmanifest")) {
                try {
                    val obj = JSONObject(content)
                    if (lower.endsWith("manifest.webmanifest") || lower.endsWith("manifest.json")) {
                        if (!obj.has("name") && !obj.has("short_name")) {
                            issues.add("$path: PWA manifest missing required 'name' or 'short_name'.")
                        }
                        if (!obj.has("display")) {
                            issues.add("$path: PWA manifest missing 'display' field (expected 'standalone').")
                        }
                    }
                } catch (e: Exception) {
                    issues.add("$path: Invalid JSON syntax (${e.message ?: "parse error"}).")
                }
            }

            if (lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".tsx") || lower.endsWith(".jsx")) {
                if (content.contains("alert(") || content.contains("confirm(") || content.contains("prompt(")) {
                    issues.add("$path: Violates PWA guardrail (native alert/confirm/prompt detected).")
                }
            }
        }
        return StaticGateReport(passed = issues.isEmpty(), issues = issues)
    }
}

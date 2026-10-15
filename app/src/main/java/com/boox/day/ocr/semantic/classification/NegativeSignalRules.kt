package com.boox.day.ocr.semantic.classification

import com.boox.day.ocr.model.Domain

/**
 * Negative signal rules - reduce weight of misleading matches when dominant evidence exists.
 *
 * Example problems solved:
 * - "service" in TECH notes (microservice) shouldn't trigger SPIRITUAL
 * - "call" in code notes (function call) shouldn't trigger CALL_LOG
 * - "meeting" in git notes shouldn't trigger PROFESSIONAL/MEETING_NOTES
 * - "class" in code shouldn't trigger EDUCATION
 */
object NegativeSignalRules {

    /**
     * Negative rule definition.
     */
    data class NegativeRule(
        /** Domain that, when dominant, triggers this suppression */
        val triggerDomain: Domain,
        /** Term whose weight should be reduced */
        val targetTerm: String,
        /** Weight multiplier: 0.0 = full suppression, 0.5 = half weight */
        val weightMultiplier: Float
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // TECH SUPPRESSIONS
    // When TECH is dominant, these terms should be heavily suppressed
    // ═══════════════════════════════════════════════════════════════════════════

    private val TECH_SUPPRESSIONS = listOf(
        NegativeRule(Domain.TECH, "call", 0.1f),         // function call, not phone call
        NegativeRule(Domain.TECH, "meeting", 0.2f),      // code meeting point, not calendar
        NegativeRule(Domain.TECH, "service", 0.1f),      // microservice, not church service
        NegativeRule(Domain.TECH, "class", 0.1f),        // code class, not school class
        NegativeRule(Domain.TECH, "object", 0.2f),       // code object
        NegativeRule(Domain.TECH, "interface", 0.1f),    // code interface
        NegativeRule(Domain.TECH, "commit", 0.1f),       // git commit, not commitment
        NegativeRule(Domain.TECH, "branch", 0.2f),       // git branch
        NegativeRule(Domain.TECH, "push", 0.2f),         // git push
        NegativeRule(Domain.TECH, "pull", 0.2f),         // git pull
        NegativeRule(Domain.TECH, "merge", 0.2f),        // git merge
        NegativeRule(Domain.TECH, "parent", 0.3f),       // parent class, not family
        NegativeRule(Domain.TECH, "child", 0.3f),        // child process, not family
        NegativeRule(Domain.TECH, "test", 0.2f),         // unit test, not exam
        NegativeRule(Domain.TECH, "project", 0.3f),      // code project
        NegativeRule(Domain.TECH, "package", 0.2f),      // code package, not shipping
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // SPIRITUAL SUPPRESSIONS
    // When SPIRITUAL is dominant
    // ═══════════════════════════════════════════════════════════════════════════

    private val SPIRITUAL_SUPPRESSIONS = listOf(
        NegativeRule(Domain.SPIRITUAL, "service", 0.1f),    // worship service
        NegativeRule(Domain.SPIRITUAL, "meeting", 0.2f),    // church meeting
        NegativeRule(Domain.SPIRITUAL, "calling", 0.1f),    // spiritual calling
        NegativeRule(Domain.SPIRITUAL, "covenant", 0.1f),   // religious covenant
        NegativeRule(Domain.SPIRITUAL, "mission", 0.2f),    // religious mission
        NegativeRule(Domain.SPIRITUAL, "class", 0.2f),      // sunday school class
        NegativeRule(Domain.SPIRITUAL, "teacher", 0.3f),    // church teacher
        NegativeRule(Domain.SPIRITUAL, "leader", 0.3f),     // church leader
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // EDUCATION SUPPRESSIONS (merged SCHOOL + STUDENT)
    // When EDUCATION is dominant (learner/institution context)
    // ═══════════════════════════════════════════════════════════════════════════

    private val EDUCATION_SUPPRESSIONS = listOf(
        NegativeRule(Domain.EDUCATION, "class", 0.1f),         // school class
        NegativeRule(Domain.EDUCATION, "test", 0.1f),          // exam test
        NegativeRule(Domain.EDUCATION, "assignment", 0.1f),
        NegativeRule(Domain.EDUCATION, "project", 0.2f),       // school project
        NegativeRule(Domain.EDUCATION, "deadline", 0.3f),      // homework deadline
        NegativeRule(Domain.EDUCATION, "submit", 0.2f),        // submit assignment
        NegativeRule(Domain.EDUCATION, "grade", 0.1f),         // school grade
        NegativeRule(Domain.EDUCATION, "homework", 0.1f),      // homework
        NegativeRule(Domain.EDUCATION, "study", 0.1f),         // study for
        NegativeRule(Domain.EDUCATION, "exam", 0.1f),          // exam prep
        NegativeRule(Domain.EDUCATION, "professor", 0.2f),     // my professor
        NegativeRule(Domain.EDUCATION, "due", 0.1f),           // due date
        NegativeRule(Domain.EDUCATION, "paper", 0.2f),         // research paper
        NegativeRule(Domain.EDUCATION, "thesis", 0.1f),        // thesis work
        NegativeRule(Domain.EDUCATION, "lab", 0.2f),           // lab report
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // TEACHING SUPPRESSIONS
    // When TEACHING is dominant (educator context)
    // ═══════════════════════════════════════════════════════════════════════════

    private val TEACHING_SUPPRESSIONS = listOf(
        NegativeRule(Domain.TEACHING, "class", 0.1f),           // teaching class
        NegativeRule(Domain.TEACHING, "student", 0.1f),         // my students
        NegativeRule(Domain.TEACHING, "grade", 0.1f),           // grading
        NegativeRule(Domain.TEACHING, "lesson", 0.1f),          // lesson plan
        NegativeRule(Domain.TEACHING, "plan", 0.2f),            // lesson plan
        NegativeRule(Domain.TEACHING, "meeting", 0.2f),         // parent conference
        NegativeRule(Domain.TEACHING, "conference", 0.1f),      // parent conference
        NegativeRule(Domain.TEACHING, "assessment", 0.1f),      // formative assessment
        NegativeRule(Domain.TEACHING, "behavior", 0.2f),        // behavior management
        NegativeRule(Domain.TEACHING, "attendance", 0.1f),      // class attendance
        NegativeRule(Domain.TEACHING, "parent", 0.1f),          // parent communication
        NegativeRule(Domain.TEACHING, "progress", 0.2f),        // progress report
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // PROFESSIONAL SUPPRESSIONS (merged WORK + MANAGEMENT + LEADERSHIP)
    // When PROFESSIONAL is dominant
    // ═══════════════════════════════════════════════════════════════════════════

    private val PROFESSIONAL_SUPPRESSIONS = listOf(
        NegativeRule(Domain.PROFESSIONAL, "meeting", 0.2f),       // work meeting
        NegativeRule(Domain.PROFESSIONAL, "call", 0.3f),          // conference call
        NegativeRule(Domain.PROFESSIONAL, "project", 0.2f),       // work project
        NegativeRule(Domain.PROFESSIONAL, "deadline", 0.2f),      // work deadline
        NegativeRule(Domain.PROFESSIONAL, "team", 0.2f),          // work team
        NegativeRule(Domain.PROFESSIONAL, "review", 0.2f),        // performance review
        NegativeRule(Domain.PROFESSIONAL, "goal", 0.3f),          // team goals
        NegativeRule(Domain.PROFESSIONAL, "report", 0.2f),        // direct report
        NegativeRule(Domain.PROFESSIONAL, "vision", 0.1f),        // strategic vision
        NegativeRule(Domain.PROFESSIONAL, "mission", 0.2f),       // company mission (not spiritual)
        NegativeRule(Domain.PROFESSIONAL, "culture", 0.2f),       // company culture
        NegativeRule(Domain.PROFESSIONAL, "alignment", 0.2f),     // organizational alignment
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // HEALTH SUPPRESSIONS
    // When HEALTH is dominant
    // ═══════════════════════════════════════════════════════════════════════════

    private val HEALTH_SUPPRESSIONS = listOf(
        NegativeRule(Domain.HEALTH, "appointment", 0.2f),   // doctor appointment
        NegativeRule(Domain.HEALTH, "call", 0.3f),          // doctor call
        NegativeRule(Domain.HEALTH, "schedule", 0.3f),      // health schedule
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // BUSINESS SUPPRESSIONS
    // When BUSINESS is dominant
    // ═══════════════════════════════════════════════════════════════════════════

    private val BUSINESS_SUPPRESSIONS = listOf(
        NegativeRule(Domain.BUSINESS, "call", 0.3f),            // sales call
        NegativeRule(Domain.BUSINESS, "meeting", 0.3f),         // client meeting
        NegativeRule(Domain.BUSINESS, "project", 0.3f),         // business project
        NegativeRule(Domain.BUSINESS, "pipeline", 0.1f),        // sales pipeline
        NegativeRule(Domain.BUSINESS, "growth", 0.2f),          // business growth
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // STRATEGY SUPPRESSIONS
    // When STRATEGY is dominant
    // ═══════════════════════════════════════════════════════════════════════════

    private val STRATEGY_SUPPRESSIONS = listOf(
        NegativeRule(Domain.STRATEGY, "goal", 0.2f),            // strategic goal
        NegativeRule(Domain.STRATEGY, "plan", 0.1f),            // strategic plan
        NegativeRule(Domain.STRATEGY, "roadmap", 0.1f),         // strategy roadmap
        NegativeRule(Domain.STRATEGY, "project", 0.3f),         // strategic initiative
        NegativeRule(Domain.STRATEGY, "review", 0.3f),          // quarterly review
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // SOCIAL_MEDIA SUPPRESSIONS
    // When SOCIAL_MEDIA is dominant
    // ═══════════════════════════════════════════════════════════════════════════

    private val SOCIAL_MEDIA_SUPPRESSIONS = listOf(
        NegativeRule(Domain.SOCIAL_MEDIA, "post", 0.1f),        // social media post
        NegativeRule(Domain.SOCIAL_MEDIA, "content", 0.1f),     // content creation
        NegativeRule(Domain.SOCIAL_MEDIA, "engagement", 0.1f),  // social engagement
        NegativeRule(Domain.SOCIAL_MEDIA, "community", 0.2f),   // online community
        NegativeRule(Domain.SOCIAL_MEDIA, "share", 0.2f),       // social sharing
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // ENTREPRENEURSHIP SUPPRESSIONS
    // When ENTREPRENEURSHIP is dominant
    // ═══════════════════════════════════════════════════════════════════════════

    private val ENTREPRENEURSHIP_SUPPRESSIONS = listOf(
        NegativeRule(Domain.ENTREPRENEURSHIP, "pitch", 0.1f),       // pitch deck
        NegativeRule(Domain.ENTREPRENEURSHIP, "funding", 0.1f),     // startup funding
        NegativeRule(Domain.ENTREPRENEURSHIP, "investor", 0.1f),    // not finance
        NegativeRule(Domain.ENTREPRENEURSHIP, "product", 0.2f),     // product-market fit
        NegativeRule(Domain.ENTREPRENEURSHIP, "launch", 0.2f),      // product launch
        NegativeRule(Domain.ENTREPRENEURSHIP, "growth", 0.2f),      // startup growth
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // LDS_LEADERSHIP SUPPRESSIONS
    // When LDS_LEADERSHIP is dominant, suppress general terms
    // ═══════════════════════════════════════════════════════════════════════════

    private val LDS_LEADERSHIP_SUPPRESSIONS = listOf(
        // Church-specific terms that overlap with other domains
        NegativeRule(Domain.LDS_LEADERSHIP, "service", 0.1f),       // church service
        NegativeRule(Domain.LDS_LEADERSHIP, "meeting", 0.1f),       // ward council, pec
        NegativeRule(Domain.LDS_LEADERSHIP, "interview", 0.1f),     // worthiness interview
        NegativeRule(Domain.LDS_LEADERSHIP, "calling", 0.1f),       // church calling
        NegativeRule(Domain.LDS_LEADERSHIP, "president", 0.1f),     // stake/quorum president
        NegativeRule(Domain.LDS_LEADERSHIP, "council", 0.1f),       // ward council
        NegativeRule(Domain.LDS_LEADERSHIP, "leader", 0.1f),        // church leader
        NegativeRule(Domain.LDS_LEADERSHIP, "mission", 0.1f),       // church mission
        NegativeRule(Domain.LDS_LEADERSHIP, "class", 0.2f),         // sunday school class
        NegativeRule(Domain.LDS_LEADERSHIP, "teacher", 0.2f),       // church teacher
        NegativeRule(Domain.LDS_LEADERSHIP, "youth", 0.2f),         // church youth program
        NegativeRule(Domain.LDS_LEADERSHIP, "family", 0.3f),        // member family
        NegativeRule(Domain.LDS_LEADERSHIP, "budget", 0.2f),        // ward budget
        NegativeRule(Domain.LDS_LEADERSHIP, "report", 0.2f),        // ministering report
        NegativeRule(Domain.LDS_LEADERSHIP, "training", 0.2f),      // leadership training
    )

    // All suppressions indexed by trigger domain
    private val ALL_SUPPRESSIONS: Map<Domain, List<NegativeRule>> = mapOf(
        Domain.TECH to TECH_SUPPRESSIONS,
        Domain.SPIRITUAL to SPIRITUAL_SUPPRESSIONS,
        Domain.EDUCATION to EDUCATION_SUPPRESSIONS,
        Domain.TEACHING to TEACHING_SUPPRESSIONS,
        Domain.PROFESSIONAL to PROFESSIONAL_SUPPRESSIONS,
        Domain.HEALTH to HEALTH_SUPPRESSIONS,
        Domain.BUSINESS to BUSINESS_SUPPRESSIONS,
        Domain.STRATEGY to STRATEGY_SUPPRESSIONS,
        Domain.SOCIAL_MEDIA to SOCIAL_MEDIA_SUPPRESSIONS,
        Domain.ENTREPRENEURSHIP to ENTREPRENEURSHIP_SUPPRESSIONS,
        Domain.LDS_LEADERSHIP to LDS_LEADERSHIP_SUPPRESSIONS
    )

    /**
     * Get suppression multiplier for a term given dominant domain.
     *
     * @param dominantDomain The dominant domain (from DominantEvidenceRules)
     * @param term The term to check
     * @return Weight multiplier (1.0 = no suppression, 0.0 = full suppression)
     */
    fun getSuppressionMultiplier(dominantDomain: Domain?, term: String): Float {
        if (dominantDomain == null) return 1.0f

        val rules = ALL_SUPPRESSIONS[dominantDomain] ?: return 1.0f

        return rules.find { it.targetTerm.equals(term, ignoreCase = true) }
            ?.weightMultiplier
            ?: 1.0f  // No suppression
    }

    /**
     * Get all suppressed terms for a dominant domain.
     */
    fun getSuppressedTerms(dominantDomain: Domain): Set<String> {
        return ALL_SUPPRESSIONS[dominantDomain]
            ?.map { it.targetTerm.lowercase() }
            ?.toSet()
            ?: emptySet()
    }

    /**
     * Apply suppression to a score.
     *
     * @param dominantDomain The dominant domain
     * @param term The term being scored
     * @param originalScore The original score
     * @return Suppressed score and whether suppression was applied
     */
    fun applySupression(
        dominantDomain: Domain?,
        term: String,
        originalScore: Float
    ): Pair<Float, Boolean> {
        val multiplier = getSuppressionMultiplier(dominantDomain, term)
        val suppressed = multiplier < 1.0f
        return (originalScore * multiplier) to suppressed
    }
}

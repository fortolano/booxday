package com.boox.day.ocr.model

import kotlinx.serialization.Serializable

/**
 * Primary classification: What area of life is this note about?
 * Exactly ONE domain per note.
 *
 * This is the new two-tier classification system (Domain + Intent).
 */
@Serializable
enum class Domain {
    // ═══════════════════════════════════════════════════════════════════════
    // MERGED DOMAINS (Phase 2 Taxonomy Rationalization)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Education & Learning (covers learners and academic institutions)
     * Sub-types: student, institution
     */
    EDUCATION,

    /**
     * Professional & Work (covers work, management, and leadership)
     * Sub-types: general, management, leadership
     */
    PROFESSIONAL,

    /**
     * Life & Personal (covers personal, family, and social)
     * Sub-types: personal, family, social
     */
    LIFE,

    // ═══════════════════════════════════════════════════════════════════════
    // DISTINCT DOMAINS
    // ═══════════════════════════════════════════════════════════════════════

    /** Educators: K-12 teachers, professors, tutors, instructors */
    TEACHING,

    /** Programming, software, IT, hardware */
    TECH,

    /** Money, budgets, investments, expenses */
    FINANCE,

    /** Medical, fitness, wellness, nutrition */
    HEALTH,

    /** Religion, faith, worship, meditation (universal across all faiths) */
    SPIRITUAL,

    /** Art, music, writing, design, hobbies */
    CREATIVE,

    /** Trips, travel planning, destinations */
    TRAVEL,

    /** Contracts, compliance, regulations */
    LEGAL,

    /** Property, maintenance, chores, renovation */
    HOME,

    /** Purchases, wishlists, product research */
    SHOPPING,

    /** Business operations, commerce, corporate, organizational */
    BUSINESS,

    /** Strategic planning, roadmaps, long-term vision, competitive analysis */
    STRATEGY,

    /** Social media, online presence, digital marketing, content creation */
    SOCIAL_MEDIA,

    /** Startups, ventures, founding, product-market fit, funding */
    ENTREPRENEURSHIP,

    /**
     * Leadership callings in The Church of Jesus Christ of Latter-Day Saints.
     * Bishops, Stake Presidents, Quorum/Auxiliary Presidents, and other callings.
     */
    LDS_LEADERSHIP,

    /** Recipes, meal planning, cooking, dietary tracking, restaurants */
    FOOD,

    /** HOA, volunteering, local government, civic engagement, neighborhood */
    COMMUNITY,

    /** Pet health, vet visits, training, feeding schedules, grooming */
    PET,

    /** Cannot confidently classify */
    MISC;

    companion object {
        fun fromString(value: String): Domain? = entries.find { it.name == value }

        /**
         * Sibling domain relationships.
         * Sibling domains have significant overlap and are acceptable alternatives
         * in classification scoring and affinity calculations.
         */
        private val SIBLINGS: Map<Domain, Set<Domain>> = mapOf(
            // Professional cluster
            PROFESSIONAL to setOf(BUSINESS, TECH, FINANCE, STRATEGY, ENTREPRENEURSHIP),
            BUSINESS to setOf(PROFESSIONAL, FINANCE, ENTREPRENEURSHIP, STRATEGY),
            STRATEGY to setOf(BUSINESS, PROFESSIONAL, ENTREPRENEURSHIP),
            ENTREPRENEURSHIP to setOf(BUSINESS, STRATEGY, PROFESSIONAL),

            // Education cluster
            EDUCATION to setOf(TEACHING),
            TEACHING to setOf(EDUCATION),

            // Life cluster
            LIFE to setOf(HEALTH, HOME),
            HEALTH to setOf(LIFE),

            // Spiritual cluster
            SPIRITUAL to setOf(LDS_LEADERSHIP),
            LDS_LEADERSHIP to setOf(SPIRITUAL),

            // Home/Food cluster
            HOME to setOf(LIFE, FOOD),
            FOOD to setOf(HOME, SHOPPING),
            SHOPPING to setOf(HOME, FOOD)
        )

        /**
         * Check if two domains are siblings (acceptable alternatives).
         */
        fun areSiblings(a: Domain, b: Domain): Boolean {
            if (a == b) return true
            return SIBLINGS[a]?.contains(b) == true || SIBLINGS[b]?.contains(a) == true
        }

        /**
         * Get all sibling domains for a given domain.
         */
        fun getSiblings(domain: Domain): Set<Domain> {
            return SIBLINGS[domain] ?: emptySet()
        }
    }
}

/**
 * Secondary classification: What is the PURPOSE of this note?
 * 0-4 intents per note (sorted by confidence).
 */
@Serializable
enum class Intent {
    // === Planning & Organization ===
    TODO_LIST,
    PLANNING,
    GOAL_SETTING,
    DAILY_REVIEW,
    FOCUS_SESSION,

    // === Recording & Capture ===
    MEETING_NOTES,
    CALL_LOG,
    BRAINSTORM,
    JOURNAL,
    LECTURE_NOTES,
    TRANSCRIPT,

    // === Teaching (Educator) Intents ===
    LESSON_PLAN,
    GRADING,
    CLASSROOM_MGMT,
    CURRICULUM_DEV,
    PARENT_COMM,
    IEP_504,
    FACULTY_MEETING,
    STUDENT_RECORD,
    ADVISING,

    // === Student (Learner) Intents ===
    STUDY_PLAN,
    ASSIGNMENT_WORK,
    READING_NOTES,
    LAB_NOTES,
    THESIS_WORK,
    COURSE_PLANNING,

    // === Reference & Information ===
    REFERENCE,
    INSTRUCTIONS,
    CONTACT_INFO,
    CHEAT_SHEET,

    // === Financial ===
    EXPENSE_LOG,
    BUDGET,

    // === Communication ===
    DRAFT_MESSAGE,
    TALKING_POINTS,

    // === Analysis & Review ===
    PROS_CONS,
    COMPARISON,
    ACTION_REVIEW,

    // === Problem Solving ===
    TROUBLESHOOTING,
    RESEARCH,

    // === People & Leadership ===
    LEADERSHIP_NOTES,
    PEOPLE_MANAGEMENT,

    // === Strategy & Business ===
    STRATEGY_PLANNING,
    BUSINESS_MODEL,
    METRICS_TRACKING,
    STAKEHOLDER_UPDATE,

    // === Entrepreneurship ===
    STARTUP_IDEAS,
    FUNDRAISING,
    PRODUCT_DEV,

    // === Social Media & Digital ===
    CONTENT_PLAN,
    SOCIAL_ANALYTICS,
    CAMPAIGN_NOTES,

    // === LDS Church Leadership ===
    LDS_COUNCIL,
    LDS_MINISTERING,

    // === Creative & Writing ===
    CREATIVE_WRITING,

    // === Food & Cooking ===
    RECIPE,

    // === Travel ===
    PACKING_LIST,

    // === Spiritual & Devotional ===
    DEVOTIONAL,

    // === Other ===
    QUOTE_SNIPPET,
    WISHLIST,
    FREEFORM;

    companion object {
        fun fromString(value: String): Intent? = entries.find { it.name == value }
    }
}

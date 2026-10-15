package com.boox.day.ocr.semantic.classification

import com.boox.day.ocr.model.Domain

/**
 * Context-aware disambiguation for ambiguous terms.
 *
 * Process:
 * 1. Compute domain scores EXCLUDING ambiguous words
 * 2. For each ambiguous word, check local window (±5 tokens) for collocations
 * 3. Assign to domain with highest (prior score + collocation match)
 *
 * This is more robust than phrase list matching because it uses:
 * - The top-2 domain scores BEFORE counting ambiguous words
 * - A local window (±5 tokens)
 * - Short, high-signal collocation lists
 */
object ContextDisambiguator {

    private const val WINDOW_SIZE = 5  // ±5 tokens around ambiguous word

    /**
     * Ambiguous terms and their domain-specific collocations.
     * Each collocation list is SHORT and high-signal.
     */
    val AMBIGUOUS_TERMS: Map<String, Map<Domain, List<String>>> = mapOf(
        "service" to mapOf(
            Domain.TECH to listOf("api", "endpoint", "deploy", "server", "layer", "microservice", "web", "rest", "grpc", "backend", "cloud"),
            Domain.PROFESSIONAL to listOf("customer", "support", "ticket", "client", "desk", "sla", "agent", "help"),
            Domain.SPIRITUAL to listOf("worship", "church", "prayer", "congregation", "sunday", "evening", "morning", "sacrament")
        ),
        "call" to mapOf(
            Domain.TECH to listOf("function", "method", "api", "callback", "invoke", "async", "await", "return", "recursive"),
            Domain.PROFESSIONAL to listOf("conference", "meeting", "zoom", "teams", "client", "phone", "scheduled", "video"),
            Domain.SPIRITUAL to listOf("calling", "called", "serve", "mission", "divine", "god", "ministry"),
            Domain.LIFE to listOf("call", "phone", "friend", "chat", "catch", "ring")
        ),
        "meeting" to mapOf(
            Domain.TECH to listOf("standup", "sprint", "scrum", "retro", "tech", "engineering", "review", "sync"),
            Domain.PROFESSIONAL to listOf("team", "manager", "agenda", "minutes", "action", "stakeholder", "project", "client"),
            Domain.SPIRITUAL to listOf("sacrament", "church", "ward", "stake", "congregation", "worship", "prayer", "sunday")
        ),
        "class" to mapOf(
            Domain.TECH to listOf("object", "method", "public", "private", "extends", "implements", "constructor", "instance", "abstract", "interface"),
            Domain.TEACHING to listOf("lesson", "rubric", "grading", "attendance", "seating", "behavior", "iep", "parent"),
            Domain.EDUCATION to listOf("homework", "study", "exam", "professor", "assignment", "due", "credits", "lecture", "student", "course", "syllabus", "teacher"),
            Domain.HEALTH to listOf("yoga", "fitness", "gym", "exercise", "instructor", "workout", "session")
        ),
        "project" to mapOf(
            Domain.TECH to listOf("repo", "git", "branch", "deploy", "sprint", "code", "build", "ci", "cd"),
            Domain.PROFESSIONAL to listOf("deadline", "budget", "stakeholder", "milestone", "resource", "timeline", "client", "deliverable"),
            Domain.EDUCATION to listOf("assignment", "group", "presentation", "professor", "grade", "research", "paper")
        ),
        "commit" to mapOf(
            Domain.TECH to listOf("git", "branch", "push", "merge", "pull", "repo", "message", "sha", "hash"),
            Domain.SPIRITUAL to listOf("commitment", "covenant", "promise", "dedication", "faith", "testimony"),
            Domain.LIFE to listOf("commitment", "goal", "promise", "dedication", "habit")
        ),
        "test" to mapOf(
            Domain.TECH to listOf("unit", "integration", "mock", "assert", "coverage", "junit", "pytest", "spec", "ci"),
            Domain.TEACHING to listOf("assessment", "rubric", "grading", "formative", "summative", "benchmark", "proctoring"),
            Domain.EDUCATION to listOf("study", "prep", "cram", "flashcards", "review", "practice", "score", "exam", "grade", "quiz", "final", "midterm", "professor"),
            Domain.HEALTH to listOf("blood", "medical", "doctor", "lab", "results", "screening", "diagnosis")
        ),
        "ticket" to mapOf(
            Domain.TECH to listOf("jira", "bug", "issue", "sprint", "backlog", "assign", "priority", "status"),
            Domain.PROFESSIONAL to listOf("support", "customer", "service", "help", "desk", "resolved", "agent"),
            Domain.TRAVEL to listOf("flight", "train", "bus", "booking", "reservation", "seat", "airline"),
            Domain.CREATIVE to listOf("concert", "movie", "show", "event", "admission", "venue")
        ),
        "goal" to mapOf(
            Domain.BUSINESS to listOf("revenue", "q1", "q2", "q3", "q4", "sales", "market", "growth", "achieve", "quarter"),
            Domain.PROFESSIONAL to listOf("okr", "quarterly", "kpi", "team", "performance", "target", "metric"),
            Domain.LIFE to listOf("milestone", "progress", "habit", "self", "improve", "personal", "resolution"),
            Domain.HEALTH to listOf("weight", "fitness", "exercise", "diet", "health", "steps"),
            Domain.FINANCE to listOf("savings", "budget", "investment", "retirement", "financial", "wealth")
        ),
        "note" to mapOf(
            Domain.EDUCATION to listOf("lecture", "class", "study", "professor", "exam", "course"),
            Domain.PROFESSIONAL to listOf("meeting", "minutes", "action", "follow", "agenda"),
            Domain.CREATIVE to listOf("music", "melody", "chord", "song", "instrument", "piano")
        ),
        "branch" to mapOf(
            Domain.TECH to listOf("git", "merge", "checkout", "main", "master", "feature", "develop"),
            Domain.FINANCE to listOf("bank", "account", "atm", "local", "location"),
            Domain.LIFE to listOf("tree", "family", "genealogy", "ancestor")
        ),
        "push" to mapOf(
            Domain.TECH to listOf("git", "remote", "origin", "branch", "commit", "force"),
            Domain.HEALTH to listOf("up", "exercise", "workout", "rep", "set"),
            Domain.PROFESSIONAL to listOf("deadline", "launch", "release", "ship")
        ),
        "merge" to mapOf(
            Domain.TECH to listOf("git", "branch", "conflict", "pull", "request", "code"),
            Domain.PROFESSIONAL to listOf("company", "acquisition", "team", "department")
        ),
        "sprint" to mapOf(
            Domain.TECH to listOf("scrum", "agile", "backlog", "story", "velocity", "standup"),
            Domain.PROFESSIONAL to listOf("project", "deadline", "deliverable", "milestone"),
            Domain.HEALTH to listOf("run", "exercise", "training", "cardio", "interval")
        ),
        // === New domain ambiguous terms ===
        "pipeline" to mapOf(
            Domain.TECH to listOf("ci", "cd", "deploy", "build", "jenkins", "github", "actions", "workflow"),
            Domain.BUSINESS to listOf("sales", "deal", "forecast", "revenue", "opportunity", "leads", "crm"),
            Domain.STRATEGY to listOf("talent", "leadership", "succession", "development")
        ),
        "roadmap" to mapOf(
            Domain.TECH to listOf("product", "feature", "release", "sprint", "backlog", "engineering"),
            Domain.STRATEGY to listOf("strategic", "annual", "multi-year", "vision", "priority", "initiative"),
            Domain.ENTREPRENEURSHIP to listOf("startup", "mvp", "milestone", "funding", "investor")
        ),
        "growth" to mapOf(
            Domain.BUSINESS to listOf("revenue", "market", "sales", "customer", "acquisition", "churn"),
            Domain.ENTREPRENEURSHIP to listOf("startup", "traction", "users", "scale", "fundraising", "investor"),
            Domain.SOCIAL_MEDIA to listOf("followers", "engagement", "audience", "viral", "content", "reach"),
            Domain.LIFE to listOf("personal", "self", "habit", "mindset", "learning", "skill")
        ),
        "engagement" to mapOf(
            Domain.SOCIAL_MEDIA to listOf("followers", "likes", "comments", "reach", "content", "post", "algorithm"),
            Domain.PROFESSIONAL to listOf("employee", "team", "morale", "retention", "culture", "survey", "executive", "leadership", "senior", "vision"),
            Domain.LIFE to listOf("wedding", "propose", "ring", "engaged", "fiance")
        ),
        "content" to mapOf(
            Domain.SOCIAL_MEDIA to listOf("instagram", "tiktok", "youtube", "post", "creator", "viral", "followers"),
            Domain.CREATIVE to listOf("write", "blog", "article", "story", "publish"),
            Domain.TECH to listOf("cms", "website", "page", "seo", "html")
        ),
        "founder" to mapOf(
            Domain.ENTREPRENEURSHIP to listOf("startup", "co-founder", "ceo", "equity", "investor", "pitch", "raise"),
            Domain.SPIRITUAL to listOf("church", "congregation", "ministry", "organization")
        ),
        "pitch" to mapOf(
            Domain.ENTREPRENEURSHIP to listOf("deck", "investor", "vc", "funding", "startup", "raise", "valuation"),
            Domain.BUSINESS to listOf("sales", "client", "proposal", "presentation"),
            Domain.CREATIVE to listOf("story", "movie", "script", "idea")
        ),
        "vision" to mapOf(
            Domain.PROFESSIONAL to listOf("strategic", "company", "organization", "culture", "mission", "values", "executive", "board", "chairman"),
            Domain.STRATEGY to listOf("long-term", "roadmap", "north star", "planning"),
            Domain.HEALTH to listOf("eye", "glasses", "optometrist", "prescription", "sight"),
            Domain.SPIRITUAL to listOf("dream", "prophecy", "revelation", "god", "divine")
        ),
        "culture" to mapOf(
            Domain.PROFESSIONAL to listOf("company", "team", "values", "organizational", "employee", "engagement"),
            Domain.LIFE to listOf("music", "art", "society", "tradition", "heritage"),
            Domain.CREATIVE to listOf("pop", "art", "entertainment", "media")
        ),
        "metrics" to mapOf(
            Domain.STRATEGY to listOf("okr", "kpi", "target", "goal", "quarterly", "review"),
            Domain.BUSINESS to listOf("revenue", "sales", "conversion", "churn", "growth"),
            Domain.SOCIAL_MEDIA to listOf("engagement", "followers", "reach", "impressions", "analytics")
        ),
        "investor" to mapOf(
            Domain.ENTREPRENEURSHIP to listOf("startup", "pitch", "funding", "round", "vc", "angel", "term sheet"),
            Domain.FINANCE to listOf("stock", "portfolio", "dividend", "returns", "market")
        ),
        "team" to mapOf(
            Domain.PROFESSIONAL to listOf("direct report", "1:1", "performance", "headcount", "org", "hire", "executive", "leadership", "senior", "vision", "culture", "project", "meeting", "deadline", "collaborate", "task"),
            Domain.TECH to listOf("engineering", "dev", "scrum", "sprint", "standup")
        ),
        // === LDS Leadership ambiguous terms ===
        "interview" to mapOf(
            Domain.LDS_LEADERSHIP to listOf("bishop", "temple", "recommend", "worthiness", "calling", "stake", "youth"),
            Domain.PROFESSIONAL to listOf("job", "candidate", "hire", "resume", "hr", "offer", "performance", "feedback", "1:1", "review")
        ),
        "council" to mapOf(
            Domain.LDS_LEADERSHIP to listOf("ward", "stake", "pec", "byc", "priesthood", "welfare", "bishop"),
            Domain.PROFESSIONAL to listOf("executive", "board", "advisory", "steering"),
            Domain.STRATEGY to listOf("strategic", "planning", "governance")
        ),
        "president" to mapOf(
            Domain.LDS_LEADERSHIP to listOf("stake", "elders", "relief", "young", "primary", "sunday", "quorum", "bishop"),
            Domain.PROFESSIONAL to listOf("ceo", "company", "executive", "board", "chairman"),
            Domain.ENTREPRENEURSHIP to listOf("founder", "startup", "ceo", "co-founder")
        ),
        "calling" to mapOf(
            Domain.LDS_LEADERSHIP to listOf("extend", "release", "sustain", "stewardship", "bishop", "ward", "stake"),
            Domain.SPIRITUAL to listOf("divine", "god", "serve", "ministry", "vocation"),
            Domain.PROFESSIONAL to listOf("phone", "conference", "video", "zoom", "teams")
        ),
        "stewardship" to mapOf(
            Domain.LDS_LEADERSHIP to listOf("calling", "ward", "stake", "bishop", "quorum", "auxiliary"),
            Domain.SPIRITUAL to listOf("faith", "god", "blessing", "responsibility"),
            Domain.FINANCE to listOf("investment", "portfolio", "fiduciary", "assets")
        ),
        "ward" to mapOf(
            Domain.LDS_LEADERSHIP to listOf("bishop", "council", "stake", "member", "sacrament", "meeting", "clerk"),
            Domain.HEALTH to listOf("hospital", "patient", "nurse", "medical", "icu", "off", "cold", "flu", "vitamin", "illness", "infection")
        ),
        "stake" to mapOf(
            Domain.LDS_LEADERSHIP to listOf("president", "conference", "ward", "high council", "bishop", "auxiliary"),
            Domain.FINANCE to listOf("equity", "ownership", "shareholder", "investor", "stock")
        ),
        "relief" to mapOf(
            Domain.LDS_LEADERSHIP to listOf("society", "president", "sister", "ward", "stake", "ministering"),
            Domain.HEALTH to listOf("pain", "stress", "anxiety", "medication"),
            Domain.LIFE to listOf("disaster", "humanitarian", "aid", "charity")
        ),
        "missionary" to mapOf(
            Domain.LDS_LEADERSHIP to listOf("ward", "full-time", "elder", "sister", "mission", "zone", "area"),
            Domain.SPIRITUAL to listOf("church", "gospel", "spread", "evangelize"),
            Domain.PROFESSIONAL to listOf("sales", "ambassador", "outreach")
        ),
        "recommend" to mapOf(
            Domain.LDS_LEADERSHIP to listOf("temple", "worthiness", "bishop", "interview", "renewal", "limited"),
            Domain.PROFESSIONAL to listOf("reference", "endorsement", "linkedin", "hire"),
            Domain.LIFE to listOf("suggestion", "advice", "tip")
        ),
        "primary" to mapOf(
            Domain.LDS_LEADERSHIP to listOf("president", "children", "sharing", "nursery", "sunbeam", "ctr", "ward"),
            Domain.EDUCATION to listOf("school", "elementary", "grade", "student"),
            Domain.TECH to listOf("key", "database", "index", "replica")
        ),
        "auxiliary" to mapOf(
            Domain.LDS_LEADERSHIP to listOf("president", "ward", "stake", "relief", "young", "primary", "organization"),
            Domain.TECH to listOf("function", "helper", "utility", "secondary")
        ),
        // === Education ambiguous terms (Teacher vs Student) ===
        "grade" to mapOf(
            Domain.TEACHING to listOf("rubric", "gradebook", "assessment", "report card", "progress", "feedback", "marking"),
            Domain.EDUCATION to listOf("gpa", "transcript", "credits", "pass", "fail", "curved", "professor", "level", "year", "elementary", "middle", "high"),
            Domain.PROFESSIONAL to listOf("quality", "rating", "tier", "level")
        ),
        "lesson" to mapOf(
            Domain.TEACHING to listOf("plan", "objective", "bell ringer", "activity", "differentiation", "pacing"),
            Domain.EDUCATION to listOf("homework", "chapter", "reading", "notes", "lecture"),
            Domain.SPIRITUAL to listOf("church", "sunday", "gospel", "scripture", "primary")
        ),
        "assignment" to mapOf(
            Domain.TEACHING to listOf("rubric", "grading", "due date", "submit", "feedback", "differentiate"),
            Domain.EDUCATION to listOf("homework", "due", "deadline", "submit", "paper", "project"),
            Domain.PROFESSIONAL to listOf("task", "project", "delegate", "responsible", "owner")
        ),
        "assessment" to mapOf(
            Domain.TEACHING to listOf("formative", "summative", "benchmark", "rubric", "grading", "standards"),
            Domain.EDUCATION to listOf("exam", "test", "quiz", "midterm", "final"),
            Domain.HEALTH to listOf("medical", "diagnosis", "evaluation", "screening")
        ),
        "curriculum" to mapOf(
            Domain.TEACHING to listOf("scope", "sequence", "standards", "pacing", "map", "alignment", "ubd"),
            Domain.EDUCATION to listOf("course", "program", "requirements", "degree")
        ),
        "student" to mapOf(
            Domain.TEACHING to listOf("iep", "504", "behavior", "parent", "conference", "grade", "attendance", "observation"),
            Domain.EDUCATION to listOf("loan", "housing", "dorm", "tuition", "financial aid", "scholarship", "enrollment", "registration", "university", "college")
        ),
        "homework" to mapOf(
            Domain.TEACHING to listOf("assign", "grade", "collect", "feedback", "differentiate", "rubric"),
            Domain.EDUCATION to listOf("due", "deadline", "finish", "submit", "study", "complete")
        ),
        "professor" to mapOf(
            Domain.TEACHING to listOf("colleague", "department", "faculty", "tenure", "publish", "research"),
            Domain.EDUCATION to listOf("office hours", "email", "lecture", "syllabus", "grade", "recommendation")
        ),
        "syllabus" to mapOf(
            Domain.TEACHING to listOf("create", "update", "curriculum", "objectives", "policies", "grading"),
            Domain.EDUCATION to listOf("read", "requirements", "schedule", "due dates", "textbook")
        ),
        "attendance" to mapOf(
            Domain.TEACHING to listOf("record", "track", "absence", "tardy", "policy", "report"),
            Domain.EDUCATION to listOf("miss", "absent", "excused", "makeup", "participation"),
            Domain.PROFESSIONAL to listOf("meeting", "required", "optional", "roll call")
        ),
        "lab" to mapOf(
            Domain.EDUCATION to listOf("report", "notebook", "experiment", "hypothesis", "data", "conclusion"),
            Domain.TEACHING to listOf("prep", "materials", "safety", "procedure", "supervision"),
            Domain.TECH to listOf("environment", "testing", "staging", "development"),
            Domain.HEALTH to listOf("blood", "test", "results", "medical", "diagnostic")
        ),
        // === Finance ambiguous terms ===
        "budget" to mapOf(
            Domain.FINANCE to listOf("annual", "fiscal", "allocation", "forecast", "variance", "personal", "household", "savings", "monthly"),
            Domain.PROFESSIONAL to listOf("project", "department", "team", "resource", "headcount", "planning"),
            Domain.BUSINESS to listOf("revenue", "cost", "margin", "profit", "capital", "quarterly")
        ),
        "expense" to mapOf(
            Domain.FINANCE to listOf("log", "report", "receipt", "reimbursement", "uber", "mileage", "travel", "business", "deductible"),
            Domain.FOOD to listOf("meal", "lunch", "dinner", "restaurant", "grocery", "food")
        ),
        // === Idiom disambiguation ===
        "bank" to mapOf(
            Domain.FINANCE to listOf("account", "savings", "loan", "mortgage", "deposit", "withdraw", "atm", "branch", "checking"),
            Domain.PROFESSIONAL to listOf("team", "deliver", "rely", "count", "trust", "depend", "time", "deadline")
        )
    )

    /**
     * Resolve domain for an ambiguous term using context.
     *
     * @param term The ambiguous term
     * @param tokens All tokens in the document
     * @param termIndex Index of the ambiguous term in tokens
     * @param priorScores Domain scores computed WITHOUT ambiguous terms
     * @return Resolved domain and confidence, or null if unresolvable
     */
    fun resolve(
        term: String,
        tokens: List<String>,
        termIndex: Int,
        priorScores: Map<Domain, Float>
    ): DisambiguationResult? {
        val termLower = term.lowercase()
        val domainCollocations = AMBIGUOUS_TERMS[termLower] ?: return null

        // Get local window
        val windowStart = maxOf(0, termIndex - WINDOW_SIZE)
        val windowEnd = minOf(tokens.size, termIndex + WINDOW_SIZE + 1)
        val localTokens = if (windowStart < windowEnd && tokens.isNotEmpty()) {
            tokens.subList(windowStart, windowEnd)
                .map { it.lowercase() }
                .toSet()
        } else {
            emptySet()
        }

        // Score each possible domain
        val domainScores = domainCollocations.map { (domain, collocations) ->
            val collocationHits = collocations.count { it in localTokens }
            val priorScore = priorScores[domain] ?: 0f

            // Combined score: collocation hits weight 0.6, prior weight 0.4
            val combinedScore = (collocationHits * 0.6f) + (priorScore * 0.4f)

            domain to DomainScore(
                domain = domain,
                collocationHits = collocationHits,
                priorScore = priorScore,
                combinedScore = combinedScore
            )
        }.toMap()

        // Find winner
        val winner = domainScores.maxByOrNull { it.value.combinedScore }
            ?.takeIf { it.value.combinedScore > 0f }
            ?: return null

        // Compute confidence
        val secondBest = domainScores.values
            .filter { it.domain != winner.key }
            .maxByOrNull { it.combinedScore }

        val confidence = if (secondBest != null && secondBest.combinedScore > 0f) {
            winner.value.combinedScore / (winner.value.combinedScore + secondBest.combinedScore)
        } else {
            0.9f  // No competition
        }

        return DisambiguationResult(
            term = termLower,
            resolvedDomain = winner.key,
            confidence = confidence,
            reason = buildReason(winner.value, localTokens, domainCollocations[winner.key]!!),
            competingDomains = domainScores.keys.filter { it != winner.key }.map { it.name }
        )
    }

    private fun buildReason(
        winner: DomainScore,
        localTokens: Set<String>,
        collocations: List<String>
    ): String {
        val hits = collocations.filter { it in localTokens }
        return if (hits.isNotEmpty()) {
            "collocation(${hits.take(3).joinToString(",")})"
        } else {
            "priorContext"
        }
    }

    /**
     * Check if a term is ambiguous (needs disambiguation).
     */
    fun isAmbiguous(term: String): Boolean {
        return term.lowercase() in AMBIGUOUS_TERMS
    }

    /**
     * Get all domains a term could belong to.
     */
    fun getPossibleDomains(term: String): Set<Domain>? {
        return AMBIGUOUS_TERMS[term.lowercase()]?.keys
    }

    data class DomainScore(
        val domain: Domain,
        val collocationHits: Int,
        val priorScore: Float,
        val combinedScore: Float
    )

    data class DisambiguationResult(
        val term: String,
        val resolvedDomain: Domain,
        val confidence: Float,
        val reason: String,
        val competingDomains: List<String>
    )
}

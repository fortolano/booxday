package com.boox.day.ocr.semantic.classification

import com.boox.day.ocr.model.Domain
import com.boox.day.ocr.model.Intent

/**
 * Dominant evidence patterns that OVERRIDE weaker signals.
 * When fired, these dramatically boost one domain and suppress competitors.
 *
 * This is the key to "intelligent" classification - certain patterns are so
 * strong they should almost guarantee a domain.
 */
object DominantEvidenceRules {

    /**
     * Dominant evidence definitions.
     * Each rule: pattern → (domain, confidence floor, suppressions)
     */
    data class DominantRule(
        val id: String,
        val domain: Domain,
        /** Minimum confidence when this rule fires */
        val confidenceFloor: Float,
        /** Domains whose signals should be suppressed */
        val suppressedDomains: Set<Domain>,
        /** Intents whose signals should be suppressed */
        val suppressedIntents: Set<Intent>
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // TECH DOMINANCE
    // ═══════════════════════════════════════════════════════════════════════════

    val TECH_CODE_BLOCK = DominantRule(
        id = "TECH_CODEBLOCK",
        domain = Domain.TECH,
        confidenceFloor = 0.85f,
        suppressedDomains = setOf(Domain.PROFESSIONAL, Domain.EDUCATION),
        suppressedIntents = setOf(Intent.CALL_LOG, Intent.MEETING_NOTES)
    )

    val TECH_STACK_TRACE = DominantRule(
        id = "TECH_STACKTRACE",
        domain = Domain.TECH,
        confidenceFloor = 0.90f,
        suppressedDomains = setOf(Domain.PROFESSIONAL, Domain.EDUCATION, Domain.FINANCE),
        suppressedIntents = setOf(Intent.CALL_LOG, Intent.MEETING_NOTES, Intent.EXPENSE_LOG)
    )

    val TECH_FILE_EXTENSIONS = DominantRule(
        id = "TECH_FILE_EXT",
        domain = Domain.TECH,
        confidenceFloor = 0.75f,
        suppressedDomains = setOf(),
        suppressedIntents = setOf()
    )

    val TECH_GIT_COMMANDS = DominantRule(
        id = "TECH_GIT",
        domain = Domain.TECH,
        confidenceFloor = 0.80f,
        suppressedDomains = setOf(Domain.PROFESSIONAL),
        suppressedIntents = setOf()
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // SPIRITUAL DOMINANCE (Universal across faiths)
    // ═══════════════════════════════════════════════════════════════════════════

    val SPIRITUAL_SCRIPTURE_REF = DominantRule(
        id = "SPIRITUAL_SCRIPTURE",
        domain = Domain.SPIRITUAL,
        confidenceFloor = 0.85f,
        suppressedDomains = setOf(Domain.PROFESSIONAL, Domain.EDUCATION),
        suppressedIntents = setOf(Intent.MEETING_NOTES)
    )

    val SPIRITUAL_PRAYER_PATTERN = DominantRule(
        id = "SPIRITUAL_PRAYER",
        domain = Domain.SPIRITUAL,
        confidenceFloor = 0.80f,
        suppressedDomains = setOf(Domain.PROFESSIONAL),
        suppressedIntents = setOf()
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // EDUCATION DOMINANCE (merged SCHOOL + STUDENT)
    // ═══════════════════════════════════════════════════════════════════════════

    val EDUCATION_EXAM_PATTERN = DominantRule(
        id = "EDUCATION_EXAM",
        domain = Domain.EDUCATION,
        confidenceFloor = 0.80f,
        suppressedDomains = setOf(Domain.PROFESSIONAL),
        suppressedIntents = setOf()
    )

    val EDUCATION_ASSIGNMENT = DominantRule(
        id = "EDUCATION_ASSIGNMENT",
        domain = Domain.EDUCATION,
        confidenceFloor = 0.75f,
        suppressedDomains = setOf(Domain.PROFESSIONAL),
        suppressedIntents = setOf()
    )

    val EDUCATION_HOMEWORK = DominantRule(
        id = "EDUCATION_HOMEWORK",
        domain = Domain.EDUCATION,
        confidenceFloor = 0.85f,
        suppressedDomains = setOf(Domain.PROFESSIONAL, Domain.TEACHING),
        suppressedIntents = setOf()
    )

    val EDUCATION_STUDY = DominantRule(
        id = "EDUCATION_STUDY",
        domain = Domain.EDUCATION,
        confidenceFloor = 0.85f,
        suppressedDomains = setOf(Domain.PROFESSIONAL),
        suppressedIntents = setOf()
    )

    val EDUCATION_THESIS = DominantRule(
        id = "EDUCATION_THESIS",
        domain = Domain.EDUCATION,
        confidenceFloor = 0.88f,
        suppressedDomains = setOf(Domain.PROFESSIONAL),
        suppressedIntents = setOf(Intent.RESEARCH)
    )

    val EDUCATION_LAB = DominantRule(
        id = "EDUCATION_LAB",
        domain = Domain.EDUCATION,
        confidenceFloor = 0.82f,
        suppressedDomains = setOf(Domain.TECH, Domain.HEALTH),
        suppressedIntents = setOf()
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // TEACHING DOMINANCE (Educators)
    // ═══════════════════════════════════════════════════════════════════════════

    val TEACHING_LESSON_PLAN = DominantRule(
        id = "TEACHING_LESSON_PLAN",
        domain = Domain.TEACHING,
        confidenceFloor = 0.90f,
        suppressedDomains = setOf(Domain.PROFESSIONAL, Domain.EDUCATION),
        suppressedIntents = setOf(Intent.MEETING_NOTES, Intent.PLANNING)
    )

    val TEACHING_IEP_504 = DominantRule(
        id = "TEACHING_IEP_504",
        domain = Domain.TEACHING,
        confidenceFloor = 0.92f,
        suppressedDomains = setOf(Domain.PROFESSIONAL, Domain.EDUCATION, Domain.HEALTH),
        suppressedIntents = setOf()
    )

    val TEACHING_GRADING = DominantRule(
        id = "TEACHING_GRADING",
        domain = Domain.TEACHING,
        confidenceFloor = 0.85f,
        suppressedDomains = setOf(Domain.PROFESSIONAL, Domain.EDUCATION),
        suppressedIntents = setOf()
    )

    val TEACHING_PARENT_CONF = DominantRule(
        id = "TEACHING_PARENT_CONF",
        domain = Domain.TEACHING,
        confidenceFloor = 0.88f,
        suppressedDomains = setOf(Domain.PROFESSIONAL, Domain.LIFE),
        suppressedIntents = setOf(Intent.MEETING_NOTES)
    )

    // Teacher-specific patterns (very distinctive)
    private val LESSON_PLAN_PATTERN = Regex(
        """(?:lesson\s+plan|learning\s+objective|learning\s+target|swbat|students\s+will\s+be\s+able|bell\s+ringer|exit\s+ticket|do\s+now|anticipatory\s+set|guided\s+practice|independent\s+practice)""",
        RegexOption.IGNORE_CASE
    )

    private val IEP_504_PATTERN = Regex(
        """(?:iep|504\s+plan|individualized\s+education|accommodation(?:s)?|modification(?:s)?|specially\s+designed\s+instruction|annual\s+goal|present\s+levels?|special\s+education|sped\s+(?:meeting|teacher|student))""",
        RegexOption.IGNORE_CASE
    )

    private val TEACHER_GRADING_PATTERN = Regex(
        """(?:grading\s+rubric|grade\s+book|gradebook|formative\s+assessment|summative\s+assessment|benchmark\s+assessment|standards.?based\s+grading|report\s+card(?:s)?|progress\s+report)""",
        RegexOption.IGNORE_CASE
    )

    private val PARENT_CONF_PATTERN = Regex(
        """(?:parent\s+(?:conference|teacher|meeting|communication|contact)|ptc|family\s+(?:conference|meeting)|back\s+to\s+school\s+night|curriculum\s+night|open\s+house)""",
        RegexOption.IGNORE_CASE
    )

    private val CLASSROOM_MGMT_PATTERN = Regex(
        """(?:seating\s+chart|classroom\s+(?:management|procedure|rule)|behavior\s+(?:plan|contract|log|intervention)|attendance\s+(?:record|sheet)|discipline\s+referral)""",
        RegexOption.IGNORE_CASE
    )

    // Student-specific patterns
    private val HOMEWORK_PATTERN = Regex(
        """(?:homework\s+(?:for|due|assignment)|assignment\s+due|project\s+due|paper\s+due|essay\s+(?:draft|due|outline)|turn\s+in\s+(?:by|on))""",
        RegexOption.IGNORE_CASE
    )

    private val STUDY_PATTERN = Regex(
        """(?:study\s+(?:for|guide|group|session|plan)|exam\s+prep|test\s+prep|flash\s*cards?|practice\s+(?:test|problems|questions)|review\s+(?:session|sheet))""",
        RegexOption.IGNORE_CASE
    )

    private val THESIS_PATTERN = Regex(
        """(?:thesis\s+(?:chapter|defense|proposal|advisor|committee)|dissertation|literature\s+review|research\s+(?:question|methodology|design)|thesis\s+draft)""",
        RegexOption.IGNORE_CASE
    )

    private val LAB_REPORT_PATTERN = Regex(
        """(?:lab\s+(?:report|notebook|notes)|experiment\s+(?:\d+|on|about)|hypothesis:|procedure:|(?:dependent|independent)\s+variable|data\s+(?:table|collection)|scientific\s+method)""",
        RegexOption.IGNORE_CASE
    )

    private val COURSE_PLANNING_PATTERN = Regex(
        """(?:(?:course|class|semester)\s+schedule|degree\s+(?:requirements?|audit)|credits?\s+(?:needed|required|remaining)|gpa|registration|prerequisite|major|minor)""",
        RegexOption.IGNORE_CASE
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // FINANCE DOMINANCE
    // ═══════════════════════════════════════════════════════════════════════════

    val FINANCE_CURRENCY_HEAVY = DominantRule(
        id = "FINANCE_CURRENCY",
        domain = Domain.FINANCE,
        confidenceFloor = 0.80f,
        suppressedDomains = setOf(),
        suppressedIntents = setOf()
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // HEALTH DOMINANCE
    // ═══════════════════════════════════════════════════════════════════════════

    val HEALTH_MEDICAL = DominantRule(
        id = "HEALTH_MEDICAL",
        domain = Domain.HEALTH,
        confidenceFloor = 0.80f,
        suppressedDomains = setOf(),
        suppressedIntents = setOf()
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // PATTERN DETECTORS
    // ═══════════════════════════════════════════════════════════════════════════

    // Code block: triple backticks OR 4+ space indented lines with code-like content
    private val CODE_BLOCK_PATTERN = Regex(
        """```[\s\S]*?```|^( {4,}|\t+)[a-zA-Z_][a-zA-Z0-9_]*\s*[=(\[{]""",
        RegexOption.MULTILINE
    )

    // Stack trace: Exception/Error at file:line format
    private val STACK_TRACE_PATTERN = Regex(
        """(Exception|Error|Caused by|at\s+[\w.]+\([\w.]+:\d+\))""",
        RegexOption.IGNORE_CASE
    )

    // File extensions in context (3+ occurrences = dominant)
    private val FILE_EXT_PATTERN = Regex(
        """\b\w+\.(kt|java|py|js|ts|tsx|jsx|cpp|hpp|h|c|go|rs|swift|xml|json|yaml|yml|sql|sh|bash|rb|php)\b""",
        RegexOption.IGNORE_CASE
    )

    // Git commands and terms
    private val GIT_PATTERN = Regex(
        """\b(git\s+(commit|push|pull|merge|branch|checkout|rebase|clone|fetch)|HEAD|MERGE_HEAD|origin/|refs/)\b""",
        RegexOption.IGNORE_CASE
    )

    // Scripture reference: Book Chapter:Verse (universal pattern across religions)
    private val SCRIPTURE_PATTERN = Regex(
        """(?:Genesis|Exodus|Leviticus|Numbers|Deuteronomy|Joshua|Judges|Ruth|Samuel|Kings|Chronicles|Ezra|Nehemiah|Esther|Job|Psalms?|Proverbs?|Ecclesiastes|Song|Isaiah|Jeremiah|Lamentations|Ezekiel|Daniel|Hosea|Joel|Amos|Obadiah|Jonah|Micah|Nahum|Habakkuk|Zephaniah|Haggai|Zechariah|Malachi|Matthew|Mark|Luke|John|Acts|Romans|Corinthians|Galatians|Ephesians|Philippians|Colossians|Thessalonians|Timothy|Titus|Philemon|Hebrews|James|Peter|Jude|Revelation|Surah|Al-|Bhagavad|Vedas?|D&C|Doctrine|Covenants)\s*\d+[:\.\s]\d+""",
        RegexOption.IGNORE_CASE
    )

    // Prayer patterns (universal across faiths)
    // Note: \bamen\b prevents matching "tournament", "amendment", etc.
    private val PRAYER_PATTERN = Regex(
        """(?:dear\s+(?:god|lord|father|heavenly)|in\s+(?:jesus'?|christ'?s?)\s+name|\bamen\b|bismillah|inshallah|alhamdulillah|om\s+shanti|namaste|shalom|baruch)""",
        RegexOption.IGNORE_CASE
    )

    // Exam patterns
    private val EXAM_PATTERN = Regex(
        """(?:exam|midterm|final\s+exam|quiz|test\s+(?:on|about|for)|study\s+guide|review\s+session|graded?|multiple\s+choice|essay\s+question)""",
        RegexOption.IGNORE_CASE
    )

    // Assignment patterns
    private val ASSIGNMENT_PATTERN = Regex(
        """(?:assignment|homework|due\s+(?:date|by)|submit(?:ted)?|professor|syllabus|course\s+(?:code|number)|lecture\s+notes?|semester|credits?)""",
        RegexOption.IGNORE_CASE
    )

    // Heavy currency (multiple money amounts = financial document)
    private val CURRENCY_PATTERN = Regex(
        """[$€£¥₹₽₩]\s*\d+(?:[.,]\d+)?|\d+(?:[.,]\d+)?\s*(?:USD|EUR|GBP|JPY|INR|CAD|AUD)"""
    )

    // Medical/health dominant patterns
    private val MEDICAL_PATTERN = Regex(
        """(?:diagnosis|prescription|mg\s*(?:daily|twice|three)|dosage|symptoms?|treatment|medication|blood\s+(?:pressure|sugar|test)|cholesterol|heart\s+rate|bpm)""",
        RegexOption.IGNORE_CASE
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // ENTREPRENEURSHIP DOMINANCE
    // ═══════════════════════════════════════════════════════════════════════════

    val ENTREPRENEURSHIP_STARTUP = DominantRule(
        id = "ENTREPRENEURSHIP_STARTUP",
        domain = Domain.ENTREPRENEURSHIP,
        confidenceFloor = 0.80f,
        suppressedDomains = setOf(Domain.PROFESSIONAL, Domain.BUSINESS),
        suppressedIntents = setOf()
    )

    // Startup/VC terminology
    private val STARTUP_PATTERN = Regex(
        """(?:startup|seed\s+round|series\s+[a-c]|venture\s+capital|vc|angel\s+investor|pitch\s+deck|term\s+sheet|cap\s+table|product.?market\s+fit|pmf|runway|burn\s+rate|accelerator|y\s*combinator|yc|bootstrap(?:ped)?|co-?founder)""",
        RegexOption.IGNORE_CASE
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // STRATEGY DOMINANCE
    // ═══════════════════════════════════════════════════════════════════════════

    val STRATEGY_PLANNING = DominantRule(
        id = "STRATEGY_PLANNING",
        domain = Domain.STRATEGY,
        confidenceFloor = 0.75f,
        suppressedDomains = setOf(Domain.PROFESSIONAL),
        suppressedIntents = setOf()
    )

    // Strategic planning terminology
    private val STRATEGY_PATTERN = Regex(
        """(?:swot\s+analysis|pest\s+analysis|porter.?s?\s+five|competitive\s+landscape|strategic\s+(?:plan|initiative|priority)|north\s+star\s+metric|roadmap\s+20\d{2}|multi.?year|long.?term\s+(?:plan|vision)|market\s+positioning|blue\s+ocean|red\s+ocean)""",
        RegexOption.IGNORE_CASE
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // SOCIAL_MEDIA DOMINANCE
    // ═══════════════════════════════════════════════════════════════════════════

    val SOCIAL_MEDIA_PLATFORM = DominantRule(
        id = "SOCIAL_MEDIA_PLATFORM",
        domain = Domain.SOCIAL_MEDIA,
        confidenceFloor = 0.80f,
        suppressedDomains = setOf(Domain.PROFESSIONAL, Domain.LIFE),
        suppressedIntents = setOf()
    )

    // Social media platform mentions and creator terminology
    private val SOCIAL_MEDIA_PATTERN = Regex(
        """(?:instagram|tiktok|youtube|linkedin|twitter|x\.com|threads|reels?|shorts|stories|hashtag|#\w{3,}|followers?|engagement\s+rate|viral|algorithm|content\s+creator|influencer|ugc|posting\s+schedule|content\s+calendar)""",
        RegexOption.IGNORE_CASE
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // PROFESSIONAL DOMINANCE (merged WORK + MANAGEMENT + LEADERSHIP)
    // ═══════════════════════════════════════════════════════════════════════════

    val PROFESSIONAL_EXECUTIVE = DominantRule(
        id = "PROFESSIONAL_EXECUTIVE",
        domain = Domain.PROFESSIONAL,
        confidenceFloor = 0.75f,
        suppressedDomains = setOf(Domain.BUSINESS),
        suppressedIntents = setOf()
    )

    val PROFESSIONAL_OPS = DominantRule(
        id = "PROFESSIONAL_OPS",
        domain = Domain.PROFESSIONAL,
        confidenceFloor = 0.70f,
        suppressedDomains = setOf(),
        suppressedIntents = setOf()
    )

    // Executive/leadership terminology
    private val LEADERSHIP_PATTERN = Regex(
        """(?:c-?suite|executive\s+(?:team|meeting|presence)|board\s+(?:of\s+directors|meeting)|servant\s+leadership|succession\s+planning|leadership\s+(?:pipeline|development)|organizational\s+health|change\s+management|thought\s+leadership|strategic\s+vision)""",
        RegexOption.IGNORE_CASE
    )

    // Management/operations terminology
    private val MANAGEMENT_PATTERN = Regex(
        """(?:direct\s+report|skip\s+level|1:1|one.?on.?one|performance\s+(?:review|cycle)|span\s+of\s+control|headcount|org\s+chart|reporting\s+structure|resource\s+allocation|capacity\s+planning)""",
        RegexOption.IGNORE_CASE
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // BUSINESS DOMINANCE
    // ═══════════════════════════════════════════════════════════════════════════

    val BUSINESS_COMMERCE = DominantRule(
        id = "BUSINESS_COMMERCE",
        domain = Domain.BUSINESS,
        confidenceFloor = 0.75f,
        suppressedDomains = setOf(Domain.PROFESSIONAL),
        suppressedIntents = setOf()
    )

    // Business/commerce terminology
    private val BUSINESS_PATTERN = Regex(
        """(?:p&l|ebitda|profit\s+margin|balance\s+sheet|cash\s+flow|quarterly\s+results|fiscal\s+year|market\s+share|competitive\s+advantage|value\s+proposition|ltv|cac|arpu|mrr|arr|b2b|b2c|enterprise\s+sales|pipeline\s+review)""",
        RegexOption.IGNORE_CASE
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // LDS LEADERSHIP DOMINANCE
    // ═══════════════════════════════════════════════════════════════════════════

    val LDS_LEADERSHIP_CALLING = DominantRule(
        id = "LDS_LEADERSHIP_CALLING",
        domain = Domain.LDS_LEADERSHIP,
        confidenceFloor = 0.90f,
        suppressedDomains = setOf(Domain.SPIRITUAL, Domain.PROFESSIONAL),
        suppressedIntents = setOf(Intent.MEETING_NOTES, Intent.LEADERSHIP_NOTES, Intent.PEOPLE_MANAGEMENT)
    )

    val LDS_LEADERSHIP_COUNCIL = DominantRule(
        id = "LDS_LEADERSHIP_COUNCIL",
        domain = Domain.LDS_LEADERSHIP,
        confidenceFloor = 0.85f,
        suppressedDomains = setOf(Domain.SPIRITUAL, Domain.PROFESSIONAL),
        suppressedIntents = setOf(Intent.MEETING_NOTES)
    )

    val LDS_LEADERSHIP_MINISTERING = DominantRule(
        id = "LDS_LEADERSHIP_MINISTERING",
        domain = Domain.LDS_LEADERSHIP,
        confidenceFloor = 0.88f,
        suppressedDomains = setOf(Domain.SPIRITUAL, Domain.LIFE),
        suppressedIntents = setOf()
    )

    // LDS Leadership positions (very high confidence - these are unambiguous)
    private val LDS_CALLING_PATTERN = Regex(
        """(?:bishop(?:ric)?|stake\s+president|stake\s+presidency|elders?\s+quorum(?:\s+president)?|relief\s+society(?:\s+president)?|young\s+(?:men|women)(?:\s+president)?|primary\s+president|sunday\s+school\s+president|high\s+council(?:or)?|ward\s+clerk|executive\s+secretary|seminary\s+teacher)""",
        RegexOption.IGNORE_CASE
    )

    // LDS Council meetings
    private val LDS_COUNCIL_PATTERN = Regex(
        """(?:ward\s+council|\bpec\b|priesthood\s+executive|\bbyc\b|bishop.?s?\s+youth\s+council|stake\s+council|welfare\s+committee)""",
        RegexOption.IGNORE_CASE
    )

    // LDS Ministering/interviews/callings
    private val LDS_MINISTERING_PATTERN = Regex(
        """(?:ministering\s+(?:assignment|interview|brother|sister|companion)|temple\s+recommend|worthiness\s+interview|tithing\s+settlement|extend(?:ing)?\s+(?:a\s+)?calling|set(?:ting)?\s+apart|come\s+follow\s+me|fast\s+offering)""",
        RegexOption.IGNORE_CASE
    )

    // LDS Programs and activities
    private val LDS_PROGRAM_PATTERN = Regex(
        """(?:mutual(?:\s+night)?|fsy|girls?\s+camp|youth\s+trek|pioneer\s+trek|high\s+adventure|seminary|early\s+morning\s+seminary|institute|lcr|leader\s+and\s+clerk)""",
        RegexOption.IGNORE_CASE
    )

    // LDS Common abbreviations (very strong signal)
    private val LDS_ABBREVIATION_PATTERN = Regex(
        """\b(?:RS|EQ|YW|YM|SS|PEC|BYC|CTR|FSY|EFY|LCR)\b"""
    )

    // Brother/Sister title pattern (distinctly LDS in leadership context)
    private val LDS_TITLE_PATTERN = Regex(
        """(?:Brother|Sister)\s+[A-Z][a-z]+(?:\s+[A-Z][a-z]+)?""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Detect all dominant evidence in text.
     * Returns list of fired rules, ordered by confidence floor (highest first).
     */
    fun detect(text: String): List<DominantRule> {
        val fired = mutableListOf<DominantRule>()

        // TECH patterns
        if (CODE_BLOCK_PATTERN.containsMatchIn(text)) {
            fired.add(TECH_CODE_BLOCK)
        }
        if (STACK_TRACE_PATTERN.findAll(text).count() >= 2) {
            fired.add(TECH_STACK_TRACE)
        }
        if (FILE_EXT_PATTERN.findAll(text).count() >= 3) {
            fired.add(TECH_FILE_EXTENSIONS)
        }
        if (GIT_PATTERN.findAll(text).count() >= 2) {
            fired.add(TECH_GIT_COMMANDS)
        }

        // SPIRITUAL patterns
        if (SCRIPTURE_PATTERN.containsMatchIn(text)) {
            fired.add(SPIRITUAL_SCRIPTURE_REF)
        }
        if (PRAYER_PATTERN.containsMatchIn(text)) {
            fired.add(SPIRITUAL_PRAYER_PATTERN)
        }

        // TEACHING patterns (check first - more specific than EDUCATION)
        if (LESSON_PLAN_PATTERN.containsMatchIn(text)) {
            fired.add(TEACHING_LESSON_PLAN)
        }
        if (IEP_504_PATTERN.containsMatchIn(text)) {
            fired.add(TEACHING_IEP_504)
        }
        if (TEACHER_GRADING_PATTERN.containsMatchIn(text)) {
            fired.add(TEACHING_GRADING)
        }
        if (PARENT_CONF_PATTERN.containsMatchIn(text)) {
            fired.add(TEACHING_PARENT_CONF)
        }
        if (CLASSROOM_MGMT_PATTERN.containsMatchIn(text)) {
            fired.add(TEACHING_LESSON_PLAN)  // Classroom mgmt implies teaching
        }

        // EDUCATION patterns (student/learner context)
        if (HOMEWORK_PATTERN.containsMatchIn(text)) {
            fired.add(EDUCATION_HOMEWORK)
        }
        if (STUDY_PATTERN.containsMatchIn(text)) {
            fired.add(EDUCATION_STUDY)
        }
        if (THESIS_PATTERN.containsMatchIn(text)) {
            fired.add(EDUCATION_THESIS)
        }
        if (LAB_REPORT_PATTERN.containsMatchIn(text)) {
            fired.add(EDUCATION_LAB)
        }
        if (COURSE_PLANNING_PATTERN.findAll(text).count() >= 2) {
            fired.add(EDUCATION_HOMEWORK)  // Course planning implies education
        }

        // EDUCATION patterns (generic academic)
        if (EXAM_PATTERN.containsMatchIn(text)) {
            fired.add(EDUCATION_EXAM_PATTERN)
        }
        if (ASSIGNMENT_PATTERN.findAll(text).count() >= 2) {
            fired.add(EDUCATION_ASSIGNMENT)
        }

        // FINANCE patterns
        if (CURRENCY_PATTERN.findAll(text).count() >= 3) {
            fired.add(FINANCE_CURRENCY_HEAVY)
        }

        // HEALTH patterns
        if (MEDICAL_PATTERN.findAll(text).count() >= 2) {
            fired.add(HEALTH_MEDICAL)
        }

        // ENTREPRENEURSHIP patterns
        if (STARTUP_PATTERN.findAll(text).count() >= 2) {
            fired.add(ENTREPRENEURSHIP_STARTUP)
        }

        // STRATEGY patterns
        if (STRATEGY_PATTERN.findAll(text).count() >= 2) {
            fired.add(STRATEGY_PLANNING)
        }

        // SOCIAL_MEDIA patterns
        if (SOCIAL_MEDIA_PATTERN.findAll(text).count() >= 2) {
            fired.add(SOCIAL_MEDIA_PLATFORM)
        }

        // PROFESSIONAL patterns (leadership + management)
        if (LEADERSHIP_PATTERN.findAll(text).count() >= 2) {
            fired.add(PROFESSIONAL_EXECUTIVE)
        }
        if (MANAGEMENT_PATTERN.findAll(text).count() >= 2) {
            fired.add(PROFESSIONAL_OPS)
        }

        // BUSINESS patterns
        if (BUSINESS_PATTERN.findAll(text).count() >= 3) {
            fired.add(BUSINESS_COMMERCE)
        }

        // LDS LEADERSHIP patterns (highest priority - very specific domain)
        if (LDS_CALLING_PATTERN.containsMatchIn(text)) {
            fired.add(LDS_LEADERSHIP_CALLING)
        }
        if (LDS_COUNCIL_PATTERN.containsMatchIn(text)) {
            fired.add(LDS_LEADERSHIP_COUNCIL)
        }
        if (LDS_MINISTERING_PATTERN.findAll(text).count() >= 1) {
            fired.add(LDS_LEADERSHIP_MINISTERING)
        }
        // Also fire on LDS programs with 2+ matches
        if (LDS_PROGRAM_PATTERN.findAll(text).count() >= 2) {
            fired.add(LDS_LEADERSHIP_MINISTERING)
        }
        // LDS abbreviations (2+ = strong signal for council context)
        if (LDS_ABBREVIATION_PATTERN.findAll(text).count() >= 2) {
            fired.add(LDS_LEADERSHIP_COUNCIL)
        }
        // Brother/Sister titles (2+ = strong signal for LDS leadership context)
        if (LDS_TITLE_PATTERN.findAll(text).count() >= 2) {
            fired.add(LDS_LEADERSHIP_CALLING)
        }

        // Sort by confidence floor (highest first)
        return fired.sortedByDescending { it.confidenceFloor }
    }

    /**
     * Check if a domain should be suppressed given dominant rules.
     */
    fun shouldSuppressDomain(domain: Domain, dominantRules: List<DominantRule>): Boolean {
        return dominantRules.any { domain in it.suppressedDomains }
    }

    /**
     * Check if an intent should be suppressed given dominant rules.
     */
    fun shouldSuppressIntent(intent: Intent, dominantRules: List<DominantRule>): Boolean {
        return dominantRules.any { intent in it.suppressedIntents }
    }

    /**
     * Get the dominant domain from fired rules (if any).
     */
    fun getDominantDomain(dominantRules: List<DominantRule>): Domain? {
        return dominantRules.firstOrNull()?.domain
    }
}

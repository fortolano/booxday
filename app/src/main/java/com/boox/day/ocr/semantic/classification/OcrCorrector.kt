package com.boox.day.ocr.semantic.classification

import kotlin.math.min

/**
 * OCR error correction with two-stage approach:
 * 1. High-precision direct substitutions (curated list)
 * 2. Weighted edit distance with confusion matrix
 *
 * This is language-based, not domain-specific.
 */
object OcrCorrector {

    // ═══════════════════════════════════════════════════════════════════════════
    // STAGE 1: Direct Substitutions (high precision, hand-curated)
    // Common OCR errors that result in non-words or common misspellings
    // ═══════════════════════════════════════════════════════════════════════════

    private val DIRECT_SUBSTITUTIONS = mapOf(
        // ─────────────────────────────────────────────────────────────────────────
        // rn → m confusions (very common in serif fonts and handwriting)
        // ─────────────────────────────────────────────────────────────────────────
        "rneet" to "meet",
        "rneeting" to "meeting",
        "rnoney" to "money",
        "surnrner" to "summer",
        "wornan" to "woman",
        "cornplete" to "complete",
        "cornputer" to "computer",
        "cornrnit" to "commit",
        "forurn" to "forum",
        "cornrnunity" to "community",
        "rnorning" to "morning",
        "remerriber" to "remember",
        "arnount" to "amount",
        "docurnent" to "document",
        "governrnent" to "government",
        "environrnent" to "environment",
        "rnanage" to "manage",
        "rnanager" to "manager",
        "perforrnance" to "performance",
        "inforrnation" to "information",
        "cornrnent" to "comment",
        "elernent" to "element",
        "rnember" to "member",
        "rernerriber" to "remember",
        "Novernber" to "November",
        "Decernber" to "December",
        "Septernber" to "September",

        // ─────────────────────────────────────────────────────────────────────────
        // l ↔ 1 confusions
        // ─────────────────────────────────────────────────────────────────────────
        "1ist" to "list",
        "1ike" to "like",
        "ca11" to "call",
        "a11" to "all",
        "wi11" to "will",
        "shou1d" to "should",
        "wou1d" to "would",
        "cou1d" to "could",
        "peop1e" to "people",
        "he1p" to "help",
        "fe1t" to "felt",
        "1earn" to "learn",
        "1esson" to "lesson",
        "1ater" to "later",
        "1ast" to "last",
        "1ove" to "love",
        "1ife" to "life",
        "p1an" to "plan",
        "p1ace" to "place",

        // ─────────────────────────────────────────────────────────────────────────
        // o ↔ 0 confusions
        // ─────────────────────────────────────────────────────────────────────────
        "t0day" to "today",
        "t0morrow" to "tomorrow",
        "g0al" to "goal",
        "g00d" to "good",
        "b00k" to "book",
        "l00k" to "look",
        "sch00l" to "school",
        "t00" to "too",
        "m0nday" to "monday",
        "m0nth" to "month",
        "n0te" to "note",
        "n0tes" to "notes",
        "w0rk" to "work",
        "h0me" to "home",
        "ph0ne" to "phone",
        "d0ne" to "done",
        "g0ne" to "gone",
        "0nly" to "only",
        "0pen" to "open",
        "cl0se" to "close",

        // ─────────────────────────────────────────────────────────────────────────
        // vv → w confusions
        // ─────────────────────────────────────────────────────────────────────────
        "vvith" to "with",
        "vvork" to "work",
        "vveek" to "week",
        "vvant" to "want",
        "vvay" to "way",
        "nevv" to "new",
        "knovv" to "know",
        "vvhat" to "what",
        "vvhen" to "when",
        "vvhere" to "where",
        "vvhy" to "why",
        "vvho" to "who",
        "vvhich" to "which",
        "revievv" to "review",
        "vievv" to "view",
        "flovv" to "flow",
        "shovv" to "show",
        "grovv" to "grow",

        // ─────────────────────────────────────────────────────────────────────────
        // cl → d confusions (common in handwriting)
        // ─────────────────────────────────────────────────────────────────────────
        "clate" to "date",
        "clouble" to "double",
        "clown" to "down",
        "clue" to "due",
        "cleal" to "deal",
        "clecision" to "decision",
        "cliscuss" to "discuss",
        "cliscussion" to "discussion",
        "clocument" to "document",
        "clone" to "done",

        // ─────────────────────────────────────────────────────────────────────────
        // Common spiritual/church terms (language-neutral OCR fixes)
        // ─────────────────────────────────────────────────────────────────────────
        "lorcl" to "lord",
        "goocl" to "good",
        "blessecl" to "blessed",
        "prayec" to "prayed",
        "praycr" to "prayer",
        "churcb" to "church",
        "faifh" to "faith",
        "worsbip" to "worship",
        "praycrs" to "prayers",
        "spirif" to "spirit",
        "bibie" to "bible",
        "bib1e" to "bible",
        "gospe1" to "gospel",

        // ─────────────────────────────────────────────────────────────────────────
        // Common tech terms
        // ─────────────────────────────────────────────────────────────────────────
        "functi0n" to "function",
        "c0de" to "code",
        "0bject" to "object",
        "cornrnand" to "command",
        "ca1endar" to "calendar",
        "schedu1e" to "schedule",
        "emai1" to "email",
        "ernail" to "email",
        "rnessage" to "message",
        "fi1e" to "file",
        "fo1der" to "folder",

        // ─────────────────────────────────────────────────────────────────────────
        // Common general terms (rn, cl, vv patterns)
        // ─────────────────────────────────────────────────────────────────────────
        "tirne" to "time",
        "horne" to "home",
        "narne" to "name",
        "sarne" to "same",
        "carne" to "came",
        "garne" to "game",
        "frarne" to "frame",
        "blarne" to "blame",

        // ─────────────────────────────────────────────────────────────────────────
        // Common misspellings (NOT OCR errors, but frequent human typos)
        // Comprehensive list based on frequency analysis (Jan 2026)
        // ─────────────────────────────────────────────────────────────────────────

        // --- Tomorrow variants (very common) ---
        "tommorow" to "tomorrow",
        "tommorrow" to "tomorrow",
        "tomarrow" to "tomorrow",
        "tomorow" to "tomorrow",

        // --- IE/EI rule violations ---
        "recieve" to "receive",
        "reciept" to "receipt",
        "recieved" to "received",
        "wierd" to "weird",
        "beleive" to "believe",
        "belive" to "believe",
        "concieve" to "conceive",
        "percieve" to "perceive",
        "decieve" to "deceive",
        "cieling" to "ceiling",

        // --- Double letter errors ---
        "occured" to "occurred",
        "occurence" to "occurrence",
        "occurrance" to "occurrence",
        "ocurence" to "occurrence",
        "accomodate" to "accommodate",
        "acommodate" to "accommodate",
        "accomadate" to "accommodate",
        "occassion" to "occasion",
        "ocasion" to "occasion",
        "embarass" to "embarrass",
        "embarras" to "embarrass",
        "embarrased" to "embarrassed",
        "vaccum" to "vacuum",
        "vacum" to "vacuum",
        "milennium" to "millennium",
        "milenium" to "millennium",

        // --- Separate/Desperate pattern ---
        "seperate" to "separate",
        "seprate" to "separate",
        "desparate" to "desperate",

        // --- Definitely variants (top misspelling) ---
        "definately" to "definitely",
        "definitly" to "definitely",
        "definetly" to "definitely",
        "defintely" to "definitely",
        "definatly" to "definitely",

        // --- Necessary variants ---
        "necesary" to "necessary",
        "neccessary" to "necessary",
        "neccesary" to "necessary",
        "nessessary" to "necessary",
        "unneccesary" to "unnecessary",
        "unecessary" to "unnecessary",

        // --- Successful variants ---
        "succesful" to "successful",
        "successfull" to "successful",
        "sucessful" to "successful",
        "sucsessful" to "successful",

        // --- Calendar/Schedule variants ---
        "calender" to "calendar",
        "calandar" to "calendar",
        "calander" to "calendar",
        "schedual" to "schedule",
        "scheudle" to "schedule",
        "scedule" to "schedule",
        "shedule" to "schedule",

        // --- Business/Committee ---
        "buisness" to "business",
        "busines" to "business",
        "bussiness" to "business",
        "comittee" to "committee",
        "commitee" to "committee",
        "committe" to "committee",
        "comitee" to "committee",

        // --- Friend variants ---
        "freind" to "friend",
        "freinds" to "friends",
        "freindly" to "friendly",

        // --- Independent/Privilege ---
        "independant" to "independent",
        "indepedent" to "independent",
        "independance" to "independence",
        "privelege" to "privilege",
        "priviledge" to "privilege",
        "priveledge" to "privilege",

        // --- Recommend variants ---
        "reccomend" to "recommend",
        "recomend" to "recommend",
        "recomment" to "recommend",
        "recomended" to "recommended",

        // --- Experience/Existence ---
        "experiance" to "experience",
        "experence" to "experience",
        "existance" to "existence",
        "exsistence" to "existence",

        // --- Available/Immediate ---
        "availabe" to "available",
        "avalible" to "available",
        "avaliable" to "available",
        "imediate" to "immediate",
        "immediat" to "immediate",
        "immidiate" to "immediate",
        "imediately" to "immediately",

        // --- Maintenance variants ---
        "maintainance" to "maintenance",
        "maintanence" to "maintenance",
        "maintenence" to "maintenance",

        // --- Restaurant ---
        "restuarant" to "restaurant",
        "restraunt" to "restaurant",
        "restaraunt" to "restaurant",
        "resteraunt" to "restaurant",

        // --- Questionnaire ---
        "questionaire" to "questionnaire",
        "questionnare" to "questionnaire",
        "questionare" to "questionnaire",

        // --- Common short words ---
        "becuase" to "because",
        "becase" to "because",
        "beacuse" to "because",
        "throgh" to "through",
        "throught" to "through",
        "thourough" to "thorough",
        "althought" to "although",
        "althogh" to "although",
        "realy" to "really",
        "relly" to "really",
        "untill" to "until",
        "finaly" to "finally",
        "basicaly" to "basically",
        "actualy" to "actually",
        "usualy" to "usually",

        // --- Opportunity ---
        "oportunity" to "opportunity",
        "oppurtunity" to "opportunity",
        "oppertunity" to "opportunity",

        // --- Consensus ---
        "concensus" to "consensus",
        "consesus" to "consensus",

        // --- February/Wednesday (calendar-related) ---
        "Febuary" to "February",
        "Februrary" to "February",
        "Febrary" to "February",
        "Wendsday" to "Wednesday",
        "Wednsday" to "Wednesday",
        "Wensday" to "Wednesday",
        "Janurary" to "January",
        "Setpember" to "September",
        "Augst" to "August",

        // ─────────────────────────────────────────────────────────────────────────
        // Tech/Development terms (common in notes)
        // ─────────────────────────────────────────────────────────────────────────
        "databse" to "database",
        "datbase" to "database",
        "configration" to "configuration",
        "configruation" to "configuration",
        "coniguration" to "configuration",
        "authenitcation" to "authentication",
        "authentiation" to "authentication",
        "auhthentication" to "authentication",
        "paramater" to "parameter",
        "parmeter" to "parameter",
        "paramter" to "parameter",
        "developement" to "development",
        "develpment" to "development",
        "devlopment" to "development",
        "apliccation" to "application",
        "aplication" to "application",
        "appplication" to "application",
        "applcation" to "application",
        "performence" to "performance",
        "perfromance" to "performance",
        "perfomance" to "performance",
        "algorithim" to "algorithm",
        "alogrithm" to "algorithm",
        "algorhythm" to "algorithm",
        "enviroment" to "environment",
        "enviornment" to "environment",

        // ─────────────────────────────────────────────────────────────────────────
        // Handwriting-specific errors (rushed/cursive writing on e-ink)
        // ─────────────────────────────────────────────────────────────────────────

        // a ↔ o when written quickly
        "appaintment" to "appointment",
        "oppoint" to "appoint",
        "appoont" to "appoint",
        "appoitment" to "appointment",

        // e ↔ i confusion
        "meating" to "meeting",
        "meetting" to "meeting",
        "meeteng" to "meeting",

        // n ↔ u in cursive (major handwriting issue)
        "bnsiness" to "business",
        "rnnutual" to "mutual",
        "commuuity" to "community",
        "communty" to "community",
        "opportnnity" to "opportunity",

        // Missing letters from fast writing
        "meetng" to "meeting",
        "callig" to "calling",
        "caling" to "calling",
        "importnt" to "important",
        "diffrent" to "different",
        "differnt" to "different",
        "intresting" to "interesting",
        "probly" to "probably",
        "probbly" to "probably",
        "prolly" to "probably",

        // ll ↔ h confusion (two tall strokes merge)
        "wih" to "will",
        "cahing" to "calling",

        // ri ↔ n confusion (cursive)
        "fnend" to "friend",
        "pnnt" to "print",
        "bnng" to "bring",

        // er ↔ n confusion (tight cursive)
        "nevn" to "never",
        "ovn" to "over",
        "undn" to "under",

        // oo ↔ u collapse
        "buk" to "book",
        "luk" to "look",
        "tuk" to "took",

        // Shorthand expansions (common in handwritten notes)
        "w/" to "with",
        "w/o" to "without",
        "b/c" to "because",
        "f/u" to "follow-up",
        "mtg" to "meeting",
        "appt" to "appointment",
        "msg" to "message"
    )

    /**
     * Artifact cleaning patterns applied before tokenization.
     * These clean common OCR errors that hurt classification.
     */
    private val ARTIFACT_PATTERNS = listOf(
        // Rejoin hyphenated words split across lines
        Regex("(\\w+)-\\s*\\n\\s*(\\w+)") to "$1$2",

        // Collapse repeated punctuation
        Regex("[!]{2,}") to "!",
        Regex("[?]{2,}") to "?",
        Regex("[.]{4,}") to "...",

        // Remove zero-width characters
        Regex("[\\u200B-\\u200D\\uFEFF]") to "",

        // Collapse multiple spaces/tabs to single space
        Regex("[ \\t]{2,}") to " "

        // NOTE: DO NOT add single-character removal - it breaks valid "I" tokens
    )

    /**
     * Apply direct substitutions to text.
     */
    fun applyDirectSubstitutions(text: String): String {
        var result = text
        for ((wrong, correct) in DIRECT_SUBSTITUTIONS) {
            // Case-insensitive replacement preserving original case pattern
            result = result.replace(Regex("(?i)\\b$wrong\\b")) { match ->
                preserveCase(match.value, correct)
            }
        }
        return result
    }

    private fun preserveCase(original: String, replacement: String): String {
        if (original.isEmpty() || replacement.isEmpty()) return replacement
        return when {
            original.all { it.isUpperCase() } -> replacement.uppercase()
            original[0].isUpperCase() -> replacement.replaceFirstChar { it.uppercase() }
            else -> replacement.lowercase()
        }
    }

    /**
     * Clean OCR artifacts that hurt tokenization.
     * Applied BEFORE direct substitutions.
     */
    fun cleanArtifacts(text: String): String {
        var result = text

        // Apply regex patterns
        for ((pattern, replacement) in ARTIFACT_PATTERNS) {
            result = pattern.replace(result, replacement)
        }

        // Deduplicate identical lines (keep first occurrence)
        val lines = result.lines()
        val seen = mutableSetOf<String>()
        val deduped = lines.filter { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) true  // Keep blank lines
            else seen.add(trimmed)  // Returns false if already seen
        }

        return deduped.joinToString("\n").trim()
    }

    /**
     * Full OCR text correction pipeline.
     * 1. Clean artifacts (hyphenation, punctuation, whitespace)
     * 2. Apply direct substitutions (rn→m, l→1, etc.)
     */
    fun correct(text: String): String {
        // Step 1: Clean artifacts
        val cleaned = cleanArtifacts(text)

        // Step 2: Apply direct substitutions (existing method)
        return applyDirectSubstitutions(cleaned)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STAGE 2: Weighted Edit Distance with Confusion Matrix
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * OCR confusion costs - common confusions cost less than random edits.
     * Lower cost = more likely OCR error, easier to match.
     *
     * Enhanced Jan 2026: Added handwriting-specific confusions common on e-ink tablets.
     */
    private val CONFUSION_COSTS = mapOf(
        // ─────────────────────────────────────────────────────────────────────────
        // TIER 1: Very common OCR confusions (cost 0.3)
        // These are nearly interchangeable in many fonts/OCR systems
        // ─────────────────────────────────────────────────────────────────────────
        ('o' to '0') to 0.3f,
        ('0' to 'o') to 0.3f,
        ('l' to '1') to 0.3f,
        ('1' to 'l') to 0.3f,
        ('l' to 'i') to 0.3f,
        ('i' to 'l') to 0.3f,
        ('i' to '1') to 0.3f,
        ('1' to 'i') to 0.3f,
        ('i' to 'j') to 0.4f,
        ('j' to 'i') to 0.4f,

        // ─────────────────────────────────────────────────────────────────────────
        // TIER 2: Common OCR/font confusions (cost 0.5)
        // ─────────────────────────────────────────────────────────────────────────
        ('n' to 'h') to 0.5f,
        ('h' to 'n') to 0.5f,
        ('c' to 'e') to 0.5f,
        ('e' to 'c') to 0.5f,
        ('u' to 'v') to 0.5f,
        ('v' to 'u') to 0.5f,
        ('a' to 'o') to 0.5f,
        ('o' to 'a') to 0.5f,
        ('g' to 'q') to 0.5f,
        ('q' to 'g') to 0.5f,
        ('s' to '5') to 0.5f,
        ('5' to 's') to 0.5f,
        ('b' to '6') to 0.5f,
        ('6' to 'b') to 0.5f,
        // Additional common (added Jan 2026)
        ('e' to 'a') to 0.5f,  // handwriting: short e looks like a
        ('a' to 'e') to 0.5f,
        ('d' to 'o') to 0.5f,  // handwriting: incomplete d
        ('o' to 'd') to 0.5f,
        ('p' to 'q') to 0.5f,  // mirror images
        ('q' to 'p') to 0.5f,
        ('b' to 'd') to 0.5f,  // mirror images
        ('d' to 'b') to 0.5f,

        // ─────────────────────────────────────────────────────────────────────────
        // TIER 3: Moderate confusions (cost 0.6)
        // Handwriting-specific - common on e-ink stylus input
        // ─────────────────────────────────────────────────────────────────────────
        ('n' to 'u') to 0.6f,  // Cursive handwriting: n and u nearly identical
        ('u' to 'n') to 0.6f,
        ('m' to 'n') to 0.6f,  // Missing hump
        ('n' to 'm') to 0.6f,
        ('w' to 'v') to 0.6f,  // Quick writing
        ('v' to 'w') to 0.6f,
        ('e' to 'i') to 0.6f,  // Quick/sloppy e
        ('i' to 'e') to 0.6f,
        ('y' to 'g') to 0.6f,  // Descender confusion
        ('g' to 'y') to 0.6f,
        ('t' to 'l') to 0.6f,  // Missing crossbar
        ('l' to 't') to 0.6f,
        ('a' to 'u') to 0.6f,  // Open/closed tops
        ('u' to 'a') to 0.6f,

        // ─────────────────────────────────────────────────────────────────────────
        // TIER 4: Lower-probability confusions (cost 0.7)
        // ─────────────────────────────────────────────────────────────────────────
        ('f' to 't') to 0.7f,
        ('t' to 'f') to 0.7f,
        ('r' to 'n') to 0.7f,
        ('n' to 'r') to 0.7f,
        // Additional moderate (added Jan 2026)
        ('s' to 'z') to 0.7f,  // Sloppy z/s
        ('z' to 's') to 0.7f,
        ('k' to 'h') to 0.7f,  // Missing leg
        ('h' to 'k') to 0.7f,
        ('c' to 'o') to 0.7f,  // Closed c
        ('o' to 'c') to 0.7f,
        ('r' to 'v') to 0.7f,  // Stylus: angled r looks like v
        ('v' to 'r') to 0.7f,
        ('p' to 'b') to 0.7f,  // Ascender/descender
        ('b' to 'p') to 0.7f,

        // ─────────────────────────────────────────────────────────────────────────
        // TIER 5: Phonetic/typing adjacency confusions (cost 0.8)
        // Common when user types from speech or makes keyboard errors
        // ─────────────────────────────────────────────────────────────────────────
        ('d' to 't') to 0.8f,  // Voiced/unvoiced pairs
        ('t' to 'd') to 0.8f,
        ('g' to 'k') to 0.8f,
        ('k' to 'g') to 0.8f,
        ('v' to 'f') to 0.8f,
        ('f' to 'v') to 0.8f,
        ('s' to 'c') to 0.8f,  // Same sound context
        ('c' to 's') to 0.8f,

        // ─────────────────────────────────────────────────────────────────────────
        // TIER 6: Additional digit confusions (cost 0.5-0.7)
        // Common in handwritten dates, numbers, and mixed content
        // ─────────────────────────────────────────────────────────────────────────
        ('2' to 'z') to 0.5f,  // Shape similarity
        ('z' to '2') to 0.5f,
        ('3' to 'e') to 0.6f,  // Reversed E
        ('e' to '3') to 0.6f,
        ('7' to 't') to 0.6f,  // European 7 with crossbar
        ('t' to '7') to 0.6f,
        ('7' to '1') to 0.5f,  // Handwriting
        ('1' to '7') to 0.5f,
        ('8' to 'b') to 0.6f,  // Shape similarity
        ('b' to '8') to 0.6f,
        ('9' to 'g') to 0.5f,  // Shape similarity
        ('g' to '9') to 0.5f,
        ('9' to 'q') to 0.5f,  // Descender confusion
        ('q' to '9') to 0.5f,
        ('4' to 'a') to 0.7f,  // Handwritten 4
        ('a' to '4') to 0.7f,

        // ─────────────────────────────────────────────────────────────────────────
        // TIER 7: Keyboard proximity errors (cost 0.8)
        // Common when typing quickly on physical or virtual keyboards
        // ─────────────────────────────────────────────────────────────────────────
        ('e' to 'r') to 0.8f,
        ('r' to 'e') to 0.8f,
        ('i' to 'o') to 0.8f,
        ('o' to 'i') to 0.8f,
        ('n' to 'm') to 0.8f,
        // m↔n already at 0.6f above
        ('u' to 'y') to 0.8f,
        ('y' to 'u') to 0.8f,
        ('t' to 'y') to 0.8f,
        ('y' to 't') to 0.8f,
        ('a' to 's') to 0.8f,
        ('s' to 'a') to 0.8f,
        ('d' to 'f') to 0.8f,
        ('f' to 'd') to 0.8f,
        ('j' to 'k') to 0.8f,
        ('k' to 'j') to 0.8f
    )

    /**
     * Multi-character OCR confusions.
     *
     * These handle cases where multiple characters merge into one or vice versa.
     * Enhanced Jan 2026 with comprehensive handwriting patterns.
     */
    private val MULTI_CHAR_CONFUSIONS = mapOf(
        // ─────────────────────────────────────────────────────────────────────────
        // Classic OCR confusions (rn/m, vv/w, cl/d)
        // ─────────────────────────────────────────────────────────────────────────
        "rn" to "m",
        "m" to "rn",
        "vv" to "w",
        "w" to "vv",
        "cl" to "d",
        "d" to "cl",
        "li" to "h",
        "h" to "li",
        "ii" to "n",
        "n" to "ii",

        // ─────────────────────────────────────────────────────────────────────────
        // Handwriting "hump" errors (#1 handwriting OCR problem)
        // ─────────────────────────────────────────────────────────────────────────
        "nn" to "m",    // Two n's merge into m
        "m" to "nn",
        "in" to "m",    // Tight "in" looks like m
        "m" to "in",
        "ni" to "m",    // Reversed
        "iii" to "m",   // Three strokes = m

        // ─────────────────────────────────────────────────────────────────────────
        // Double-stroke errors (ll, tt patterns)
        // ─────────────────────────────────────────────────────────────────────────
        "ll" to "h",    // Two tall strokes read as h
        "h" to "ll",
        "tt" to "ll",   // Crossbars vanish
        "ll" to "tt",
        "ff" to "ll",   // f stems become l stems
        "ll" to "ff",

        // ─────────────────────────────────────────────────────────────────────────
        // Cursive ligature errors (ri, er patterns)
        // ─────────────────────────────────────────────────────────────────────────
        "ri" to "n",    // Cursive ri close together
        "n" to "ri",
        "er" to "n",    // Tight "er" looks like n
        "n" to "er",
        "ir" to "n",    // Tight ir
        "ch" to "h",    // c disappears
        "h" to "ch",
        "th" to "h",    // t cross lost
        "h" to "th",

        // ─────────────────────────────────────────────────────────────────────────
        // Double letter collapse/expansion (oo, ee, ss)
        // ─────────────────────────────────────────────────────────────────────────
        "oo" to "u",    // book → buk
        "u" to "oo",
        "ee" to "e",    // need → ned (collapsed)
        "ss" to "s",    // miss → mis
        "uu" to "w",    // Double u = w (literally)
        "w" to "uu",
        "iu" to "w",    // Stylus: iu looks like w
        "w" to "iu",

        // ─────────────────────────────────────────────────────────────────────────
        // Ligatures and special characters
        // ─────────────────────────────────────────────────────────────────────────
        "ae" to "æ",
        "æ" to "ae",
        "oe" to "œ",
        "œ" to "oe",
        "fi" to "fi",   // fi ligature (unicode)
        "fl" to "fl"    // fl ligature (unicode)
    )

    /**
     * Compute weighted edit distance considering OCR confusions.
     *
     * Enhanced Jan 2026:
     * - Damerau-Levenshtein transposition support (swapped adjacent letters cost 0.5)
     * - Bi-directional multi-character confusion matching
     * - Single transposition detection (fast path)
     *
     * @param s1 First string (from OCR)
     * @param s2 Second string (dictionary word)
     * @return Weighted edit distance (lower = more similar)
     */
    fun weightedEditDistance(s1: String, s2: String): Float {
        val a = s1.lowercase()
        val b = s2.lowercase()

        if (a == b) return 0f
        if (a.isEmpty()) return b.length.toFloat()
        if (b.isEmpty()) return a.length.toFloat()

        // Fast path: Check for single transposition (very common typo)
        // "hte" -> "the", "recieve" -> "receive"
        if (a.length == b.length && a.length >= 2) {
            val transpositionDistance = checkSingleTransposition(a, b)
            if (transpositionDistance != null) return transpositionDistance
        }

        // Check multi-character confusions (both directions)
        for ((pattern, replacement) in MULTI_CHAR_CONFUSIONS) {
            // Forward: a contains pattern -> compare with b
            if (a.contains(pattern)) {
                val expanded = a.replace(pattern, replacement)
                if (expanded == b) return 0.3f
            }
            // Reverse: b contains pattern -> compare with a
            if (b.contains(pattern)) {
                val expanded = b.replace(pattern, replacement)
                if (expanded == a) return 0.3f
            }
        }

        // Damerau-Levenshtein with weighted costs
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { FloatArray(n + 1) }

        for (i in 0..m) dp[i][0] = i.toFloat()
        for (j in 0..n) dp[0][j] = j.toFloat()

        for (i in 1..m) {
            for (j in 1..n) {
                val charA = a[i - 1]
                val charB = b[j - 1]

                val cost = if (charA == charB) {
                    0f
                } else {
                    // Check if this is a common OCR confusion
                    CONFUSION_COSTS[charA to charB] ?: 1.0f
                }

                var minDist = min(
                    min(
                        dp[i - 1][j] + 1.0f,      // Deletion
                        dp[i][j - 1] + 1.0f       // Insertion
                    ),
                    dp[i - 1][j - 1] + cost       // Substitution (weighted)
                )

                // Damerau transposition: check if swapping adjacent chars helps
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    minDist = min(minDist, dp[i - 2][j - 2] + 0.5f)  // Transposition cost
                }

                dp[i][j] = minDist
            }
        }

        return dp[m][n]
    }

    /**
     * Fast check for single adjacent transposition.
     * Returns weighted distance if found, null otherwise.
     *
     * Examples: "hte" -> "the" (cost 0.5), "recieve" -> "receive" (cost 0.5)
     */
    private fun checkSingleTransposition(a: String, b: String): Float? {
        if (a.length != b.length) return null

        var diffCount = 0
        var firstDiff = -1

        for (i in a.indices) {
            if (a[i] != b[i]) {
                diffCount++
                if (diffCount == 1) firstDiff = i
                if (diffCount > 2) return null  // More than one transposition
            }
        }

        // Check if it's exactly a transposition
        if (diffCount == 2 && firstDiff < a.length - 1) {
            if (a[firstDiff] == b[firstDiff + 1] && a[firstDiff + 1] == b[firstDiff]) {
                return 0.5f  // Single transposition
            }
        }

        return null
    }

    /**
     * Check if a word fuzzy-matches a dictionary term.
     *
     * @param ocrWord Word from OCR (potentially garbled)
     * @param dictWord Dictionary word to match against
     * @param threshold Maximum weighted distance (default: adaptive based on length)
     * @return true if words are close enough
     */
    fun fuzzyMatches(ocrWord: String, dictWord: String, threshold: Float? = null): Boolean {
        val effectiveThreshold = threshold ?: when (dictWord.length) {
            in 1..4 -> 0.8f    // Short words: strict
            in 5..8 -> 1.5f    // Medium: moderate
            else -> 2.2f       // Long words: lenient
        }

        return weightedEditDistance(ocrWord, dictWord) <= effectiveThreshold
    }

    /**
     * Find the best match for an OCR word from a list of dictionary words.
     *
     * @param ocrWord Word from OCR
     * @param dictWords Dictionary words to match against
     * @param maxDistance Maximum allowed distance
     * @return Best matching word and its distance, or null if no match
     */
    fun findBestMatch(
        ocrWord: String,
        dictWords: List<String>,
        maxDistance: Float = 2.0f
    ): Pair<String, Float>? {
        var bestMatch: String? = null
        var bestDistance = Float.MAX_VALUE

        for (dictWord in dictWords) {
            val distance = weightedEditDistance(ocrWord, dictWord)
            if (distance < bestDistance && distance <= maxDistance) {
                bestDistance = distance
                bestMatch = dictWord
            }
        }

        return bestMatch?.let { it to bestDistance }
    }
}

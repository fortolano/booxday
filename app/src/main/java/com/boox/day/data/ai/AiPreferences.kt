package com.boox.day.data.ai

import kotlinx.serialization.Serializable

/**
 * User preferences for AI-driven Smart Suggestions personalization.
 * Stored via KvProxy in the Kv table.
 *
 * These preferences allow the AI to understand the user holistically and
 * generate more relevant, personalized task suggestions.
 */
@Serializable
data class AiPreferences(
    // ========== Life Priorities (1-5 scale) ==========
    // Higher values mean more suggestions related to that area
    val workPriority: Int = 3,
    val educationPriority: Int = 3,
    val familyPriority: Int = 3,
    val healthPriority: Int = 3,
    val socialPriority: Int = 3,
    val hobbyPriority: Int = 3,
    val financePriority: Int = 3,
    val homePriority: Int = 3,
    val growthPriority: Int = 3,

    // ========== About Me ==========
    val lifeSituations: Set<LifeSituation> = setOf(LifeSituation.WORKING_PROFESSIONAL),
    val energyPattern: EnergyPattern = EnergyPattern.STEADY,
    val livingEnvironment: LivingEnvironment = LivingEnvironment.SUBURBAN,
    val industry: Industry = Industry.NOT_SET,
    val profession: Profession = Profession.NOT_SET,

    // ========== Lifestyle Vibes (multi-select) ==========
    val isActiveLifestyle: Boolean = false,
    val isWeekendWarrior: Boolean = false,
    val isHomebody: Boolean = false,
    val isSocialButterfly: Boolean = false,
    val isQuietIntrovert: Boolean = false,
    val isOutdoorsLover: Boolean = false,
    val isFoodie: Boolean = false,
    val isDigitalNative: Boolean = false,

    // ========== Situation Flags ==========
    val hasKids: Boolean = false,
    val hasPets: Boolean = false,
    val isCareGiver: Boolean = false,
    val livesAlone: Boolean = false,

    // ========== Interests ==========
    val interests: Set<Interest> = emptySet(),

    // ========== AI Behavior Settings ==========
    val suggestionCount: SuggestionCount = SuggestionCount.MODERATE,
    val suggestionStyle: SuggestionStyle = SuggestionStyle.BALANCED,
    val includeHealthReminders: Boolean = true,
    val includeSocialReminders: Boolean = true,
    val includeDeadlineWarnings: Boolean = true,

    // ========== Special Modes ==========
    val vacationMode: Boolean = false,
    val crunchMode: Boolean = false,
    val holidayMode: Boolean = false,

    // ========== Daily Rhythm (NEW) ==========
    val planningHorizon: PlanningHorizon = PlanningHorizon.DAY_BY_DAY,
    val focusStyle: FocusStyle = FocusStyle.MIXED,
    val sleepSchedule: SleepSchedule = SleepSchedule.FLEXIBLE,
    val commuteStyle: CommuteStyle = CommuteStyle.HYBRID,
    val routinePreference: RoutinePreference = RoutinePreference.BALANCED,
    val stressResponse: StressResponse = StressResponse.ORGANIZE,

    // ========== Meta ==========
    val setupComplete: Boolean = false,
    val lastUpdated: Long = 0L
) {
    companion object {
        /**
         * Returns a summary of priorities for prompt building.
         * Only includes priorities that differ from the default (3).
         */
        fun AiPreferences.getPrioritySummary(): String {
            val priorities = mutableListOf<String>()
            if (workPriority != 3) priorities.add("work: ${priorityLabel(workPriority)}")
            if (educationPriority != 3) priorities.add("education: ${priorityLabel(educationPriority)}")
            if (familyPriority != 3) priorities.add("family: ${priorityLabel(familyPriority)}")
            if (healthPriority != 3) priorities.add("health: ${priorityLabel(healthPriority)}")
            if (socialPriority != 3) priorities.add("social: ${priorityLabel(socialPriority)}")
            if (hobbyPriority != 3) priorities.add("hobbies: ${priorityLabel(hobbyPriority)}")
            if (financePriority != 3) priorities.add("finance: ${priorityLabel(financePriority)}")
            if (homePriority != 3) priorities.add("home: ${priorityLabel(homePriority)}")
            if (growthPriority != 3) priorities.add("personal growth: ${priorityLabel(growthPriority)}")
            return if (priorities.isEmpty()) "balanced priorities" else priorities.joinToString(", ")
        }

        private fun priorityLabel(priority: Int): String = when (priority) {
            1 -> "very low"
            2 -> "low"
            3 -> "medium"
            4 -> "high"
            5 -> "very high"
            else -> "medium"
        }

        /**
         * Returns a summary of lifestyle flags for prompt building.
         */
        fun AiPreferences.getLifestyleSummary(): String {
            val traits = mutableListOf<String>()
            if (isActiveLifestyle) traits.add("active lifestyle")
            if (isWeekendWarrior) traits.add("weekend warrior")
            if (isHomebody) traits.add("homebody")
            if (isSocialButterfly) traits.add("social butterfly")
            if (isQuietIntrovert) traits.add("quiet introvert")
            if (isOutdoorsLover) traits.add("outdoors lover")
            if (isFoodie) traits.add("foodie")
            if (isDigitalNative) traits.add("digital native")
            return if (traits.isEmpty()) "" else traits.joinToString(", ")
        }

        /**
         * Returns a summary of industry/profession for prompt building.
         */
        fun AiPreferences.getWorkProfileSummary(): String {
            val parts = mutableListOf<String>()
            if (industry != Industry.NOT_SET) parts.add(industry.displayName)
            if (profession != Profession.NOT_SET) parts.add(profession.displayName)
            return if (parts.isEmpty()) "" else parts.joinToString(", ")
        }

        /**
         * Returns a summary of situation flags for prompt building.
         */
        fun AiPreferences.getSituationSummary(): String {
            val situations = mutableListOf<String>()
            if (hasKids) situations.add("has children")
            if (hasPets) situations.add("has pets")
            if (isCareGiver) situations.add("caregiver")
            if (livesAlone) situations.add("lives alone")
            return if (situations.isEmpty()) "" else situations.joinToString(", ")
        }

        /**
         * Returns a summary of daily rhythm preferences for prompt building.
         */
        fun AiPreferences.getDailyRhythmSummary(): String {
            val traits = mutableListOf<String>()
            traits.add("plans ${planningHorizon.displayName.lowercase()}")
            traits.add("${focusStyle.displayName.lowercase()} work style")
            traits.add("${sleepSchedule.displayName.lowercase()}")
            traits.add("${commuteStyle.displayName.lowercase()} work arrangement")
            traits.add("${routinePreference.displayName.lowercase()} routine")
            traits.add("when stressed: ${stressResponse.displayName.lowercase()}")
            return traits.joinToString(", ")
        }
    }
}

/**
 * User's current life situation/role.
 */
@Serializable
enum class LifeSituation(val displayName: String, val description: String) {
    STUDENT("Student", "Focused on education and learning"),
    WORKING_PROFESSIONAL("Working Professional", "Career-focused with regular work schedule"),
    FREELANCER("Freelancer/Self-Employed", "Flexible schedule, self-managed work"),
    STAY_AT_HOME_PARENT("Stay-at-Home Parent", "Primary focus on family and home"),
    RETIRED("Retired", "Free from work obligations"),
    BETWEEN_JOBS("Between Jobs", "Currently seeking new opportunities"),
    PART_TIME("Part-Time Worker", "Working reduced hours"),
    ENTREPRENEUR("Entrepreneur", "Building or running own business")
}

/**
 * User's energy pattern throughout the day.
 */
@Serializable
enum class EnergyPattern(val displayName: String, val description: String) {
    MORNING_PERSON("Morning Person", "Most productive in early hours"),
    NIGHT_OWL("Night Owl", "Most productive in evening/night"),
    STEADY("Steady Throughout", "Consistent energy all day"),
    AFTERNOON_PEAK("Afternoon Peak", "Most productive mid-day")
}

/**
 * User's living environment.
 */
@Serializable
enum class LivingEnvironment(val displayName: String, val description: String) {
    URBAN("Urban/City", "Fast-paced city life"),
    SUBURBAN("Suburban", "Balance of access and space"),
    RURAL("Rural/Countryside", "Quiet, nature-focused living"),
    REMOTE("Remote/Off-Grid", "Minimal access to urban amenities")
}

/**
 * User's industry for personalization.
 */
@Serializable
enum class Industry(val displayName: String) {
    NOT_SET("Not set"),
    TECHNOLOGY("Technology and Software"),
    HEALTHCARE("Healthcare and Medical"),
    EDUCATION("Education and Training"),
    FINANCE("Finance and Insurance"),
    GOVERNMENT("Government and Public Sector"),
    RETAIL("Retail and Consumer Services"),
    MANUFACTURING("Manufacturing and Industrial"),
    CONSTRUCTION_REAL_ESTATE("Construction and Real Estate"),
    MEDIA("Media and Entertainment"),
    PROFESSIONAL_SERVICES("Professional Services"),
    HOSPITALITY("Hospitality and Travel"),
    NONPROFIT("Nonprofit and Community"),
    OTHER("Other or Not listed")
}

/**
 * User's profession for personalization.
 */
@Serializable
enum class Profession(val displayName: String) {
    NOT_SET("Not set"),
    ENGINEERING_IT("Engineering and IT"),
    PRODUCT_PROJECT("Product or Project Management"),
    DESIGN_CREATIVE("Design and Creative"),
    MARKETING_COMMUNICATIONS("Marketing and Communications"),
    SALES_BUSINESS_DEV("Sales and Business Development"),
    OPERATIONS_ADMIN("Operations and Administration"),
    FINANCE_ACCOUNTING("Finance and Accounting"),
    HR_PEOPLE_OPS("HR and People Ops"),
    HEALTHCARE_CLINICAL("Healthcare and Clinical"),
    EDUCATION_RESEARCH("Education, Research, and Teaching"),
    LEGAL_COMPLIANCE("Legal and Compliance"),
    ENTREPRENEUR("Entrepreneur or Founder"),
    STUDENT("Student"),
    OTHER("Other or Not listed")
}

/**
 * User interests for personalized suggestions.
 */
@Serializable
enum class Interest(val displayName: String, val category: InterestCategory) {
    // Creative
    READING("Reading", InterestCategory.CREATIVE),
    WRITING("Writing", InterestCategory.CREATIVE),
    ART("Art & Drawing", InterestCategory.CREATIVE),
    MUSIC("Music", InterestCategory.CREATIVE),
    PHOTOGRAPHY("Photography", InterestCategory.CREATIVE),
    CRAFTS("Crafts & DIY", InterestCategory.CREATIVE),

    // Physical
    FITNESS("Fitness & Exercise", InterestCategory.PHYSICAL),
    YOGA("Yoga & Meditation", InterestCategory.PHYSICAL),
    SPORTS("Sports", InterestCategory.PHYSICAL),
    HIKING("Hiking & Nature", InterestCategory.PHYSICAL),
    CYCLING("Cycling", InterestCategory.PHYSICAL),

    // Intellectual
    TECHNOLOGY("Technology & Gadgets", InterestCategory.INTELLECTUAL),
    SCIENCE("Science", InterestCategory.INTELLECTUAL),
    LANGUAGES("Language Learning", InterestCategory.INTELLECTUAL),
    HISTORY("History", InterestCategory.INTELLECTUAL),
    PODCASTS("Podcasts", InterestCategory.INTELLECTUAL),

    // Social
    COOKING("Cooking & Baking", InterestCategory.SOCIAL),
    GARDENING("Gardening", InterestCategory.SOCIAL),
    TRAVEL("Travel", InterestCategory.SOCIAL),
    VOLUNTEERING("Volunteering", InterestCategory.SOCIAL),
    GAMING("Gaming", InterestCategory.SOCIAL),

    // Professional
    INVESTING("Investing & Finance", InterestCategory.PROFESSIONAL),
    CAREER_GROWTH("Career Development", InterestCategory.PROFESSIONAL),
    NETWORKING("Networking", InterestCategory.PROFESSIONAL),
    SIDE_PROJECTS("Side Projects", InterestCategory.PROFESSIONAL)
}

/**
 * Categories for grouping interests in UI.
 */
@Serializable
enum class InterestCategory(val displayName: String) {
    CREATIVE("Creative"),
    PHYSICAL("Physical & Active"),
    INTELLECTUAL("Intellectual"),
    SOCIAL("Social & Lifestyle"),
    PROFESSIONAL("Professional")
}

/**
 * How many suggestions the user wants to receive.
 */
@Serializable
enum class SuggestionCount(val displayName: String, val count: Int) {
    MINIMAL("Minimal (3-4)", 4),
    MODERATE("Moderate (5-7)", 6),
    COMPREHENSIVE("Comprehensive (8-10)", 9)
}

/**
 * The tone/style of AI suggestions.
 */
@Serializable
enum class SuggestionStyle(val displayName: String, val description: String) {
    PRACTICAL("Practical", "Focus on concrete, actionable tasks"),
    BALANCED("Balanced", "Mix of practical tasks and wellness reminders"),
    HOLISTIC("Holistic", "Include self-care, reflection, and work-life balance")
}

// ═══════════════════════════════════════════════════════════════════════════
// DAILY RHYTHM ENUMS (NEW)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * How far ahead the user typically plans.
 */
@Serializable
enum class PlanningHorizon(val displayName: String, val description: String) {
    DAY_BY_DAY("Day by Day", "Focus on today, plan tomorrow when it comes"),
    WEEKLY("Weekly View", "Plan the week ahead on Sunday/Monday"),
    MONTHLY("Monthly Goals", "Set monthly objectives, break into weeks")
}

/**
 * How the user prefers to work on tasks.
 */
@Serializable
enum class FocusStyle(val displayName: String, val description: String) {
    DEEP_FOCUS("Deep Focus", "Long uninterrupted blocks for complex work"),
    TASK_SWITCHING("Task Switching", "Jump between tasks frequently"),
    MIXED("Mixed", "Deep focus for some tasks, switching for others")
}

/**
 * User's typical sleep pattern.
 */
@Serializable
enum class SleepSchedule(val displayName: String, val description: String) {
    EARLY_BIRD("Early Bird", "Early to bed, early to rise"),
    NIGHT_OWL("Night Owl", "Late nights, later mornings"),
    FLEXIBLE("Flexible", "Varies based on circumstances")
}

/**
 * User's typical commute/work location situation.
 */
@Serializable
enum class CommuteStyle(val displayName: String, val description: String) {
    REMOTE("Fully Remote", "Work from home every day"),
    COMMUTE("Daily Commute", "Go to office/workplace daily"),
    HYBRID("Hybrid", "Mix of remote and in-person"),
    VARIES("Varies", "Different every week")
}

/**
 * User's preference for routine vs spontaneity.
 */
@Serializable
enum class RoutinePreference(val displayName: String, val description: String) {
    STRUCTURED("Structured", "I thrive on consistent routines"),
    SPONTANEOUS("Spontaneous", "I prefer flexibility and variety"),
    BALANCED("Balanced", "Routine for some things, flexible for others")
}

/**
 * How the user responds to stress/pressure.
 */
@Serializable
enum class StressResponse(val displayName: String, val description: String) {
    ORGANIZE("Get Organized", "Make lists, break things down, tackle it"),
    DECOMPRESS("Take Breaks", "Step back, breathe, then return refreshed"),
    PUSH_THROUGH("Push Through", "Power through until it's done")
}

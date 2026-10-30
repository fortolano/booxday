package com.boox.day.data.ai

import kotlinx.serialization.Serializable

/**
 * The AI options (AI Settings → AI options), stored via KvProxy in the Kv table. Since 29 Oct 2026
 * only suggestionCount, vacationMode, includeHealthReminders and includeSocialReminders are used
 * ([keptOptions][AiPreferences.Companion.keptOptions]); the other fields are from the removed AI
 * Preferences questions and are kept only so older saved data loads.
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
         * The AI options BooxDay still uses (29 Oct 2026). A measured test of every AI Preferences
         * answer (252 AI answers) found only these change what the AI says: how many suggestions,
         * Vacation, and the Health and Social suggestion switches. The owner chose to keep these
         * three and remove the rest, so every other stored answer is dropped here and never reaches
         * a prompt or a score. The other fields stay in the class only so older saved data still loads.
         */
        fun AiPreferences?.keptOptions(): AiPreferences = AiPreferences(
            suggestionCount = this?.suggestionCount ?: SuggestionCount.MODERATE,
            includeHealthReminders = this?.includeHealthReminders ?: true,
            includeSocialReminders = this?.includeSocialReminders ?: true,
            vacationMode = this?.vacationMode ?: false,
            setupComplete = true,
            lastUpdated = this?.lastUpdated ?: 0L
        )

        /** The preferences to put in AI requests: only the kept options ([keptOptions]). */
        fun AiPreferences?.forPrompt(): AiPreferences = keptOptions()

        /** The one special mode in force: Vacation, the only mode kept (29 Oct 2026). */
        fun AiPreferences.activeMode(): SpecialMode? = if (vacationMode) SpecialMode.VACATION else null
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

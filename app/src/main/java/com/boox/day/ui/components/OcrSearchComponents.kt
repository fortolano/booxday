package com.boox.day.ui.components

import com.boox.day.ocr.model.Domain
import com.boox.day.ocr.model.Intent

// Readable labels for the handwriting categories and purposes (Boox Notes filters). The search-tab
// components that lived here were removed with the Boox Notes redesign (Oct 2026).

/**
 * Format domain enum to readable label.
 */
internal fun formatDomainLabel(domain: Domain): String {
    return when (domain) {
        // Merged domains
        Domain.EDUCATION -> "Education"
        Domain.PROFESSIONAL -> "Professional"
        Domain.LIFE -> "Life"
        // Distinct domains
        Domain.TEACHING -> "Teaching"
        Domain.TECH -> "Tech"
        Domain.FINANCE -> "Finance"
        Domain.HEALTH -> "Health"
        Domain.SPIRITUAL -> "Spiritual"
        Domain.CREATIVE -> "Creative"
        Domain.TRAVEL -> "Travel"
        Domain.LEGAL -> "Legal"
        Domain.HOME -> "Home"
        Domain.SHOPPING -> "Shopping"
        Domain.BUSINESS -> "Business"
        Domain.STRATEGY -> "Strategy"
        Domain.SOCIAL_MEDIA -> "Social Media"
        Domain.ENTREPRENEURSHIP -> "Startup"
        Domain.LDS_LEADERSHIP -> "Church"
        Domain.FOOD -> "Food"
        Domain.COMMUNITY -> "Community"
        Domain.PET -> "Pets"
        Domain.MISC -> "Other"
    }
}

/**
 * Format intent enum to readable label.
 */
internal fun formatIntentLabel(intent: Intent): String {
    return when (intent) {
        Intent.TODO_LIST -> "To-Do"
        Intent.PLANNING -> "Planning"
        Intent.GOAL_SETTING -> "Goals"
        Intent.DAILY_REVIEW -> "Review"
        Intent.FOCUS_SESSION -> "Focus"
        Intent.MEETING_NOTES -> "Meeting"
        Intent.CALL_LOG -> "Call"
        Intent.BRAINSTORM -> "Ideas"
        Intent.JOURNAL -> "Journal"
        Intent.LECTURE_NOTES -> "Lecture"
        Intent.LESSON_PLAN -> "Lesson"
        Intent.GRADING -> "Grading"
        Intent.CLASSROOM_MGMT -> "Class Mgmt"
        Intent.CURRICULUM_DEV -> "Curriculum"
        Intent.PARENT_COMM -> "Parent"
        Intent.IEP_504 -> "IEP/504"
        Intent.FACULTY_MEETING -> "Faculty"
        Intent.STUDENT_RECORD -> "Student"
        Intent.ADVISING -> "Advising"
        Intent.STUDY_PLAN -> "Study"
        Intent.ASSIGNMENT_WORK -> "Assignment"
        Intent.READING_NOTES -> "Reading"
        Intent.LAB_NOTES -> "Lab"
        Intent.THESIS_WORK -> "Thesis"
        Intent.COURSE_PLANNING -> "Course"
        Intent.REFERENCE -> "Reference"
        Intent.INSTRUCTIONS -> "Instructions"
        Intent.CONTACT_INFO -> "Contacts"
        Intent.EXPENSE_LOG -> "Expenses"
        Intent.BUDGET -> "Budget"
        Intent.DRAFT_MESSAGE -> "Draft"
        Intent.TALKING_POINTS -> "Talking Pts"
        Intent.PROS_CONS -> "Pros/Cons"
        Intent.COMPARISON -> "Compare"
        Intent.ACTION_REVIEW -> "Review"
        Intent.TROUBLESHOOTING -> "Troubleshoot"
        Intent.RESEARCH -> "Research"
        Intent.LEADERSHIP_NOTES -> "Leadership"
        Intent.PEOPLE_MANAGEMENT -> "People Mgmt"
        Intent.STRATEGY_PLANNING -> "Strategy"
        Intent.BUSINESS_MODEL -> "Biz Model"
        Intent.METRICS_TRACKING -> "Metrics"
        Intent.STAKEHOLDER_UPDATE -> "Stakeholder"
        Intent.STARTUP_IDEAS -> "Startup"
        Intent.FUNDRAISING -> "Fundraising"
        Intent.PRODUCT_DEV -> "Product"
        Intent.CONTENT_PLAN -> "Content"
        Intent.SOCIAL_ANALYTICS -> "Analytics"
        Intent.CAMPAIGN_NOTES -> "Campaign"
        Intent.LDS_COUNCIL -> "Council"
        Intent.LDS_MINISTERING -> "Ministering"
        Intent.CREATIVE_WRITING -> "Writing"
        Intent.RECIPE -> "Recipe"
        Intent.PACKING_LIST -> "Packing"
        Intent.DEVOTIONAL -> "Devotional"
        Intent.TRANSCRIPT -> "Transcript"
        Intent.CHEAT_SHEET -> "Cheat Sheet"
        Intent.QUOTE_SNIPPET -> "Quote"
        Intent.WISHLIST -> "Wishlist"
        Intent.FREEFORM -> "Notes"
    }
}

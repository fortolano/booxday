package com.boox.day.ai

import com.boox.day.data.ai.AgenticProposal
import com.boox.day.data.ai.ProposalAction
import org.junit.Assert.assertEquals
import org.junit.Test

class AgenticPlanningServiceTest {

    @Test
    fun `normalizeProposalText removes punctuation and normalizes whitespace`() {
        val normalized = AgenticPlanningService.normalizeProposalText("  Call   Bob, today!!  ")

        assertEquals("call bob today", normalized)
    }

    @Test
    fun `dedupeAgenticProposals collapses add task duplicates and skips existing tasks`() {
        val proposals = listOf(
            addTaskProposal(id = "first", priority = 1, title = "Call Bob"),
            addTaskProposal(id = "duplicate", priority = 2, title = "Follow up", taskText = "call   bob!!"),
            addTaskProposal(id = "existing", priority = 3, title = "Review budget"),
            addTaskProposal(id = "keep", priority = 4, title = "Prepare slides")
        )

        val deduped = AgenticPlanningService.dedupeAgenticProposals(
            proposals = proposals,
            existingTaskTexts = listOf("Review budget")
        )

        assertEquals(listOf("first", "keep"), deduped.map { it.id })
    }

    private fun addTaskProposal(
        id: String,
        priority: Int,
        title: String,
        taskText: String? = null
    ) = AgenticProposal(
        id = id,
        action = ProposalAction.ADD_TASK,
        title = title,
        description = "Add this task",
        reason = "Useful for the selected day",
        confidence = 0.9f,
        priority = priority,
        taskText = taskText
    )
}

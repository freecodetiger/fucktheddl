package com.zpc.fucktheddl.commitments

import com.zpc.fucktheddl.agent.AgentApplyResult
import com.zpc.fucktheddl.agent.AgentCommitmentsPayload
import com.zpc.fucktheddl.agent.AgentProposal

interface CommitmentRepository {
    fun listCommitments(ownerUserId: String): AgentCommitmentsPayload
    fun applyProposal(ownerUserId: String, proposal: AgentProposal): AgentApplyResult
    fun deleteCommitment(ownerUserId: String, commitmentId: String): AgentApplyResult
}

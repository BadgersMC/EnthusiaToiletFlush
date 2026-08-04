from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected one match in {path}, found {count}: {old[:80]!r}")
    file.write_text(text.replace(old, new, 1))


service = "velocity/src/main/kotlin/com/badgersmc/queuerestart/velocity/application/network/NetworkRestartService.kt"

replace_once(
    service,
    "    private fun recover() {\n",
    """    private fun isLegacyRecoveryRegression(plan: RestartPlan): Boolean =
        plan.failure == LEGACY_INTERRUPTED_FAILURE &&
            plan.completedAt == null &&
            plan.executionDeadlineAt == null &&
            plan.dispatchedActionKeys.isEmpty() &&
            plan.acceptedActionKeys.isEmpty() &&
            plan.baselineBootIds.isEmpty() &&
            plan.proxyBaselineBootId == null &&
            plan.targetResults.isEmpty()

    private fun recover() {
""",
)

replace_once(
    service,
    """            when {
                plan.state == PlanState.NEEDS_REVIEW -> {
                    plan.maintenanceEnabled = plan.type != PlanType.SERVER
                }
                plan.state == PlanState.DISPATCHING -> recoverDispatching(plan, now)
""",
    """            when {
                plan.state in setOf(
                    PlanState.COMPLETED,
                    PlanState.CANCELLED,
                    PlanState.FAILED,
                    PlanState.MISSED,
                ) -> {
                    // actionStarted is durable history, not evidence that a
                    // terminal plan became active again after proxy startup.
                    plan.maintenanceEnabled = false
                    plan.executionDeadlineAt = null
                }
                plan.state == PlanState.NEEDS_REVIEW && plan.completedAt != null -> {
                    // Affected builds could overwrite a verified COMPLETED plan
                    // solely because actionStarted remained true. completedAt is
                    // written by the terminal transition, so restore it once.
                    plan.state = PlanState.COMPLETED
                    plan.failure = ""
                    plan.maintenanceEnabled = false
                    plan.executionDeadlineAt = null
                    plan.targetResults.putIfAbsent(
                        "recovery",
                        "restored completed plan after legacy recovery regression",
                    )
                }
                plan.state == PlanState.NEEDS_REVIEW && isLegacyRecoveryRegression(plan) -> {
                    // The known regression also produced records with the exact
                    // legacy failure but none of the durable evidence written by
                    // a real dispatch path. Close only that impossible state.
                    plan.state = PlanState.FAILED
                    plan.failure = "legacy recovery regression reconciled"
                    plan.maintenanceEnabled = false
                    plan.executionDeadlineAt = null
                }
                plan.state == PlanState.NEEDS_REVIEW -> {
                    plan.maintenanceEnabled = plan.type != PlanType.SERVER
                }
                plan.state == PlanState.DISPATCHING -> recoverDispatching(plan, now)
""",
)

replace_once(
    service,
    "                plan.state in setOf(PlanState.PREFLIGHT, PlanState.TRANSFERRING) || plan.actionStarted -> {\n",
    """                plan.state in setOf(PlanState.PREFLIGHT, PlanState.TRANSFERRING) ||
                    (plan.active() && plan.actionStarted) -> {
""",
)

replace_once(
    service,
    '                    plan.failure = "execution was interrupted after a destructive action may have started"\n',
    "                    plan.failure = LEGACY_INTERRUPTED_FAILURE\n",
)

replace_once(
    service,
    "    companion object {\n        private const val MAX_TERMINAL_HISTORY = 500\n",
    """    companion object {
        private const val LEGACY_INTERRUPTED_FAILURE =
            "execution was interrupted after a destructive action may have started"
        private const val MAX_TERMINAL_HISTORY = 500
""",
)

replace_once(
    "docs/network-restarts.md",
    """interrupted during preflight, transfer, or dispatch are marked
`NEEDS_REVIEW` and are never replayed automatically, preventing restart loops.
""",
    """interrupted during preflight, transfer, or dispatch are marked
`NEEDS_REVIEW` and are never replayed automatically, preventing restart loops.
Recovery preserves terminal plans even when their durable `actionStarted`
history remains true. On the first startup after this fix, records produced by
the known recovery regression are repaired only when a persisted completion
timestamp proves completion, or when the exact legacy failure has none of the
dispatch, acceptance, boot-baseline, result, or deadline evidence written by a
real destructive execution. Ambiguous records remain `NEEDS_REVIEW`.
""",
)

replace_once(
    "docs/requirements.md",
    "- REQ-062: If the companion plugin is missing on the target server when a restart is requested, then the system shall refuse to arm the restart and log an error.\n",
    """- REQ-062: If the companion plugin is missing on the target server when a restart is requested, then the system shall refuse to arm the restart and log an error.
- REQ-063: During proxy startup recovery, terminal restart plans shall remain terminal regardless of historical `actionStarted` state. The system shall repair legacy `NEEDS_REVIEW` records only when a persisted completion timestamp or the complete absence of durable destructive-execution evidence proves the known recovery regression; ambiguous records shall remain `NEEDS_REVIEW`.
""",
)

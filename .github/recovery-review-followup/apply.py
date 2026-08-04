from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected one match in {path}, found {count}: {old[:100]!r}")
    file.write_text(text.replace(old, new, 1))


service = "velocity/src/main/kotlin/com/badgersmc/queuerestart/velocity/application/network/NetworkRestartService.kt"
tests = "velocity/src/test/kotlin/com/badgersmc/queuerestart/velocity/application/network/NetworkRestartRecoveryRegressionTest.kt"

replace_once(
    service,
    """    private fun isLegacyRecoveryRegression(plan: RestartPlan): Boolean =
        plan.failure == LEGACY_INTERRUPTED_FAILURE &&
            plan.completedAt == null &&
""",
    """    private fun isLegacyRecoveryRegression(plan: RestartPlan): Boolean =
        plan.type in setOf(PlanType.PROXY, PlanType.NETWORK) &&
            plan.actionStarted &&
            plan.failure == LEGACY_INTERRUPTED_FAILURE &&
            plan.completedAt == null &&
""",
)

replace_once(
    tests,
    "import java.time.Instant\n",
    "import java.time.Instant\nimport java.util.UUID\n",
)

replace_once(
    tests,
    """    @Test
    fun `review with durable dispatch evidence remains fail closed`() {
        val store = MemoryStore()
        val now = Instant.now()
        val review = plan(
            PlanType.PROXY,
            PlanState.NEEDS_REVIEW,
            now,
            actionStarted = true,
            maintenanceEnabled = true,
            failure = LEGACY_FAILURE,
        )
        review.dispatchedActionKeys += "${review.id}:proxy"
        store.save(listOf(review))
        val control = FakeControl()

        val recovered = service(control, store).allPlans().single()

        assertThat(recovered.state).isEqualTo(PlanState.NEEDS_REVIEW)
        assertThat(recovered.maintenanceEnabled).isTrue()
        assertThat(control.maintenanceEnables).isGreaterThanOrEqualTo(1)
    }
""",
    """    @Test
    fun `every durable execution evidence field keeps review fail closed`() {
        val now = Instant.now()
        val evidence = listOf<Pair<String, (RestartPlan) -> Unit>>(
            "dispatch key" to { it.dispatchedActionKeys += "${it.id}:proxy" },
            "acceptance key" to { it.acceptedActionKeys += "${it.id}:proxy" },
            "backend baseline" to { it.baselineBootIds[smp] = UUID.randomUUID() },
            "proxy baseline" to { it.proxyBaselineBootId = UUID.randomUUID() },
            "result" to { it.targetResults["proxy"] = "accepted" },
            "execution deadline" to { it.executionDeadlineAt = now.plusSeconds(60) },
        )

        evidence.forEach { (name, addEvidence) ->
            val store = MemoryStore()
            val review = plan(
                PlanType.PROXY,
                PlanState.NEEDS_REVIEW,
                now,
                actionStarted = true,
                maintenanceEnabled = true,
                failure = LEGACY_FAILURE,
            )
            addEvidence(review)
            store.save(listOf(review))
            val control = FakeControl()

            val recovered = service(control, store).allPlans().single()

            assertThat(recovered.state).describedAs(name).isEqualTo(PlanState.NEEDS_REVIEW)
            assertThat(recovered.maintenanceEnabled).describedAs(name).isTrue()
            assertThat(control.maintenanceEnables).describedAs(name).isGreaterThanOrEqualTo(1)
        }
    }

    @Test
    fun `legacy failure without actionStarted remains unresolved`() {
        val store = MemoryStore()
        val now = Instant.now()
        val review = plan(
            PlanType.PROXY,
            PlanState.NEEDS_REVIEW,
            now,
            actionStarted = false,
            maintenanceEnabled = true,
            failure = LEGACY_FAILURE,
        )
        store.save(listOf(review))

        val recovered = service(FakeControl(), store).allPlans().single()

        assertThat(recovered.state).isEqualTo(PlanState.NEEDS_REVIEW)
    }

    @Test
    fun `server review is never cleared by proxy network legacy migration`() {
        val store = MemoryStore()
        val now = Instant.now()
        val review = plan(
            PlanType.SERVER,
            PlanState.NEEDS_REVIEW,
            now,
            actionStarted = true,
            failure = LEGACY_FAILURE,
        )
        store.save(listOf(review))

        val recovered = service(FakeControl(), store).allPlans().single()

        assertThat(recovered.state).isEqualTo(PlanState.NEEDS_REVIEW)
    }
""",
)

replace_once(
    tests,
    "        targets = if (type == PlanType.PROXY) emptySet() else setOf(hub, smp),\n",
    """        targets = when (type) {
            PlanType.PROXY -> emptySet()
            PlanType.SERVER -> setOf(smp)
            PlanType.NETWORK -> setOf(hub, smp)
        },
""",
)

replace_once(
    "docs/network-restarts.md",
    """timestamp proves completion, or when the exact legacy failure has none of the
dispatch, acceptance, boot-baseline, result, or deadline evidence written by a
real destructive execution. Ambiguous records remain `NEEDS_REVIEW`.
""",
    """timestamp proves completion, or when a proxy/network record has only the
legacy `actionStarted` flag and none of the dispatch, acceptance, boot-baseline,
result, or deadline evidence written by a real destructive execution. Ambiguous
records remain `NEEDS_REVIEW`.
""",
)

replace_once(
    "docs/requirements.md",
    """- REQ-063: During proxy startup recovery, terminal restart plans shall remain terminal regardless of historical `actionStarted` state. The system shall repair legacy `NEEDS_REVIEW` records only when a persisted completion timestamp or the complete absence of durable destructive-execution evidence proves the known recovery regression; ambiguous records shall remain `NEEDS_REVIEW`.
""",
    """- REQ-063: During proxy startup recovery, terminal restart plans shall remain terminal regardless of historical `actionStarted` state. The system shall repair legacy `NEEDS_REVIEW` records only when a persisted completion timestamp proves completion, or when a proxy/network record contains the exact legacy failure and has only the historical `actionStarted` flag with no dispatch, acceptance, boot-baseline, result, or deadline evidence. Ambiguous records and server review records shall remain `NEEDS_REVIEW`.
""",
)

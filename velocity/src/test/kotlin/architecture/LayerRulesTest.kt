package architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

private const val BASE = "com.badgersmc.queuerestart.velocity"

class LayerRulesTest {

    private val scope = Konsist.scopeFromProject()

    @Test
    fun `domain must not import application or infrastructure`() {
        scope.files
            .filter { it.packagee?.name?.startsWith("$BASE.domain") == true }
            .assertFalse { file ->
                file.imports.any {
                    it.name.startsWith("$BASE.application") ||
                        it.name.startsWith("$BASE.infrastructure")
                }
            }
    }

    @Test
    fun `application must not import infrastructure`() {
        scope.files
            .filter { it.packagee?.name?.startsWith("$BASE.application") == true }
            .assertFalse { file ->
                file.imports.any { it.name.startsWith("$BASE.infrastructure") }
            }
    }

    @Test
    fun `domain must not import velocity or paper or adventure or configurate`() {
        scope.files
            .filter { it.packagee?.name?.startsWith("$BASE.domain") == true }
            .assertFalse { file ->
                file.imports.any {
                    it.name.startsWith("com.velocitypowered") ||
                        it.name.startsWith("com.velocityctd") ||
                        it.name.startsWith("org.bukkit") ||
                        it.name.startsWith("io.papermc") ||
                        it.name.startsWith("net.kyori") ||
                        it.name.startsWith("org.spongepowered.configurate") ||
                        it.name.startsWith("com.cronutils")
                }
            }
    }
}

package io.johnsonlee.testpilot.loader

import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat

class BytecodeRewriterTest {

    private val rewriter = BytecodeRewriter()

    @Test
    fun `should map Android Activity to TestPilot Activity`() {
        val mapped = rewriter.getMappedName("android/app/Activity")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/activity/Activity")
    }

    @Test
    fun `should map Android View to TestPilot View`() {
        val mapped = rewriter.getMappedName("android/view/View")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/view/View")
    }

    @Test
    fun `should map Android ViewGroup to TestPilot ViewGroup`() {
        val mapped = rewriter.getMappedName("android/view/ViewGroup")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/view/ViewGroup")
    }

    @Test
    fun `should map Android TextView to TestPilot TextView`() {
        val mapped = rewriter.getMappedName("android/widget/TextView")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/TextView")
    }

    @Test
    fun `should map Android Button to TestPilot Button`() {
        val mapped = rewriter.getMappedName("android/widget/Button")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/Button")
    }

    @Test
    fun `should map Android Context to TestPilot Context`() {
        val mapped = rewriter.getMappedName("android/content/Context")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/content/Context")
    }

    @Test
    fun `should map Android Bundle to TestPilot Bundle`() {
        val mapped = rewriter.getMappedName("android/os/Bundle")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/os/Bundle")
    }

    @Test
    fun `should not map non-Android classes`() {
        val mapped = rewriter.getMappedName("com/example/MyClass")
        assertThat(mapped).isEqualTo("com/example/MyClass")
    }

    @Test
    fun `should not map java classes`() {
        val mapped = rewriter.getMappedName("java/lang/String")
        assertThat(mapped).isEqualTo("java/lang/String")
    }

    @Test
    fun `shouldRewrite returns true for Android classes`() {
        assertThat(rewriter.shouldRewrite("android/app/Activity")).isTrue()
        assertThat(rewriter.shouldRewrite("android/view/View")).isTrue()
        assertThat(rewriter.shouldRewrite("android/widget/Button")).isTrue()
    }

    @Test
    fun `shouldRewrite returns false for non-Android classes`() {
        assertThat(rewriter.shouldRewrite("com/example/MyClass")).isFalse()
        assertThat(rewriter.shouldRewrite("java/lang/String")).isFalse()
    }
}

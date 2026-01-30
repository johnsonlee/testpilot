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
    fun `should map Android Fragment to TestPilot Fragment`() {
        val mapped = rewriter.getMappedName("android/app/Fragment")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/app/Fragment")
    }

    @Test
    fun `should map Android FragmentManager to TestPilot FragmentManager`() {
        val mapped = rewriter.getMappedName("android/app/FragmentManager")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/app/FragmentManager")
    }

    @Test
    fun `should map Android FragmentTransaction to TestPilot FragmentTransaction`() {
        val mapped = rewriter.getMappedName("android/app/FragmentTransaction")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/app/FragmentTransaction")
    }

    @Test
    fun `should map AndroidX Fragment to TestPilot Fragment`() {
        val mapped = rewriter.getMappedName("androidx/fragment/app/Fragment")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/app/Fragment")
    }

    @Test
    fun `should map AndroidX FragmentManager to TestPilot FragmentManager`() {
        val mapped = rewriter.getMappedName("androidx/fragment/app/FragmentManager")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/app/FragmentManager")
    }

    @Test
    fun `should map AndroidX FragmentTransaction to TestPilot FragmentTransaction`() {
        val mapped = rewriter.getMappedName("androidx/fragment/app/FragmentTransaction")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/app/FragmentTransaction")
    }

    @Test
    fun `should map AndroidX FragmentActivity to TestPilot FragmentActivity`() {
        val mapped = rewriter.getMappedName("androidx/fragment/app/FragmentActivity")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/app/FragmentActivity")
    }

    @Test
    fun `should map AppCompatActivity to TestPilot FragmentActivity`() {
        val mapped = rewriter.getMappedName("androidx/appcompat/app/AppCompatActivity")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/app/FragmentActivity")
    }

    @Test
    fun `should map AndroidX RecyclerView to TestPilot RecyclerView`() {
        val mapped = rewriter.getMappedName("androidx/recyclerview/widget/RecyclerView")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/RecyclerView")
    }

    @Test
    fun `should map RecyclerView Adapter to TestPilot RecyclerView Adapter`() {
        val mapped = rewriter.getMappedName("androidx/recyclerview/widget/RecyclerView\$Adapter")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/RecyclerView\$Adapter")
    }

    @Test
    fun `should map RecyclerView ViewHolder to TestPilot RecyclerView ViewHolder`() {
        val mapped = rewriter.getMappedName("androidx/recyclerview/widget/RecyclerView\$ViewHolder")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/RecyclerView\$ViewHolder")
    }

    @Test
    fun `should map RecyclerView LayoutManager to TestPilot RecyclerView LayoutManager`() {
        val mapped = rewriter.getMappedName("androidx/recyclerview/widget/RecyclerView\$LayoutManager")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/RecyclerView\$LayoutManager")
    }

    @Test
    fun `should map RecyclerView ItemDecoration to TestPilot RecyclerView ItemDecoration`() {
        val mapped = rewriter.getMappedName("androidx/recyclerview/widget/RecyclerView\$ItemDecoration")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/RecyclerView\$ItemDecoration")
    }

    @Test
    fun `should map RecyclerView ItemAnimator to TestPilot RecyclerView ItemAnimator`() {
        val mapped = rewriter.getMappedName("androidx/recyclerview/widget/RecyclerView\$ItemAnimator")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/RecyclerView\$ItemAnimator")
    }

    @Test
    fun `should map RecyclerView OnScrollListener to TestPilot RecyclerView OnScrollListener`() {
        val mapped = rewriter.getMappedName("androidx/recyclerview/widget/RecyclerView\$OnScrollListener")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/RecyclerView\$OnScrollListener")
    }

    @Test
    fun `should map RecyclerView State to TestPilot RecyclerView State`() {
        val mapped = rewriter.getMappedName("androidx/recyclerview/widget/RecyclerView\$State")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/RecyclerView\$State")
    }

    @Test
    fun `should map LinearLayoutManager to TestPilot RecyclerView LinearLayoutManager`() {
        val mapped = rewriter.getMappedName("androidx/recyclerview/widget/LinearLayoutManager")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/RecyclerView\$LinearLayoutManager")
    }

    @Test
    fun `should map GridLayoutManager to TestPilot RecyclerView GridLayoutManager`() {
        val mapped = rewriter.getMappedName("androidx/recyclerview/widget/GridLayoutManager")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/RecyclerView\$GridLayoutManager")
    }

    // ViewPager (androidx)

    @Test
    fun `should map AndroidX ViewPager to TestPilot ViewPager`() {
        val mapped = rewriter.getMappedName("androidx/viewpager/widget/ViewPager")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/ViewPager")
    }

    @Test
    fun `should map ViewPager OnPageChangeListener to TestPilot ViewPager OnPageChangeListener`() {
        val mapped = rewriter.getMappedName("androidx/viewpager/widget/ViewPager\$OnPageChangeListener")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/ViewPager\$OnPageChangeListener")
    }

    @Test
    fun `should map ViewPager SimpleOnPageChangeListener to TestPilot ViewPager SimpleOnPageChangeListener`() {
        val mapped = rewriter.getMappedName("androidx/viewpager/widget/ViewPager\$SimpleOnPageChangeListener")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/ViewPager\$SimpleOnPageChangeListener")
    }

    @Test
    fun `should map ViewPager PageTransformer to TestPilot ViewPager PageTransformer`() {
        val mapped = rewriter.getMappedName("androidx/viewpager/widget/ViewPager\$PageTransformer")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/ViewPager\$PageTransformer")
    }

    @Test
    fun `should map AndroidX PagerAdapter to TestPilot ViewPager PagerAdapter`() {
        val mapped = rewriter.getMappedName("androidx/viewpager/widget/PagerAdapter")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/ViewPager\$PagerAdapter")
    }

    // ViewPager (legacy support lib)

    @Test
    fun `should map support lib ViewPager to TestPilot ViewPager`() {
        val mapped = rewriter.getMappedName("android/support/v4/view/ViewPager")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/ViewPager")
    }

    @Test
    fun `should map support lib ViewPager OnPageChangeListener to TestPilot`() {
        val mapped = rewriter.getMappedName("android/support/v4/view/ViewPager\$OnPageChangeListener")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/ViewPager\$OnPageChangeListener")
    }

    @Test
    fun `should map support lib ViewPager SimpleOnPageChangeListener to TestPilot`() {
        val mapped = rewriter.getMappedName("android/support/v4/view/ViewPager\$SimpleOnPageChangeListener")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/ViewPager\$SimpleOnPageChangeListener")
    }

    @Test
    fun `should map support lib ViewPager PageTransformer to TestPilot`() {
        val mapped = rewriter.getMappedName("android/support/v4/view/ViewPager\$PageTransformer")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/ViewPager\$PageTransformer")
    }

    @Test
    fun `should map support lib PagerAdapter to TestPilot ViewPager PagerAdapter`() {
        val mapped = rewriter.getMappedName("android/support/v4/view/PagerAdapter")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/ViewPager\$PagerAdapter")
    }

    // FragmentPagerAdapter

    @Test
    fun `should map AndroidX FragmentPagerAdapter to TestPilot FragmentPagerAdapter`() {
        val mapped = rewriter.getMappedName("androidx/fragment/app/FragmentPagerAdapter")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/FragmentPagerAdapter")
    }

    @Test
    fun `should map AndroidX FragmentStatePagerAdapter to TestPilot FragmentStatePagerAdapter`() {
        val mapped = rewriter.getMappedName("androidx/fragment/app/FragmentStatePagerAdapter")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/FragmentStatePagerAdapter")
    }

    @Test
    fun `should map support lib FragmentPagerAdapter to TestPilot FragmentPagerAdapter`() {
        val mapped = rewriter.getMappedName("android/support/v4/app/FragmentPagerAdapter")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/FragmentPagerAdapter")
    }

    @Test
    fun `should map support lib FragmentStatePagerAdapter to TestPilot FragmentStatePagerAdapter`() {
        val mapped = rewriter.getMappedName("android/support/v4/app/FragmentStatePagerAdapter")
        assertThat(mapped).isEqualTo("io/johnsonlee/testpilot/simulator/widget/FragmentStatePagerAdapter")
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

package io.johnsonlee.testpilot.simulator.widget

import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.graphics.Canvas
import io.johnsonlee.testpilot.simulator.graphics.Color
import io.johnsonlee.testpilot.simulator.graphics.DrawCommand
import io.johnsonlee.testpilot.simulator.graphics.Paint
import io.johnsonlee.testpilot.simulator.graphics.toImage
import io.johnsonlee.testpilot.simulator.resources.Resources
import io.johnsonlee.testpilot.simulator.view.MotionEvent
import io.johnsonlee.testpilot.simulator.view.View
import io.johnsonlee.testpilot.simulator.view.ViewGroup
import io.johnsonlee.testpilot.simulator.window.Window
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

class RecyclerViewTest {

    private val context = object : Context() {
        override val resources = Resources()
        override fun getString(resId: Int) = ""
    }

    /** A View that fills itself with a solid color. */
    private class ColorView(
        context: Context,
        private val fillColor: Int
    ) : View(context) {
        override fun draw(canvas: Canvas) {
            canvas.drawRect(
                0f, 0f, width.toFloat(), height.toFloat(),
                Paint(color = fillColor, style = Paint.Style.FILL)
            )
        }
    }

    private fun createAdapter(
        count: Int,
        itemHeight: Int = 50
    ): RecyclerView.Adapter<RecyclerView.ViewHolder> {
        return object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = View(context).apply {
                    layoutParams = View.LayoutParams(View.LayoutParams.MATCH_PARENT, itemHeight)
                }
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
            override fun getItemCount() = count
        }
    }

    private fun createTextAdapter(items: List<String>): RecyclerView.Adapter<RecyclerView.ViewHolder> {
        return object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val tv = TextView(context)
                return object : RecyclerView.ViewHolder(tv) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                (holder.itemView as TextView).text = items[position]
            }

            override fun getItemCount() = items.size
        }
    }

    private fun createColorAdapterByType(
        colors: List<Int>,
        itemHeight: Int = 50
    ): RecyclerView.Adapter<RecyclerView.ViewHolder> {
        return object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = ColorView(context, viewType).apply {
                    layoutParams = View.LayoutParams(View.LayoutParams.MATCH_PARENT, itemHeight)
                }
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
            override fun getItemViewType(position: Int) = colors[position]
            override fun getItemCount() = colors.size
        }
    }

    private fun createHorizontalAdapter(
        count: Int,
        itemWidth: Int = 40
    ): RecyclerView.Adapter<RecyclerView.ViewHolder> {
        return object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = View(context).apply {
                    layoutParams = View.LayoutParams(itemWidth, View.LayoutParams.MATCH_PARENT)
                }
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
            override fun getItemCount() = count
        }
    }

    private fun setupRecyclerView(
        adapter: RecyclerView.Adapter<*>,
        layoutManager: RecyclerView.LayoutManager,
        windowWidth: Int = 100,
        windowHeight: Int = 100
    ): Pair<RecyclerView, Window> {
        val rv = RecyclerView(context)
        rv.layoutManager = layoutManager
        rv.adapter = adapter
        val window = Window(windowWidth, windowHeight)
        window.setContentView(rv)
        return rv to window
    }

    private fun renderToImage(window: Window): BufferedImage {
        window.measureAndLayout()
        return window.draw().toImage()
    }

    // --- Adapter binding tests ---

    @Test
    fun `setting adapter populates children`() {
        val rv = RecyclerView(context)
        rv.layoutManager = RecyclerView.LinearLayoutManager(context)
        rv.adapter = createAdapter(3)
        assertThat(rv.childCount).isEqualTo(3)
    }

    @Test
    fun `adapter items are bound with correct positions`() {
        val rv = RecyclerView(context)
        rv.layoutManager = RecyclerView.LinearLayoutManager(context)
        rv.adapter = createAdapter(3)

        for (i in 0 until 3) {
            val vh = rv.findViewHolderForAdapterPosition(i)
            assertThat(vh).isNotNull
            assertThat(vh!!.adapterPosition).isEqualTo(i)
        }
    }

    @Test
    fun `getItemViewType is respected`() {
        val createdViewTypes = mutableListOf<Int>()
        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                createdViewTypes.add(viewType)
                val view = View(context).apply {
                    layoutParams = View.LayoutParams(View.LayoutParams.MATCH_PARENT, 50)
                }
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
            override fun getItemViewType(position: Int) = position * 10
            override fun getItemCount() = 3
        }

        val rv = RecyclerView(context)
        rv.layoutManager = RecyclerView.LinearLayoutManager(context)
        rv.adapter = adapter

        assertThat(createdViewTypes).containsExactly(0, 10, 20)
    }

    @Test
    fun `notifyDataSetChanged re-populates items`() {
        var itemCount = 2
        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = View(context).apply {
                    layoutParams = View.LayoutParams(View.LayoutParams.MATCH_PARENT, 50)
                }
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
            override fun getItemCount() = itemCount
        }

        val rv = RecyclerView(context)
        rv.layoutManager = RecyclerView.LinearLayoutManager(context)
        rv.adapter = adapter
        assertThat(rv.childCount).isEqualTo(2)

        itemCount = 5
        adapter.notifyDataSetChanged()
        assertThat(rv.childCount).isEqualTo(5)
    }

    @Test
    fun `setting adapter to null clears children`() {
        val rv = RecyclerView(context)
        rv.layoutManager = RecyclerView.LinearLayoutManager(context)
        rv.adapter = createAdapter(3)
        assertThat(rv.childCount).isEqualTo(3)

        rv.adapter = null
        assertThat(rv.childCount).isEqualTo(0)
    }

    // --- ViewHolder lookup tests ---

    @Test
    fun `findViewHolderForAdapterPosition returns correct holder`() {
        val rv = RecyclerView(context)
        rv.layoutManager = RecyclerView.LinearLayoutManager(context)
        rv.adapter = createAdapter(3)

        val vh0 = rv.findViewHolderForAdapterPosition(0)
        val vh1 = rv.findViewHolderForAdapterPosition(1)
        val vh2 = rv.findViewHolderForAdapterPosition(2)
        val vhOut = rv.findViewHolderForAdapterPosition(3)

        assertThat(vh0).isNotNull
        assertThat(vh1).isNotNull
        assertThat(vh2).isNotNull
        assertThat(vhOut).isNull()
        assertThat(vh0!!.itemView).isSameAs(rv.getChildAt(0))
        assertThat(vh1!!.itemView).isSameAs(rv.getChildAt(1))
        assertThat(vh2!!.itemView).isSameAs(rv.getChildAt(2))
    }

    @Test
    fun `getChildAdapterPosition returns correct position`() {
        val rv = RecyclerView(context)
        rv.layoutManager = RecyclerView.LinearLayoutManager(context)
        rv.adapter = createAdapter(3)

        for (i in 0 until 3) {
            val child = rv.getChildAt(i)
            assertThat(rv.getChildAdapterPosition(child)).isEqualTo(i)
        }

        // Unknown view returns -1
        val unknown = View(context)
        assertThat(rv.getChildAdapterPosition(unknown)).isEqualTo(-1)
    }

    // --- LinearLayoutManager tests ---

    @Test
    fun `vertical linear layout positions items top-to-bottom`() {
        val (rv, _) = setupRecyclerView(
            createAdapter(3, itemHeight = 30),
            RecyclerView.LinearLayoutManager(context),
            windowWidth = 100,
            windowHeight = 200
        )

        assertThat(rv.getChildAt(0).top).isEqualTo(0)
        assertThat(rv.getChildAt(0).bottom).isEqualTo(30)
        assertThat(rv.getChildAt(1).top).isEqualTo(30)
        assertThat(rv.getChildAt(1).bottom).isEqualTo(60)
        assertThat(rv.getChildAt(2).top).isEqualTo(60)
        assertThat(rv.getChildAt(2).bottom).isEqualTo(90)
    }

    @Test
    fun `horizontal linear layout positions items left-to-right`() {
        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = View(context).apply {
                    layoutParams = View.LayoutParams(40, View.LayoutParams.MATCH_PARENT)
                }
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
            override fun getItemCount() = 3
        }

        val (rv, _) = setupRecyclerView(
            adapter,
            RecyclerView.LinearLayoutManager(context, RecyclerView.LinearLayoutManager.HORIZONTAL),
            windowWidth = 200,
            windowHeight = 100
        )

        assertThat(rv.getChildAt(0).left).isEqualTo(0)
        assertThat(rv.getChildAt(0).right).isEqualTo(40)
        assertThat(rv.getChildAt(1).left).isEqualTo(40)
        assertThat(rv.getChildAt(1).right).isEqualTo(80)
        assertThat(rv.getChildAt(2).left).isEqualTo(80)
        assertThat(rv.getChildAt(2).right).isEqualTo(120)
    }

    @Test
    fun `linear layout measures children correctly`() {
        val (rv, _) = setupRecyclerView(
            createAdapter(2, itemHeight = 40),
            RecyclerView.LinearLayoutManager(context),
            windowWidth = 100,
            windowHeight = 200
        )

        val child0 = rv.getChildAt(0)
        assertThat(child0.getMeasuredWidth()).isEqualTo(100)
        assertThat(child0.getMeasuredHeight()).isEqualTo(40)
        assertThat(child0.width).isEqualTo(100)
        assertThat(child0.height).isEqualTo(40)
    }

    // --- GridLayoutManager tests ---

    @Test
    fun `grid layout positions items in rows and columns`() {
        val (rv, _) = setupRecyclerView(
            createAdapter(6, itemHeight = 50),
            RecyclerView.GridLayoutManager(context, spanCount = 3),
            windowWidth = 300,
            windowHeight = 300
        )

        // Row 0: items 0, 1, 2
        assertThat(rv.getChildAt(0).left).isEqualTo(0)
        assertThat(rv.getChildAt(0).right).isEqualTo(100)
        assertThat(rv.getChildAt(0).top).isEqualTo(0)

        assertThat(rv.getChildAt(1).left).isEqualTo(100)
        assertThat(rv.getChildAt(1).right).isEqualTo(200)
        assertThat(rv.getChildAt(1).top).isEqualTo(0)

        assertThat(rv.getChildAt(2).left).isEqualTo(200)
        assertThat(rv.getChildAt(2).right).isEqualTo(300)
        assertThat(rv.getChildAt(2).top).isEqualTo(0)

        // Row 1: items 3, 4, 5
        assertThat(rv.getChildAt(3).left).isEqualTo(0)
        assertThat(rv.getChildAt(3).top).isEqualTo(50)

        assertThat(rv.getChildAt(4).left).isEqualTo(100)
        assertThat(rv.getChildAt(4).top).isEqualTo(50)

        assertThat(rv.getChildAt(5).left).isEqualTo(200)
        assertThat(rv.getChildAt(5).top).isEqualTo(50)
    }

    @Test
    fun `grid layout wraps to next row`() {
        val (rv, _) = setupRecyclerView(
            createAdapter(4, itemHeight = 25),
            RecyclerView.GridLayoutManager(context, spanCount = 3),
            windowWidth = 300,
            windowHeight = 300
        )

        // First 3 items in row 0
        assertThat(rv.getChildAt(0).top).isEqualTo(0)
        assertThat(rv.getChildAt(1).top).isEqualTo(0)
        assertThat(rv.getChildAt(2).top).isEqualTo(0)

        // 4th item wraps to row 1
        assertThat(rv.getChildAt(3).top).isEqualTo(25)
        assertThat(rv.getChildAt(3).left).isEqualTo(0)
    }

    // --- Draw command tests ---

    @Test
    fun `adapter items produce draw commands`() {
        val items = listOf("Alpha", "Beta", "Gamma")
        val (_, window) = setupRecyclerView(
            createTextAdapter(items),
            RecyclerView.LinearLayoutManager(context),
            windowWidth = 200,
            windowHeight = 200
        )

        val texts = window.draw().getCommands()
            .filterIsInstance<DrawCommand.Text>()
            .map { it.text }

        assertThat(texts).containsExactly("Alpha", "Beta", "Gamma")
    }

    @Test
    fun `notifyDataSetChanged updates rendered output`() {
        val items = mutableListOf("First", "Second")
        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val tv = TextView(context)
                return object : RecyclerView.ViewHolder(tv) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                (holder.itemView as TextView).text = items[position]
            }

            override fun getItemCount() = items.size
        }

        val (_, window) = setupRecyclerView(
            adapter,
            RecyclerView.LinearLayoutManager(context),
            windowWidth = 200,
            windowHeight = 200
        )

        val textsBefore = window.draw().getCommands()
            .filterIsInstance<DrawCommand.Text>()
            .map { it.text }
        assertThat(textsBefore).containsExactly("First", "Second")

        items.clear()
        items.addAll(listOf("One", "Two", "Three"))
        adapter.notifyDataSetChanged()

        // Re-measure and layout after data change
        window.measureAndLayout()
        val textsAfter = window.draw().getCommands()
            .filterIsInstance<DrawCommand.Text>()
            .map { it.text }
        assertThat(textsAfter).containsExactly("One", "Two", "Three")
    }

    // --- Pixel-level tests ---

    @Test
    fun `pixel - adapter items fill recycler view`() {
        val colors = listOf(Color.RED, Color.BLUE)
        val (_, window) = setupRecyclerView(
            createColorAdapterByType(colors, itemHeight = 50),
            RecyclerView.LinearLayoutManager(context),
            windowWidth = 100,
            windowHeight = 100
        )

        val image = renderToImage(window)

        // First item (RED) occupies y=0..49
        assertThat(image.getRGB(50, 25)).isEqualTo(Color.RED)
        // Second item (BLUE) occupies y=50..99
        assertThat(image.getRGB(50, 75)).isEqualTo(Color.BLUE)
    }

    @Test
    fun `pixel - grid layout renders items in grid pattern`() {
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE)
        val (_, window) = setupRecyclerView(
            createColorAdapterByType(colors, itemHeight = 50),
            RecyclerView.GridLayoutManager(context, spanCount = 2),
            windowWidth = 100,
            windowHeight = 100
        )

        val image = renderToImage(window)

        // Row 0: RED at (0,0)-(50,50), GREEN at (50,0)-(100,50)
        assertThat(image.getRGB(25, 25)).isEqualTo(Color.RED)
        assertThat(image.getRGB(75, 25)).isEqualTo(Color.GREEN)

        // Row 1: BLUE at (0,50)-(50,100), WHITE at (50,50)-(100,100)
        assertThat(image.getRGB(25, 75)).isEqualTo(Color.BLUE)
        assertThat(image.getRGB(75, 75)).isEqualTo(Color.WHITE)
    }

    // --- Scroll offset tests ---

    @Test
    fun `scrollToPosition moves viewport to target item`() {
        // 10 items x 30px each = 300px total in 100px viewport (maxScroll = 200)
        val (rv, window) = setupRecyclerView(
            createAdapter(10, itemHeight = 30),
            RecyclerView.LinearLayoutManager(context),
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        rv.scrollToPosition(5)

        // Item 5: top=150, bottom=180, below viewport → scroll so bottom aligns: 180-100=80
        assertThat(rv.computeVerticalScrollOffset()).isEqualTo(80)
    }

    @Test
    fun `scrollToPosition for horizontal layout`() {
        val (rv, window) = setupRecyclerView(
            createHorizontalAdapter(5, itemWidth = 40),
            RecyclerView.LinearLayoutManager(context, RecyclerView.LinearLayoutManager.HORIZONTAL),
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        rv.scrollToPosition(3)

        // Item 3: left=120, right=160, beyond viewport → scroll so right aligns: 160-100=60
        assertThat(rv.computeHorizontalScrollOffset()).isEqualTo(60)
    }

    @Test
    fun `scrollBy adjusts offset`() {
        val (rv, window) = setupRecyclerView(
            createAdapter(5, itemHeight = 30),
            RecyclerView.LinearLayoutManager(context),
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        rv.scrollBy(0, 40)

        assertThat(rv.computeVerticalScrollOffset()).isEqualTo(40)
    }

    @Test
    fun `scrollBy clamps to valid range`() {
        // 5 items x 30px = 150px total, viewport = 100px, max scroll = 50
        val (rv, window) = setupRecyclerView(
            createAdapter(5, itemHeight = 30),
            RecyclerView.LinearLayoutManager(context),
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        // Scroll past end
        rv.scrollBy(0, 200)
        assertThat(rv.computeVerticalScrollOffset()).isEqualTo(50)

        // Scroll past beginning
        rv.scrollBy(0, -200)
        assertThat(rv.computeVerticalScrollOffset()).isEqualTo(0)
    }

    @Test
    fun `smoothScrollToPosition behaves like scrollToPosition`() {
        // 10 items x 30px = 300px total in 100px viewport (maxScroll = 200)
        val (rv, window) = setupRecyclerView(
            createAdapter(10, itemHeight = 30),
            RecyclerView.LinearLayoutManager(context),
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        rv.smoothScrollToPosition(5)

        // Same as scrollToPosition(5): item bottom=180, offset=180-100=80
        assertThat(rv.computeVerticalScrollOffset()).isEqualTo(80)
    }

    @Test
    fun `computeVerticalScrollOffset returns current offset`() {
        val (rv, window) = setupRecyclerView(
            createAdapter(5, itemHeight = 30),
            RecyclerView.LinearLayoutManager(context),
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        assertThat(rv.computeVerticalScrollOffset()).isEqualTo(0)
        rv.scrollBy(0, 25)
        assertThat(rv.computeVerticalScrollOffset()).isEqualTo(25)
    }

    @Test
    fun `computeVerticalScrollRange returns total content height`() {
        // 5 items x 30px = 150px
        val (rv, window) = setupRecyclerView(
            createAdapter(5, itemHeight = 30),
            RecyclerView.LinearLayoutManager(context),
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        assertThat(rv.computeVerticalScrollRange()).isEqualTo(150)
        assertThat(rv.computeVerticalScrollExtent()).isEqualTo(100)
    }

    // --- OnScrollListener tests ---

    @Test
    fun `OnScrollListener notified on scrollToPosition`() {
        // 10 items x 30px = 300px total in 100px viewport (maxScroll = 200)
        val (rv, window) = setupRecyclerView(
            createAdapter(10, itemHeight = 30),
            RecyclerView.LinearLayoutManager(context),
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        var capturedDy = 0
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                capturedDy = dy
            }
        })

        rv.scrollToPosition(5) // item 5 bottom=180, offset moves to 80

        assertThat(capturedDy).isEqualTo(80)
    }

    @Test
    fun `OnScrollListener notified on scrollBy`() {
        val (rv, window) = setupRecyclerView(
            createAdapter(5, itemHeight = 30),
            RecyclerView.LinearLayoutManager(context),
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        var capturedDx = 0
        var capturedDy = 0
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                capturedDx = dx
                capturedDy = dy
            }
        })

        rv.scrollBy(0, 40)

        assertThat(capturedDx).isEqualTo(0)
        assertThat(capturedDy).isEqualTo(40)
    }

    @Test
    fun `removeOnScrollListener stops notifications`() {
        val (rv, window) = setupRecyclerView(
            createAdapter(5, itemHeight = 30),
            RecyclerView.LinearLayoutManager(context),
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        var callCount = 0
        val listener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                callCount++
            }
        }
        rv.addOnScrollListener(listener)
        rv.scrollBy(0, 10)
        assertThat(callCount).isEqualTo(1)

        rv.removeOnScrollListener(listener)
        rv.scrollBy(0, 10)
        assertThat(callCount).isEqualTo(1) // not incremented
    }

    // --- Pixel-level scroll tests ---

    @Test
    fun `pixel - scrollToPosition shifts visible content`() {
        // 4 items x 50px each in 100px viewport: RED, GREEN, BLUE, WHITE
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE)
        val (rv, window) = setupRecyclerView(
            createColorAdapterByType(colors, itemHeight = 50),
            RecyclerView.LinearLayoutManager(context),
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        // scrollToPosition(2): item 2 bottom=150 > 100 → offset = 150-100 = 50
        // viewport shows GREEN (y=0..49), BLUE (y=50..99)
        rv.scrollToPosition(2)
        val image = renderToImage(window)

        assertThat(image.getRGB(50, 25)).isEqualTo(Color.GREEN)
        assertThat(image.getRGB(50, 75)).isEqualTo(Color.BLUE)
    }

    @Test
    fun `pixel - scrollBy shifts visible content by exact pixels`() {
        // 4 items x 50px each in 100px viewport: RED, GREEN, BLUE, WHITE
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE)
        val (rv, window) = setupRecyclerView(
            createColorAdapterByType(colors, itemHeight = 50),
            RecyclerView.LinearLayoutManager(context),
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        // scrollBy(0, 25) → offset = 25 → RED visible from y=0..24, GREEN from y=25..74, BLUE partially from y=75..99
        rv.scrollBy(0, 25)
        val image = renderToImage(window)

        // y=12 → original y=37 → still RED (0..49)
        assertThat(image.getRGB(50, 12)).isEqualTo(Color.RED)
        // y=50 → original y=75 → GREEN (50..99)
        assertThat(image.getRGB(50, 50)).isEqualTo(Color.GREEN)
    }

    // --- Fix 1: totalContentWidth/Height iterate all children ---

    @Test
    fun `computeHorizontalScrollRange correct for grid with incomplete last row`() {
        // Vertical grid: 3 columns, 5 items → row 0 has 3 items, row 1 has 2
        // Old code used last child (item 4, col 1) → right=200, missing col 2
        val (rv, window) = setupRecyclerView(
            createAdapter(5, itemHeight = 50),
            RecyclerView.GridLayoutManager(context, spanCount = 3),
            windowWidth = 300,
            windowHeight = 300
        )
        window.measureAndLayout()

        // Full width should be 300 (3 columns x 100px each), not 200
        assertThat(rv.computeHorizontalScrollRange()).isEqualTo(300)
    }

    @Test
    fun `computeVerticalScrollRange correct for grid with incomplete last column`() {
        // Horizontal grid: 3 rows, 5 items → col 0 has 3 items, col 1 has 2
        // Old code used last child (item 4, row 1) → bottom=200, missing row 2
        val (rv, window) = setupRecyclerView(
            createAdapter(5, itemHeight = 50),
            RecyclerView.GridLayoutManager(
                context, spanCount = 3,
                orientation = RecyclerView.LinearLayoutManager.HORIZONTAL
            ),
            windowWidth = 300,
            windowHeight = 300
        )
        window.measureAndLayout()

        // Full height should be 300 (3 rows x 100px each), not 200
        assertThat(rv.computeVerticalScrollRange()).isEqualTo(300)
    }

    // --- Fix 2: scrollToPosition minimum scroll ---

    @Test
    fun `scrollToPosition does not scroll when item is already visible`() {
        // 10 items x 30px in 100px viewport
        val (rv, window) = setupRecyclerView(
            createAdapter(10, itemHeight = 30),
            RecyclerView.LinearLayoutManager(context),
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        // Item 2: top=60, bottom=90 → fully visible in viewport 0..100
        rv.scrollToPosition(2)

        assertThat(rv.computeVerticalScrollOffset()).isEqualTo(0)
    }

    @Test
    fun `scrollToPosition scrolls just enough when item is below viewport`() {
        // 10 items x 30px in 100px viewport
        val (rv, window) = setupRecyclerView(
            createAdapter(10, itemHeight = 30),
            RecyclerView.LinearLayoutManager(context),
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        // Item 5: top=150, bottom=180 → below viewport
        // Minimum scroll: align bottom → 180-100=80
        rv.scrollToPosition(5)

        assertThat(rv.computeVerticalScrollOffset()).isEqualTo(80)
    }

    @Test
    fun `scrollToPosition scrolls just enough when item is above viewport`() {
        // 10 items x 30px in 100px viewport
        val (rv, window) = setupRecyclerView(
            createAdapter(10, itemHeight = 30),
            RecyclerView.LinearLayoutManager(context),
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        // First scroll far down
        rv.scrollToPosition(8) // top=240, bottom=270 → offset=270-100=170

        // Now scroll back to item 2: top=60 < 170 → align top → offset=60
        rv.scrollToPosition(2)

        assertThat(rv.computeVerticalScrollOffset()).isEqualTo(60)
    }

    @Test
    fun `scrollToPosition for horizontal does not scroll when visible`() {
        val (rv, window) = setupRecyclerView(
            createHorizontalAdapter(5, itemWidth = 40),
            RecyclerView.LinearLayoutManager(context, RecyclerView.LinearLayoutManager.HORIZONTAL),
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        // Item 1: left=40, right=80 → visible in viewport 0..100
        rv.scrollToPosition(1)

        assertThat(rv.computeHorizontalScrollOffset()).isEqualTo(0)
    }

    // --- Fix 3: Touch dispatch respects scroll offset ---

    @Test
    fun `touch dispatch hits first child when not scrolled`() {
        val clickedPositions = mutableListOf<Int>()
        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = View(context).apply {
                    layoutParams = View.LayoutParams(View.LayoutParams.MATCH_PARENT, 30)
                    isClickable = true
                }
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                holder.itemView.setOnClickListener { clickedPositions.add(position) }
            }

            override fun getItemCount() = 5
        }

        val (rv, window) = setupRecyclerView(
            adapter,
            RecyclerView.LinearLayoutManager(context),
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        // Tap at y=15 → item 0 (0..30)
        val downTime = System.currentTimeMillis()
        rv.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 50f, 15f))
        rv.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime + 100, MotionEvent.ACTION_UP, 50f, 15f))

        assertThat(clickedPositions).containsExactly(0)
    }

    @Test
    fun `touch dispatch hits correct child after vertical scroll`() {
        val clickedPositions = mutableListOf<Int>()
        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = View(context).apply {
                    layoutParams = View.LayoutParams(View.LayoutParams.MATCH_PARENT, 30)
                    isClickable = true
                }
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                holder.itemView.setOnClickListener { clickedPositions.add(position) }
            }

            override fun getItemCount() = 10
        }

        val (rv, window) = setupRecyclerView(
            adapter,
            RecyclerView.LinearLayoutManager(context),
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        // Scroll down 90px → viewport starts at layout y=90
        rv.scrollBy(0, 90)

        // Tap at viewport y=15 → layout y=90+15=105 → item 3 (90..120)
        val downTime = System.currentTimeMillis()
        rv.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 50f, 15f))
        rv.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime + 100, MotionEvent.ACTION_UP, 50f, 15f))

        assertThat(clickedPositions).containsExactly(3)
    }

    @Test
    fun `touch dispatch hits correct child after horizontal scroll`() {
        val clickedPositions = mutableListOf<Int>()
        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = View(context).apply {
                    layoutParams = View.LayoutParams(40, View.LayoutParams.MATCH_PARENT)
                    isClickable = true
                }
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                holder.itemView.setOnClickListener { clickedPositions.add(position) }
            }

            override fun getItemCount() = 10
        }

        val (rv, window) = setupRecyclerView(
            adapter,
            RecyclerView.LinearLayoutManager(context, RecyclerView.LinearLayoutManager.HORIZONTAL),
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        // Scroll right 80px → viewport starts at layout x=80
        rv.scrollBy(80, 0)

        // Tap at viewport x=15 → layout x=80+15=95 → item 2 (80..120)
        val downTime = System.currentTimeMillis()
        rv.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 15f, 50f))
        rv.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime + 100, MotionEvent.ACTION_UP, 15f, 50f))

        assertThat(clickedPositions).containsExactly(2)
    }

    // --- Fix 4: scrollBy respects layout orientation ---

    @Test
    fun `scrollBy ignores dx for vertical layout`() {
        val (rv, window) = setupRecyclerView(
            createAdapter(5, itemHeight = 30),
            RecyclerView.LinearLayoutManager(context),
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        rv.scrollBy(50, 0)

        // Vertical layout ignores dx
        assertThat(rv.computeHorizontalScrollOffset()).isEqualTo(0)
        assertThat(rv.computeVerticalScrollOffset()).isEqualTo(0)
    }

    @Test
    fun `scrollBy ignores dy for horizontal layout`() {
        val (rv, window) = setupRecyclerView(
            createHorizontalAdapter(5, itemWidth = 40),
            RecyclerView.LinearLayoutManager(context, RecyclerView.LinearLayoutManager.HORIZONTAL),
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        rv.scrollBy(0, 50)

        // Horizontal layout ignores dy
        assertThat(rv.computeHorizontalScrollOffset()).isEqualTo(0)
        assertThat(rv.computeVerticalScrollOffset()).isEqualTo(0)
    }

    @Test
    fun `scrollBy clamping works for horizontal layout`() {
        // 5 items x 40px = 200px total, viewport 100px, maxScrollX = 100
        val (rv, window) = setupRecyclerView(
            createHorizontalAdapter(5, itemWidth = 40),
            RecyclerView.LinearLayoutManager(context, RecyclerView.LinearLayoutManager.HORIZONTAL),
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        // Scroll past end
        rv.scrollBy(300, 0)
        assertThat(rv.computeHorizontalScrollOffset()).isEqualTo(100)

        // Scroll past beginning
        rv.scrollBy(-300, 0)
        assertThat(rv.computeHorizontalScrollOffset()).isEqualTo(0)
    }

    // --- Fix 5: scrollToPositionWithOffset ---

    @Test
    fun `scrollToPositionWithOffset aligns item at given offset from viewport start`() {
        // 10 items x 30px in 100px viewport
        val lm = RecyclerView.LinearLayoutManager(context)
        val (rv, window) = setupRecyclerView(
            createAdapter(10, itemHeight = 30),
            lm,
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        // Place item 3 (top=90) at 20px from viewport top → offset = 90-20 = 70
        lm.scrollToPositionWithOffset(3, 20)

        assertThat(rv.computeVerticalScrollOffset()).isEqualTo(70)
    }

    @Test
    fun `scrollToPositionWithOffset with zero offset aligns item to viewport top`() {
        val lm = RecyclerView.LinearLayoutManager(context)
        val (rv, window) = setupRecyclerView(
            createAdapter(10, itemHeight = 30),
            lm,
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        // Place item 5 (top=150) at viewport top → offset = 150-0 = 150
        lm.scrollToPositionWithOffset(5, 0)

        assertThat(rv.computeVerticalScrollOffset()).isEqualTo(150)
    }

    @Test
    fun `scrollToPositionWithOffset clamps to valid range`() {
        // 5 items x 30px = 150px, viewport 100px, maxScroll = 50
        val lm = RecyclerView.LinearLayoutManager(context)
        val (rv, window) = setupRecyclerView(
            createAdapter(5, itemHeight = 30),
            lm,
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        // Item 4 (top=120), offset=0 → want scrollY=120, but maxScroll=50
        lm.scrollToPositionWithOffset(4, 0)

        assertThat(rv.computeVerticalScrollOffset()).isEqualTo(50)
    }

    @Test
    fun `scrollToPositionWithOffset works for horizontal layout`() {
        val lm = RecyclerView.LinearLayoutManager(context, RecyclerView.LinearLayoutManager.HORIZONTAL)
        val (rv, window) = setupRecyclerView(
            createHorizontalAdapter(10, itemWidth = 40),
            lm,
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        // Item 3 (left=120), offset=10 → scrollX = 120-10 = 110
        lm.scrollToPositionWithOffset(3, 10)

        assertThat(rv.computeHorizontalScrollOffset()).isEqualTo(110)
    }

    @Test
    fun `scrollToPositionWithOffset notifies scroll listeners`() {
        val lm = RecyclerView.LinearLayoutManager(context)
        val (rv, window) = setupRecyclerView(
            createAdapter(10, itemHeight = 30),
            lm,
            windowWidth = 100,
            windowHeight = 100
        )
        window.measureAndLayout()

        var capturedDy = 0
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                capturedDy = dy
            }
        })

        // Item 3 (top=90), offset=20 → scrollY=70, dy=70
        lm.scrollToPositionWithOffset(3, 20)

        assertThat(capturedDy).isEqualTo(70)
    }
}

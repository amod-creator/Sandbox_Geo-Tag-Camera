package com.amod.geotagcamera.collage

object CollageLayoutEngine {

    fun getTemplates(imageCount: Int, ratio: CollageRatio): List<Template> {
        val allOptions = allTemplates.filter { it.imageCount == imageCount }
        val ratioValue = ratio.value()
        return allOptions.filter { template ->
            if (ratioValue > 1.2f && template.orientation == LayoutOrientation.STRICT_TALL) return@filter false
            if (ratioValue < 0.8f && template.orientation == LayoutOrientation.STRICT_WIDE) return@filter false
            true
        }
    }

    fun getTemplate(imageCount: Int, ratio: CollageRatio, index: Int): Template {
        val templates = getTemplates(imageCount, ratio)
        if (templates.isEmpty()) return allTemplates.first()
        return templates[index.coerceIn(0, templates.lastIndex)]
    }

    enum class LayoutOrientation { NEUTRAL, STRICT_TALL, STRICT_WIDE }

    data class Template(
        val name: String,
        val imageCount: Int,
        val orientation: LayoutOrientation = LayoutOrientation.NEUTRAL,
        val cells: List<LayoutCell>
    )

    private val allTemplates: List<Template> by lazy {
        buildList {
            // 2 IMAGES - 14 Layouts
            add(Template("Vertical Split", 2, LayoutOrientation.STRICT_WIDE, listOf(LayoutCell(0f, 0f, 0.5f, 1f), LayoutCell(0.5f, 0f, 1f, 1f))))
            add(Template("Horizontal Split", 2, LayoutOrientation.STRICT_TALL, listOf(LayoutCell(0f, 0f, 1f, 0.5f), LayoutCell(0f, 0.5f, 1f, 1f))))
            add(Template("Diagonal Split 1", 2, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0f, 0f, 0.6f, 1f), LayoutCell(0.6f, 0f, 1f, 1f))))
            add(Template("Diagonal Split 2", 2, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0f, 0f, 1f, 0.6f), LayoutCell(0f, 0.6f, 1f, 1f))))
            add(Template("Thumb Left", 2, LayoutOrientation.STRICT_WIDE, listOf(LayoutCell(0f, 0f, 0.35f, 1f), LayoutCell(0.35f, 0f, 1f, 1f))))
            add(Template("Thumb Right", 2, LayoutOrientation.STRICT_WIDE, listOf(LayoutCell(0f, 0f, 0.65f, 1f), LayoutCell(0.65f, 0f, 1f, 1f))))
            add(Template("Thumb Top", 2, LayoutOrientation.STRICT_TALL, listOf(LayoutCell(0f, 0f, 1f, 0.35f), LayoutCell(0f, 0.35f, 1f, 1f))))
            add(Template("Thumb Bottom", 2, LayoutOrientation.STRICT_TALL, listOf(LayoutCell(0f, 0f, 1f, 0.65f), LayoutCell(0f, 0.65f, 1f, 1f))))
            add(Template("PIP Top Right", 2, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0f, 0f, 1f, 1f), LayoutCell(0.6f, 0.05f, 0.95f, 0.4f))))
            add(Template("PIP Bottom Left", 2, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0f, 0f, 1f, 1f), LayoutCell(0.05f, 0.6f, 0.4f, 0.95f))))
            add(Template("Sidebar Left", 2, LayoutOrientation.STRICT_WIDE, listOf(LayoutCell(0f, 0f, 0.25f, 1f), LayoutCell(0.25f, 0f, 1f, 1f))))
            add(Template("Sidebar Right", 2, LayoutOrientation.STRICT_WIDE, listOf(LayoutCell(0f, 0f, 0.75f, 1f), LayoutCell(0.75f, 0f, 1f, 1f))))
            add(Template("Header Top", 2, LayoutOrientation.STRICT_TALL, listOf(LayoutCell(0f, 0f, 1f, 0.25f), LayoutCell(0f, 0.25f, 1f, 1f))))
            add(Template("Footer Bottom", 2, LayoutOrientation.STRICT_TALL, listOf(LayoutCell(0f, 0f, 1f, 0.75f), LayoutCell(0f, 0.75f, 1f, 1f))))
            
            // 3 IMAGES - 14 Layouts
            add(Template("Top 1 Bottom 2", 3, LayoutOrientation.STRICT_TALL, listOf(LayoutCell(0f, 0f, 1f, 0.5f), LayoutCell(0f, 0.5f, 0.5f, 1f), LayoutCell(0.5f, 0.5f, 1f, 1f))))
            add(Template("Bottom 1 Top 2", 3, LayoutOrientation.STRICT_TALL, listOf(LayoutCell(0f, 0f, 0.5f, 0.5f), LayoutCell(0.5f, 0f, 1f, 0.5f), LayoutCell(0f, 0.5f, 1f, 1f))))
            add(Template("Left 1 Right 2", 3, LayoutOrientation.STRICT_WIDE, listOf(LayoutCell(0f, 0f, 0.5f, 1f), LayoutCell(0.5f, 0f, 1f, 0.5f), LayoutCell(0.5f, 0.5f, 1f, 1f))))
            add(Template("Right 1 Left 2", 3, LayoutOrientation.STRICT_WIDE, listOf(LayoutCell(0f, 0f, 0.5f, 0.5f), LayoutCell(0f, 0.5f, 0.5f, 1f), LayoutCell(0.5f, 0f, 1f, 1f))))
            add(Template("3 Columns", 3, LayoutOrientation.STRICT_WIDE, listOf(LayoutCell(0f, 0f, 0.333f, 1f), LayoutCell(0.333f, 0f, 0.667f, 1f), LayoutCell(0.667f, 0f, 1f, 1f))))
            add(Template("3 Rows", 3, LayoutOrientation.STRICT_TALL, listOf(LayoutCell(0f, 0f, 1f, 0.333f), LayoutCell(0f, 0.333f, 1f, 0.667f), LayoutCell(0f, 0.667f, 1f, 1f))))
            add(Template("Center Focus", 3, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0f, 0.25f, 0.2f, 0.75f), LayoutCell(0.2f, 0f, 0.8f, 1f), LayoutCell(0.8f, 0.25f, 1f, 0.75f))))
            add(Template("Main + Stack Right", 3, LayoutOrientation.STRICT_WIDE, listOf(LayoutCell(0f, 0f, 0.7f, 1f), LayoutCell(0.7f, 0f, 1f, 0.5f), LayoutCell(0.7f, 0.5f, 1f, 1f))))
            add(Template("Main + Stack Left", 3, LayoutOrientation.STRICT_WIDE, listOf(LayoutCell(0f, 0f, 0.3f, 0.5f), LayoutCell(0f, 0.5f, 0.3f, 1f), LayoutCell(0.3f, 0f, 1f, 1f))))
            add(Template("Main + Stack Bottom", 3, LayoutOrientation.STRICT_TALL, listOf(LayoutCell(0f, 0f, 1f, 0.7f), LayoutCell(0f, 0.7f, 0.5f, 1f), LayoutCell(0.5f, 0.7f, 1f, 1f))))
            add(Template("Main + Stack Top", 3, LayoutOrientation.STRICT_TALL, listOf(LayoutCell(0f, 0f, 0.5f, 0.3f), LayoutCell(0.5f, 0f, 1f, 0.3f), LayoutCell(0f, 0.3f, 1f, 1f))))
            add(Template("PIP Double", 3, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0f, 0f, 1f, 1f), LayoutCell(0.05f, 0.05f, 0.35f, 0.35f), LayoutCell(0.65f, 0.65f, 0.95f, 0.95f))))
            add(Template("Diagonal Split 3", 3, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0f, 0f, 0.33f, 1f), LayoutCell(0.33f, 0f, 0.66f, 1f), LayoutCell(0.66f, 0f, 1f, 1f))))
            add(Template("Geometric 3", 3, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0f, 0f, 0.6f, 0.6f), LayoutCell(0.6f, 0f, 1f, 1f), LayoutCell(0f, 0.6f, 0.6f, 1f))))

            // 4 IMAGES - 14 Layouts
            add(Template("2x2 Grid", 4, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0f, 0f, 0.5f, 0.5f), LayoutCell(0.5f, 0f, 1f, 0.5f), LayoutCell(0f, 0.5f, 0.5f, 1f), LayoutCell(0.5f, 0.5f, 1f, 1f))))
            add(Template("Left 1 Right 3", 4, LayoutOrientation.STRICT_WIDE, listOf(LayoutCell(0f, 0f, 0.5f, 1f), LayoutCell(0.5f, 0f, 1f, 0.333f), LayoutCell(0.5f, 0.333f, 1f, 0.667f), LayoutCell(0.5f, 0.667f, 1f, 1f))))
            add(Template("Right 1 Left 3", 4, LayoutOrientation.STRICT_WIDE, listOf(LayoutCell(0f, 0f, 0.5f, 0.333f), LayoutCell(0f, 0.333f, 0.5f, 0.667f), LayoutCell(0f, 0.667f, 0.5f, 1f), LayoutCell(0.5f, 0f, 1f, 1f))))
            add(Template("Top 1 Bottom 3", 4, LayoutOrientation.STRICT_TALL, listOf(LayoutCell(0f, 0f, 1f, 0.5f), LayoutCell(0f, 0.5f, 0.333f, 1f), LayoutCell(0.333f, 0.5f, 0.667f, 1f), LayoutCell(0.667f, 0.5f, 1f, 1f))))
            add(Template("Bottom 1 Top 3", 4, LayoutOrientation.STRICT_TALL, listOf(LayoutCell(0f, 0f, 0.333f, 0.5f), LayoutCell(0.333f, 0f, 0.667f, 0.5f), LayoutCell(0.667f, 0f, 1f, 0.5f), LayoutCell(0f, 0.5f, 1f, 1f))))
            add(Template("Geometric Centered", 4, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0f, 0f, 1f, 0.25f), LayoutCell(0f, 0.75f, 1f, 1f), LayoutCell(0f, 0.25f, 0.5f, 0.75f), LayoutCell(0.5f, 0.25f, 1f, 0.75f))))
            add(Template("4 Columns", 4, LayoutOrientation.STRICT_WIDE, listOf(LayoutCell(0f, 0f, 0.25f, 1f), LayoutCell(0.25f, 0f, 0.5f, 1f), LayoutCell(0.5f, 0f, 0.75f, 1f), LayoutCell(0.75f, 0f, 1f, 1f))))
            add(Template("4 Rows", 4, LayoutOrientation.STRICT_TALL, listOf(LayoutCell(0f, 0f, 1f, 0.25f), LayoutCell(0f, 0.25f, 1f, 0.5f), LayoutCell(0f, 0.5f, 1f, 0.75f), LayoutCell(0f, 0.75f, 1f, 1f))))
            add(Template("Focus Offset Left", 4, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0f, 0f, 0.6f, 0.6f), LayoutCell(0.6f, 0f, 1f, 0.33f), LayoutCell(0.6f, 0.33f, 1f, 0.66f), LayoutCell(0.6f, 0.66f, 1f, 1f))))
            add(Template("Focus Offset Right", 4, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0.4f, 0.4f, 1f, 1f), LayoutCell(0f, 0f, 0.4f, 0.33f), LayoutCell(0f, 0.33f, 0.4f, 0.66f), LayoutCell(0f, 0.66f, 0.4f, 1f))))
            add(Template("Cross Split", 4, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0f, 0f, 0.33f, 0.5f), LayoutCell(0.33f, 0f, 1f, 0.5f), LayoutCell(0f, 0.5f, 0.66f, 1f), LayoutCell(0.66f, 0.5f, 1f, 1f))))
            add(Template("Middle Strip Vertical", 4, LayoutOrientation.STRICT_WIDE, listOf(LayoutCell(0f, 0f, 0.25f, 1f), LayoutCell(0.25f, 0f, 0.75f, 0.5f), LayoutCell(0.25f, 0.5f, 0.75f, 1f), LayoutCell(0.75f, 0f, 1f, 1f))))
            add(Template("Middle Strip Horizontal", 4, LayoutOrientation.STRICT_TALL, listOf(LayoutCell(0f, 0f, 1f, 0.25f), LayoutCell(0f, 0.25f, 0.5f, 0.75f), LayoutCell(0.5f, 0.25f, 1f, 0.75f), LayoutCell(0f, 0.75f, 1f, 1f))))
            add(Template("Diamond Sim", 4, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0.25f, 0f, 0.75f, 0.5f), LayoutCell(0f, 0.25f, 0.5f, 0.75f), LayoutCell(0.5f, 0.25f, 1f, 0.75f), LayoutCell(0.25f, 0.5f, 0.75f, 1f))))

            // 5 IMAGES - 14 Layouts
            add(Template("Top 2 Bottom 3", 5, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0f, 0f, 0.5f, 0.5f), LayoutCell(0.5f, 0f, 1f, 0.5f), LayoutCell(0f, 0.5f, 0.333f, 1f), LayoutCell(0.333f, 0.5f, 0.667f, 1f), LayoutCell(0.667f, 0.5f, 1f, 1f))))
            add(Template("Top 3 Bottom 2", 5, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0f, 0f, 0.333f, 0.5f), LayoutCell(0.333f, 0f, 0.667f, 0.5f), LayoutCell(0.667f, 0f, 1f, 0.5f), LayoutCell(0f, 0.5f, 0.5f, 1f), LayoutCell(0.5f, 0.5f, 1f, 1f))))
            add(Template("Left 1 Right 4", 5, LayoutOrientation.STRICT_WIDE, listOf(LayoutCell(0f, 0f, 0.5f, 1f), LayoutCell(0.5f, 0f, 1f, 0.25f), LayoutCell(0.5f, 0.25f, 1f, 0.5f), LayoutCell(0.5f, 0.5f, 1f, 0.75f), LayoutCell(0.5f, 0.75f, 1f, 1f))))
            add(Template("Right 1 Left 4", 5, LayoutOrientation.STRICT_WIDE, listOf(LayoutCell(0f, 0f, 0.5f, 0.25f), LayoutCell(0f, 0.25f, 0.5f, 0.5f), LayoutCell(0f, 0.5f, 0.5f, 0.75f), LayoutCell(0f, 0.75f, 0.5f, 1f), LayoutCell(0.5f, 0f, 1f, 1f))))
            add(Template("Center 1 Corners 4", 5, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0.25f, 0.25f, 0.75f, 0.75f), LayoutCell(0f, 0f, 1f, 0.25f), LayoutCell(0f, 0.75f, 1f, 1f), LayoutCell(0f, 0.25f, 0.25f, 0.75f), LayoutCell(0.75f, 0.25f, 1f, 0.75f))))
            add(Template("L-Shape 5", 5, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0f, 0f, 0.66f, 0.66f), LayoutCell(0.66f, 0f, 1f, 0.33f), LayoutCell(0.66f, 0.33f, 1f, 0.66f), LayoutCell(0f, 0.66f, 0.5f, 1f), LayoutCell(0.5f, 0.66f, 1f, 1f))))
            add(Template("Inverted L-Shape", 5, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0.33f, 0.33f, 1f, 1f), LayoutCell(0f, 0f, 0.5f, 0.33f), LayoutCell(0.5f, 0f, 1f, 0.33f), LayoutCell(0f, 0.33f, 0.33f, 0.66f), LayoutCell(0f, 0.66f, 0.33f, 1f))))
            add(Template("Top 1 Bottom 4", 5, LayoutOrientation.STRICT_TALL, listOf(LayoutCell(0f, 0f, 1f, 0.5f), LayoutCell(0f, 0.5f, 0.25f, 1f), LayoutCell(0.25f, 0.5f, 0.5f, 1f), LayoutCell(0.5f, 0.5f, 0.75f, 1f), LayoutCell(0.75f, 0.5f, 1f, 1f))))
            add(Template("Bottom 1 Top 4", 5, LayoutOrientation.STRICT_TALL, listOf(LayoutCell(0f, 0f, 0.25f, 0.5f), LayoutCell(0.25f, 0f, 0.5f, 0.5f), LayoutCell(0.5f, 0f, 0.75f, 0.5f), LayoutCell(0.75f, 0f, 1f, 0.5f), LayoutCell(0f, 0.5f, 1f, 1f))))
            add(Template("Middle Split Vertical", 5, LayoutOrientation.STRICT_WIDE, listOf(LayoutCell(0f, 0f, 0.33f, 0.5f), LayoutCell(0f, 0.5f, 0.33f, 1f), LayoutCell(0.33f, 0f, 0.66f, 1f), LayoutCell(0.66f, 0f, 1f, 0.5f), LayoutCell(0.66f, 0.5f, 1f, 1f))))
            add(Template("Middle Split Horizontal", 5, LayoutOrientation.STRICT_TALL, listOf(LayoutCell(0f, 0f, 0.5f, 0.33f), LayoutCell(0.5f, 0f, 1f, 0.33f), LayoutCell(0f, 0.33f, 1f, 0.66f), LayoutCell(0f, 0.66f, 0.5f, 1f), LayoutCell(0.5f, 0.66f, 1f, 1f))))
            add(Template("Stairs Layout", 5, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0f, 0f, 0.5f, 0.33f), LayoutCell(0.5f, 0f, 1f, 0.66f), LayoutCell(0f, 0.33f, 0.5f, 0.66f), LayoutCell(0f, 0.66f, 0.5f, 1f), LayoutCell(0.5f, 0.66f, 1f, 1f))))
            add(Template("Grid Center PIP", 5, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0f, 0f, 0.5f, 0.5f), LayoutCell(0.5f, 0f, 1f, 0.5f), LayoutCell(0f, 0.5f, 0.5f, 1f), LayoutCell(0.5f, 0.5f, 1f, 1f), LayoutCell(0.25f, 0.25f, 0.75f, 0.75f))))
            add(Template("5 Columns", 5, LayoutOrientation.STRICT_WIDE, listOf(LayoutCell(0f, 0f, 0.2f, 1f), LayoutCell(0.2f, 0f, 0.4f, 1f), LayoutCell(0.4f, 0f, 0.6f, 1f), LayoutCell(0.6f, 0f, 0.8f, 1f), LayoutCell(0.8f, 0f, 1f, 1f))))

            // 6 IMAGES - 14 Layouts
            add(Template("3x2 Grid", 6, LayoutOrientation.STRICT_TALL, listOf(LayoutCell(0f, 0f, 0.5f, 0.333f), LayoutCell(0.5f, 0f, 1f, 0.333f), LayoutCell(0f, 0.333f, 0.5f, 0.667f), LayoutCell(0.5f, 0.333f, 1f, 0.667f), LayoutCell(0f, 0.667f, 0.5f, 1f), LayoutCell(0.5f, 0.667f, 1f, 1f))))
            add(Template("2x3 Grid", 6, LayoutOrientation.STRICT_WIDE, listOf(LayoutCell(0f, 0f, 0.333f, 0.5f), LayoutCell(0.333f, 0f, 0.667f, 0.5f), LayoutCell(0.667f, 0f, 1f, 0.5f), LayoutCell(0f, 0.5f, 0.333f, 1f), LayoutCell(0.333f, 0.5f, 0.667f, 1f), LayoutCell(0.667f, 0.5f, 1f, 1f))))
            add(Template("Top 1 Mid 2 Bot 3", 6, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0f, 0f, 1f, 0.333f), LayoutCell(0f, 0.333f, 0.5f, 0.667f), LayoutCell(0.5f, 0.333f, 1f, 0.667f), LayoutCell(0f, 0.667f, 0.33f, 1f), LayoutCell(0.33f, 0.667f, 0.66f, 1f), LayoutCell(0.66f, 0.667f, 1f, 1f))))
            add(Template("Pinwheel 6", 6, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0f, 0f, 0.66f, 0.5f), LayoutCell(0.66f, 0f, 1f, 0.33f), LayoutCell(0.66f, 0.33f, 1f, 0.66f), LayoutCell(0f, 0.5f, 0.33f, 1f), LayoutCell(0.33f, 0.5f, 0.66f, 1f), LayoutCell(0.66f, 0.66f, 1f, 1f))))
            add(Template("Center 2 Corners 4", 6, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0f, 0f, 0.33f, 0.33f), LayoutCell(0.66f, 0f, 1f, 0.33f), LayoutCell(0.33f, 0f, 0.66f, 0.5f), LayoutCell(0.33f, 0.5f, 0.66f, 1f), LayoutCell(0f, 0.66f, 0.33f, 1f), LayoutCell(0.66f, 0.66f, 1f, 1f))))
            add(Template("Grid Offset 1", 6, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0f, 0f, 0.66f, 0.66f), LayoutCell(0.66f, 0f, 1f, 0.33f), LayoutCell(0.66f, 0.33f, 1f, 0.66f), LayoutCell(0f, 0.66f, 0.33f, 1f), LayoutCell(0.33f, 0.66f, 0.66f, 1f), LayoutCell(0.66f, 0.66f, 1f, 1f))))
            add(Template("Vertical Strip Left", 6, LayoutOrientation.STRICT_WIDE, listOf(LayoutCell(0f, 0f, 0.5f, 1f), LayoutCell(0.5f, 0f, 1f, 0.2f), LayoutCell(0.5f, 0.2f, 1f, 0.4f), LayoutCell(0.5f, 0.4f, 1f, 0.6f), LayoutCell(0.5f, 0.6f, 1f, 0.8f), LayoutCell(0.5f, 0.8f, 1f, 1f))))
            add(Template("Vertical Strip Right", 6, LayoutOrientation.STRICT_WIDE, listOf(LayoutCell(0f, 0f, 0.5f, 0.2f), LayoutCell(0f, 0.2f, 0.5f, 0.4f), LayoutCell(0f, 0.4f, 0.5f, 0.6f), LayoutCell(0f, 0.6f, 0.5f, 0.8f), LayoutCell(0f, 0.8f, 0.5f, 1f), LayoutCell(0.5f, 0f, 1f, 1f))))
            add(Template("Horizontal Strip Top", 6, LayoutOrientation.STRICT_TALL, listOf(LayoutCell(0f, 0f, 1f, 0.5f), LayoutCell(0f, 0.5f, 0.2f, 1f), LayoutCell(0.2f, 0.5f, 0.4f, 1f), LayoutCell(0.4f, 0.5f, 0.6f, 1f), LayoutCell(0.6f, 0.5f, 0.8f, 1f), LayoutCell(0.8f, 0.5f, 1f, 1f))))
            add(Template("Horizontal Strip Bottom", 6, LayoutOrientation.STRICT_TALL, listOf(LayoutCell(0f, 0f, 0.2f, 0.5f), LayoutCell(0.2f, 0f, 0.4f, 0.5f), LayoutCell(0.4f, 0f, 0.6f, 0.5f), LayoutCell(0.6f, 0f, 0.8f, 0.5f), LayoutCell(0.8f, 0f, 1f, 0.5f), LayoutCell(0f, 0.5f, 1f, 1f))))
            add(Template("Brick Wall", 6, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0f, 0f, 0.5f, 0.33f), LayoutCell(0.5f, 0f, 1f, 0.33f), LayoutCell(0f, 0.33f, 0.33f, 0.66f), LayoutCell(0.33f, 0.33f, 0.66f, 0.66f), LayoutCell(0.66f, 0.33f, 1f, 0.66f), LayoutCell(0f, 0.66f, 1f, 1f))))
            add(Template("Diamond Sim 6", 6, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0.25f, 0f, 0.75f, 0.33f), LayoutCell(0f, 0.33f, 0.5f, 0.66f), LayoutCell(0.5f, 0.33f, 1f, 0.66f), LayoutCell(0.25f, 0.66f, 0.75f, 1f), LayoutCell(0f, 0f, 0.25f, 0.33f), LayoutCell(0.75f, 0.66f, 1f, 1f))))
            add(Template("6 Blocks", 6, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0f, 0f, 0.4f, 0.4f), LayoutCell(0.4f, 0f, 1f, 0.4f), LayoutCell(0f, 0.4f, 0.6f, 0.8f), LayoutCell(0.6f, 0.4f, 1f, 0.8f), LayoutCell(0f, 0.8f, 0.5f, 1f), LayoutCell(0.5f, 0.8f, 1f, 1f))))
            add(Template("Central Hub", 6, LayoutOrientation.NEUTRAL, listOf(LayoutCell(0.25f, 0.25f, 0.75f, 0.75f), LayoutCell(0f, 0f, 1f, 0.25f), LayoutCell(0f, 0.75f, 1f, 1f), LayoutCell(0f, 0.25f, 0.25f, 0.5f), LayoutCell(0f, 0.5f, 0.25f, 0.75f), LayoutCell(0.75f, 0.25f, 1f, 0.75f))))
        }
    }
}

// SwipeToDeleteCallback.kt
package com.sKapit.smartassistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SwipeToDeleteCallback(
    private val context: Context,
    private val onSwipe: (Int) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

    override fun onChildDraw(
        c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
        dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView
        val density = context.resources.displayMetrics.density
        val marginH = (8 * density).toInt()
        val marginV = (6 * density).toInt()
        val cornerRadius = 12 * density
        val samOffsetDistance = (-35 * density).toInt()

        val swipeColor = ContextCompat.getColor(context, R.color.swipeBackground)
        val background = GradientDrawable().apply {
            setColor(swipeColor)
            this.cornerRadius = cornerRadius
        }

        val top = itemView.top + marginV
        val bottom = itemView.bottom - marginV
        val swipeRatio = Math.abs(dX) / itemView.width
        background.alpha = Math.min(140, (swipeRatio * 2 * 255).toInt())

        val icon = ContextCompat.getDrawable(context, R.drawable.ic_sam_super)

        if (icon != null) {
            val cardHeight = bottom - top
            val iconSize = (cardHeight * 1.2).toInt()
            val iconTop = top + (cardHeight - iconSize) / 2
            val iconBottom = iconTop + iconSize

            if (dX > 0) { // Swipe Right
                val leftBounds = itemView.left + marginH
                val rightBounds = itemView.left + dX.toInt()
                if (rightBounds > leftBounds) {
                    background.setBounds(leftBounds, top, rightBounds, bottom)
                    background.draw(c)
                }
                val iconRight = itemView.left + dX.toInt() - samOffsetDistance
                val iconLeft = iconRight - iconSize
                icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                icon.draw(c)
            } else if (dX < 0) { // Swipe Left
                val rightBounds = itemView.right - marginH
                val leftBounds = itemView.right + dX.toInt()
                if (leftBounds < rightBounds) {
                    background.setBounds(leftBounds, top, rightBounds, bottom)
                    background.draw(c)
                }
                val iconLeft = itemView.right + dX.toInt() + samOffsetDistance
                val iconRight = iconLeft + iconSize
                icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)

                c.save()
                c.scale(-1f, 1f, iconLeft + (iconSize / 2f), iconTop + (iconSize / 2f))
                icon.draw(c)
                c.restore()
            }
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        onSwipe(viewHolder.adapterPosition)
    }
}
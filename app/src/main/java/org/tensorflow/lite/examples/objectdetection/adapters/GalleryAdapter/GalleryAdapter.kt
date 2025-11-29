package org.tensorflow.lite.examples.objectdetection.adapters

import android.graphics.BitmapFactory
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class GalleryAdapter(
    private var images: List<File>
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    inner class ViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val size = parent.measuredWidth / 4
        val iv = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, size)
            scaleType = ImageView.ScaleType.CENTER_CROP
            adjustViewBounds = true
            setPadding(4, 4, 4, 4)
        }
        return ViewHolder(iv)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = images[position]
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        holder.imageView.setImageBitmap(bitmap)
    }

    override fun getItemCount(): Int = images.size

    fun updateData(newImages: List<File>) {
        images = newImages
        notifyDataSetChanged()
    }
}

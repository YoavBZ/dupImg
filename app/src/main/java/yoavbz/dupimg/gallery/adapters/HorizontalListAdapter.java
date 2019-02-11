package yoavbz.dupimg.gallery.adapters;

import android.app.Activity;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import yoavbz.dupimg.R;
import yoavbz.dupimg.models.Image;

import java.util.ArrayList;

/**
 * The type Horizontal list adapters.
 */
public class HorizontalListAdapter extends RecyclerView.Adapter<HorizontalListAdapter.ViewHolder> {

	private final ArrayList<Image> images;
	private final Activity activity;
	private final OnImageClickListener clickListener;
	private int currentPosition = 0;
	private ArrayList<Image> toDelete;

	/**
	 * Instantiates a new Horizontal list adapters.
	 *
	 * @param activity      The activity
	 * @param images        The list images
	 * @param clickListener The click listener for the images
	 * @param toDelete      Reference to the list of images to delete (selected images)
	 */
	public HorizontalListAdapter(Activity activity, ArrayList<Image> images, OnImageClickListener clickListener,
	                             ArrayList<Image> toDelete) {
		this.activity = activity;
		this.images = images;
		this.clickListener = clickListener;
		this.toDelete = toDelete;
	}

	@NonNull
	@Override
	public HorizontalListAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_horizontal, null));
	}

	@Override
	public void onBindViewHolder(@NonNull final HorizontalListAdapter.ViewHolder holder, final int position) {
		holder.image = images.get(position);

		Glide.with(activity)
		     .load(holder.image.getUri())
		     .apply(new RequestOptions().placeholder(R.drawable.gallery_placeholder))
		     .into(holder.thumbnail);

		holder.updateViews();
		holder.updateThumbnailFilter(position);
		holder.updateListener(position);
	}

	@Override
	public int getItemCount() {
		return images.size();
	}

	public void setSelectedItem(int position) {
		if (position >= getItemCount()) {
			return;
		}
		currentPosition = position;
		notifyDataSetChanged();
	}

	public interface OnImageClickListener {
		void onImageClick(int position);
	}

	class ViewHolder extends RecyclerView.ViewHolder {

		ImageView thumbnail;
		CheckBox checkbox;
		String filename;
		Image image;

		ViewHolder(View layout) {
			super(layout);
			thumbnail = layout.findViewById(R.id.image);
			checkbox = layout.findViewById(R.id.checkbox);
		}

		void updateViews() {
			filename = image.toString();
			// Set checkbox state according to selection
			checkbox.setChecked(toDelete.contains(image));
		}

		void updateThumbnailFilter(int position) {
			// Set thumbnail color filter
			ColorMatrix matrix = new ColorMatrix();
			if (currentPosition != position) {
				matrix.setSaturation(0);
				thumbnail.setAlpha(0.5f);
			} else {
				matrix.setSaturation(1);
				thumbnail.setAlpha(1f);
			}
			ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
			thumbnail.setColorFilter(filter);
		}

		void updateListener(int position) {
			// Set listeners
			thumbnail.setOnClickListener(view -> {
				if (!toDelete.contains(image)) {
					// Select holder
					toDelete.add(image);
					checkbox.setChecked(true);
				} else {
					// Deselect holder
					toDelete.remove(image);
					checkbox.setChecked(false);
				}
				activity.invalidateOptionsMenu();
				// Always perform clickListener click, to navigate to selected thumbnail
				clickListener.onImageClick(position);
			});
		}
	}
}

package yoavbz.dupimg.gallery.adapters;

import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.List;

import yoavbz.dupimg.R;

/**
 * The type Horizontal list adapters.
 */
public class HorizontalListAdapter extends RecyclerView.Adapter<HorizontalListAdapter.ViewHolder> {

	private final List<String> paths;
	private final AppCompatActivity activity;
	private final OnImageClickListener clickListener;
	private final ArrayList<String> toDelete;
	private int currentPosition = 0;

	/**
	 * Instantiates a new Horizontal list adapters.
	 *
	 * @param activity      The activity
	 * @param paths         The list images
	 * @param clickListener The click listener for the images
	 * @param toDelete      Reference to the list of images to delete (selected images)
	 */
	public HorizontalListAdapter(AppCompatActivity activity, List<String> paths, OnImageClickListener clickListener,
	                             ArrayList<String> toDelete) {
		this.activity = activity;
		this.paths = paths;
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
		holder.imagePath = paths.get(position);

		Glide.with(activity)
		     .load(holder.imagePath)
		     .apply(new RequestOptions().placeholder(R.drawable.gallery_placeholder))
		     .into(holder.thumbnail);

		holder.updateViews();
		holder.updateThumbnailFilter(position);
		holder.updateListener(position);
	}

	@Override
	public int getItemCount() {
		return paths.size();
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
		String imagePath;

		ViewHolder(View layout) {
			super(layout);
			thumbnail = layout.findViewById(R.id.image);
			checkbox = layout.findViewById(R.id.checkbox);
		}

		void updateViews() {
			// Set checkbox state according to selection
			checkbox.setChecked(toDelete.contains(imagePath));
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
				if (!toDelete.contains(imagePath)) {
					// Select holder
					toDelete.add(imagePath);
					checkbox.setChecked(true);
				} else {
					// Deselect holder
					toDelete.remove(imagePath);
					checkbox.setChecked(false);
				}
				activity.invalidateOptionsMenu();
				// Always perform clickListener click, to navigate to selected thumbnail
				clickListener.onImageClick(position);
			});
		}
	}
}

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
	private ArrayList<Image> selected;

	/**
	 * Instantiates a new Horizontal list adapters.
	 *
	 * @param activity           the activity
	 * @param images             the images
	 * @param imageClickListener the img click
	 * @param toDelete           Reference to the images list to delete
	 */
	public HorizontalListAdapter(Activity activity, ArrayList<Image> images, OnImageClickListener imageClickListener,
	                             ArrayList<Image> toDelete) {
		this.activity = activity;
		this.images = images;
		clickListener = imageClickListener;
		selected = toDelete;
	}

	@NonNull
	@Override
	public HorizontalListAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_horizontal, null));
	}

	@Override
	public void onBindViewHolder(@NonNull final HorizontalListAdapter.ViewHolder holder, final int position) {
		Image image = images.get(position);
		holder.filename = image.toString();

		Glide.with(activity)
		     .load(image.getUri())
		     .apply(new RequestOptions().placeholder(R.drawable.gallery_placeholder))
		     .into(holder.image);

		// Set thumbnail color filter
		ColorMatrix matrix = new ColorMatrix();
		if (currentPosition != position) {
			matrix.setSaturation(0);
			holder.image.setAlpha(0.5f);
		} else {
			matrix.setSaturation(1);
			holder.image.setAlpha(1f);
		}
		ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
		holder.image.setColorFilter(filter);

		// Set listeners
		Image currentImage = images.get(position);
		holder.image.setOnClickListener(view -> {
			if (!selected.contains(currentImage)) {
				// Select holder
				selected.add(image);
				holder.checkbox.setChecked(true);
			} else {
				// Deselect holder
				selected.remove(image);
				holder.checkbox.setChecked(false);
			}
			activity.invalidateOptionsMenu();
			// Always perform clickListener click, to navigate to selected image
			clickListener.onImageClick(position);
		});

		// Set checkbox state according to selection
		if (selected.contains(image)) {
			holder.checkbox.setChecked(true);
		} else {
			holder.checkbox.setChecked(false);
		}
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

		ImageView image;
		CheckBox checkbox;
		String filename;

		ViewHolder(View layout) {
			super(layout);
			image = layout.findViewById(R.id.image);
			checkbox = layout.findViewById(R.id.checkbox);
		}
	}
}

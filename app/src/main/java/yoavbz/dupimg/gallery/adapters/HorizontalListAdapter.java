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
import com.bignerdranch.android.multiselector.MultiSelector;
import com.bignerdranch.android.multiselector.SwappingHolder;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import yoavbz.dupimg.R;
import yoavbz.dupimg.models.Image;

import java.util.ArrayList;

/**
 * The type Horizontal list adapters.
 */
public class HorizontalListAdapter extends RecyclerView.Adapter<HorizontalListAdapter.ViewHolder> {

	private final ArrayList<Image> mDataset;
	private final Activity mActivity;
	private final OnImageClickListener mClickListener;
	private int mCurrentItem = 0;
	private MultiSelector multiSelector;

	/**
	 * Instantiates a new Horizontal list adapters.
	 *
	 * @param activity      the activity
	 * @param images        the images
	 * @param imgClick      the img click
	 * @param multiSelector the activity's multiSelector instance
	 */
	public HorizontalListAdapter(Activity activity, ArrayList<Image> images, OnImageClickListener imgClick, MultiSelector multiSelector) {
		mActivity = activity;
		mDataset = images;
		mClickListener = imgClick;
		this.multiSelector = multiSelector;
	}

	@NonNull
	@Override
	public HorizontalListAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		ViewHolder holder = new HorizontalListAdapter.ViewHolder(
				LayoutInflater.from(parent.getContext()).inflate(R.layout.item_horizontal, null));

		holder.setSelectionModeBackgroundDrawable(null);
		holder.setDefaultModeBackgroundDrawable(null);

		return holder;
	}

	@Override
	public void onBindViewHolder(@NonNull final HorizontalListAdapter.ViewHolder holder, final int position) {
		Image image = mDataset.get(position);
		holder.filename = image.toString();

		Glide.with(mActivity)
		     .load(image.getUri())
		     .apply(new RequestOptions().placeholder(R.drawable.media_gallery_placeholder))
		     .into(holder.image);

		// Set thumbnail color filter
		ColorMatrix matrix = new ColorMatrix();
		if (mCurrentItem != position) {
			matrix.setSaturation(0);
			holder.image.setAlpha(0.5f);
		} else {
			matrix.setSaturation(1);
			holder.image.setAlpha(1f);
		}
		ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
		holder.image.setColorFilter(filter);

		// Set listeners
		holder.image.setOnClickListener(view -> {
			if (!multiSelector.isSelected(position, 0)) {
				// Select holder
				multiSelector.setSelected(holder, true);
				holder.checkbox.setChecked(true);
			} else {
				// Deselect holder
				multiSelector.setSelected(holder, false);
				holder.checkbox.setChecked(false);
			}
			mActivity.invalidateOptionsMenu();
			// Always perform mClickListener click, to navigate to selected image
			mClickListener.onImageClick(position);
		});

		// Set checkbox state according to selection
		if (multiSelector.isSelected(position, 0)) {
			holder.checkbox.setChecked(true);
		} else {
			holder.checkbox.setChecked(false);
		}
	}

	@Override
	public int getItemCount() {
		return mDataset.size();
	}

	public void setSelectedItem(int position) {
		if (position >= getItemCount()) {
			return;
		}
		mCurrentItem = position;
		notifyDataSetChanged();
	}

	public interface OnImageClickListener {
		void onImageClick(int pos);
	}

	public class ViewHolder extends SwappingHolder {

		public ImageView image;
		CheckBox checkbox;
		String filename;

		ViewHolder(View layout) {
			super(layout, multiSelector);
			layout.setLongClickable(true);
			image = layout.findViewById(R.id.image);
			checkbox = layout.findViewById(R.id.checkbox);
		}
	}
}

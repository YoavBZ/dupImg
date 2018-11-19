package yoavbz.galleryml.gallery.adapters;

import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.bignerdranch.android.multiselector.MultiSelector;
import com.bignerdranch.android.multiselector.SwappingHolder;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import yoavbz.galleryml.R;
import yoavbz.galleryml.models.Image;

import java.util.ArrayList;

/**
 * The type Horizontal list adapters.
 */
public class HorizontalListAdapter extends RecyclerView.Adapter<HorizontalListAdapter.ViewHolder> {

	private final ArrayList<Image> mDataset;
	private final Context mContext;
	private final OnImageClick mClickListener;
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
	public HorizontalListAdapter(Context activity, ArrayList<Image> images, OnImageClick imgClick, MultiSelector multiSelector) {
		mContext = activity;
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

		Glide.with(mContext)
		     .load(image.getPath().toString())
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
			// Select this ViewHolder if in selection mode, otherwise call mClickListener
			if (multiSelector.isSelectable()) {
				if (!multiSelector.isSelected(position, 0)) {
					// Select
					multiSelector.setSelected(holder, true);
					holder.checkbox.setImageDrawable(mContext.getDrawable(R.drawable.ic_check_box));
				} else {
					// Deselect
					multiSelector.setSelected(holder, false);
					holder.checkbox.setImageDrawable(mContext.getDrawable(R.drawable.ic_check_box_outline_blank));
					// Exit selection mode if there're no more selections
					if (multiSelector.getSelectedPositions().isEmpty()) {
						multiSelector.setSelectable(false);
						notifyDataSetChanged();
					}
				}
			} else {
				mClickListener.onClick(position);
			}
		});

		holder.image.setOnLongClickListener(view -> {
			Log.d("ViewHolder", "onLongClick!");
			if (!multiSelector.isSelectable()) {
				multiSelector.setSelectable(true);
				multiSelector.setSelected(holder, true);
				notifyDataSetChanged();
				return true;
			}
			return false;
		});

		// Set checkbox state according to selection
		if (multiSelector.isSelectable()) {
			holder.checkbox.setVisibility(View.VISIBLE);
			if (multiSelector.isSelected(position, 0)) {
				holder.checkbox.setImageDrawable(mContext.getDrawable(R.drawable.ic_check_box));
			} else {
				holder.checkbox.setImageDrawable(mContext.getDrawable(R.drawable.ic_check_box_outline_blank));
			}
		} else {
			holder.checkbox.setVisibility(View.GONE);
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

	public interface OnImageClick {
		void onClick(int pos);
	}

	public class ViewHolder extends SwappingHolder {

		public ImageView image;
		public ImageView checkbox;
		String filename;

		ViewHolder(View layout) {
			super(layout, multiSelector);
			layout.setLongClickable(true);
			image = layout.findViewById(R.id.iv);
			checkbox = layout.findViewById(R.id.checkbox);
		}
	}
}

package yoavbz.galleryml.gallery.cluster.adapter;

import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
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
import yoavbz.galleryml.gallery.Image;

import java.util.ArrayList;

/**
 * The type Horizontal list adapters.
 */
public class HorizontalListAdapter extends RecyclerView.Adapter<HorizontalListAdapter.ViewHolder> {

	private final ArrayList<Image> mDataset;
	private final Context mContext;
	private final OnImageClick mClickListener;
	public int mBestItem = -1;
	private int mCurrentItem = -1;
	private MultiSelector multiSelector;

	/**
	 * Instantiates a new Horizontal list adapters.
	 *
	 * @param activity the activity
	 * @param images   the images
	 * @param imgClick the img click
	 */
	public HorizontalListAdapter(Context activity, ArrayList<Image> images, OnImageClick imgClick) {
		mContext = activity;
		mDataset = images;
		mClickListener = imgClick;
		multiSelector = new MultiSelector();
	}

	@Override
	public HorizontalListAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		return new HorizontalListAdapter.ViewHolder(LayoutInflater.from(parent.getContext())
		                                                          .inflate(R.layout.item_horizontal, null));
	}

	@Override
	public void onBindViewHolder(final HorizontalListAdapter.ViewHolder holder, final int position) {
		Image image = mDataset.get(position);
		Glide.with(mContext)
		     .load(image.getPath().toString())
		     .apply(new RequestOptions().placeholder(R.drawable.media_gallery_placeholder))
		     .into(holder.image);
		holder.filename = image.getFileName();

		ColorMatrix matrix = new ColorMatrix();
		if (mCurrentItem != position) {
			matrix.setSaturation(0);
			ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
			holder.image.setColorFilter(filter);
			holder.image.setAlpha(0.5f);
		} else {
			matrix.setSaturation(1);
			ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
			holder.image.setColorFilter(filter);
			holder.image.setAlpha(1f);
		}
		if (mBestItem == position) {
			holder.image_best.setVisibility(View.VISIBLE);
		} else {
			holder.image_best.setVisibility(View.GONE);
		}
		holder.image.setOnClickListener(view -> {
			// Select this ViewHolder if in selection mode, otherwise call mClickListener
			if (!multiSelector.tapSelection(holder)) {
				mClickListener.onClick(position);
			}
		});
		holder.image.setOnLongClickListener(view -> {
			Log.d("ViewHolder", "onLongClick!");
			if (!multiSelector.isSelectable()) {
				multiSelector.setSelectable(true);
				multiSelector.setSelected(holder, true);
				return true;
			}
			return false;
		});
	}

	@Override
	public int getItemCount() {
		return mDataset.size();
	}

	public void setSelectedItem(int position) {
		if (position >= mDataset.size()) {
			return;
		}
		mCurrentItem = position;
		notifyDataSetChanged();
	}

	public void setBestItem(String filename) {
		for (int i = 0; i < mDataset.size(); i++) {
			Image image = mDataset.get(i);
			if (image.getFileName().equals(filename)) {
				mBestItem = i;
			}
		}
	}

	public interface OnImageClick {
		void onClick(int pos);
	}

	public class ViewHolder extends SwappingHolder {

		public ImageView image;
		public ImageView image_best;
		public String filename;

		ViewHolder(View itemView) {
			super(itemView, multiSelector);
			itemView.setLongClickable(true);
			image = itemView.findViewById(R.id.iv);
			image_best = itemView.findViewById(R.id.iv_best);
		}
	}
}

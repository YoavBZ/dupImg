package yoavbz.dupimg.gallery;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import org.apache.commons.math3.ml.clustering.Cluster;
import yoavbz.dupimg.MainActivity;
import yoavbz.dupimg.R;
import yoavbz.dupimg.gallery.adapters.GridImagesAdapter;
import yoavbz.dupimg.models.Image;

import java.util.ArrayList;
import java.util.List;

public class GalleryView extends RecyclerView {

	private final MainActivity activity;
	private GridImagesAdapter mAdapter;
	private ArrayList<Cluster<Image>> imageClusters;
	private Drawable mPlaceHolder;

	/**
	 * Instantiates a new Media gallery view.
	 *
	 * @param context the context
	 */
	public GalleryView(Context context) {
		super(context);
		this.activity = (MainActivity) context;
		initAdapter();
	}

	/**
	 * Instantiates a new Media gallery view.
	 *
	 * @param context the context
	 * @param attrs   the attrs
	 */
	public GalleryView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		this.activity = (MainActivity) context;
		initAdapter();
		TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.GalleryView,
		                                                         0, 0);
		int spanCount = a.getInteger(R.styleable.GalleryView_span_count, 2);
		setSpanCount(spanCount);
		mPlaceHolder = a.getDrawable(R.styleable.GalleryView_place_holder);
		int orientation = a.getInt(R.styleable.GalleryView_gallery_orientation, VERTICAL);
		setOrientation(orientation);
	}

	/**
	 * Init.
	 */
	public void initAdapter() {
		imageClusters = new ArrayList<>();
		mAdapter = new GridImagesAdapter(activity, imageClusters, mPlaceHolder);
		setAdapter(mAdapter);
	}

	/**
	 * Sets images.
	 *
	 * @param clusters The image clusters
	 */
	public void setImageClusters(List<Cluster<Image>> clusters) {
		imageClusters.clear();
		if (!clusters.isEmpty()) {
			imageClusters.addAll(clusters);
			activity.textView.setVisibility(View.GONE);
		} else {
			activity.textView.setText("No duplicates were found :)");
		}
		notifyDataSetChanged();
	}

	/**
	 * Notify adapter for data set changed.
	 */
	public void notifyDataSetChanged() {
		if (mAdapter == null) {
			initAdapter();
		} else {
			mAdapter.notifyDataSetChanged();
		}
	}

	/**
	 * Sets on imageView click listener.
	 *
	 * @param onImageClickListener the on imageView click listener
	 */
	public void setOnImageClickListener(OnClusterClickListener onImageClickListener) {
		mAdapter.setOnImageClickListener(onImageClickListener);
	}

	/**
	 * Updates the span count in each row and fits the images size accordingly
	 *
	 * @param spanCount The span count
	 */
	public void setSpanCount(int spanCount) {
		// Fitting images size
		Point sizes = new Point();
		activity.getWindowManager().getDefaultDisplay().getSize(sizes);
		int width = sizes.x / spanCount;
		int height = 900 / spanCount;
		mAdapter.setImageSize(width, height);

		setLayoutManager(new GridLayoutManager(activity, spanCount));
	}

	/**
	 * Sets orientation for imageView scrolling.
	 *
	 * @param orientation the orientation
	 */
	public void setOrientation(int orientation) {
		((GridLayoutManager) getLayoutManager()).setOrientation(orientation);
	}

	public List<Image> getAllImages() {
		ArrayList<Image> images = new ArrayList<>();
		for (Cluster<Image> cluster : imageClusters) {
			images.addAll(cluster.getPoints());
		}
		return images;
	}

	public int getSpanCount() {
		return ((GridLayoutManager) getLayoutManager()).getSpanCount();
	}

	public interface OnClusterClickListener {
		/**
		 * @param cluster          The image list of the clicked cluster
		 * @param clusterThumbnail The thumbnail of the clicked cluster
		 */
		void onClusterClick(List<Image> cluster, ImageView clusterThumbnail);
	}
}

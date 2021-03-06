package yoavbz.dupimg.gallery;

import android.content.Context;
import android.graphics.Point;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.AttributeSet;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.apache.commons.math3.ml.clustering.Cluster;

import java.util.ArrayList;
import java.util.List;

import yoavbz.dupimg.Image;
import yoavbz.dupimg.MainActivity;
import yoavbz.dupimg.R;
import yoavbz.dupimg.gallery.adapters.GridImagesAdapter;

public class GalleryView extends RecyclerView {

	private final MainActivity activity;
	private final ArrayList<Cluster<Image>> imageClusters = new ArrayList<>();
	private final TransitionSet layoutTransition = new TransitionSet()
			.addTransition(new ChangeBounds())
			.addTransition(new Fade(Fade.IN));
	private GridImagesAdapter adapter;

	/**
	 * Instantiates a new Media gallery view.
	 *
	 * @param context the context
	 * @param attrs   the attrs
	 */
	public GalleryView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		this.activity = (MainActivity) context;
		init();
	}

	/**
	 * Initiates {@link GridLayoutManager}, {@link GridImagesAdapter} and {@link ScaleGestureDetector}.
	 */
	public void init() {
		setLayoutManager(new GridLayoutManager(activity, 2));
		adapter = new GridImagesAdapter(activity, imageClusters);
		setAdapter(adapter);
		setSpanCount(2);
	}

	/**
	 * Sets image clusters, updates main TextView and notifies adapter on data change.
	 *
	 * @param clusters The image clusters
	 */
	public void setImageClusters(List<Cluster<Image>> clusters) {
		imageClusters.clear();
		if (!clusters.isEmpty()) {
			imageClusters.addAll(clusters);
			activity.textView.setVisibility(View.GONE);
		} else {
			activity.textView.setText(activity.getString(R.string.no_duplicates));
		}
		adapter.notifyDataSetChanged();
	}

	public boolean isEmpty() {
		return imageClusters.isEmpty();
	}

	/**
	 * Sets on imageView click listener.
	 *
	 * @param onImageClickListener the on imageView click listener
	 */
	public void setOnImageClickListener(OnClusterClickListener onImageClickListener) {
		adapter.setOnImageClickListener(onImageClickListener);
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
		adapter.setImageSize(width, height);
		// Updating LayoutManager
		TransitionManager.beginDelayedTransition(this, layoutTransition);
		((GridLayoutManager) getLayoutManager()).setSpanCount(spanCount);
		adapter.notifyDataSetChanged();
	}

	public List<Image> getAllImages() {
		ArrayList<Image> images = new ArrayList<>();
		for (Cluster<Image> cluster : imageClusters) {
			images.addAll(cluster.getPoints());
		}
		return images;
	}

	public interface OnClusterClickListener {
		/**
		 * @param clusterPaths     The image list of the clicked cluster
		 * @param clusterThumbnail The thumbnail of the clicked cluster
		 */
		void onClusterClick(List<String> clusterPaths, ImageView clusterThumbnail);
	}
}

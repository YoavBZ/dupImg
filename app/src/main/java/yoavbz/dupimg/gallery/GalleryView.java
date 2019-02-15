package yoavbz.dupimg.gallery;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;
import org.apache.commons.math3.ml.clustering.Cluster;
import yoavbz.dupimg.MainActivity;
import yoavbz.dupimg.R;
import yoavbz.dupimg.gallery.adapters.GridImagesAdapter;
import yoavbz.dupimg.models.Image;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("ConstantConditions")
public class GalleryView extends RecyclerView {

	private final MainActivity activity;
	private GridImagesAdapter mAdapter;
	private ArrayList<Cluster<Image>> imageClusters;
	private Drawable mPlaceHolder;
	// Scaling
	private ScaleGestureDetector scaleDetector;
	private TransitionSet transition;

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
		TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.GalleryView, 0, 0);
		setSpanCount(2);
		mPlaceHolder = a.getDrawable(R.styleable.GalleryView_place_holder);
		transition = new TransitionSet()
				.addTransition(new ChangeBounds())
				.addTransition(new Fade(Fade.IN));
		initScaling();
	}

	private void initScaling() {
		// Initiating ScaleGestureDetector
		scaleDetector = new ScaleGestureDetector(activity, new ScaleGestureDetector.SimpleOnScaleGestureListener() {

			private static final float DISTANCE_THRESHOLD = 500; // In px
			private float initialSpan;

			@Override
			public boolean onScaleBegin(ScaleGestureDetector detector) {
				initialSpan = detector.getCurrentSpan();
				return true;
			}

			@Override
			public boolean onScale(ScaleGestureDetector detector) {
				int spanCount = ((GridLayoutManager) getLayoutManager()).getSpanCount();
				if (measureScale(detector)) {
					float scaleFactor = detector.getScaleFactor();
					if (scaleFactor < 1f && spanCount > 1) {
						setSpanCount(spanCount + 1);
						initialSpan = detector.getCurrentSpan();
					} else if (scaleFactor > 1f && spanCount < 3) {
						setSpanCount(spanCount - 1);
						initialSpan = detector.getCurrentSpan();
					}
				}
				return true;
			}

			private boolean measureScale(@NonNull ScaleGestureDetector detector) {
				float distance = Math.abs(detector.getCurrentSpan() - initialSpan);
				return distance > DISTANCE_THRESHOLD;
			}
		});
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
			activity.textView.setText(activity.getString(R.string.no_duplicates));
		}
		notifyDataSetChanged();
	}

	/**
	 * Notify adapter for data set changed.
	 */
	public void notifyDataSetChanged() {
		mAdapter.notifyDataSetChanged();
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
		// Updating LayoutManager
		TransitionManager.beginDelayedTransition(this, transition);
		setLayoutManager(new GridLayoutManager(activity, spanCount));
	}

	public List<Image> getAllImages() {
		ArrayList<Image> images = new ArrayList<>();
		for (Cluster<Image> cluster : imageClusters) {
			images.addAll(cluster.getPoints());
		}
		return images;
	}

	@Override
	@SuppressLint("ClickableViewAccessibility")
	public boolean onTouchEvent(@NonNull MotionEvent ev) {
		super.onTouchEvent(ev);
		scaleDetector.onTouchEvent(ev);
		return true;
	}

	public interface OnClusterClickListener {
		/**
		 * @param cluster          The image list of the clicked cluster
		 * @param clusterThumbnail The thumbnail of the clicked cluster
		 */
		void onClusterClick(List<Image> cluster, ImageView clusterThumbnail);
	}
}

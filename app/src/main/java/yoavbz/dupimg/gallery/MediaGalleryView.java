package yoavbz.dupimg.gallery;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import org.apache.commons.math3.ml.clustering.Cluster;
import yoavbz.dupimg.R;
import yoavbz.dupimg.gallery.adapters.GridImagesAdapter;
import yoavbz.dupimg.models.Image;

import java.util.ArrayList;
import java.util.List;

public class MediaGalleryView extends RecyclerView {

	public static final int HORIZONTAL = 0;
	public static final int VERTICAL = 1;
	public static final int DEFAULT_SIZE = 6131;
	private final Context mContext;
	private GridImagesAdapter mAdapter;
	private ArrayList<Cluster<Image>> imageClusters;
	private Drawable mPlaceHolder;
	private int mSpanCount;
	private int mOrientation;
	private int mWidth;
	private int mHeight;

	/**
	 * Instantiates a new Media gallery view.
	 *
	 * @param context the context
	 */
	public MediaGalleryView(Context context) {
		super(context);
		this.mContext = context;
		init();
	}

	/**
	 * Instantiates a new Media gallery view.
	 *
	 * @param context the context
	 * @param attrs   the attrs
	 */
	public MediaGalleryView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		this.mContext = context;
		TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.MediaGalleryView,
		                                                         0, 0);
		mSpanCount = a.getInteger(R.styleable.MediaGalleryView_span_count, 2);
		mPlaceHolder = a.getDrawable(R.styleable.MediaGalleryView_place_holder);
		mOrientation = a.getInt(R.styleable.MediaGalleryView_gallery_orientation, VERTICAL);
		mWidth = a.getDimensionPixelSize(R.styleable.MediaGalleryView_image_width, DEFAULT_SIZE);
		mHeight = a.getDimensionPixelSize(R.styleable.MediaGalleryView_image_height, 450);
		if (mPlaceHolder == null) {
			mPlaceHolder = ContextCompat.getDrawable(mContext, R.drawable.media_gallery_placeholder);
		}
		init();
	}

	/**
	 * Init.
	 */
	public void init() {
		imageClusters = new ArrayList<>();
		mAdapter = new GridImagesAdapter(mContext, imageClusters, mPlaceHolder);
		setOrientation(mOrientation);
		mAdapter.setImageSize(mWidth, mHeight);
		setAdapter(mAdapter);
	}

	/**
	 * Sets images.
	 *
	 * @param clusters the item list
	 */
	public void setImageClusters(List<Cluster<Image>> clusters) {
		this.imageClusters.clear();
		this.imageClusters.addAll(clusters);
	}

	/**
	 * Notify adapter for data set changed.
	 */
	public void notifyDataSetChanged() {
		if (mAdapter != null) {
			mAdapter.notifyDataSetChanged();
		} else {
			init();
		}
	}

	/**
	 * Sets place holder.
	 *
	 * @param placeHolder the place holder
	 */
	public void setPlaceHolder(int placeHolder) {
		this.mPlaceHolder = ContextCompat.getDrawable(mContext, placeHolder);
		mAdapter.setImgPlaceHolder(mPlaceHolder);
	}

	/**
	 * Sets on imageView click listener.
	 *
	 * @param onImageClickListener the on imageView click listener
	 */
	public void setOnImageClickListener(MediaGalleryView.OnImageClicked onImageClickListener) {
		mAdapter.setOnImageClickListener(onImageClickListener);
	}

	/**
	 * Span count in each row.
	 *
	 * @param spanCount the span count
	 */
	public void setSpanCount(int spanCount) {
		this.mSpanCount = spanCount;
		setLayoutManager(new GridLayoutManager(mContext, mSpanCount));
	}

	/**
	 * Sets orientation for imageView scrolling.
	 *
	 * @param orientation the orientation
	 */
	public void setOrientation(int orientation) {
		this.mOrientation = orientation;
		if (orientation == HORIZONTAL) {
			setLayoutManager(new GridLayoutManager(mContext, mSpanCount, GridLayoutManager.HORIZONTAL, false));
		} else if (orientation == VERTICAL) {
			setLayoutManager(new GridLayoutManager(mContext, mSpanCount, GridLayoutManager.VERTICAL, false));
		}
	}

	public void setImageSize(int width, int height) {
		this.mWidth = width;
		this.mHeight = height;
		mAdapter.setImageSize(width, height);
	}

	/**
	 * The interface On imageView clicked.
	 */
	public interface OnImageClicked {
		/**
		 * On imageView clicked.
		 *
		 * @param pos the pos
		 */
		void onImageClicked(int pos);
	}
}

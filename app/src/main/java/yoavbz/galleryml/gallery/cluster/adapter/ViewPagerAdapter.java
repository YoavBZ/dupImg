package yoavbz.galleryml.gallery.cluster.adapter;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.view.PagerAdapter;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import com.bumptech.glide.Glide;
import com.github.chrisbanes.photoview.PhotoViewAttacher;
import yoavbz.galleryml.gallery.Image;
import yoavbz.galleryml.R;

import java.util.ArrayList;

/**
 * The type View pager adapter.
 */
public class ViewPagerAdapter extends PagerAdapter {

	private Activity activity;
	private LayoutInflater mLayoutInflater;
	private ArrayList<Image> mDataSet;
	private boolean isShowing = true;
	private Toolbar toolbar;
	private RecyclerView imagesHorizontalList;
	private ImageView imageView;

	/**
	 * Instantiates a new View pager adapter.
	 *
	 * @param activity             the activity
	 * @param dataSet              the images
	 * @param toolbar              the toolbar
	 * @param imagesHorizontalList the images horizontal list
	 */
	public ViewPagerAdapter(Activity activity, ArrayList<Image> dataSet, Toolbar toolbar, RecyclerView
			imagesHorizontalList) {
		this.activity = activity;
		mLayoutInflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		this.mDataSet = dataSet;
		this.toolbar = toolbar;
		this.imagesHorizontalList = imagesHorizontalList;
	}

	@Override
	public int getCount() {
		return mDataSet.size();
	}

	@Override
	public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
		return view == object;
	}

	@NonNull
	@Override
	public Object instantiateItem(@NonNull ViewGroup container, int position) {
		View itemView = mLayoutInflater.inflate(R.layout.pager_item, container, false);
		Image image = mDataSet.get(position);
		imageView = itemView.findViewById(R.id.iv);
		Glide.with(activity).load(image.getPath().toString()).into(imageView);
		container.addView(itemView);
		return itemView;
	}

	private void onTap() {
		PhotoViewAttacher mPhotoViewAttacher = new PhotoViewAttacher(imageView);

		mPhotoViewAttacher.setOnPhotoTapListener((view, x, y) -> {
			if (isShowing) {
				isShowing = false;
				toolbar.animate()
						.translationY(-toolbar.getBottom())
						.setInterpolator(new AccelerateInterpolator())
						.start();
				imagesHorizontalList.animate()
						.translationY(imagesHorizontalList.getBottom())
						.setInterpolator(new AccelerateInterpolator())
						.start();
			} else {
				isShowing = true;
				toolbar.animate().translationY(0).setInterpolator(new DecelerateInterpolator()).start();
				imagesHorizontalList.animate().translationY(0).setInterpolator(new DecelerateInterpolator()).start();
			}
		});
	}

	@Override
	public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
		container.removeView((RelativeLayout) object);
	}
}

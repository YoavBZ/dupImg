package yoavbz.dupimg.gallery.adapters;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.v4.view.PagerAdapter;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.RelativeLayout;
import com.bumptech.glide.Glide;
import com.github.chrisbanes.photoview.PhotoView;
import com.github.chrisbanes.photoview.PhotoViewAttacher;
import yoavbz.dupimg.R;
import yoavbz.dupimg.models.Image;

import java.util.ArrayList;

/**
 * The type View pager adapter.
 */
public class ViewPagerAdapter extends PagerAdapter {

	private Activity activity;
	private ArrayList<Image> mDataSet;
	private boolean hideHorizontalList = true;
	private Toolbar toolbar;
	private RecyclerView imagesHorizontalList;
	private String transition;

	/**
	 * Instantiates a new View pager adapter.
	 *
	 * @param activity             the activity
	 * @param dataSet              the images
	 * @param toolbar              the toolbar
	 * @param imagesHorizontalList the images horizontal list
	 * @param transition           the transition name
	 */
	public ViewPagerAdapter(Activity activity, ArrayList<Image> dataSet, Toolbar toolbar, RecyclerView
			imagesHorizontalList, String transition) {
		this.activity = activity;
		this.mDataSet = dataSet;
		this.toolbar = toolbar;
		this.imagesHorizontalList = imagesHorizontalList;
		this.transition = transition;
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
		View itemView = activity.getLayoutInflater().inflate(R.layout.pager_item, container, false);
		Image image = mDataSet.get(position);
		PhotoView photoView = itemView.findViewById(R.id.image);
		Glide.with(activity)
		     .load(image.getUri())
		     .into(photoView);
		if (position == 0) {
			photoView.setTransitionName(transition);
			photoView.getViewTreeObserver().addOnPreDrawListener(
					new ViewTreeObserver.OnPreDrawListener() {
						@Override
						public boolean onPreDraw() {
							photoView.getViewTreeObserver().removeOnPreDrawListener(this);
							activity.startPostponedEnterTransition();
							photoView.setTransitionName(null);
							return true;
						}
					});
		}
		// Setting OnPhotoTapListener to show/hide the imagesHorizontalList
		PhotoViewAttacher photoViewAttacher = new PhotoViewAttacher(photoView);
		photoViewAttacher.setOnPhotoTapListener((view, x, y) -> {
			Log.d("ViewPagerAdapter", "onTap");
			if (hideHorizontalList) {
				hideHorizontalList = false;
				toolbar.animate()
				       .translationY(-toolbar.getBottom())
				       .setInterpolator(new AccelerateInterpolator())
				       .start();
				imagesHorizontalList.animate()
				                    .translationY(imagesHorizontalList.getBottom())
				                    .setInterpolator(new AccelerateInterpolator())
				                    .start();
			} else {
				hideHorizontalList = true;
				toolbar.animate().translationY(0).setInterpolator(new DecelerateInterpolator()).start();
				imagesHorizontalList.animate().translationY(0).setInterpolator(new DecelerateInterpolator()).start();
			}
		});

		container.addView(itemView);
		return itemView;
	}

	@Override
	public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
		container.removeView((RelativeLayout) object);
	}
}

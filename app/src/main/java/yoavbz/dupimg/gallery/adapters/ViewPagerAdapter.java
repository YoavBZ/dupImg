package yoavbz.dupimg.gallery.adapters;

import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.github.chrisbanes.photoview.PhotoView;
import com.github.chrisbanes.photoview.PhotoViewAttacher;
import yoavbz.dupimg.R;
import yoavbz.dupimg.models.Image;

import java.util.ArrayList;

/**
 * The type View pager adapter.
 */
public class ViewPagerAdapter extends PagerAdapter {

	private AppCompatActivity activity;
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
	public ViewPagerAdapter(AppCompatActivity activity, ArrayList<Image> dataSet, Toolbar toolbar, RecyclerView
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
		if (position == 0 && transition != null) {
			// Starting transition when loading is finished (happens only once for first cluster image)
			photoView.setTransitionName(transition);
			Glide.with(activity)
			     .load(image.getPath())
			     .apply(RequestOptions.noAnimation())
			     .listener(new RequestListener<Drawable>() {
				     @Override
				     public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
					     photoView.setTransitionName(null);
					     transition = null;
					     return false;
				     }

				     @Override
				     public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
					     activity.startPostponedEnterTransition();
					     photoView.setTransitionName(null);
					     transition = null;
					     return false;
				     }
			     })
			     .into(photoView);
		} else {
			// Default image loading
			Glide.with(activity)
			     .load(image.getPath())
			     .into(photoView);
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

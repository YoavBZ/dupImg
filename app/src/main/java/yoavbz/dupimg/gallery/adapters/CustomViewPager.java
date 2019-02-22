package yoavbz.dupimg.gallery.adapters;

import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;

/**
 * A Custom view pager class for suppressing gesture exceptions.
 */
public class CustomViewPager extends ViewPager {
	/**
	 * Instantiates a new Custom view pager.
	 *
	 * @param context the context
	 */
	public CustomViewPager(Context context) {
		super(context);
	}

	/**
	 * Instantiates a new Custom view pager.
	 *
	 * @param context the context
	 * @param attrs   the attrs
	 */
	public CustomViewPager(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		try {
			return super.onTouchEvent(event);
		} catch (IllegalArgumentException ignored) {
		}
		return false;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
		try {
			return super.onInterceptTouchEvent(event);
		} catch (IllegalArgumentException ignored) {
		}
		return false;
	}
}
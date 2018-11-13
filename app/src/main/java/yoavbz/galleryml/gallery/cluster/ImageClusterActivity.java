package yoavbz.galleryml.gallery.cluster;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.Spanned;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;
import yoavbz.galleryml.R;
import yoavbz.galleryml.gallery.Constants;
import yoavbz.galleryml.gallery.Image;
import yoavbz.galleryml.gallery.cluster.adapter.HorizontalListAdapter;
import yoavbz.galleryml.gallery.cluster.adapter.ViewPagerAdapter;

import java.util.ArrayList;

/**
 * The type Media gallery activity.
 */
public class ImageClusterActivity extends AppCompatActivity implements ViewPager.OnPageChangeListener, HorizontalListAdapter.OnImgClick {

	private ViewPager mViewPager;
	private RecyclerView imagesHorizontalList;
	private HorizontalListAdapter hAdapter;
	protected Toolbar mToolbar;
	protected ArrayList<Image> dataSet;
	protected String title;
	protected int selectedImagePosition;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(getResourceLayoutId());
		initValues();
		initViews();
		initAdapters();
	}

	private void initValues() {
		Intent intent = getIntent();
		if (intent == null || intent.getExtras() == null)
			return;
		Bundle bundle = intent.getExtras();
		dataSet = bundle.getParcelableArrayList(Constants.IMAGES);
		title = bundle.getString(Constants.TITLE);
		selectedImagePosition = 0;
	}

	private void initViews() {
		mToolbar = findViewById(R.id.toolbar_media_gallery);
		if (getSupportActionBar() != null) {
			mToolbar.setVisibility(View.GONE);
			getSupportActionBar().setTitle(String.valueOf(title));
		} else {
			setSupportActionBar(mToolbar);
			mToolbar.setTitle(String.valueOf(title));
		}
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);

		mViewPager = findViewById(R.id.pager);
		imagesHorizontalList = findViewById(R.id.horizontal_list);
		RelativeLayout mMainLayout = findViewById(R.id.mainLayout);
		mMainLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.white));
	}

	public void initAdapters() {
		mViewPager.setAdapter(new ViewPagerAdapter(this, dataSet, mToolbar, imagesHorizontalList));
		hAdapter = new HorizontalListAdapter(this, dataSet, this);
		imagesHorizontalList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
		imagesHorizontalList.setAdapter(hAdapter);
		hAdapter.notifyDataSetChanged();
		mViewPager.addOnPageChangeListener(this);
		hAdapter.setSelectedItem(selectedImagePosition);
		mViewPager.setCurrentItem(selectedImagePosition);
	}

	public int getResourceLayoutId() {
		return R.layout.activity_gallery;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.image_toolbar_menu, menu);
		if (hAdapter.mBestItem == -1) {
			menu.findItem(R.id.action_select).setVisible(false);
		} else {
			menu.findItem(R.id.action_select).setVisible(true);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Fetching selected item
		HorizontalListAdapter.ViewHolder bestItem = (HorizontalListAdapter.ViewHolder) imagesHorizontalList.findViewHolderForAdapterPosition(hAdapter.mBestItem);

		if (item.getItemId() == R.id.action_select && bestItem != null) {
			Spanned message = Html.fromHtml("Your choice: <b>" + bestItem.filename + "</b><br>Are you sure you want to delete the rest of the images?");
			new AlertDialog.Builder(this).setTitle("Delete Duplicates?")
					.setMessage(message)
					.setPositiveButton("DELETE", (dialog, which) -> {
						Toast.makeText(this, "Deleting duplicates..", Toast.LENGTH_LONG).show();
						// Retuning the selected item filename
						Intent data = new Intent();
						data.putExtra("filename", bestItem.filename);
						setResult(RESULT_OK, data);
						finish();
					})
					.setNegativeButton("Cancel", null)
					.show();
		} else if (item.getItemId() == R.id.action_mark_best) {
			// Refreshing action bar menu
			invalidateOptionsMenu();
			// Fetching current item
			HorizontalListAdapter.ViewHolder currentItem = (HorizontalListAdapter.ViewHolder) imagesHorizontalList.findViewHolderForAdapterPosition(mViewPager
					.getCurrentItem());
			// Replacing current selected item with the new one
			if (bestItem != null) {
				bestItem.image_best.setVisibility(View.GONE);
			}
			currentItem.image_best.setVisibility(View.VISIBLE);
			// Updating selected item
			hAdapter.setBestItem(currentItem.filename);
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onSupportNavigateUp() {
		onBackPressed();
		return true;
	}

	@Override
	public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
	}

	@Override
	public void onPageSelected(int position) {
		imagesHorizontalList.smoothScrollToPosition(position);
		hAdapter.setSelectedItem(position);
	}

	@Override
	public void onPageScrollStateChanged(int state) {

	}

	@Override
	public void onClick(int pos) {
		mViewPager.setCurrentItem(pos, true);
	}

}

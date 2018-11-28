package yoavbz.dupimg.gallery;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import com.bignerdranch.android.multiselector.MultiSelector;
import yoavbz.dupimg.MainActivity;
import yoavbz.dupimg.R;
import yoavbz.dupimg.gallery.adapters.CustomViewPager;
import yoavbz.dupimg.gallery.adapters.HorizontalListAdapter;
import yoavbz.dupimg.gallery.adapters.ViewPagerAdapter;
import yoavbz.dupimg.models.Image;

import java.util.ArrayList;

/**
 * The type Media gallery activity.
 */
public class ImageClusterActivity extends AppCompatActivity
		implements ViewPager.OnPageChangeListener, HorizontalListAdapter.OnImageClick {

	protected Toolbar mToolbar;
	protected ArrayList<Image> dataSet;
	protected String title;
	private CustomViewPager mViewPager;
	private RecyclerView imagesHorizontalList;
	private HorizontalListAdapter hAdapter;
	private MultiSelector multiSelector;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(getResourceLayoutId());
		multiSelector = new MultiSelector();
		initValues();
		initViews();
		initAdapters();
	}

	private void initValues() {
		Intent intent = getIntent();
		if (intent == null || intent.getExtras() == null) {
			return;
		}
		Bundle bundle = intent.getExtras();
		dataSet = bundle.getParcelableArrayList("IMAGES");
	}

	private void initViews() {
		mToolbar = findViewById(R.id.toolbar_media_gallery);
		if (getSupportActionBar() != null) {
			mToolbar.setVisibility(View.GONE);
			getSupportActionBar().setTitle("Long click to select:");
		} else {
			setSupportActionBar(mToolbar);
			mToolbar.setTitle("Long click to select:");
		}
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);

		mViewPager = findViewById(R.id.pager);
		imagesHorizontalList = findViewById(R.id.horizontal_list);
	}

	public void initAdapters() {
		mViewPager.setAdapter(new ViewPagerAdapter(this, dataSet, mToolbar, imagesHorizontalList));
		// Adapter for the horizontal RecyclerView of the dataset
		hAdapter = new HorizontalListAdapter(this, dataSet, this, multiSelector);
		imagesHorizontalList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
		imagesHorizontalList.setAdapter(hAdapter);
		hAdapter.notifyDataSetChanged();
		mViewPager.addOnPageChangeListener(this);
	}

	public int getResourceLayoutId() {
		return R.layout.activity_gallery;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.image_toolbar_menu, menu);
		return true;
	}

	/**
	 * Handles selecting images to keep, by popping a verification dialog
	 *
	 * @param item The selected menu item
	 * @return super method return value
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.action_select) {
			ArrayList<String> selected = new ArrayList<>();
			for (int i : multiSelector.getSelectedPositions()) {
				selected.add(dataSet.get(i).toString());
			}
			Spanned message = Html.fromHtml("Your choice:<br><b>" + String.join(",<br>", selected) +
					                                "</b><br>Are you sure you want to delete the rest of the images?",
			                                0);
			new AlertDialog.Builder(this)
					.setTitle("Delete Duplicates?")
					.setMessage(message)
					.setPositiveButton("DELETE", (dialog, which) -> {
						Toast.makeText(this, "Deleting duplicates..", Toast.LENGTH_LONG).show();
						// Removing selected images from dataset
						for (int i = dataSet.size() - 1; i > 0; i--) {
							if (multiSelector.isSelected(i, 0)) {
								dataSet.remove(i);
							}
						}
						Log.d(MainActivity.TAG, "ImageClusterActivity: dataSet after delete: " + dataSet.toString());
						Intent data = new Intent();
						data.putExtra("toDelete", dataSet);
						setResult(RESULT_OK, data);
						finish();
					})
					.setNegativeButton("Cancel", null)
					.show();
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Behaves like onBackPressed
	 *
	 * @return true always
	 */
	@Override
	public boolean onSupportNavigateUp() {
		onBackPressed();
		return true;
	}

	/**
	 * Exits selection mode (and notifies adapter) or returning to main activity
	 */
	@Override
	public void onBackPressed() {
		if (multiSelector.isSelectable()) {
			// Exit selection mod
			multiSelector.clearSelections();
			multiSelector.setSelectable(false);
			// Remove checkboxes
			hAdapter.notifyDataSetChanged();
		} else {
			super.onBackPressed();
		}
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

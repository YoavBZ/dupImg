package yoavbz.dupimg.gallery;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;
import yoavbz.dupimg.Image;
import yoavbz.dupimg.R;
import yoavbz.dupimg.gallery.adapters.CustomViewPager;
import yoavbz.dupimg.gallery.adapters.HorizontalListAdapter;
import yoavbz.dupimg.gallery.adapters.ViewPagerAdapter;

import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * The type Media gallery activity.
 */
public class ImageClusterActivity extends AppCompatActivity
		implements ViewPager.OnPageChangeListener, HorizontalListAdapter.OnImageClickListener {

	private Toolbar mToolbar;
	private ArrayList<Image> images;
	private ArrayList<Image> toDelete;
	private CustomViewPager mViewPager;
	private RecyclerView imagesHorizontalList;
	private HorizontalListAdapter hAdapter;
	private String transition;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		postponeEnterTransition();
		setContentView(R.layout.activity_gallery);
		initValues();
		initViews();
		initAdapters();
	}

	private void initValues() {
		images = getIntent().getParcelableArrayListExtra("IMAGES");
		toDelete = new ArrayList<>();
		transition = getIntent().getStringExtra("transition");
	}

	@SuppressWarnings("ConstantConditions")
	private void initViews() {
		mToolbar = findViewById(R.id.toolbar_media_gallery);
		mToolbar.setVisibility(View.GONE);
		ActionBar actionBar = getSupportActionBar();
		actionBar.setTitle(R.string.cluster_activity_title);
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeButtonEnabled(true);

		mViewPager = findViewById(R.id.pager);
		imagesHorizontalList = findViewById(R.id.horizontal_list);
	}

	private void initAdapters() {
		mViewPager.setAdapter(new ViewPagerAdapter(this, images, mToolbar, imagesHorizontalList, transition));
		hAdapter = new HorizontalListAdapter(this, images, this, toDelete);
		imagesHorizontalList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL,
		                                                              false));
		imagesHorizontalList.setAdapter(hAdapter);
		hAdapter.notifyDataSetChanged();
		mViewPager.addOnPageChangeListener(this);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.image_toolbar_menu, menu);
		menu.findItem(R.id.action_delete).setVisible(!toDelete.isEmpty());
		return true;
	}

	/**
	 * Handles the selection of images to keep, by popping a verification dialog
	 *
	 * @param item The selected menu item
	 * @return super method return value
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.action_delete) {
			Spanned message =
					Html.fromHtml("You chose to delete <b>" + toDelete.size() +
							              "</b> out of <b>" + images.size() +
							              "</b> images.<br>Are you sure you want to delete the selected images?", 0);
			new AlertDialog.Builder(this)
					.setTitle("Delete Duplicates?")
					.setMessage(message)
					.setPositiveButton("DELETE", (dialog, which) -> {
						Toast.makeText(this, "Deleting duplicates..", Toast.LENGTH_LONG).show();
						// Deleting unselected images
						new Thread(() -> {
							for (Image image : toDelete) {
								image.delete(this);
							}
							Intent data = new Intent();
							data.putStringArrayListExtra("deleted",
							                             (ArrayList<String>) toDelete.stream()
							                                                         .map(Image::getPath)
							                                                         .collect(Collectors.toList()));
							setResult(RESULT_OK, data);
							finish();
						}).start();
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
	public void onImageClick(int position) {
		mViewPager.setCurrentItem(position, true);
	}

}

package yoavbz.dupimg;

import android.Manifest;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.v4.app.Fragment;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.SwitchCompat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.github.paolorotolo.appintro.AppIntro;
import com.github.paolorotolo.appintro.AppIntroBaseFragment;
import com.github.paolorotolo.appintro.AppIntroFragment;
import com.github.paolorotolo.appintro.model.SliderPage;
import yoavbz.dupimg.background.NotificationJobService;

import java.util.concurrent.TimeUnit;

import static yoavbz.dupimg.MainActivity.JOB_ID;

public class IntroActivity extends AppIntro {

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getSupportActionBar().hide();
		setResult(RESULT_CANCELED);

		// TODO: Increase description size
		SliderPage welcomeSlide = new SliderPage();
		welcomeSlide.setTitle("Welcome!");
		welcomeSlide.setDescription(
				"This App will help you clean your duplicated images");
		welcomeSlide.setImageDrawable(R.drawable.clean_duplicates);
		welcomeSlide.setBgColor(Color.TRANSPARENT);
		addSlide(AppIntroFragment.newInstance(welcomeSlide));

		SliderPage permissionSlide = new SliderPage();
		permissionSlide.setTitle("App Permissions");
		permissionSlide.setDescription(
				"In order to scan and modify files, please click the 'Next' button and approve the asked permissions");
		permissionSlide.setImageDrawable(R.drawable.permissions);
		permissionSlide.setBgColor(Color.TRANSPARENT);
		PermissionFragment permissionsFragment = PermissionFragment.newInstance(permissionSlide);
		addSlide(permissionsFragment);

		SliderPage defaultDirSlide = new SliderPage();
		defaultDirSlide.setTitle("Camera Directory");
		defaultDirSlide.setDescription("Please select your default camera directory:");
		defaultDirSlide.setImageDrawable(R.drawable.ic_camera);
		defaultDirSlide.setBgColor(Color.TRANSPARENT);
		DefaultDirFragment defaultDirFragment = DefaultDirFragment.newInstance(defaultDirSlide);
		addSlide(defaultDirFragment);

		SliderPage backgroundMonitorSlide = new SliderPage();
		backgroundMonitorSlide.setTitle("Background Monitor");
		backgroundMonitorSlide.setDescription(
				"Periodically scan your default directory for duplications");
		backgroundMonitorSlide.setImageDrawable(R.drawable.monitor_notification);
		backgroundMonitorSlide.setBgColor(Color.TRANSPARENT);
		BackgroundMonitorFragment backgroundMonitorFragment = BackgroundMonitorFragment.newInstance(
				backgroundMonitorSlide);
		addSlide(backgroundMonitorFragment);

		SliderPage doneSlide = new SliderPage();
		doneSlide.setTitle("Done!");
		doneSlide.setDescription("We are good to go :)");
		doneSlide.setBgColor(Color.TRANSPARENT);
		AppIntroFragment doneFragment = AppIntroFragment.newInstance(doneSlide);
		addSlide(doneFragment);

		// Adding listener for hiding next button when changing to defaultDirSlide until selecting directory
		pager.addOnPageChangeListener(getOnPageChangeListener(doneFragment));

		showSkipButton(false);
		askForPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
		                               Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
		showSeparator(true);
		setGoBackLock(true);
		setFadeAnimation();
	}

	@Override
	public void onDonePressed(Fragment currentFragment) {
		// Returning to the main activity
		PreferenceManager.getDefaultSharedPreferences(getBaseContext()).edit()
		                 .putBoolean("showIntro", false)
		                 .apply();
		setResult(RESULT_OK);
		finish();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (grantResults.length == 0) {
			return;
		}
		for (int result : grantResults) {
			if (result == PackageManager.PERMISSION_DENIED) {
				Toast.makeText(this, "Cannot launch app without permissions", Toast.LENGTH_LONG).show();
				finish();
				return;
			}
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			Uri uri = data.getData();
			PreferenceManager.getDefaultSharedPreferences(this).edit()
			                 .putString("dirUri", uri.toString())
			                 .apply();
			pager.goToNextSlide();
		}
	}

	@NonNull
	private PagerOnPageChangeListener getOnPageChangeListener(AppIntroFragment doneFragment) {
		return new PagerOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				super.onPageSelected(position);
				switch (position) {
					case 2:
						setButtonState(nextButton, false);
						break;
					case 3: {
						LottieAnimationView animationView = new LottieAnimationView(IntroActivity.this);
						animationView.setAnimation(R.raw.done);
						((LinearLayout) doneFragment.getView()).addView(animationView, 1);
						animationView.playAnimation();
					}
				}
			}
		};
	}

	public static class PermissionFragment extends AppIntroBaseFragment {

		@Nullable
		@Override
		public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
		                         @Nullable Bundle savedInstanceState) {
			LinearLayout view = (LinearLayout) super.onCreateView(inflater, container, savedInstanceState);
			AppCompatImageView imageView = view.findViewById(com.github.paolorotolo.appintro.R.id.image);
			view.removeViewAt(1);
			((LinearLayout) imageView.getParent()).removeAllViews();

			ConstraintLayout constraintLayout = new ConstraintLayout(getContext());

			LottieAnimationView animationView = new LottieAnimationView(getContext());
			animationView.setId(View.generateViewId());
			animationView.setAnimation(R.raw.click);
			animationView.setSpeed(2.5f);
			animationView.setRepeatCount(LottieDrawable.INFINITE);
			animationView.setScale(0.15f);
			animationView.setElevation(5f);
			constraintLayout.addView(animationView);
			constraintLayout.addView(imageView);
			view.addView(constraintLayout, 2);

			ConstraintSet constrains = new ConstraintSet();
			constrains.clone(constraintLayout);
			constrains.connect(animationView.getId(), ConstraintSet.LEFT, imageView.getId(), ConstraintSet.LEFT);
			constrains.connect(animationView.getId(), ConstraintSet.RIGHT, imageView.getId(), ConstraintSet.RIGHT);
			constrains.connect(animationView.getId(), ConstraintSet.TOP, imageView.getId(), ConstraintSet.TOP);
			constrains.connect(animationView.getId(), ConstraintSet.BOTTOM, imageView.getId(), ConstraintSet.BOTTOM);
			constrains.setHorizontalBias(animationView.getId(), 0.878f);
			constrains.setVerticalBias(animationView.getId(), 0.628f);
			constrains.applyTo(constraintLayout);

			animationView.playAnimation();
			return view;
		}

		public static PermissionFragment newInstance(SliderPage sliderPage) {
			PermissionFragment slide = new PermissionFragment();
			Bundle args = new Bundle();
			args.putString(ARG_TITLE, sliderPage.getTitleString());
			args.putString(ARG_TITLE_TYPEFACE, sliderPage.getTitleTypeface());
			args.putString(ARG_DESC, sliderPage.getDescriptionString());
			args.putString(ARG_DESC_TYPEFACE, sliderPage.getDescTypeface());
			args.putInt(ARG_DRAWABLE, sliderPage.getImageDrawable());
			args.putInt(ARG_BG_COLOR, sliderPage.getBgColor());
			args.putInt(ARG_TITLE_COLOR, sliderPage.getTitleColor());
			args.putInt(ARG_DESC_COLOR, sliderPage.getDescColor());
			slide.setArguments(args);

			return slide;
		}

		@Override
		protected int getLayoutId() {
			return com.github.paolorotolo.appintro.R.layout.fragment_intro;
		}

	}

	public static class DefaultDirFragment extends AppIntroBaseFragment {

		@Nullable
		@Override
		public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
		                         @Nullable Bundle savedInstanceState) {
			LinearLayout view = (LinearLayout) super.onCreateView(inflater, container, savedInstanceState);
			Button selectDirButton = new Button(getContext());
			selectDirButton.setText("Select directory");
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(700, 190);
			lp.gravity = Gravity.CENTER;
			selectDirButton.setLayoutParams(lp);
			selectDirButton.setOnClickListener(v -> {
				Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
				intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
				startActivityForResult(intent, 1234);
			});
			view.addView(selectDirButton);
			return view;
		}

		public static DefaultDirFragment newInstance(SliderPage sliderPage) {
			DefaultDirFragment slide = new DefaultDirFragment();
			Bundle args = new Bundle();
			args.putString(ARG_TITLE, sliderPage.getTitleString());
			args.putString(ARG_TITLE_TYPEFACE, sliderPage.getTitleTypeface());
			args.putString(ARG_DESC, sliderPage.getDescriptionString());
			args.putString(ARG_DESC_TYPEFACE, sliderPage.getDescTypeface());
			args.putInt(ARG_DRAWABLE, sliderPage.getImageDrawable());
			args.putInt(ARG_BG_COLOR, sliderPage.getBgColor());
			args.putInt(ARG_TITLE_COLOR, sliderPage.getTitleColor());
			args.putInt(ARG_DESC_COLOR, sliderPage.getDescColor());
			slide.setArguments(args);

			return slide;
		}

		@Override
		protected int getLayoutId() {
			return com.github.paolorotolo.appintro.R.layout.fragment_intro;
		}

	}

	public static class BackgroundMonitorFragment extends AppIntroBaseFragment {

		@Nullable
		@Override
		public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
		                         @Nullable Bundle savedInstanceState) {
			LinearLayout view = (LinearLayout) super.onCreateView(inflater, container, savedInstanceState);
			SwitchCompat monitorSwitch = (SwitchCompat) getLayoutInflater().inflate(R.layout.background_switch, null);
			JobScheduler scheduler = (JobScheduler) getContext().getSystemService(JOB_SCHEDULER_SERVICE);
			monitorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
				if (isChecked) {
					// Turn on background jobService
					JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(getContext().getPackageName(),
					                                                            NotificationJobService.class.getName()))
							.setMinimumLatency(TimeUnit.MINUTES.toMillis(15))
							.setPersisted(true)
							.build();
					scheduler.schedule(job);
					PreferenceManager.getDefaultSharedPreferences(getContext()).edit()
					                 .putBoolean("showIntro", false)
					                 .apply();
					getActivity().setResult(RESULT_OK);
				} else {
					// Turn off background jobService
					scheduler.cancel(JOB_ID);
				}
			});
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(700, 190);
			lp.gravity = Gravity.CENTER;
			monitorSwitch.setLayoutParams(lp);
			monitorSwitch.setTextColor(Color.WHITE);
			view.addView(monitorSwitch);
			return view;
		}

		public static BackgroundMonitorFragment newInstance(SliderPage sliderPage) {
			BackgroundMonitorFragment slide = new BackgroundMonitorFragment();
			Bundle args = new Bundle();
			args.putString(ARG_TITLE, sliderPage.getTitleString());
			args.putString(ARG_TITLE_TYPEFACE, sliderPage.getTitleTypeface());
			args.putString(ARG_DESC, sliderPage.getDescriptionString());
			args.putString(ARG_DESC_TYPEFACE, sliderPage.getDescTypeface());
			args.putInt(ARG_DRAWABLE, sliderPage.getImageDrawable());
			args.putInt(ARG_BG_COLOR, sliderPage.getBgColor());
			args.putInt(ARG_TITLE_COLOR, sliderPage.getTitleColor());
			args.putInt(ARG_DESC_COLOR, sliderPage.getDescColor());
			slide.setArguments(args);

			return slide;
		}

		@Override
		protected int getLayoutId() {
			return com.github.paolorotolo.appintro.R.layout.fragment_intro;
		}

	}
}

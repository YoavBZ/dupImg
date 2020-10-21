package yoavbz.dupimg.intro;

import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.SwitchCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.github.paolorotolo.appintro.AppIntroBaseFragment;
import com.github.paolorotolo.appintro.model.SliderPage;

import java.util.HashSet;
import java.util.Set;

import yoavbz.dupimg.R;
import yoavbz.dupimg.treeview.Directory;
import yoavbz.dupimg.treeview.DirectoryTreeView;
import yoavbz.dupimg.treeview.FileUtils;

@SuppressWarnings("ConstantConditions")
class CustomFragments {

	public static class PermissionFragment extends AppIntroBaseFragment {

		static PermissionFragment newInstance(@NonNull SliderPage sliderPage) {
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

		@Nullable
		@Override
		public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
		                         @Nullable Bundle savedInstanceState) {
			LinearLayout view = (LinearLayout) super.onCreateView(inflater, container, savedInstanceState);
			// Changing description size
			TextView description = view.findViewById(com.github.paolorotolo.appintro.R.id.description);
			description.setTextSize(19f);
			// Removing image from main layout
			ImageView imageView = view.findViewById(com.github.paolorotolo.appintro.R.id.image);
			view.removeViewAt(1);
			ViewGroup.LayoutParams imageLayoutParams = ((LinearLayout) imageView.getParent()).getLayoutParams();
			((LinearLayout) imageView.getParent()).removeAllViews();
			// Constructing ConstraintLayout & LottieAnimationView
			ConstraintLayout constraintLayout = new ConstraintLayout(getContext());
			LottieAnimationView animationView = new LottieAnimationView(getContext());
			animationView.setId(View.generateViewId());
			animationView.setAnimation(R.raw.click);
			animationView.setSpeed(2.5f);
			animationView.setRepeatCount(LottieDrawable.INFINITE);
			animationView.setScale(0.15f);
			animationView.setElevation(5f);
			// Adding image and animation to ConstraintLayout
			constraintLayout.addView(animationView);
			constraintLayout.addView(imageView);
			constraintLayout.setLayoutParams(imageLayoutParams);
			// Adding ConstraintLayout to main layout
			view.addView(constraintLayout, 2);
			// Setting constrains for ConstraintLayout
			ConstraintSet constrains = new ConstraintSet();
			constrains.clone(constraintLayout);
			constrains.connect(animationView.getId(), ConstraintSet.LEFT, imageView.getId(), ConstraintSet.LEFT);
			constrains.connect(animationView.getId(), ConstraintSet.RIGHT, imageView.getId(), ConstraintSet.RIGHT);
			constrains.connect(animationView.getId(), ConstraintSet.TOP, imageView.getId(), ConstraintSet.TOP);
			constrains.connect(animationView.getId(), ConstraintSet.BOTTOM, imageView.getId(), ConstraintSet.BOTTOM);
			constrains.setHorizontalBias(animationView.getId(), 0.885f);
			constrains.setVerticalBias(animationView.getId(), 0.66f);
			constrains.applyTo(constraintLayout);

			animationView.playAnimation();
			return view;
		}

		@Override
		protected int getLayoutId() {
			return com.github.paolorotolo.appintro.R.layout.fragment_intro;
		}
	}

	public static class SelectDirFragment extends AppIntroBaseFragment {

		static SelectDirFragment newInstance(@NonNull SliderPage sliderPage) {
			SelectDirFragment slide = new SelectDirFragment();
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

		@Nullable
		@Override
		public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
		                         @Nullable Bundle savedInstanceState) {
			LinearLayout view = (LinearLayout) super.onCreateView(inflater, container, savedInstanceState);

			TextView desc = view.findViewById(com.github.paolorotolo.appintro.R.id.description);
			desc.setTextSize(19f);

			AppCompatButton selectDirButton = (AppCompatButton) inflater.inflate(R.layout.select_dir_button, view, false);
			selectDirButton.setOnClickListener(v -> {
				DirectoryTreeView dirTreeView = new DirectoryTreeView(getContext());
				Set<String> selectedDirs = new HashSet<>();
				dirTreeView.setOnDirStateChangeListener((dir, state) -> {
					if (state == Directory.DirState.FULL) {
						selectedDirs.add(dir.getFile().getPath());
					} else if (state == Directory.DirState.NONE) {
						selectedDirs.remove(dir.getFile().getPath());
					}
				});
				new AlertDialog.Builder(getContext())
						.setTitle("Select directories to scan:")
						.setView(dirTreeView)
						.setPositiveButton("OK", (dialog, which) -> {
							for (String dir : selectedDirs) {
								FileUtils.showSdcardDialogIfNeeded(getActivity(), dir);
							}
							((IntroActivity) getActivity()).getPager().goToNextSlide();
							PreferenceManager.getDefaultSharedPreferences(getContext()).edit()
							                 .putStringSet("dirs", selectedDirs)
							                 .apply();
						})
						.setNegativeButton("Cancel", null)
						.show();
			});
			view.addView(selectDirButton);
			return view;
		}

		@Override
		protected int getLayoutId() {
			return com.github.paolorotolo.appintro.R.layout.fragment_intro;
		}

	}

	public static class BackgroundMonitorFragment extends AppIntroBaseFragment {

		static BackgroundMonitorFragment newInstance(@NonNull SliderPage sliderPage) {
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

		@Nullable
		@Override
		public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
		                         @Nullable Bundle savedInstanceState) {
			LinearLayout view = (LinearLayout) super.onCreateView(inflater, container, savedInstanceState);

			TextView desc = view.findViewById(com.github.paolorotolo.appintro.R.id.description);
			desc.setTextSize(19f);

			SwitchCompat monitorSwitch = (SwitchCompat) inflater.inflate(R.layout.background_switch, null);
			monitorSwitch.setTextColor(Color.WHITE);
			monitorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
				PreferenceManager.getDefaultSharedPreferences(getContext()).edit()
				                 .putBoolean("isJobSchedule", isChecked)
				                 .apply();
			});
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(700, 190);
			lp.gravity = Gravity.CENTER;
			monitorSwitch.setLayoutParams(lp);
			view.addView(monitorSwitch);
			return view;
		}

		@Override
		protected int getLayoutId() {
			return com.github.paolorotolo.appintro.R.layout.fragment_intro;
		}
	}

	public static class AnimatedFragment extends AppIntroBaseFragment {

		static final String ARG_DESC_SIZE = "desc_size";
		static final String ARG_ANIMATION = "animation";

		@RawRes
		private int animationId;
		private LottieAnimationView animationView;
		private float descSize;

		static AnimatedFragment newInstance(@NonNull AnimatedSliderPage sliderPage) {
			AnimatedFragment fragment = new AnimatedFragment();
			Bundle args = new Bundle();
			args.putString(ARG_TITLE, sliderPage.getTitleString());
			args.putString(ARG_TITLE_TYPEFACE, sliderPage.getTitleTypeface());
			args.putString(ARG_DESC, sliderPage.getDescriptionString());
			args.putString(ARG_DESC_TYPEFACE, sliderPage.getDescTypeface());
			args.putInt(ARG_DRAWABLE, sliderPage.getImageDrawable());
			args.putInt(ARG_BG_COLOR, sliderPage.getBgColor());
			args.putInt(ARG_TITLE_COLOR, sliderPage.getTitleColor());
			args.putInt(ARG_DESC_COLOR, sliderPage.getDescColor());
			args.putFloat(ARG_DESC_SIZE, sliderPage.getDescSize());
			args.putInt(ARG_ANIMATION, sliderPage.getAnimationId());
			fragment.setArguments(args);
			return fragment;
		}

		@Override
		public void onCreate(@Nullable Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			if (getArguments() != null && getArguments().size() != 0) {
				descSize = getArguments().getFloat(ARG_DESC_SIZE, 18);
				animationId = getArguments().getInt(ARG_ANIMATION);
			}
		}

		@Override
		public void onActivityCreated(@Nullable Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);
			if (savedInstanceState != null) {
				descSize = savedInstanceState.getInt(ARG_DESC_SIZE);
				animationId = savedInstanceState.getInt(ARG_ANIMATION);
			}
		}

		@Nullable
		@Override
		public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
		                         @Nullable Bundle savedInstanceState) {
			View v = super.onCreateView(inflater, container, savedInstanceState);
			// Updating description size
			TextView d = v.findViewById(com.github.paolorotolo.appintro.R.id.description);
			d.setTextSize(descSize);
			// Handling animation if necessary
			if (animationId != 0) {
				ImageView i = v.findViewById(com.github.paolorotolo.appintro.R.id.image);
				LinearLayout imageLayout = ((LinearLayout) i.getParent());
				// Removing original ImageView
				imageLayout.removeView(i);
				// Adding LottieAnimationView
				animationView = new LottieAnimationView(getContext());
				animationView.setTag("Animation");
				animationView.setAnimation(animationId);
				animationView.setSpeed(0.8f);
				imageLayout.addView(animationView);
			}
			return v;
		}

		@Override
		public void onSlideSelected() {
			super.onSlideSelected();
			if (animationView != null) {
				animationView.playAnimation();
			}
		}

		@Override
		public void onSaveInstanceState(Bundle outState) {
			outState.putFloat(ARG_DESC_SIZE, descSize);
			outState.putInt(ARG_ANIMATION, animationId);
			super.onSaveInstanceState(outState);
		}

		@Override
		protected int getLayoutId() {
			return com.github.paolorotolo.appintro.R.layout.fragment_intro;
		}
	}
}

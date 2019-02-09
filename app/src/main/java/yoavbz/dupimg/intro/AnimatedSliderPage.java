package yoavbz.dupimg.intro;

import android.support.annotation.RawRes;
import com.github.paolorotolo.appintro.model.SliderPage;

class AnimatedSliderPage extends SliderPage {

	private float descSize;
	@RawRes
	private int animationId;

	float getDescSize() {
		return descSize;
	}

	void setDescSize(float descSize) {
		this.descSize = descSize;
	}

	int getAnimationId() {
		return animationId;
	}

	void setAnimationId(int animationId) {
		this.animationId = animationId;
	}
}

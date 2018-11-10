package yoavbz.galleryml.gallery.cluster;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import yoavbz.galleryml.gallery.Image;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;

public class ImageCluster implements Parcelable {
	private Date start;
	private Date end;
	private ArrayList<Image> images = new ArrayList<>();

	public ImageCluster() {
	}

	public ImageCluster(Image firstImage) {
		start = firstImage.getDateTaken();
		addImage(firstImage);
	}

	public void addImage(Image image) {
		if (images.isEmpty()) {
			start = image.getDateTaken();
		}
		images.add(image);
		end = image.getDateTaken();
	}

	public Date getStart() {
		return start;
	}

	public Date getEnd() {
		return end;
	}

	public ArrayList<Image> getImages() {
		return images;
	}

	public Image getFirstImage() {
		return images.get(0);
	}

	public int size() {
		return images.size();
	}

	// --- Parcelable interface function ---

	private ImageCluster(Parcel in) {
		try {
			start = Image.dateFormat.parse(in.readString());
			end = Image.dateFormat.parse(in.readString());
		} catch (ParseException e) {
			Log.e("Image", "Got an exception while constructing ImageCluster", e);
		}
		images = new ArrayList<>();
		in.readTypedList(images, Image.CREATOR);
	}

	public static final Parcelable.Creator CREATOR = new Parcelable.Creator() {
		public ImageCluster createFromParcel(Parcel in) {
			return new ImageCluster(in);
		}

		public ImageCluster[] newArray(int size) {
			return new ImageCluster[size];
		}
	};

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(Image.dateFormat.format(start));
		dest.writeString(Image.dateFormat.format(end.clone()));
		dest.writeTypedList(images);
	}
}

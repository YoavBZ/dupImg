package yoavbz.galleryml.gallery;

import android.arch.persistence.room.*;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.media.ExifInterface;
import android.util.Log;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Entity(tableName = "images")
public class Image implements Comparable, Parcelable {

	@PrimaryKey private Path path;
	private Date dateTaken;
	private double[] featureVector;
	public static DateFormat dateFormat = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");

	public Image(Path path) {
		this.path = path;
		try {
			ExifInterface exif = new ExifInterface(this.path.toString());
			String date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
			dateTaken = dateFormat.parse(date);
		} catch (ParseException | IOException e) {
			Log.e("Image", "Got an exception while constructing Image", e);
		} catch (Exception e) {
			Log.e("Image", "Failed fetching date of image! Was it taken by camera?");
		}
	}

	public String getPath() {
		return path.toString();
	}

	public String getFileName() {
		return path.getFileName().toString();
	}

	public void setFeatureVector(double[] featureVector) {
		this.featureVector = featureVector;
	}

	public double[] getFeatureVector() {
		return featureVector;
	}

	public Date getDateTaken() {
		return dateTaken;
	}

	// --- Comparable interface function ---
	@Override
	public int compareTo(@NonNull Object otherImage) {
		if (dateTaken == null) {
			return -1;
		}
		return dateTaken.compareTo(((Image) otherImage).dateTaken);
	}

	// --- Parcelable interface function ---
	private Image(Parcel in) {
		this.path = Paths.get(in.readString());
		try {
			this.dateTaken = dateFormat.parse(in.readString());
		} catch (ParseException e) {
			Log.e("Image", "Got an exception while constructing Image", e);
		}
		this.featureVector = in.createDoubleArray();
	}

	public static final Creator<Image> CREATOR = new Creator<Image>() {
		public Image createFromParcel(Parcel in) {
			return new Image(in);
		}

		public Image[] newArray(int size) {
			return new Image[size];
		}
	};

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(path.toString());
		dest.writeString(dateFormat.format(dateTaken));
		dest.writeDoubleArray(featureVector);
	}

}

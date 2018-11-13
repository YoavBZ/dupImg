package yoavbz.galleryml.gallery;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.media.ExifInterface;
import android.util.Log;
import org.apache.commons.math3.ml.clustering.DoublePoint;
import yoavbz.galleryml.ImageClassifier;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

@Entity(tableName = "images")
public class Image implements Parcelable {

	@PrimaryKey
	@NonNull
	private Path path;
	private Date dateTaken;
	private DoublePoint point;

	public Image(Path path) {
		this.path = path;
		try {
			ExifInterface exif = new ExifInterface(this.path.toString());
			String date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
			DateFormat dateFormat = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");
			dateTaken = dateFormat.parse(date);
		} catch (Exception e) {
			Log.e("Image", "Got an exception while constructing image");
		}
	}

	public Image(String path) {
		this(Paths.get(path));
	}

	public Path getPath() {
		return path;
	}

	public void setPath(Path path) {
		this.path = path;
	}

	public String getFileName() {
		return path.getFileName().toString();
	}

	public Date getDateTaken() {
		return dateTaken;
	}

	public void setDateTaken(Date dateTaken) {
		this.dateTaken = dateTaken;
	}

	public DoublePoint getPoint() {
		return point;
	}


	public void setPoint(DoublePoint point) {
		this.point = point;
	}

	/**
	 * Calculating the feature vector using a given classifier
	 * @param classifier The classifier to run the TensorFlow interpreter for calculating feature vector
	 */
	public void calculateFeatureVector(ImageClassifier classifier) {
		Bitmap bitmap = BitmapFactory.decodeFile(path.toString());
		bitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, false);
		point = new DoublePoint(classifier.recognizeImage(bitmap));
	}

	// --- Parcelable interface functions ---

	private Image(Parcel in) {
		path = Paths.get(in.readString());
		dateTaken = new Date(in.readLong());
		point = new DoublePoint(in.createDoubleArray());
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
		dest.writeLong(dateTaken.getTime());
		dest.writeDoubleArray(point.getPoint());
	}
}

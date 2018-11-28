package yoavbz.dupimg.models;

import android.app.Activity;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.media.ExifInterface;
import android.util.Log;
import android.widget.Toast;
import org.apache.commons.math3.ml.clustering.Clusterable;
import yoavbz.dupimg.ImageClassifier;
import yoavbz.dupimg.database.ImageDao;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Entity(tableName = "images")
public class Image implements Parcelable, Clusterable {

	public static final Creator<Image> CREATOR = new Creator<Image>() {
		public Image createFromParcel(Parcel in) {
			return new Image(in);
		}

		public Image[] newArray(int size) {
			return new Image[size];
		}
	};

	@PrimaryKey
	@NonNull
	private Path path;
	private Date dateTaken;
	private double[] point;

	public Image(@NonNull Path path) {
		this.path = path;
		try {
			ExifInterface exif = new ExifInterface(this.path.toString());
			String date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
			if (date == null) {
				date = exif.getAttribute(ExifInterface.TAG_DATETIME);
			}
			DateFormat dateFormat = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");
			dateTaken = dateFormat.parse(date);
		} catch (IOException | ParseException e) {
			Log.e("Image", "Got an exception while constructing image: " + e);
		}
	}

	public Image(String path) {
		this(Paths.get(path));
	}

	private Image(Parcel in) {
		path = Paths.get(in.readString());
		dateTaken = new Date(in.readLong());
		point = in.createDoubleArray();
	}

	@NonNull
	public Path getPath() {
		return path;
	}

	public void setPath(@NonNull Path path) {
		this.path = path;
	}

	public Date getDateTaken() {
		return dateTaken;
	}

	public void setDateTaken(Date dateTaken) {
		this.dateTaken = dateTaken;
	}

	@Override
	public double[] getPoint() {
		return point;
	}

	@Override
	public String toString() {
		return path.getFileName().toString();
	}

	// --- Clusterable interface functions ---

	public void setPoint(double[] point) {
		this.point = point;
	}

	/**
	 * @return A Bitmap representation of the image
	 */
	public Bitmap getBitmap(){
		Bitmap bitmap = BitmapFactory.decodeFile(path.toString());
		return Bitmap.createScaledBitmap(bitmap, 224, 224, false);
	}

	/**
	 * Calculating the feature vector using a given classifier
	 *
	 * @param classifier The classifier to run the TensorFlow interpreter for calculating feature vector
	 */
	public void calculateFeatureVector(ImageClassifier classifier) {
		point = classifier.recognizeImage(getBitmap());
	}

	public void delete(Activity activity, ImageDao dao) {
		if (path.toFile().delete()) {
			dao.delete(this);
		} else {
			activity.runOnUiThread(() -> {
				Toast.makeText(activity, "Couldn't delete file " + toString(), Toast.LENGTH_SHORT).show();
			});
		}
	}

	// --- Parcelable interface functions ---

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(path.toString());
		dest.writeLong(dateTaken.getTime());
		dest.writeDoubleArray(point);
	}

	@Override
	public int describeContents() {
		return 0;
	}
}

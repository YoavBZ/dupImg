package yoavbz.dupimg.database;

import android.content.Context;
import androidx.room.*;
import yoavbz.dupimg.Image;

import java.util.Arrays;
import java.util.stream.Collectors;

@Database(entities = Image.class, version = 1, exportSchema = false)
@TypeConverters(ImageDatabase.Converters.class)
public abstract class ImageDatabase extends RoomDatabase {

	private static ImageDatabase INSTANCE;

	public static ImageDatabase getAppDatabase(Context context) {
		if (INSTANCE == null) {
			INSTANCE = Room.databaseBuilder(context.getApplicationContext(), ImageDatabase.class,
			                                "image-database.db").build();
		}
		return INSTANCE;
	}

	public abstract ImageDao imageDao();

	@SuppressWarnings("WeakerAccess")
	static class Converters {

		@TypeConverter
		public static String doubleArrayToString(double[] point) {
			return Arrays.stream(point).mapToObj(String::valueOf).collect(Collectors.joining(","));
		}

		@TypeConverter
		public static double[] stringToDoubleArray(String str) {
			return Arrays.stream(str.split(",")).mapToDouble(Double::parseDouble).toArray();
		}
	}
}
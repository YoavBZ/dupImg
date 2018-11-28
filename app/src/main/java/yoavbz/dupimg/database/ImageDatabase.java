package yoavbz.dupimg.database;

import android.arch.persistence.room.*;
import android.content.Context;
import yoavbz.dupimg.models.Image;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
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

	static class Converters {
		@TypeConverter
		public static Date fromTimestamp(Long value) {
			return value == null ? null : new Date(value);
		}

		@TypeConverter
		public static Long dateToTimestamp(Date date) {
			return date == null ? null : date.getTime();
		}

		@TypeConverter
		public static String pathToString(Path path) {
			return path.toString();
		}

		@TypeConverter
		public static Path pathToString(String path) {
			return Paths.get(path);
		}

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

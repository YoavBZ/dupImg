package yoavbz.dupimg.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

import yoavbz.dupimg.Image;

@Dao
public abstract class ImageDao {

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	public abstract void insert(List<Image> images);

	@Query("DELETE FROM images WHERE path = :path")
	public abstract void delete(String path);

	@Update
	public abstract void update(Image image);

	@Query("SELECT * FROM images ORDER BY dateTaken ASC")
	public abstract List<Image> getAll();

	@Query("SELECT path FROM images WHERE path IN (:paths) ORDER BY dateTaken ASC")
	public abstract List<String> getByPaths(List<String> paths);

	@Transaction
	public boolean deleteNotInList(List<String> localImages) {
		boolean deleted = false;
		for (String path : getAllPaths()) {
			if (!localImages.contains(path)) {
				delete(path);
				deleted = true;
			}
		}
		return deleted;
	}

	@Query("SELECT path FROM images ORDER BY dateTaken DESC")
	public abstract List<String> getAllPaths();
}

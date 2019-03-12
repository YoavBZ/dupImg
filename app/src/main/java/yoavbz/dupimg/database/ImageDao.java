package yoavbz.dupimg.database;

import androidx.room.*;
import yoavbz.dupimg.models.Image;

import java.util.List;

@Dao
public abstract class ImageDao {

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	public abstract void insert(List<Image> images);

	@Delete
	public abstract void delete(Image image);

	@Query("DELETE FROM images WHERE path = :path")
	public abstract void delete(String path);

	@Update
	public abstract void update(Image image);

	@Query("SELECT * FROM images ORDER BY dateTaken DESC")
	public abstract List<Image> getAll();

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

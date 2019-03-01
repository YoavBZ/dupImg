package yoavbz.dupimg.utils;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;

import java.util.List;

public class DirectoryTreeView extends RecyclerView {

	private DirectoryTreeAdapter adapter;

	public DirectoryTreeView(@NonNull Context context) {
		super(context);
		init();
	}

	public DirectoryTreeView(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public DirectoryTreeView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	public void init() {
		setLayoutManager(new LinearLayoutManager(getContext()));
		adapter = new DirectoryTreeAdapter(this);
		setAdapter(adapter);
	}

	public void setOnDirStateChangeListener(Directory.OnDirStateChangeListener listener) {
		Directory.setOnDirStateChangeListener(listener);
	}

	public void removeAllChildren(List<Directory> children) {
		for (Directory child : children) {
			if (child.isExpanded()) {
				child.setExpanded(false);
				removeAllChildren(child.getChildren());
				List<Directory> directories = adapter.getDirectories();
				removePrevItems(directories, directories.indexOf(child), child.getChildren().size());
			}
		}
	}

	private int getExpandedPosition(int level) {
		List<Directory> directories = adapter.getDirectories();
		for (Directory dir : directories) {
			if (level == dir.getLevel() && dir.isExpanded()) {
				return directories.indexOf(dir);
			}
		}
		return -1;
	}

	private int getNumItemsToRemove(int expandedPosition, int level) {
		int itemsToRemove = 0;
		List<Directory> directories = adapter.getDirectories();
		for (int i = expandedPosition + 1; i < directories.size() && level < directories.get(i).getLevel(); i++) {
			itemsToRemove++;
		}
		return itemsToRemove;
	}

	public void toggleItemsGroup(int position) {
		if (position == -1) {
			return;
		}
		List<Directory> directories = adapter.getDirectories();
		Directory dir = directories.get(position);

		if (dir.isExpanded()) {
			// Collapsing clicked directory

			dir.setExpanded(false);
			removeAllChildren(dir.getChildren());
			removePrevItems(directories, position, dir.getChildren().size());
		} else {
			// Expanding clicked directory

			int i = getExpandedPosition(dir.getLevel());
			if (i != -1) {
				// Collapsing expanded position
				int numItemsToRemove = getNumItemsToRemove(i, dir.getLevel());
				removePrevItems(directories, i, numItemsToRemove);
				directories.get(i).setExpanded(false);
				adapter.collapse(i);
				// Expanding selected position
				if (directories.indexOf(dir) > i) {
					addItems(dir, directories, position - numItemsToRemove);
				} else {
					addItems(dir, directories, position);
				}
			} else {
				addItems(dir, directories, position);
			}
		}
	}

	private void removePrevItems(List<Directory> newDirectoryList, int position, int numberOfItemsAdded) {
		for (int i = 0; i < numberOfItemsAdded; i++) {
			// TODO: Does the next line helps?!
			newDirectoryList.get(position + 1).setExpanded(false);
			newDirectoryList.remove(position + 1);
		}
		adapter.setDirectories(newDirectoryList);
		adapter.notifyItemRangeRemoved(position + 1, numberOfItemsAdded);
	}

	private void addItems(@NonNull Directory expandingDir, List<Directory> newDirectoryList, int position) {
		if (expandingDir.hasChildren()) {
			expandingDir.setExpanded(true);
			newDirectoryList.addAll(position + 1, expandingDir.getChildren());
			adapter.setDirectories(newDirectoryList);
			adapter.notifyItemRangeInserted(position + 1, expandingDir.getChildren().size());
//			smoothScrollToPosition(position);
		}
	}

}

package yoavbz.dupimg.utils;

import android.support.annotation.NonNull;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static yoavbz.dupimg.utils.Directory.DirState.PARTIAL;

public class Directory {

	private static FileFilter dirFilter = file -> file.canRead() && file.isDirectory() && !file.isHidden();
	private static OnDirStateChangeListener listener;
	private String name;
	private File dir;
	private Directory parent;
	private List<Directory> children;
	private int level;
	private boolean expanded = false;
	private DirState state = DirState.NONE;

	public Directory(@NonNull File dir) {
		this.dir = dir;
		name = dir.getPath();
		level = 0;
	}

	public Directory(@NonNull File dir, @NonNull Directory parent) {
		this.dir = dir;
		name = dir.getName();
		this.parent = parent;
		this.level = parent.level + 1;
	}

	public static void setOnDirStateChangeListener(OnDirStateChangeListener listener) {
		Directory.listener = listener;
	}

	public boolean isExpanded() {
		return expanded;
	}

	public void setExpanded(boolean expanded) {
		this.expanded = expanded;
	}

	public int getLevel() {
		return level;
	}

	public List<Directory> getChildren() {
		if (children == null) {
			children = new ArrayList<>();
			for (File f : dir.listFiles(dirFilter)) {
				children.add(new Directory(f, this));
			}
			children.sort(Comparator.comparing(Directory::getName));
		}
		return children;
	}

	public String getName() {
		return name;
	}

	public DirState getState() {
		return state;
	}

	public void setState(DirState state, boolean updateParent, boolean updateChildren) {
		this.state = state;
		if (listener != null) {
			listener.onDirStateChange(this, state);
		}
		if (updateParent && parent != null) {
			// When setting state to PARTIAL all parents' state should be PARTIAL
			if (state == PARTIAL) {
				parent.setState(PARTIAL, true, false);
			} else {
				for (Directory child : parent.getChildren()) {
					if (child.getState() != state) {
						state = PARTIAL;
						break;
					}
				}
				parent.setState(state, true, false);
			}
		}
		if (updateChildren) {
			if (this.state != DirState.PARTIAL && getChildren() != null) {
				for (Directory dir : getChildren()) {
					dir.setState(this.state, false, true);
				}
			}
		}
	}

	public Directory getParent() {
		return parent;
	}

	public boolean hasChildren() {
		return getChildren().size() > 0;
	}

	public File getFile() {
		return dir;
	}

	public enum DirState {
		FULL,
		PARTIAL,
		NONE;

		public static DirState toState(Boolean bool) {
			return (bool == null) ? PARTIAL :
					(bool ? FULL :
							NONE);
		}

		public Boolean toBoolean() {
			return (this == PARTIAL) ? null :
					this == FULL;
		}
	}

	public interface OnDirStateChangeListener {
		void onDirStateChange(Directory dir, DirState state);
	}
}
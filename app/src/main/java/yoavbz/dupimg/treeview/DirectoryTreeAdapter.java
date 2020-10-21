package yoavbz.dupimg.treeview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.buildware.widget.indeterm.IndeterminateCheckBox;

import java.util.ArrayList;
import java.util.List;

import hendrawd.storageutil.library.StorageUtil;
import yoavbz.dupimg.R;
import yoavbz.dupimg.treeview.Directory.DirState;

public class DirectoryTreeAdapter extends RecyclerView.Adapter<DirectoryTreeAdapter.DirHolder> {

	private final DirectoryTreeView directoryTreeView;
	private final Context context;
	private List<Directory> directories = new ArrayList<>();

	DirectoryTreeAdapter(@NonNull DirectoryTreeView directoryTreeView) {
		super();
		this.context = directoryTreeView.getContext();
		this.directoryTreeView = directoryTreeView;
		for (File dir : FileUtils.getStorageDirectories(context)) {
			directories.add(new Directory(dir));
		}
	}

	@NonNull
	@Override
	@SuppressLint("InflateParams")
	public DirHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new DirHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dir_tree, null));
	}

	@Override
	public void onBindViewHolder(@NonNull DirHolder holder, int position) {
		Directory directory = directories.get(position);

		holder.dirName.setText(directory.toString());
		Drawable icon;
		if (directory.getLevel() == 0) {
			holder.checkbox.setVisibility(View.GONE);
			icon = ContextCompat.getDrawable(context, R.drawable.ic_sd_card);
		} else {
			// Setting checkBox according to state, preventing infinite recursive calls
			holder.checkbox.setOnStateChangedListener(null);
			holder.checkbox.setState(directory.getState().toBoolean());
			holder.checkbox.setOnStateChangedListener((checkBox, checkBoxState) -> {
				DirState state = DirState.toState(checkBoxState);
				int pos = holder.getAdapterPosition();
				Directory dir = directories.get(pos);
				dir.setState(state, true, true);

				// Updating parents
				notifyParents(dir);
				notifyItemChanged(getParentIndex(dir));
				// Updating current dir and children (if expanded)
				notifyItemRangeChanged(pos, getLastChildIndex(dir));
			});
			icon = ContextCompat.getDrawable(context, R.drawable.ic_folder);
		}
		holder.icon.setImageDrawable(icon);

		// Setting expandButton according to its children number and expanding state
		if (directory.hasChildren()) {
			holder.expandButton.setVisibility(View.VISIBLE);
			holder.expandButton.setRotation(directory.isExpanded() ? 90 : 0);
		} else {
			holder.expandButton.setVisibility(View.INVISIBLE);
		}

		// Setting left margin according to the level
		float density = context.getResources().getDisplayMetrics().density;
		((LinearLayout.MarginLayoutParams) holder.expandButton.getLayoutParams()).leftMargin =
				(int) ((getItemViewType(position) * 20) * density + 0.5f);
	}

	@Override
	public int getItemCount() {
		return directories.size();
	}

	@Override
	public int getItemViewType(int position) {
		return directories.get(position).getLevel();
	}

	List<Directory> getDirectories() {
		return directories;
	}

	void setDirectories(List<Directory> directories) {
		this.directories = directories;
	}

	private void notifyParents(Directory dir) {
		while (dir != null) {
			notifyItemChanged(getParentIndex(dir));
			dir = dir.getParent();
		}
	}

	private int getParentIndex(@NonNull Directory dir) {
		int currentPosition = directories.indexOf(dir);
		int currentLevel = dir.getLevel();
		if (dir.getLevel() == 0) {
			return currentPosition;
		}
		for (int i = currentPosition; ; i--) {
			int level = directories.get(i).getLevel();
			if (level == currentLevel - 1) {
				return i;
			}
		}
	}

	private int getLastChildIndex(@NonNull Directory dir) {
		int currentLevel = dir.getLevel();
		int i = directories.indexOf(dir) + 1;
		while (i < directories.size() && currentLevel < directories.get(i).getLevel()) {
			i++;
		}
		return i;
	}

	void collapse(int index) {
		DirHolder holder = (DirHolder) directoryTreeView.findViewHolderForAdapterPosition(index);
		if (holder != null) {
			holder.expandButton.animate()
			                   .rotation(0)
			                   .start();
		}
	}

	class DirHolder extends RecyclerView.ViewHolder {

		ImageView expandButton, icon;
		IndeterminateCheckBox checkbox;
		TextView dirName;

		DirHolder(View layout) {
			super(layout);
			expandButton = layout.findViewById(R.id.expand_button);
			checkbox = layout.findViewById(R.id.checkbox);
			icon = layout.findViewById(R.id.icon);
			dirName = layout.findViewById(R.id.dir_name);

			expandButton.setOnClickListener(arrow -> {
				int position = getAdapterPosition();
				Directory dir = directories.get(position);

				if (dir.hasChildren()) {
					directoryTreeView.toggleItemsGroup(position);
					arrow.animate()
					     .rotation(dir.isExpanded() ? 90 : 0)
					     .start();
				}
			});
		}
	}
}

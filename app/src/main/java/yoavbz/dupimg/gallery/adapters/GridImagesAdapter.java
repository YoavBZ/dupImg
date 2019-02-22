package yoavbz.dupimg.gallery.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import org.apache.commons.math3.ml.clustering.Cluster;
import yoavbz.dupimg.R;
import yoavbz.dupimg.gallery.GalleryView;
import yoavbz.dupimg.models.Image;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class GridImagesAdapter extends RecyclerView.Adapter<GridImagesAdapter.ViewHolder> {
	private ArrayList<Cluster<Image>> clusters;
	private Context context;
	private Drawable imgPlaceHolderResId;
	private GalleryView.OnClusterClickListener clickListener;
	private int height;
	private int width;
	@SuppressLint("SimpleDateFormat")
	private static SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");

	public GridImagesAdapter(Context activity, ArrayList<Cluster<Image>> imageClusters, Drawable imgPlaceHolderResId) {
		super();
		this.clusters = imageClusters;
		this.context = activity;
		this.imgPlaceHolderResId = imgPlaceHolderResId;
	}

	@Override
	@NonNull
	@SuppressLint("InflateParams")
	public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image, null));
	}

	@Override
	public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
		holder.updateThumbnailSize();
		List<Image> images = clusters.get(position).getPoints();
		holder.firstImage = images.get(0);
		// Setting cluster size
		Glide.with(context)
		     .load(holder.firstImage.getUri())
		     .apply(new RequestOptions()
				            .placeholder(imgPlaceHolderResId)
				            // Saving original image to cache for future loadings
				            .diskCacheStrategy(DiskCacheStrategy.RESOURCE))
		     .transition(DrawableTransitionOptions.withCrossFade(500))
		     .into(holder.clusterThumbnail);
		holder.clusterSize.setText(String.valueOf(images.size()));
		// Setting cluster date
		String date = dateFormat.format(holder.firstImage.getDateTaken());
		holder.clusterDate.setText(date);
	}

	@Override
	public int getItemCount() {
		return clusters.size();
	}

	public void setOnImageClickListener(GalleryView.OnClusterClickListener onImageClickListener) {
		this.clickListener = onImageClickListener;
	}

	public void setImageSize(int width, int height) {
		this.width = width;
		this.height = height;
	}

	class ViewHolder extends RecyclerView.ViewHolder {
		ImageView clusterThumbnail;
		TextView clusterSize;
		TextView clusterDate;
		Image firstImage;

		ViewHolder(View itemView) {
			super(itemView);
			// Finding views
			clusterThumbnail = itemView.findViewById(R.id.cluster_thumbnail);
			clusterSize = itemView.findViewById(R.id.cluster_size);
			clusterDate = itemView.findViewById(R.id.timestamp);
			// Setting item OnClickListener
			itemView.setOnClickListener(view -> {
				if (clickListener != null) {
					List<Image> cluster = clusters.get(getAdapterPosition()).getPoints();
					clickListener.onClusterClick(cluster, clusterThumbnail);
				}
			});
		}

		private void updateThumbnailSize() {
			ViewGroup.LayoutParams params = clusterThumbnail.getLayoutParams();
			params.height = height;
			params.width = width;
		}
	}
}

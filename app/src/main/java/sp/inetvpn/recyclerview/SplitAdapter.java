package sp.inetvpn.recyclerview;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import sp.inetvpn.R;
import sp.inetvpn.model.SplitList;

/**
 * Created by Jay on 24-02-2018.
 * Edited by Mehrab on 04-2024
 */
public class SplitAdapter extends RecyclerView.Adapter<SplitAdapter.ViewHolder> {
    public List<SplitList> splitList;
    private final Context context;

    public SplitAdapter(Context context, List<SplitList> lstStudent) {
        this.context = context;
        this.splitList = lstStudent;
    }

    public void toggleSelection(boolean isChecked) {
        if (!splitList.isEmpty()) {
            for (int i = 0; i < splitList.size(); i++) {
                splitList.get(i).setSelected(isChecked);
            }
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView recycler_name;
        public TextView recycler_package_name;
        public CheckBox recycler_checkbox;
        public ImageView split_icon_list;

        public ViewHolder(View view) {
            super(view);
            //type cast not required for level 26 and above but still...!
            recycler_name = view.findViewById(R.id.recycler_name);
            recycler_package_name = view.findViewById(R.id.recycler_package_name);
            recycler_checkbox = view.findViewById(R.id.recycler_checkbox);
            split_icon_list = view.findViewById(R.id.split_icon_list);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.split_list_view, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        if (splitList != null) {
            //in some cases, it will prevent unwanted situations
            holder.recycler_name.setText(splitList.get(position).getAppName());
            holder.recycler_package_name.setText(splitList.get(position).getPackageName());
            holder.split_icon_list.setImageDrawable(splitList.get(position).getSplitIconList());
            holder.recycler_checkbox.setChecked(splitList.get(position).isSelected());
            holder.recycler_checkbox.setTag(splitList.get(position));

            holder.recycler_checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                //Unchecked
                splitList.get(position).setSelected(isChecked);//Checked
            });
        }
    }

    @Override
    public int getItemCount() {
        return (splitList == null) ? 0 : splitList.size();
    }
}
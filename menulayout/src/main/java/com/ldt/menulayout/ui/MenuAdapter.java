package com.ldt.menulayout.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


import com.ldt.menulayout.R;
import com.ldt.menulayout.model.Item;

import java.util.ArrayList;
import java.util.List;

public class MenuAdapter extends RecyclerView.Adapter<MenuAdapter.MenuItemHolder> implements OnItemClickListener {
    private static final String TAG = "CircleEventTypeAdapter";

    public static final int ICON_MENU_ITEM = 1;
    public static final int MENU_ITEM = 0;


    private ArrayList<Item> mData = new ArrayList<>();

    public List<Item> getData() {
        return mData;
    }

    @Override
    public void onEventItemClick(Item item, int position) {
        if(mListener!=null) mListener.onEventItemClick(item , position);
    }



    private OnItemClickListener mListener;
    public void setListener(OnItemClickListener listener) {
        mListener = listener;
    }
    public void removeListener() {
        mListener = null;
    }


    public void setData(List<Item> data) {
        mData.clear();
        if (data !=null) {
            mData.addAll(data);
        }
        notifyDataSetChanged();
    }


    @NonNull
    @Override
    public MenuItemHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if(viewType==ICON_MENU_ITEM)
         return new IconMenuItemHolder(inflater.inflate(R.layout.item_card_with_icon,parent,false),this);
        else return new MenuItemHolder(inflater.inflate(R.layout.item_card,parent,false),this);

    }

    @Override
    public void onBindViewHolder(@NonNull MenuItemHolder holder, int position) {
        holder.bind(mData.get(position));
    }

    @Override
    public int getItemViewType(int position) {
        if(getData().get(position).getDrawableRes()==null) return MENU_ITEM;
        return ICON_MENU_ITEM;
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    public static class MenuItemHolder extends RecyclerView.ViewHolder {

        TextView mTitle;

        TextView mDescription;

        public MenuItemHolder(View itemView) {
            super(itemView);
            mTitle = itemView.findViewById(R.id.title);
            mDescription = itemView.findViewById(R.id.description);
            itemView.findViewById(R.id.constraint_root).setOnClickListener(this::clickPanel);
        }

        public MenuItemHolder(View itemView, OnItemClickListener listener) {
            super(itemView);
            mListener = listener;
            mTitle = itemView.findViewById(R.id.title);
            mDescription = itemView.findViewById(R.id.description);
            itemView.findViewById(R.id.constraint_root).setOnClickListener(this::clickPanel);
        }

        OnItemClickListener mListener;
        Item mItem;

        void clickPanel(View ignored) {
            if(mListener!=null) mListener.onEventItemClick(mItem, getAdapterPosition());
        }

        public void bind(Item item) {
            mItem = item;
            mTitle.setText(item.getTitle());


            if(item.getDescription()!=null&&!item.getDescription().isEmpty()) {
                mDescription.setVisibility(View.VISIBLE);
                mDescription.setText(item.getDescription());
            }
            else {
                mDescription.setVisibility(View.INVISIBLE);
            }

        }
    }

    public static class IconMenuItemHolder extends MenuItemHolder {

        ImageView mIcon;

        public IconMenuItemHolder(View itemView, OnItemClickListener listener) {
            super(itemView, listener);
            mIcon = itemView.findViewById(R.id.icon);
        }


        public void bind(Item item) {
            mItem = item;
            mTitle.setText(item.getTitle());
            mDescription.setVisibility(View.GONE);
            int padding = (int)(mIcon.getContext().getResources().getDimension(R.dimen.oneDp)*item.getDrawablePadding());
            mIcon.setPadding(padding,padding,padding,padding);
            mIcon.setImageResource(item.getDrawableRes());
            mIcon.setColorFilter(item.getTintColor());
        }
    }
}

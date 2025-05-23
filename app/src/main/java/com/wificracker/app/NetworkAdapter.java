package com.wificracker.app;

import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class NetworkAdapter extends RecyclerView.Adapter<NetworkAdapter.ViewHolder> {
    private final List<WiFiNetwork> networks;
    private final SparseBooleanArray selectedItems;
    private final OnNetworkSelectedListener listener;

    public interface OnNetworkSelectedListener {
        void onNetworkSelected(WiFiNetwork network, boolean isSelected);
    }

    public NetworkAdapter(List<WiFiNetwork> networks, OnNetworkSelectedListener listener) {
        this.networks = networks;
        this.listener = listener;
        this.selectedItems = new SparseBooleanArray();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_network, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WiFiNetwork network = networks.get(position);
        holder.tvSSID.setText(network.getSSID());
        holder.tvBSSID.setText(network.getBSSID());
        holder.checkBox.setChecked(selectedItems.get(position));
        holder.itemView.setOnClickListener(v -> {
            boolean isSelected = !selectedItems.get(position);
            selectedItems.put(position, isSelected);
            holder.checkBox.setChecked(isSelected);
            listener.onNetworkSelected(network, isSelected);
        });
    }

    @Override
    public int getItemCount() {
        return networks.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvSSID, tvBSSID;
        CheckBox checkBox;

        ViewHolder(View itemView) {
            super(itemView);
            tvSSID = itemView.findViewById(R.id.tvSSID);
            tvBSSID = itemView.findViewById(R.id.tvBSSID);
            checkBox = itemView.findViewById(R.id.checkBox);
        }
    }
} 
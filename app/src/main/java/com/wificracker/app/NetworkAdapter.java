package com.wificracker.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class NetworkAdapter extends RecyclerView.Adapter<NetworkAdapter.NetworkViewHolder> {
    private List<WiFiNetwork> networks;
    private final OnNetworkSelectedListener listener;

    public interface OnNetworkSelectedListener {
        void onNetworkSelected(WiFiNetwork network, boolean isSelected);
    }

    public NetworkAdapter(List<WiFiNetwork> networks, OnNetworkSelectedListener listener) {
        this.networks = networks;
        this.listener = listener;
    }

    @NonNull
    @Override
    public NetworkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_network, parent, false);
        return new NetworkViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NetworkViewHolder holder, int position) {
        WiFiNetwork network = networks.get(position);
        holder.bind(network);
    }

    @Override
    public int getItemCount() {
        return networks.size();
    }

    public void updateNetworks(List<WiFiNetwork> newNetworks) {
        this.networks = newNetworks;
        notifyDataSetChanged();
    }

    class NetworkViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvSsid;
        private final TextView tvSignal;
        private final TextView tvSecurity;
        private final CheckBox cbSelected;

        NetworkViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSsid = itemView.findViewById(R.id.tvSsid);
            tvSignal = itemView.findViewById(R.id.tvSignal);
            tvSecurity = itemView.findViewById(R.id.tvSecurity);
            cbSelected = itemView.findViewById(R.id.cbSelected);
        }

        void bind(WiFiNetwork network) {
            tvSsid.setText(network.getSsid());
            tvSignal.setText(String.format("Signal: %d dBm", network.getSignalStrength()));
            tvSecurity.setText(network.getCapabilities());
            cbSelected.setChecked(network.isSelected());

            cbSelected.setOnCheckedChangeListener((buttonView, isChecked) -> {
                network.setSelected(isChecked);
                if (listener != null) {
                    listener.onNetworkSelected(network, isChecked);
                }
            });
        }
    }
} 
package com.example.zylogi_motoristas;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton; // Importe
import java.util.ArrayList;
import java.util.List;

public class PickupAdapter extends RecyclerView.Adapter<PickupAdapter.PickupViewHolder> {

    private List<Pickup> pickups = new ArrayList<>();
    private final OnPickupActionClickListener listener; // Nosso listener

    // 1. A INTERFACE PRECISA SER DEFINIDA AQUI DENTRO
    public interface OnPickupActionClickListener {
        void onCollectedClick(Pickup pickup);
        void onNotCollectedClick(Pickup pickup);
    }

    // 2. O CONSTRUTOR DEVE RECEBER O LISTENER
    public PickupAdapter(OnPickupActionClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public PickupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pickup, parent, false);
        return new PickupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PickupViewHolder holder, int position) {
        Pickup currentPickup = pickups.get(position);
        holder.bind(currentPickup, listener);
    }

    @Override
    public int getItemCount() {
        return pickups.size();
    }

    public void setPickups(List<Pickup> pickups) {
        this.pickups = pickups;
        notifyDataSetChanged();
    }

    class PickupViewHolder extends RecyclerView.ViewHolder {
        private TextView companyName, contactName, phone, fullAddress;
        private MaterialButton buttonCollected, buttonNotCollected;

        public PickupViewHolder(@NonNull View itemView) {
            super(itemView);
            companyName = itemView.findViewById(R.id.textViewCompanyName);
            contactName = itemView.findViewById(R.id.textViewContactName);
            phone = itemView.findViewById(R.id.textViewPhone);
            fullAddress = itemView.findViewById(R.id.textViewFullAddress);
            buttonCollected = itemView.findViewById(R.id.buttonCollected);
            buttonNotCollected = itemView.findViewById(R.id.buttonNotCollected);
        }

        public void bind(final Pickup pickup, final OnPickupActionClickListener listener) {
            if (pickup.getClient() != null) {
                companyName.setText(pickup.getClient().getCompanyName());
                phone.setText("Telefone: " + pickup.getClient().getPhone());
            }

            if (pickup.getClientAddress() != null) {
                ClientAddress address = pickup.getClientAddress();
                contactName.setText("Contato: " + address.getContactName());
                String fullAddressText = String.format("%s, %s", address.getAddress(), address.getAddressNumber());
                this.fullAddress.setText(fullAddressText);
            }

            // Configura os cliques dos botões para chamar o listener
            buttonCollected.setOnClickListener(v -> listener.onCollectedClick(pickup));
            buttonNotCollected.setOnClickListener(v -> listener.onNotCollectedClick(pickup));
        }
    }
}
package com.example.zylogi_motoristas;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class PickupAdapter extends RecyclerView.Adapter<PickupAdapter.PickupViewHolder> {

    private List<Pickup> pickups = new ArrayList<>();

    @NonNull
    @Override
    public PickupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pickup, parent, false);
        return new PickupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PickupViewHolder holder, int position) {
        Pickup currentPickup = pickups.get(position);
        holder.bind(currentPickup);
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
        // Referências para todos os TextViews do novo layout
        private TextView companyName, contactName, phone, fullAddress, observation, isFragile;

        public PickupViewHolder(@NonNull View itemView) {
            super(itemView);
            companyName = itemView.findViewById(R.id.textViewCompanyName);
            contactName = itemView.findViewById(R.id.textViewContactName);
            phone = itemView.findViewById(R.id.textViewPhone);
            fullAddress = itemView.findViewById(R.id.textViewFullAddress);
            observation = itemView.findViewById(R.id.textViewObservation);
            isFragile = itemView.findViewById(R.id.textViewIsFragile);
        }

        public void bind(Pickup pickup) {
            // Checamos se os objetos aninhados não são nulos para evitar crashes
            if (pickup.getClient() != null) {
                companyName.setText(pickup.getClient().getCompanyName());
                phone.setText("Telefone: " + pickup.getClient().getPhone());
            }

            if (pickup.getClientAddress() != null) {
                ClientAddress address = pickup.getClientAddress();
                contactName.setText("Contato: " + address.getContactName());

                String neighborhood = address.getNeighborhood() != null ? address.getNeighborhood().getName() : "";
                String city = address.getCity() != null ? address.getCity().getName() : "";

                String fullAddressText = String.format("%s, %s, %s, %s - %s",
                        address.getAddress(),
                        address.getAddressNumber(),
                        neighborhood,
                        city,
                        address.getZipCode());
                this.fullAddress.setText(fullAddressText);
            }

            observation.setText("Observação: " + (pickup.getObservation() != null ? pickup.getObservation() : "Nenhuma"));
            isFragile.setText("Frágil: " + (pickup.isFragile() ? "Sim" : "Não"));
        }
    }
}
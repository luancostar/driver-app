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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class PickupAdapter extends RecyclerView.Adapter<PickupAdapter.PickupViewHolder> {

    private List<Pickup> pickups = new ArrayList<>();
    private final OnPickupActionClickListener listener; // Nosso listener

    // 1. A INTERFACE PRECISA SER DEFINIDA AQUI DENTRO
    public interface OnPickupActionClickListener {
        void onCollectedClick(Pickup pickup);
        void onNotCollectedClick(Pickup pickup);
        void onObservationClick(Pickup pickup);
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
        private TextView companyName, contactName, phone, address, scheduledDate, pickupId;
        private MaterialButton buttonCollected, buttonNotCollected;
        private android.widget.ImageView iconObservation;

        public PickupViewHolder(@NonNull View itemView) {
            super(itemView);
            companyName = itemView.findViewById(R.id.textViewCompanyName);
            contactName = itemView.findViewById(R.id.textViewContactName);
            phone = itemView.findViewById(R.id.textViewPhone);
            address = itemView.findViewById(R.id.textViewFullAddress);
            pickupId = itemView.findViewById(R.id.textViewPickupId);
            scheduledDate = itemView.findViewById(R.id.textViewScheduledDate);
            buttonCollected = itemView.findViewById(R.id.buttonCollected);
            buttonNotCollected = itemView.findViewById(R.id.buttonNotCollected);
            iconObservation = itemView.findViewById(R.id.iconObservation);
        }

        public void bind(final Pickup pickup, final OnPickupActionClickListener listener) {
            // Exibir referenceId da coleta
            if (pickup.getReferenceId() != null && !pickup.getReferenceId().isEmpty()) {
                pickupId.setText("🆔 ID: #" + pickup.getReferenceId());
            } else {
                pickupId.setText("🆔 ID: Não informado");
            }

            if (pickup.getClient() != null) {
                companyName.setText(pickup.getClient().getCompanyName());
                phone.setText("Telefone: " + pickup.getClient().getPhone());
            }

            if (pickup.getClientAddress() != null && pickup.getClientAddress().getContactName() != null) {
                contactName.setText("Contato: " + pickup.getClientAddress().getContactName());
            } else {
                contactName.setText("Contato: Não informado");
            }

            if (pickup.getClientAddress() != null) {
                ClientAddress clientAddress = pickup.getClientAddress();
                StringBuilder fullAddressText = new StringBuilder();
                
                if (clientAddress.getAddress() != null) {
                    fullAddressText.append(clientAddress.getAddress());
                }
                
                if (clientAddress.getAddressNumber() != null) {
                    if (fullAddressText.length() > 0) fullAddressText.append(", ");
                    fullAddressText.append(clientAddress.getAddressNumber());
                }
                
                if (clientAddress.getNeighborhood() != null && clientAddress.getNeighborhood().getName() != null) {
                    if (fullAddressText.length() > 0) fullAddressText.append(" - ");
                    fullAddressText.append(clientAddress.getNeighborhood().getName());
                }
                
                if (clientAddress.getCity() != null && clientAddress.getCity().getName() != null) {
                    if (fullAddressText.length() > 0) fullAddressText.append(", ");
                    fullAddressText.append(clientAddress.getCity().getName());
                }
                
                address.setText(fullAddressText.toString());
            }

            // Configura a data de agendamento
            if (pickup.getScheduledDate() != null && !pickup.getScheduledDate().trim().isEmpty()) {
                String formattedDate = formatScheduledDate(pickup.getScheduledDate());
                scheduledDate.setText("📅 Agendado para: " + formattedDate);
                scheduledDate.setVisibility(View.VISIBLE);
            } else {
                scheduledDate.setText("📅 Agendado para: Hoje");
                scheduledDate.setVisibility(View.VISIBLE);
            }

            // Configura a visibilidade e clique do ícone de observação
            if (pickup.getObservation() != null && !pickup.getObservation().trim().isEmpty()) {
                iconObservation.setVisibility(View.VISIBLE);
                iconObservation.setOnClickListener(v -> listener.onObservationClick(pickup));
            } else {
                iconObservation.setVisibility(View.GONE);
            }

            // Verifica se há operação pendente para esta coleta
            checkPendingOperationAndConfigureButtons(pickup, listener);
        }
        
        private void checkPendingOperationAndConfigureButtons(final Pickup pickup, final OnPickupActionClickListener listener) {
            // Obtém instância do OfflineRepository
            com.example.zylogi_motoristas.offline.OfflineRepository offlineRepository = 
                com.example.zylogi_motoristas.offline.OfflineRepository.getInstance(itemView.getContext());
            
            // Verifica se há operação pendente para este pickup
            offlineRepository.hasOperationForPickup(pickup.getId(), new com.example.zylogi_motoristas.offline.OfflineRepository.BooleanCallback() {
                @Override
                public void onResult(boolean hasPendingOperation) {
                    // Executa na thread principal para atualizar a UI
                    itemView.post(() -> {
                        if (hasPendingOperation) {
                            // Desabilita os botões e muda a aparência
                            buttonCollected.setEnabled(false);
                            buttonNotCollected.setEnabled(false);
                            
                            // Muda o texto dos botões para indicar que está pendente
                            buttonCollected.setText("⏳ AGUARDANDO SINCRONIZAÇÃO");
                            buttonNotCollected.setText("⏳ AGUARDANDO SINCRONIZAÇÃO");
                            
                            // Muda a cor para cinza
                            buttonCollected.setBackgroundTintList(
                                android.content.res.ColorStateList.valueOf(0xFF9E9E9E)); // Cinza
                            buttonNotCollected.setBackgroundTintList(
                                android.content.res.ColorStateList.valueOf(0xFF9E9E9E)); // Cinza
                            
                            // Remove os listeners de clique
                            buttonCollected.setOnClickListener(null);
                            buttonNotCollected.setOnClickListener(null);
                        } else {
                            // Habilita os botões e restaura a aparência normal
                            buttonCollected.setEnabled(true);
                            buttonNotCollected.setEnabled(true);
                            
                            // Restaura o texto original
                            buttonCollected.setText("✅ COLETADO");
                            buttonNotCollected.setText("❌ NÃO COLETADO");
                            
                            // Restaura as cores originais
                            buttonCollected.setBackgroundTintList(
                                android.content.res.ColorStateList.valueOf(0xFF4CAF50)); // Verde
                            buttonNotCollected.setBackgroundTintList(
                                android.content.res.ColorStateList.valueOf(0xFFD32F2F)); // Vermelho
                            
                            // Configura os cliques dos botões para chamar o listener
                            buttonCollected.setOnClickListener(v -> listener.onCollectedClick(pickup));
                            buttonNotCollected.setOnClickListener(v -> listener.onNotCollectedClick(pickup));
                        }
                    });
                }
            });
        }

        // Método auxiliar para formatar a data de agendamento
        private String formatScheduledDate(String scheduledDate) {
            try {
                // Extrai apenas a parte da data (ignora horário se houver)
                String dateOnly = scheduledDate.substring(0, Math.min(scheduledDate.length(), 10));
                
                // Converte de yyyy-MM-dd para dd/MM/yyyy
                LocalDate date = LocalDate.parse(dateOnly, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault()));
            } catch (Exception e) {
                // Em caso de erro, retorna a data original
                return scheduledDate;
            }
        }
    }
}
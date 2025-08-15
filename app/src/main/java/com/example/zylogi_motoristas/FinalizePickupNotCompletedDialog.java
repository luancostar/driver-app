package com.example.zylogi_motoristas;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import androidx.annotation.NonNull;

public class FinalizePickupNotCompletedDialog extends Dialog {

    public interface OnFinalizeListener {
        void onFinalize(Occurrence occurrence, String observation);
    }

    private TextView textViewPickupIdDialog;
    private Spinner spinnerOccurrence;
    private EditText editTextObservation;
    private Button buttonCancel, buttonFinalize, buttonCamera;
    private TextView textViewPhotoStatus;
    private OnFinalizeListener listener;
    private List<Occurrence> occurrenceList;
    private ApiService apiService;
    private AuthSessionManager authSessionManager;
    private String photoBase64 = null;
    private Pickup currentPickup;

    public FinalizePickupNotCompletedDialog(@NonNull Context context, Pickup pickup, OnFinalizeListener listener) {
        super(context);
        this.listener = listener;
        this.currentPickup = pickup;
        this.occurrenceList = new ArrayList<>();
        this.apiService = RetrofitClient.getClient(context).create(ApiService.class);
        this.authSessionManager = new AuthSessionManager(context);
        
        setupDialog(pickup);
    }

    private void setupDialog(Pickup pickup) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_finalize_pickup, null);
        setContentView(view);
        
        textViewPickupIdDialog = view.findViewById(R.id.textViewPickupIdDialog);
        spinnerOccurrence = view.findViewById(R.id.spinnerOccurrence);
        editTextObservation = view.findViewById(R.id.editTextObservation);
        buttonCancel = view.findViewById(R.id.buttonCancel);
        buttonFinalize = view.findViewById(R.id.buttonFinalize);
        buttonCamera = view.findViewById(R.id.buttonCamera);
        textViewPhotoStatus = view.findViewById(R.id.textViewPhotoStatus);
        
        // Configurar o texto do pickup
        if (pickup != null && pickup.getReferenceId() != null) {
            textViewPickupIdDialog.setText("🆔 Coleta: " + pickup.getReferenceId());
        } else {
            textViewPickupIdDialog.setText("🆔 Coleta: ID não disponível");
        }
        
        // Carregar ocorrências da API
        loadOccurrencesFromApi();
        
        // Configurar listeners dos botões
        buttonCancel.setOnClickListener(v -> dismiss());
        
        buttonCamera.setOnClickListener(v -> {
            openCamera();
        });
        
        buttonFinalize.setOnClickListener(v -> {
            int selectedPosition = spinnerOccurrence.getSelectedItemPosition();
            String observation = editTextObservation.getText().toString().trim();

            if (selectedPosition == 0) { // "Selecione uma ocorrência"
                Toast.makeText(getContext(), "Por favor, selecione uma ocorrência", Toast.LENGTH_SHORT).show();
                return;
            }

            // Obter a ocorrência selecionada
            Occurrence selectedOccurrence = null;
            Log.d("FinalizePickupNotCompletedDialog", "Selected position: " + selectedPosition);
            Log.d("FinalizePickupNotCompletedDialog", "Occurrence list size: " + occurrenceList.size());
            
            if (!occurrenceList.isEmpty() && selectedPosition <= occurrenceList.size()) {
                // Usando ocorrência da API
                selectedOccurrence = occurrenceList.get(selectedPosition - 1); // -1 porque o primeiro item é "Selecione..."
                Log.d("FinalizePickupNotCompletedDialog", "Usando ocorrência da API: " + selectedOccurrence.getName() + " (ID: " + selectedOccurrence.getId() + ")");
            } else {
                // Fallback: criar um objeto Occurrence temporário com o nome selecionado
                String selectedName = spinnerOccurrence.getSelectedItem().toString();
                selectedOccurrence = new Occurrence();
                selectedOccurrence.setName(selectedName);
                selectedOccurrence.setOccurrenceNumber(selectedPosition); // Usar a posição como número temporário
                // Para fallback, vamos usar um ID baseado na posição
                selectedOccurrence.setId("fallback_" + selectedPosition);
                Log.d("FinalizePickupNotCompletedDialog", "Usando fallback: " + selectedName + " (ID temporário: fallback_" + selectedPosition + ")");
            }

            // Finalizar com detalhes completos
            finalizePickupWithDetails(selectedOccurrence, observation);
        });
    }

    private void loadOccurrencesFromApi() {
        String token = authSessionManager.getAuthToken();
        if (token != null && !token.isEmpty()) {
            apiService.getDriverOccurrences().enqueue(new Callback<List<Occurrence>>() {
                @Override
                public void onResponse(Call<List<Occurrence>> call, Response<List<Occurrence>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        occurrenceList = response.body();
                        Log.d("FinalizePickupNotCompletedDialog", "Ocorrências carregadas: " + occurrenceList.size());
                        setupOccurrenceSpinner();
                    } else {
                        Log.e("FinalizePickupNotCompletedDialog", "Falha ao carregar ocorrências: " + response.code());
                        setupFallbackSpinner();
                    }
                }
                
                @Override
                public void onFailure(Call<List<Occurrence>> call, Throwable t) {
                    Log.e("FinalizePickupNotCompletedDialog", "Erro ao carregar ocorrências: " + t.getMessage());
                    setupFallbackSpinner();
                }
            });
        } else {
            Log.w("FinalizePickupNotCompletedDialog", "Token não disponível, usando spinner de fallback");
            setupFallbackSpinner();
        }
    }

    private void setupLoadingSpinner() {
        List<String> loadingItems = new ArrayList<>();
        loadingItems.add("Carregando ocorrências...");
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), 
            android.R.layout.simple_spinner_item, loadingItems);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerOccurrence.setAdapter(adapter);
        spinnerOccurrence.setEnabled(false);
    }

    private void setupOccurrenceSpinner() {
        List<String> occurrenceNames = new ArrayList<>();
        occurrenceNames.add("Selecione uma ocorrência");
        
        for (Occurrence occurrence : occurrenceList) {
            occurrenceNames.add(occurrence.getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            getContext(),
            android.R.layout.simple_spinner_item,
            occurrenceNames
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerOccurrence.setAdapter(adapter);
        spinnerOccurrence.setEnabled(true);
        
        Log.d("FinalizePickupNotCompletedDialog", "Spinner configurado com " + occurrenceNames.size() + " itens");
    }

    private void setupFallbackSpinner() {
        // Lista de fallback caso a API falhe
        String[] fallbackOccurrences = {
            "Selecione uma ocorrência",
            "Cliente ausente",
            "Endereço não localizado",
            "Material não estava pronto",
            "Estabelecimento fechado",
            "Outros"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            getContext(),
            android.R.layout.simple_spinner_item,
            fallbackOccurrences
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerOccurrence.setAdapter(adapter);
        spinnerOccurrence.setEnabled(true);
        
        Log.d("FinalizePickupNotCompletedDialog", "Usando spinner de fallback com " + fallbackOccurrences.length + " itens");
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED) {
            
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (cameraIntent.resolveActivity(getContext().getPackageManager()) != null) {
                // Usar o launcher da MainActivity
                if (getContext() instanceof MainActivity) {
                    ((MainActivity) getContext()).startCameraForResult(this);
                }
            } else {
                Toast.makeText(getContext(), "Câmera não disponível", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(getContext(), "Permissão de câmera necessária", Toast.LENGTH_SHORT).show();
        }
    }

    public void onPhotoTaken(Bitmap photo) {
        if (photo != null) {
            // Converter para Base64
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            photo.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            photoBase64 = Base64.encodeToString(byteArray, Base64.DEFAULT);
            
            // Atualizar UI
            textViewPhotoStatus.setText("Foto capturada ✓");
            textViewPhotoStatus.setTextColor(getContext().getResources().getColor(android.R.color.holo_green_dark));
            
            Log.d("FinalizePickupNotCompletedDialog", "Foto capturada e convertida para Base64");
        }
    }

    private void finalizePickupWithDetails(Occurrence occurrence, String observation) {
        // Preparar dados para envio
        String occurrenceId = (occurrence != null && occurrence.getId() != null) ? occurrence.getId() : "";
        String driverAttachmentUrl = photoBase64 != null ? "data:image/jpeg;base64," + photoBase64 : "";
        
        // Logs de debug para rastrear os valores
        Log.d("FinalizePickupNotCompletedDialog", "=== DADOS PARA ENVIO (NOT_COMPLETED) ===");
        Log.d("FinalizePickupNotCompletedDialog", "Pickup ID: " + currentPickup.getId());
        Log.d("FinalizePickupNotCompletedDialog", "Observação: '" + observation + "'");
        Log.d("FinalizePickupNotCompletedDialog", "Occurrence ID: '" + occurrenceId + "'");
        Log.d("FinalizePickupNotCompletedDialog", "Driver Attachment URL: " + (photoBase64 != null ? "Foto presente (Base64)" : "Sem foto"));
        
        if (occurrence != null) {
            Log.d("FinalizePickupNotCompletedDialog", "Occurrence Name: " + occurrence.getName());
            Log.d("FinalizePickupNotCompletedDialog", "Occurrence ID from object: " + occurrence.getId());
        } else {
            Log.d("FinalizePickupNotCompletedDialog", "Occurrence object is null");
        }
        
        // Usar MainViewModel para finalizar
        MainActivity activity = null;
        
        // Verificar se o contexto é MainActivity ou se podemos obter a MainActivity
        if (getContext() instanceof MainActivity) {
            activity = (MainActivity) getContext();
        } else if (getContext() instanceof android.view.ContextThemeWrapper) {
            // Para ContextThemeWrapper, tentar obter a base context
            android.content.Context baseContext = ((android.view.ContextThemeWrapper) getContext()).getBaseContext();
            if (baseContext instanceof MainActivity) {
                activity = (MainActivity) baseContext;
            } else {
                // Tentar através do getOwnerActivity se for um Dialog
                if (getOwnerActivity() instanceof MainActivity) {
                    activity = (MainActivity) getOwnerActivity();
                }
            }
        }
        
        if (activity != null) {
            Log.d("FinalizePickupNotCompletedDialog", "Chamando MainViewModel.finalizePickupWithDetailsNotCompleted...");
            activity.getMainViewModel().finalizePickupWithDetailsNotCompleted(
                currentPickup, 
                observation, 
                occurrenceId, 
                driverAttachmentUrl
            );
            Log.d("FinalizePickupNotCompletedDialog", "Chamada do MainViewModel concluída");
        } else {
            Log.e("FinalizePickupNotCompletedDialog", "Não foi possível obter MainActivity. Context: " + getContext().getClass().getSimpleName());
        }
        
        // Chamar o listener original para compatibilidade
        if (listener != null) {
            listener.onFinalize(occurrence, observation);
        }
        
        dismiss();
    }
}
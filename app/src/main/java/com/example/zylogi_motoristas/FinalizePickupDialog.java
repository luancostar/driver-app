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

public class FinalizePickupDialog extends Dialog {

    public interface OnFinalizeListener {
        void onFinalize(Occurrence occurrence, String observation);
    }

    private TextView pickupIdText;
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

    public FinalizePickupDialog(@NonNull Context context, Pickup pickup, OnFinalizeListener listener) {
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

        // Inicializar views
        pickupIdText = view.findViewById(R.id.textViewPickupIdDialog);
        spinnerOccurrence = view.findViewById(R.id.spinnerOccurrence);
        editTextObservation = view.findViewById(R.id.editTextObservation);
        buttonCancel = view.findViewById(R.id.buttonCancel);
        buttonFinalize = view.findViewById(R.id.buttonFinalize);
        buttonCamera = view.findViewById(R.id.buttonCamera);
        textViewPhotoStatus = view.findViewById(R.id.textViewPhotoStatus);

        // Configurar referenceId da coleta
        if (pickup.getReferenceId() != null && !pickup.getReferenceId().isEmpty()) {
            pickupIdText.setText("🆔 ID: #" + pickup.getReferenceId());
        } else {
            pickupIdText.setText("🆔 ID: Não informado");
        }

        // Carregar ocorrências da API
        loadOccurrencesFromApi();

        // Configurar botão para COLETADO (verde)
        buttonFinalize.setText("Coletado");
        if (buttonFinalize instanceof com.google.android.material.button.MaterialButton) {
            ((com.google.android.material.button.MaterialButton) buttonFinalize).setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF4CAF50)); // Verde
        } else {
            buttonFinalize.setBackgroundColor(0xFF4CAF50);
        }
        buttonFinalize.setTextColor(0xFFFFFFFF); // Branco

        // Configurar listeners dos botões
        buttonCancel.setOnClickListener(v -> dismiss());
        
        buttonCamera.setOnClickListener(v -> openCamera());
        
        buttonFinalize.setOnClickListener(v -> {
            int selectedPosition = spinnerOccurrence.getSelectedItemPosition();
            String observation = editTextObservation.getText().toString().trim();

            if (selectedPosition == 0) { // "Selecione uma ocorrência"
                Toast.makeText(getContext(), "Por favor, selecione uma ocorrência", Toast.LENGTH_SHORT).show();
                return;
            }

            // Obter a ocorrência selecionada
            Occurrence selectedOccurrence = null;
            Log.d("FinalizePickupDialog", "Selected position: " + selectedPosition);
            Log.d("FinalizePickupDialog", "Occurrence list size: " + occurrenceList.size());
            
            if (!occurrenceList.isEmpty() && selectedPosition <= occurrenceList.size()) {
                // Usando ocorrência da API
                selectedOccurrence = occurrenceList.get(selectedPosition - 1); // -1 porque o primeiro item é "Selecione..."
                Log.d("FinalizePickupDialog", "Usando ocorrência da API: " + selectedOccurrence.getName() + " (ID: " + selectedOccurrence.getId() + ")");
            } else {
                // Fallback: criar um objeto Occurrence temporário com o nome selecionado
                String selectedName = spinnerOccurrence.getSelectedItem().toString();
                selectedOccurrence = new Occurrence();
                selectedOccurrence.setName(selectedName);
                selectedOccurrence.setOccurrenceNumber(selectedPosition); // Usar a posição como número temporário
                // Para fallback, vamos usar um ID baseado na posição
                selectedOccurrence.setId("fallback_" + selectedPosition);
                Log.d("FinalizePickupDialog", "Usando fallback: " + selectedName + " (ID temporário: fallback_" + selectedPosition + ")");
            }

            // Finalizar com detalhes completos
            finalizePickupWithDetails(selectedOccurrence, observation);
        });

        // Configurar propriedades do dialog
        setCancelable(true);
        setCanceledOnTouchOutside(true);
    }

    private void loadOccurrencesFromApi() {
        Log.d("FinalizePickupDialog", "Iniciando carregamento de ocorrências da API");
        
        // Verificar se o usuário está logado
        String token = authSessionManager.getAuthToken();
        if (token == null) {
            Log.w("FinalizePickupDialog", "Usuário não está logado - usando lista de fallback");
            Toast.makeText(getContext(), "Usuário não autenticado - usando lista padrão", Toast.LENGTH_SHORT).show();
            setupFallbackSpinner();
            return;
        }
        
        Log.d("FinalizePickupDialog", "Token encontrado - prosseguindo com requisição");
        // Mostrar loading temporário
        setupLoadingSpinner();
        
        Call<List<Occurrence>> call = apiService.getDriverOccurrences();
        call.enqueue(new Callback<List<Occurrence>>() {
            @Override
            public void onResponse(Call<List<Occurrence>> call, Response<List<Occurrence>> response) {
                Log.d("FinalizePickupDialog", "Resposta recebida - Código: " + response.code());
                if (response.isSuccessful() && response.body() != null) {
                    List<Occurrence> occurrences = response.body();
                    Log.d("FinalizePickupDialog", "Número de ocorrências recebidas: " + occurrences.size());
                    
                    // O endpoint /occurrences/driver já retorna apenas as ocorrências disponíveis para motoristas
                    occurrenceList.clear();
                    occurrenceList.addAll(occurrences);
                    
                    // Log das ocorrências recebidas
                    for (Occurrence occ : occurrences) {
                        Log.d("FinalizePickupDialog", "Ocorrência: " + occ.getName() + " (ID: " + occ.getId() + ")");
                    }
                    
                    setupOccurrenceSpinner();
                } else {
                    Log.e("FinalizePickupDialog", "Erro na resposta - Código: " + response.code());
                    Toast.makeText(getContext(), "Erro ao carregar ocorrências", Toast.LENGTH_SHORT).show();
                    setupFallbackSpinner();
                }
            }

            @Override
            public void onFailure(Call<List<Occurrence>> call, Throwable t) {
                Log.e("FinalizePickupDialog", "Falha na requisição: " + t.getMessage());
                Toast.makeText(getContext(), "Erro de conexão ao carregar ocorrências", Toast.LENGTH_SHORT).show();
                setupFallbackSpinner();
            }
        });
    }

    private void setupLoadingSpinner() {
        String[] loading = {"Carregando ocorrências..."};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            getContext(),
            android.R.layout.simple_spinner_item,
            loading
        );
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
    }

    private void setupFallbackSpinner() {
        // Lista de fallback caso a API falhe
        String[] fallbackOccurrences = {
            "Selecione uma ocorrência",
            "Coleta realizada com sucesso",
            "Cliente ausente",
            "Endereço não localizado",
            "Material não estava pronto",
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
    }

    private void openCamera() {
        // Verificar permissão da câmera
        if (ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getContext(), "Permissão da câmera necessária", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getContext().getPackageManager()) != null) {
            // Como estamos em um Dialog, precisamos usar a Activity pai
            if (getContext() instanceof MainActivity) {
                ((MainActivity) getContext()).startCameraForResult(this);
            }
        } else {
            Toast.makeText(getContext(), "Câmera não disponível", Toast.LENGTH_SHORT).show();
        }
    }

    public void onPhotoTaken(Bitmap photo) {
        if (photo != null) {
            // Converter bitmap para base64
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            photo.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            photoBase64 = Base64.encodeToString(byteArray, Base64.DEFAULT);
            
            textViewPhotoStatus.setText("✅ Foto capturada");
            textViewPhotoStatus.setTextColor(ContextCompat.getColor(getContext(), android.R.color.holo_green_dark));
        }
    }

    private void finalizePickupWithDetails(Occurrence occurrence, String observation) {
        // Preparar dados para envio
        String occurrenceId = (occurrence != null && occurrence.getId() != null) ? occurrence.getId() : "";
        String driverAttachmentUrl = photoBase64 != null ? "data:image/jpeg;base64," + photoBase64 : "";
        
        // Logs de debug para rastrear os valores
        Log.d("FinalizePickupDialog", "=== DADOS PARA ENVIO ===");
        Log.d("FinalizePickupDialog", "Pickup ID: " + currentPickup.getId());
        Log.d("FinalizePickupDialog", "Observação: '" + observation + "'");
        Log.d("FinalizePickupDialog", "Occurrence ID: '" + occurrenceId + "'");
        Log.d("FinalizePickupDialog", "Driver Attachment URL: " + (photoBase64 != null ? "Foto presente (Base64)" : "Sem foto"));
        
        if (occurrence != null) {
            Log.d("FinalizePickupDialog", "Occurrence Name: " + occurrence.getName());
            Log.d("FinalizePickupDialog", "Occurrence ID from object: " + occurrence.getId());
        } else {
            Log.d("FinalizePickupDialog", "Occurrence object is null");
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
            Log.d("FinalizePickupDialog", "Chamando MainViewModel.finalizePickupWithDetails...");
            activity.getMainViewModel().finalizePickupWithDetails(
                currentPickup, 
                observation, 
                occurrenceId, 
                driverAttachmentUrl
            );
            Log.d("FinalizePickupDialog", "Chamada do MainViewModel concluída");
        } else {
            Log.e("FinalizePickupDialog", "Não foi possível obter MainActivity. Context: " + getContext().getClass().getSimpleName());
        }
        
        // Chamar o listener original para compatibilidade
        if (listener != null) {
            listener.onFinalize(occurrence, observation);
        }
        
        dismiss();
    }
}
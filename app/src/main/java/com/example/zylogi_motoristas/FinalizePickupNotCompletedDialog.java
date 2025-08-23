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
    private Button buttonCancel, buttonFinalize, buttonCamera, buttonGallery;
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
        buttonGallery = view.findViewById(R.id.buttonGallery);
        textViewPhotoStatus = view.findViewById(R.id.textViewPhotoStatus);
        
        // Configurar o texto do pickup
        if (pickup != null && pickup.getReferenceId() != null) {
            textViewPickupIdDialog.setText("🆔 Coleta: " + pickup.getReferenceId());
        } else {
            textViewPickupIdDialog.setText("🆔 Coleta: ID não disponível");
        }
        
        // Carregar ocorrências da API
        loadOccurrencesFromApi();
        
        // Configurar botão para NÃO COLETADO (vermelho)
        buttonFinalize.setText("Não Coletado");
        if (buttonFinalize instanceof com.google.android.material.button.MaterialButton) {
            ((com.google.android.material.button.MaterialButton) buttonFinalize).setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFFD32F2F)); // Vermelho
        } else {
            buttonFinalize.setBackgroundColor(0xFFD32F2F);
        }
        buttonFinalize.setTextColor(0xFFFFFFFF); // Branco

        // Configurar listeners dos botões
        buttonCancel.setOnClickListener(v -> dismiss());
        
        buttonCamera.setOnClickListener(v -> {
            Log.d("FinalizePickupNotCompletedDialog", "Botão da câmera clicado!");
            openCamera();
        });
        
        buttonGallery.setOnClickListener(v -> {
            Log.d("FinalizePickupNotCompletedDialog", "Botão da galeria clicado!");
            openGallery();
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
                // Para fallback, não definir ID para evitar erro de UUID na API
                selectedOccurrence.setId(null);
                Log.d("FinalizePickupNotCompletedDialog", "Usando fallback: " + selectedName + " (sem ID para evitar erro de UUID)");
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
        Log.d("FinalizePickupNotCompletedDialog", "openCamera() chamado");
        
        // Verificar permissão da câmera
        int permissionStatus = ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.CAMERA);
        Log.d("FinalizePickupNotCompletedDialog", "Status da permissão da câmera: " + permissionStatus + " (GRANTED=" + PackageManager.PERMISSION_GRANTED + ")");
        
        if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
            Log.d("FinalizePickupNotCompletedDialog", "Permissão já concedida, abrindo câmera diretamente");
            openCameraAfterPermission();
        } else {
            Log.d("FinalizePickupNotCompletedDialog", "Permissão da câmera negada, solicitando permissão");
            // Solicitar permissão através da MainActivity
            if (getContext() instanceof MainActivity) {
                Log.d("FinalizePickupNotCompletedDialog", "Context é MainActivity, chamando requestCameraPermission");
                ((MainActivity) getContext()).requestCameraPermission();
            } else {
                Log.e("FinalizePickupNotCompletedDialog", "Context não é MainActivity: " + getContext().getClass().getSimpleName());
                Toast.makeText(getContext(), "Permissão de câmera necessária", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    public void openCameraAfterPermission() {
        Log.d("FinalizePickupNotCompletedDialog", "openCameraAfterPermission() chamado");
        Log.d("FinalizePickupNotCompletedDialog", "Context class: " + getContext().getClass().getName());
        
        Context context = getContext();
        if (context instanceof MainActivity) {
            Log.d("FinalizePickupNotCompletedDialog", "Chamando startCameraForResult na MainActivity");
            ((MainActivity) context).startCameraForResult(this);
        } else {
            Log.e("FinalizePickupNotCompletedDialog", "Context não é MainActivity: " + context.getClass().getName());
            // Tentar encontrar a MainActivity através do contexto
            if (context instanceof android.view.ContextThemeWrapper) {
                Context baseContext = ((android.view.ContextThemeWrapper) context).getBaseContext();
                Log.d("FinalizePickupNotCompletedDialog", "BaseContext class: " + baseContext.getClass().getName());
                if (baseContext instanceof MainActivity) {
                    Log.d("FinalizePickupNotCompletedDialog", "Usando BaseContext como MainActivity");
                    ((MainActivity) baseContext).startCameraForResult(this);
                    return;
                }
            }
            Toast.makeText(getContext(), "Erro ao abrir câmera", Toast.LENGTH_SHORT).show();
        }
    }

    public void onPhotoTaken(Bitmap photo) {
        if (photo != null) {
            Log.d("FinalizePickupNotCompletedDialog", "=== PROCESSANDO FOTO DA CÂMERA ====");
            Log.d("FinalizePickupNotCompletedDialog", "Bitmap recebido - Width: " + photo.getWidth() + ", Height: " + photo.getHeight());
            
            // Converter para Base64
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            photo.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            photoBase64 = Base64.encodeToString(byteArray, Base64.NO_WRAP);
            
            Log.d("FinalizePickupNotCompletedDialog", "Foto convertida para Base64 - Tamanho: " + byteArray.length + " bytes");
            Log.d("FinalizePickupNotCompletedDialog", "Base64 length: " + photoBase64.length() + " caracteres");
            Log.d("FinalizePickupNotCompletedDialog", "Base64 preview (primeiros 100 chars): " + photoBase64.substring(0, Math.min(100, photoBase64.length())));
            
            // Atualizar UI
            textViewPhotoStatus.setText("✅ Foto capturada");
            textViewPhotoStatus.setTextColor(getContext().getResources().getColor(android.R.color.holo_green_dark));
            
            Log.d("FinalizePickupNotCompletedDialog", "Foto da câmera salva como Base64 com sucesso!");
        } else {
            Log.e("FinalizePickupNotCompletedDialog", "Erro: Bitmap da foto da câmera é nulo");
        }
    }
    
    private void openGallery() {
        Log.d("FinalizePickupNotCompletedDialog", "openGallery() chamado");
        
        // Verificar permissão baseada na versão do Android
        String permission;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permission = android.Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            permission = android.Manifest.permission.READ_EXTERNAL_STORAGE;
        }
        
        int permissionStatus = ContextCompat.checkSelfPermission(getContext(), permission);
        Log.d("FinalizePickupNotCompletedDialog", "Status da permissão de armazenamento (" + permission + "): " + permissionStatus + " (GRANTED=" + PackageManager.PERMISSION_GRANTED + ")");
        
        if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
            Log.d("FinalizePickupNotCompletedDialog", "Permissão já concedida, abrindo galeria diretamente");
            openGalleryAfterPermission();
        } else {
            Log.d("FinalizePickupNotCompletedDialog", "Permissão de armazenamento negada, solicitando permissão");
            // Solicitar permissão através da MainActivity
            if (getContext() instanceof MainActivity) {
                Log.d("FinalizePickupNotCompletedDialog", "Context é MainActivity, chamando requestStoragePermission");
                ((MainActivity) getContext()).requestStoragePermission();
            } else {
                Log.e("FinalizePickupNotCompletedDialog", "Context não é MainActivity: " + getContext().getClass().getSimpleName());
                Toast.makeText(getContext(), "Permissão de armazenamento necessária", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    public void openGalleryAfterPermission() {
        Log.d("FinalizePickupNotCompletedDialog", "openGalleryAfterPermission() chamado");
        Log.d("FinalizePickupNotCompletedDialog", "Context class: " + getContext().getClass().getName());
        
        Context context = getContext();
        if (context instanceof MainActivity) {
            Log.d("FinalizePickupNotCompletedDialog", "Chamando startGalleryForResult na MainActivity");
            ((MainActivity) context).startGalleryForResult(this);
        } else {
            Log.e("FinalizePickupNotCompletedDialog", "Context não é MainActivity: " + context.getClass().getName());
            // Tentar encontrar a MainActivity através do contexto
            if (context instanceof android.view.ContextThemeWrapper) {
                Context baseContext = ((android.view.ContextThemeWrapper) context).getBaseContext();
                Log.d("FinalizePickupNotCompletedDialog", "BaseContext class: " + baseContext.getClass().getName());
                if (baseContext instanceof MainActivity) {
                    Log.d("FinalizePickupNotCompletedDialog", "Usando BaseContext como MainActivity");
                    ((MainActivity) baseContext).startGalleryForResult(this);
                    return;
                }
            }
            Toast.makeText(getContext(), "Erro ao abrir galeria", Toast.LENGTH_SHORT).show();
        }
    }
    
    public void onPhotoSelected(Bitmap photo) {
        if (photo != null) {
            Log.d("FinalizePickupNotCompletedDialog", "=== PROCESSANDO FOTO DA GALERIA ====");
            Log.d("FinalizePickupNotCompletedDialog", "Bitmap recebido - Width: " + photo.getWidth() + ", Height: " + photo.getHeight());
            
            // Converter para Base64
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            photo.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            photoBase64 = Base64.encodeToString(byteArray, Base64.NO_WRAP);
            
            Log.d("FinalizePickupNotCompletedDialog", "Foto da galeria convertida para Base64");
            Log.d("FinalizePickupNotCompletedDialog", "Tamanho do array de bytes: " + byteArray.length);
            Log.d("FinalizePickupNotCompletedDialog", "Tamanho da string Base64: " + photoBase64.length());
            Log.d("FinalizePickupNotCompletedDialog", "Preview Base64: " + photoBase64.substring(0, Math.min(50, photoBase64.length())));
            
            // Atualizar UI
            textViewPhotoStatus.setText("✅ Foto da galeria selecionada!");
            textViewPhotoStatus.setTextColor(getContext().getResources().getColor(android.R.color.holo_green_dark));
            
            Log.d("FinalizePickupNotCompletedDialog", "Foto da galeria salva como Base64 com sucesso!");
        } else {
            Log.e("FinalizePickupNotCompletedDialog", "Erro: Bitmap da foto da galeria é nulo");
            textViewPhotoStatus.setText("❌ Erro ao selecionar foto");
            textViewPhotoStatus.setTextColor(getContext().getResources().getColor(android.R.color.holo_red_dark));
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
        Log.d("FinalizePickupNotCompletedDialog", "photoBase64 is null: " + (photoBase64 == null));
        if (photoBase64 != null) {
            Log.d("FinalizePickupNotCompletedDialog", "photoBase64 length: " + photoBase64.length());
            Log.d("FinalizePickupNotCompletedDialog", "photoBase64 preview: " + photoBase64.substring(0, Math.min(50, photoBase64.length())));
        }
        Log.d("FinalizePickupNotCompletedDialog", "driverAttachmentUrl: " + driverAttachmentUrl);
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
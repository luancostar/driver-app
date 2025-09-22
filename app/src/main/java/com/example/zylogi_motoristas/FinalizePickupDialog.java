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
    private EditText editTextQuantity;
    private TextView labelOccurrence, labelQuantity;
    private Button buttonCancel, buttonFinalize, buttonCamera, buttonGallery;
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
        editTextQuantity = view.findViewById(R.id.editTextQuantity);
        labelOccurrence = view.findViewById(R.id.labelOccurrence);
        labelQuantity = view.findViewById(R.id.labelQuantity);
        buttonCancel = view.findViewById(R.id.buttonCancel);
        buttonFinalize = view.findViewById(R.id.buttonFinalize);
        buttonCamera = view.findViewById(R.id.buttonCamera);
        buttonGallery = view.findViewById(R.id.buttonGallery);
        textViewPhotoStatus = view.findViewById(R.id.textViewPhotoStatus);

        // Configurar estado inicial do textViewPhotoStatus
        textViewPhotoStatus.setText("Nenhuma foto selecionada");
        textViewPhotoStatus.setTextColor(getContext().getResources().getColor(android.R.color.darker_gray));
        textViewPhotoStatus.setVisibility(View.VISIBLE);

        // Para coletas coletadas, ocultar spinner de ocorrências e mostrar campo de quantidade
        labelOccurrence.setVisibility(View.GONE);
        spinnerOccurrence.setVisibility(View.GONE);
        labelQuantity.setVisibility(View.VISIBLE);
        editTextQuantity.setVisibility(View.VISIBLE);

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
        
        buttonCamera.setOnClickListener(v -> {
            Log.d("FinalizePickupDialog", "Botão da câmera clicado!");
            openCamera();
        });
        
        buttonGallery.setOnClickListener(v -> {
            Log.d("FinalizePickupDialog", "Botão da galeria clicado!");
            openGallery();
        });
        
        buttonFinalize.setOnClickListener(v -> {
            String observation = editTextObservation.getText().toString().trim();
            
            // Coletar ocorrência selecionada no spinner
            Occurrence selectedOccurrence = null;
            int selectedPosition = spinnerOccurrence.getSelectedItemPosition();
            if (selectedPosition > 0 && occurrenceList != null && selectedPosition <= occurrenceList.size()) {
                // Posição 0 é "Selecione uma ocorrência", então subtraímos 1
                selectedOccurrence = occurrenceList.get(selectedPosition - 1);
                Log.d("FinalizePickupDialog", "Ocorrência selecionada: " + selectedOccurrence.getName() + " (ID: " + selectedOccurrence.getId() + ")");
            } else {
                Log.d("FinalizePickupDialog", "Nenhuma ocorrência selecionada ou posição inválida: " + selectedPosition);
            }

            // Finalizar com todos os dados coletados
            finalizePickupWithDetails(selectedOccurrence, observation);
        });

        // Configurar propriedades do dialog
        setCancelable(true);
        setCanceledOnTouchOutside(true);
    }

    private void loadOccurrencesFromApi() {
        Log.d("FinalizePickupDialog", "Iniciando carregamento de ocorrências");
        
        // Verificar conectividade
        com.example.zylogi_motoristas.offline.ConnectivityManager connectivityManager = 
            com.example.zylogi_motoristas.offline.ConnectivityManager.getInstance(getContext());
        
        // Se tiver conexão, buscar diretamente da API
        if (connectivityManager.isConnected()) {
            Log.d("FinalizePickupDialog", "Conexão disponível, buscando ocorrências diretamente da API");
            loadOccurrencesFromApiDirectly();
        } else {
            // Se não tiver conexão, usar cache
            Log.d("FinalizePickupDialog", "Sem conexão, tentando usar cache");
            com.example.zylogi_motoristas.offline.OfflineRepository offlineRepository = 
                com.example.zylogi_motoristas.offline.OfflineRepository.getInstance(getContext());
            
            offlineRepository.getCachedOccurrences(new com.example.zylogi_motoristas.offline.OfflineRepository.OccurrenceListCallback() {
                @Override
                public void onSuccess(List<Occurrence> cachedOccurrences) {
                    if (cachedOccurrences != null && !cachedOccurrences.isEmpty()) {
                        Log.d("FinalizePickupDialog", "Usando ocorrências do cache: " + cachedOccurrences.size());
                        occurrenceList.clear();
                        occurrenceList.addAll(cachedOccurrences);
                        
                        // Log das ocorrências do cache
                        for (Occurrence occ : cachedOccurrences) {
                            Log.d("FinalizePickupDialog", "Ocorrência do cache: " + occ.getName() + " (ID: " + occ.getId() + ")");
                        }
                        
                        setupOccurrenceSpinner();
                    } else {
                        Log.d("FinalizePickupDialog", "Cache vazio, usando spinner de fallback");
                        setupFallbackSpinner();
                    }
                }
                
                @Override
                public void onError(String error) {
                    Log.e("FinalizePickupDialog", "Erro ao buscar cache: " + error);
                    setupFallbackSpinner();
                }
            });
        }
    }

    private void loadOccurrencesFromApiDirectly() {
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
                    Log.d("FinalizePickupDialog", "Número de ocorrências recebidas da API: " + occurrences.size());
                    
                    // O endpoint /occurrences/driver já retorna apenas as ocorrências disponíveis para motoristas
                    occurrenceList.clear();
                    occurrenceList.addAll(occurrences);
                    
                    // Log das ocorrências recebidas
                    for (Occurrence occ : occurrences) {
                        Log.d("FinalizePickupDialog", "Ocorrência da API: " + occ.getName() + " (ID: " + occ.getId() + ")");
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
        Log.d("FinalizePickupDialog", "openCamera() chamado");
        
        // Verificar permissão da câmera
        int permissionStatus = ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.CAMERA);
        Log.d("FinalizePickupDialog", "Status da permissão da câmera: " + permissionStatus + " (GRANTED=" + PackageManager.PERMISSION_GRANTED + ")");
        
        if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
            Log.d("FinalizePickupDialog", "Permissão da câmera negada, solicitando permissão");
            // Solicitar permissão através da MainActivity
            if (getContext() instanceof MainActivity) {
                Log.d("FinalizePickupDialog", "Context é MainActivity, chamando requestCameraPermission");
                ((MainActivity) getContext()).requestCameraPermission();
            } else {
                Log.e("FinalizePickupDialog", "Context não é MainActivity: " + getContext().getClass().getSimpleName());
                Toast.makeText(getContext(), "Permissão da câmera necessária", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        
        Log.d("FinalizePickupDialog", "Permissão já concedida, abrindo câmera diretamente");
        openCameraAfterPermission();
    }
    
    public void openCameraAfterPermission() {
        Log.d("FinalizePickupDialog", "openCameraAfterPermission() chamado");
        Log.d("FinalizePickupDialog", "Context class: " + getContext().getClass().getName());
        
        Context context = getContext();
        if (context instanceof MainActivity) {
            Log.d("FinalizePickupDialog", "Chamando startCameraForResult na MainActivity");
            ((MainActivity) context).startCameraForResult(this);
        } else {
            Log.e("FinalizePickupDialog", "Context não é MainActivity: " + context.getClass().getName());
            // Tentar encontrar a MainActivity através do contexto
            if (context instanceof android.view.ContextThemeWrapper) {
                Context baseContext = ((android.view.ContextThemeWrapper) context).getBaseContext();
                Log.d("FinalizePickupDialog", "BaseContext class: " + baseContext.getClass().getName());
                if (baseContext instanceof MainActivity) {
                    Log.d("FinalizePickupDialog", "Usando BaseContext como MainActivity");
                    ((MainActivity) baseContext).startCameraForResult(this);
                    return;
                }
            }
            Toast.makeText(getContext(), "Erro ao abrir câmera", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void openGallery() {
        Log.d("FinalizePickupDialog", "openGallery() chamado");
        
        // Verificar permissão baseada na versão do Android
        String permission;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permission = android.Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            permission = android.Manifest.permission.READ_EXTERNAL_STORAGE;
        }
        
        int permissionStatus = ContextCompat.checkSelfPermission(getContext(), permission);
        Log.d("FinalizePickupDialog", "Status da permissão de armazenamento (" + permission + "): " + permissionStatus + " (GRANTED=" + PackageManager.PERMISSION_GRANTED + ")");
        
        if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
            Log.d("FinalizePickupDialog", "Permissão de armazenamento negada, solicitando permissão");
            // Solicitar permissão através da MainActivity
            if (getContext() instanceof MainActivity) {
                Log.d("FinalizePickupDialog", "Context é MainActivity, chamando requestStoragePermission");
                ((MainActivity) getContext()).requestStoragePermission();
            } else {
                Log.e("FinalizePickupDialog", "Context não é MainActivity: " + getContext().getClass().getSimpleName());
                Toast.makeText(getContext(), "Permissão de armazenamento necessária", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        
        Log.d("FinalizePickupDialog", "Permissão já concedida, abrindo galeria diretamente");
        openGalleryAfterPermission();
    }
    
    public void openGalleryAfterPermission() {
        Log.d("FinalizePickupDialog", "openGalleryAfterPermission() chamado");
        Log.d("FinalizePickupDialog", "Context class: " + getContext().getClass().getName());
        
        Context context = getContext();
        if (context instanceof MainActivity) {
            Log.d("FinalizePickupDialog", "Chamando startGalleryForResult na MainActivity");
            ((MainActivity) context).startGalleryForResult(this);
        } else {
            Log.e("FinalizePickupDialog", "Context não é MainActivity: " + context.getClass().getName());
            // Tentar encontrar a MainActivity através do contexto
            if (context instanceof android.view.ContextThemeWrapper) {
                Context baseContext = ((android.view.ContextThemeWrapper) context).getBaseContext();
                Log.d("FinalizePickupDialog", "BaseContext class: " + baseContext.getClass().getName());
                if (baseContext instanceof MainActivity) {
                    Log.d("FinalizePickupDialog", "Usando BaseContext como MainActivity");
                    ((MainActivity) baseContext).startGalleryForResult(this);
                    return;
                }
            }
            Toast.makeText(getContext(), "Erro ao abrir galeria", Toast.LENGTH_SHORT).show();
        }
    }

    public void onPhotoTaken(Bitmap photo) {
        if (photo != null) {
            Log.d("FinalizePickupDialog", "=== PROCESSANDO FOTO ===");
            Log.d("FinalizePickupDialog", "Bitmap recebido - Width: " + photo.getWidth() + ", Height: " + photo.getHeight());
            
            // Converter bitmap para base64
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            photo.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            photoBase64 = Base64.encodeToString(byteArray, Base64.NO_WRAP);
            
            Log.d("FinalizePickupDialog", "Foto convertida para Base64 - Tamanho: " + byteArray.length + " bytes");
            Log.d("FinalizePickupDialog", "Base64 length: " + photoBase64.length() + " caracteres");
            Log.d("FinalizePickupDialog", "Base64 preview (primeiros 100 chars): " + photoBase64.substring(0, Math.min(100, photoBase64.length())));
            
            textViewPhotoStatus.setText("✅ Foto capturada");
            textViewPhotoStatus.setTextColor(ContextCompat.getColor(getContext(), android.R.color.holo_green_dark));
        } else {
            Log.e("FinalizePickupDialog", "Bitmap recebido é null!");
        }
    }
    
    public void onPhotoSelected(Bitmap photo) {
        if (photo != null) {
            Log.d("FinalizePickupDialog", "=== PROCESSANDO FOTO DA GALERIA ===");
            Log.d("FinalizePickupDialog", "Bitmap recebido - Width: " + photo.getWidth() + ", Height: " + photo.getHeight());
            
            // Converter para Base64
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            photo.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            photoBase64 = Base64.encodeToString(byteArray, Base64.NO_WRAP);
            
            Log.d("FinalizePickupDialog", "Foto da galeria convertida para Base64");
            Log.d("FinalizePickupDialog", "Tamanho do array de bytes: " + byteArray.length);
            Log.d("FinalizePickupDialog", "Tamanho da string Base64: " + photoBase64.length());
            Log.d("FinalizePickupDialog", "Preview Base64: " + photoBase64.substring(0, Math.min(50, photoBase64.length())));
            Log.d("FinalizePickupDialog", "Foto da galeria salva como Base64 com sucesso!");
            textViewPhotoStatus.setText("✅ Foto da galeria selecionada!");
            textViewPhotoStatus.setTextColor(ContextCompat.getColor(getContext(), android.R.color.holo_green_dark));
        } else {
            Log.e("FinalizePickupDialog", "Erro: Bitmap da foto da galeria é nulo");
            textViewPhotoStatus.setText("❌ Erro ao selecionar foto");
            textViewPhotoStatus.setTextColor(ContextCompat.getColor(getContext(), android.R.color.holo_red_dark));
        }
    }

    private void finalizePickupWithDetails(Occurrence occurrence, String observation) {
        // occurrenceId agora é opcional - enviar apenas se uma ocorrência foi selecionada
        String occurrenceId = (occurrence != null && occurrence.getId() != null) ? occurrence.getId() : null;
        String driverAttachmentUrl = photoBase64 != null ? "data:image/jpeg;base64," + photoBase64 : "";
        
        // Obter quantidade de itens coletados
        String quantityText = editTextQuantity.getText().toString().trim();
        Integer driverNumberPackages = null;
        if (!quantityText.isEmpty()) {
            try {
                driverNumberPackages = Integer.parseInt(quantityText);
            } catch (NumberFormatException e) {
                Log.e("FinalizePickupDialog", "Erro ao converter quantidade: " + e.getMessage());
            }
        }
        
        // Logs de debug para rastrear os valores
        Log.d("FinalizePickupDialog", "=== DADOS PARA ENVIO ===");
        Log.d("FinalizePickupDialog", "Pickup ID: " + currentPickup.getId());
        Log.d("FinalizePickupDialog", "Observação: '" + observation + "'");
        Log.d("FinalizePickupDialog", "Occurrence ID: '" + occurrenceId + "'");
        Log.d("FinalizePickupDialog", "photoBase64 is null: " + (photoBase64 == null));
        if (photoBase64 != null) {
            Log.d("FinalizePickupDialog", "photoBase64 length: " + photoBase64.length());
            Log.d("FinalizePickupDialog", "photoBase64 preview: " + photoBase64.substring(0, Math.min(50, photoBase64.length())));
        }
        Log.d("FinalizePickupDialog", "driverAttachmentUrl: " + driverAttachmentUrl);
        Log.d("FinalizePickupDialog", "driverAttachmentUrl length: " + (driverAttachmentUrl != null ? driverAttachmentUrl.length() : "null"));
        
        Log.d("FinalizePickupDialog", "Occurrence ID para envio: " + (occurrenceId != null ? occurrenceId : "null (opcional)"));
        
        Log.d("FinalizePickupDialog", "Driver Number Packages: " + driverNumberPackages);
        
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
                driverAttachmentUrl,
                driverNumberPackages
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

    // Método para encontrar ocorrência por referenceId
    private Occurrence findOccurrenceByReferenceId(int referenceId) {
        Log.d("FinalizePickupDialog", "=== BUSCANDO OCORRÊNCIA COM REFERENCE ID: " + referenceId + " ===");
        Log.d("FinalizePickupDialog", "Total de ocorrências carregadas: " + (occurrenceList != null ? occurrenceList.size() : "null"));
        
        if (occurrenceList != null) {
            for (int i = 0; i < occurrenceList.size(); i++) {
                Occurrence occurrence = occurrenceList.get(i);
                Log.d("FinalizePickupDialog", "Ocorrência [" + i + "]: Nome=" + occurrence.getName() + ", ID=" + occurrence.getId() + ", ReferenceId=" + occurrence.getReferenceId());
                
                if (occurrence.getReferenceId() != null && occurrence.getReferenceId().equals(String.valueOf(referenceId))) {
                    Log.d("FinalizePickupDialog", "✅ ENCONTRADA! Ocorrência com referenceId=" + referenceId + ": " + occurrence.getName());
                    return occurrence;
                }
            }
        }
        
        Log.d("FinalizePickupDialog", "❌ NÃO ENCONTRADA! Nenhuma ocorrência com referenceId=" + referenceId);
        return null;
    }
    
    /**
     * Cria uma ocorrência padrão para coletas completadas quando a API não está disponível
     */
    private Occurrence createDefaultOccurrence() {
        Occurrence defaultOccurrence = new Occurrence();
        defaultOccurrence.setId(null); // Não definir ID para evitar erro de UUID na API
        defaultOccurrence.setReferenceId("1");
        defaultOccurrence.setName("Coleta Realizada");
        defaultOccurrence.setOccurrenceNumber(1);
        defaultOccurrence.setClientFault(false);
        defaultOccurrence.setSendToApp(true);
        defaultOccurrence.setActivated(true);
        
        Log.i("FinalizePickupDialog", "Criada ocorrência padrão offline (sem ID para evitar erro de UUID): " + defaultOccurrence.getName());
        return defaultOccurrence;
    }
}
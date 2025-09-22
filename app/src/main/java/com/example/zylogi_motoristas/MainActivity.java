package com.example.zylogi_motoristas;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.graphics.BitmapFactory;
import java.io.IOException;
import java.io.InputStream;

import com.auth0.android.jwt.JWT;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

// 1. A Activity agora "assina o contrato" do listener do Adapter
public class MainActivity extends AppCompatActivity implements PickupAdapter.OnPickupActionClickListener {

    private MainViewModel mainViewModel;
    private RecyclerView carouselRecyclerView;
    private PickupAdapter carouselAdapter;
    private FloatingActionButton fabSync;
    private SwipeRefreshLayout swipeRefreshLayout;

    private CircularProgressIndicator progressIndicator;
    private TextView textViewProgressPercentage;
    private TextView textViewProgressSummary;
    
    // Elementos da barra superior
    private TextView textWelcome;
    private TextView textDateTime;
    private TextView textLocation;
    private TextView textTemperature;
    private MaterialButton buttonLogout;
    
    // Gerenciadores
    private AuthSessionManager authSessionManager;
    private FusedLocationProviderClient fusedLocationClient;
    private Handler handler;
    private Runnable timeUpdateRunnable;
    
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private FinalizePickupDialog currentDialog;
    private FinalizePickupNotCompletedDialog currentNotCompletedDialog;
    
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_main);

            // Inicializar gerenciadores
            authSessionManager = new AuthSessionManager(this);
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            handler = new Handler(Looper.getMainLooper());

            setupViews();
            setupCarousel();
            setupCameraLauncher();
            setupTopBar();

            mainViewModel = new ViewModelProvider(this).get(MainViewModel.class);
            
            // Inicializar SyncManager para garantir sincronização offline
            com.example.zylogi_motoristas.offline.SyncManager.getInstance(this);

            setupListeners();
            observeViewModel();
            startTimeUpdates();
            updateLocation();

            mainViewModel.fetchPickups();
            
            // Verificar e solicitar permissão da câmera ao iniciar o app
            checkCameraPermission();
            
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Erro durante inicialização da MainActivity", e);
            Toast.makeText(this, "Erro ao inicializar o aplicativo. Tente novamente.", Toast.LENGTH_LONG).show();
            
            // Tentar voltar para LoginActivity em caso de erro
            try {
                Intent intent = new Intent(this, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception ex) {
                android.util.Log.e("MainActivity", "Erro ao navegar para LoginActivity", ex);
            }
            finish();
        }
    }

    private void setupViews() {
        carouselRecyclerView = findViewById(R.id.carouselRecyclerView);
        fabSync = findViewById(R.id.fabSync);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        progressIndicator = findViewById(R.id.progressIndicator);
        textViewProgressPercentage = findViewById(R.id.textViewProgressPercentage);
        textViewProgressSummary = findViewById(R.id.textViewProgressSummary);
        
        // Elementos da barra superior
        textWelcome = findViewById(R.id.textWelcome);
        textDateTime = findViewById(R.id.textDateTime);
        textLocation = findViewById(R.id.textLocation);
        textTemperature = findViewById(R.id.textTemperature);
        buttonLogout = findViewById(R.id.buttonLogout);
    }

    private void setupCarousel() {
        carouselRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        // 2. A Activity (this) é passada como o listener para o Adapter
        carouselAdapter = new PickupAdapter(this);
        carouselRecyclerView.setAdapter(carouselAdapter);
        new LinearSnapHelper().attachToRecyclerView(carouselRecyclerView);
    }

    private void setupCameraLauncher() {
        Log.d("MainActivity", "Configurando cameraLauncher e galleryLauncher");
        
        // Configurar launcher da câmera
        cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Log.d("MainActivity", "Resultado da câmera: " + result.getResultCode());
                
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Bundle extras = result.getData().getExtras();
                    if (extras != null) {
                        Bitmap imageBitmap = (Bitmap) extras.get("data");
                        if (imageBitmap != null) {
                            Log.d("MainActivity", "Foto capturada com sucesso");
                            deliverPhotoToDialog(imageBitmap);
                        }
                    }
                } else {
                    Log.d("MainActivity", "Câmera cancelada ou erro");
                }
            }
        );
        
        // Configurar launcher da galeria
        galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Log.d("MainActivity", "Resultado da galeria: " + result.getResultCode());
                
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        Log.d("MainActivity", "Imagem selecionada da galeria: " + imageUri.toString());
                        try {
                            InputStream inputStream = getContentResolver().openInputStream(imageUri);
                            Bitmap imageBitmap = BitmapFactory.decodeStream(inputStream);
                            if (imageBitmap != null) {
                                Log.d("MainActivity", "Imagem da galeria carregada com sucesso");
                                deliverPhotoFromGalleryToDialog(imageBitmap);
                            } else {
                                Log.e("MainActivity", "Erro ao decodificar imagem da galeria");
                                Toast.makeText(this, "Erro ao carregar imagem", Toast.LENGTH_SHORT).show();
                            }
                            if (inputStream != null) {
                                inputStream.close();
                            }
                        } catch (IOException e) {
                            Log.e("MainActivity", "Erro ao carregar imagem da galeria: " + e.getMessage());
                            Toast.makeText(this, "Erro ao carregar imagem", Toast.LENGTH_SHORT).show();
                        }
                    }
                } else {
                    Log.d("MainActivity", "Galeria cancelada ou erro");
                }
            }
        );
    }
    
    private void deliverPhotoToDialog(Bitmap bitmap) {
        if (currentDialog != null) {
            currentDialog.onPhotoTaken(bitmap);
        } else if (currentNotCompletedDialog != null) {
            currentNotCompletedDialog.onPhotoTaken(bitmap);
        } else {
            Log.w("MainActivity", "Nenhum diálogo ativo para receber a foto");
        }
    }
    
    private void deliverPhotoFromGalleryToDialog(Bitmap bitmap) {
        if (currentDialog != null) {
            Log.d("MainActivity", "Entregando foto da galeria para FinalizePickupDialog");
            currentDialog.onPhotoSelected(bitmap);
        } else if (currentNotCompletedDialog != null) {
            Log.d("MainActivity", "Entregando foto da galeria para FinalizePickupNotCompletedDialog");
            currentNotCompletedDialog.onPhotoSelected(bitmap);
        }
    }

    private void setupListeners() {
        fabSync.setOnClickListener(v -> mainViewModel.fetchPickups());
        swipeRefreshLayout.setOnRefreshListener(() -> mainViewModel.fetchPickups());
        buttonLogout.setOnClickListener(v -> performLogout());
    }
    
    private void setupTopBar() {
        // Configurar nome do motorista
        updateDriverName();
        
        // Configurar data e hora inicial
        updateDateTime();
        
        // Configurar localização inicial
        textLocation.setText("📍 Carregando...");
        textTemperature.setText("🌡️ --°C");
    }
    
    private void updateDriverName() {
        String token = authSessionManager.getAuthToken();
        if (token != null) {
            try {
                JWT jwt = new JWT(token);
                String driverName = jwt.getClaim("name").asString();
                if (driverName != null && !driverName.isEmpty()) {
                    // Extrair apenas o primeiro nome
                    String firstName = driverName.split(" ")[0];
                    textWelcome.setText("Bem-vindo, " + firstName + " \uD83D\uDE4B\u200D♂\uFE0F");
                } else {
                    textWelcome.setText("Bem-vindo, Motorista");
                }
            } catch (Exception e) {
                textWelcome.setText("Bem-vindo, Motorista");
            }
        } else {
            textWelcome.setText("Bem-vindo, Motorista");
        }
    }
    
    private void startTimeUpdates() {
        timeUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateDateTime();
                handler.postDelayed(this, 1000); // Atualizar a cada segundo
            }
        };
        handler.post(timeUpdateRunnable);
    }
    
    private void updateDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        Calendar calendar = Calendar.getInstance();
        String currentDate = sdf.format(calendar.getTime());
        textDateTime.setText(currentDate);
    }
    
    private void updateLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 
                LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        
        fusedLocationClient.getLastLocation()
            .addOnSuccessListener(this, location -> {
                if (location != null) {
                    // Usar geocoding reverso para obter o nome do bairro
                    getNeighborhoodName(location.getLatitude(), location.getLongitude());
                    
                    // Simular temperatura (em um app real, você usaria uma API de clima)
                    int temperature = 20 + (int)(Math.random() * 15); // Temperatura entre 20-35°C
                    textTemperature.setText(String.format(Locale.getDefault(), "🌡️ %d°C", temperature));
                } else {
                    textLocation.setText("📍 Localização indisponível");
                }
            })
            .addOnFailureListener(e -> {
                textLocation.setText("📍 Erro na localização");
            });
    }
    
    private void getNeighborhoodName(double latitude, double longitude) {
        // Executar geocoding em thread separada para evitar bloqueio da UI
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                
                // Verificar se o Geocoder está disponível (requer conexão)
                if (!Geocoder.isPresent()) {
                    runOnUiThread(() -> textLocation.setText("📍 Localização offline"));
                    return;
                }
                
                List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                
                runOnUiThread(() -> {
                    if (addresses != null && !addresses.isEmpty()) {
                        Address address = addresses.get(0);
                        String neighborhood = address.getSubLocality(); // Bairro
                        
                        if (neighborhood != null && !neighborhood.isEmpty()) {
                            textLocation.setText("📍 " + neighborhood);
                        } else {
                            // Se não conseguir o bairro, tenta a cidade
                            String city = address.getLocality();
                            if (city != null && !city.isEmpty()) {
                                textLocation.setText("📍 " + city);
                            } else {
                                textLocation.setText("📍 Localização encontrada");
                            }
                        }
                    } else {
                        textLocation.setText("📍 Endereço não encontrado");
                    }
                });
                
            } catch (java.io.IOException e) {
                // Erro de rede - sem conexão com a internet
                Log.w("MainActivity", "Geocoding falhou - sem conexão: " + e.getMessage());
                runOnUiThread(() -> textLocation.setText("📍 Localização offline"));
            } catch (Exception e) {
                // Outros erros
                Log.e("MainActivity", "Erro no geocoding: " + e.getMessage(), e);
                runOnUiThread(() -> textLocation.setText("📍 Erro na localização"));
            }
        }).start();
    }
    
    private void performLogout() {
        // Limpar sessão
        authSessionManager.clearSession();
        
        // Parar atualizações de tempo
        if (timeUpdateRunnable != null) {
            handler.removeCallbacks(timeUpdateRunnable);
        }
        
        // Navegar para tela de login
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateLocation();
            } else {
                textLocation.setText("📍 Permissão negada");
            }
        } else if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            // Verificar resultados das permissões solicitadas
            boolean cameraGranted = false;
            boolean storageGranted = false;
            
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.permission.CAMERA)) {
                    cameraGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                    Log.d("MainActivity", "Permissão da câmera: " + (cameraGranted ? "concedida" : "negada"));
                } else if (permissions[i].equals(Manifest.permission.READ_EXTERNAL_STORAGE) || 
                          permissions[i].equals(Manifest.permission.READ_MEDIA_IMAGES)) {
                    storageGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                    Log.d("MainActivity", "Permissão de galeria: " + (storageGranted ? "concedida" : "negada"));
                }
            }
            
            // Mostrar mensagens apropriadas
            if (!cameraGranted) {
                Toast.makeText(this, "Permissão da câmera é necessária para tirar fotos", Toast.LENGTH_LONG).show();
            }
            if (!storageGranted) {
                Toast.makeText(this, "Permissão de galeria é necessária para acessar fotos", Toast.LENGTH_LONG).show();
            }
            
            // Se pelo menos uma permissão foi concedida e há um diálogo ativo, tentar abrir a funcionalidade correspondente
            if (cameraGranted && (currentDialog != null || currentNotCompletedDialog != null)) {
                Log.d("MainActivity", "Permissão da câmera concedida, tentando abrir câmera");
                if (currentDialog != null) {
                    currentDialog.openCameraAfterPermission();
                } else if (currentNotCompletedDialog != null) {
                    currentNotCompletedDialog.openCameraAfterPermission();
                }
            }
            
        } else if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            boolean storageGranted = false;
            
            // Verificar qual permissão foi concedida
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.permission.READ_EXTERNAL_STORAGE) || 
                    permissions[i].equals(Manifest.permission.READ_MEDIA_IMAGES)) {
                    storageGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                    Log.d("MainActivity", "Permissão " + permissions[i] + ": " + (storageGranted ? "concedida" : "negada"));
                    break;
                }
            }
            
            if (storageGranted) {
                Log.d("MainActivity", "Permissão de galeria concedida pelo usuário");
                // Tentar abrir a galeria novamente
                if (currentDialog != null) {
                    currentDialog.openGalleryAfterPermission();
                } else if (currentNotCompletedDialog != null) {
                    currentNotCompletedDialog.openGalleryAfterPermission();
                }
            } else {
                Log.d("MainActivity", "Permissão de galeria negada pelo usuário");
                Toast.makeText(this, "Permissão de galeria é necessária para acessar fotos", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timeUpdateRunnable != null) {
            handler.removeCallbacks(timeUpdateRunnable);
        }
    }

    private void observeViewModel() {
        // MUDANÇA AQUI: Observa a nova lista de coletas ABERTAS
        mainViewModel.openPickups.observe(this, openPickups -> {
            if (openPickups != null) {
                // Atualiza o adapter do carrossel com a lista filtrada
                carouselAdapter.setPickups(openPickups);
            }
        });

        // O resto dos observers continua exatamente o mesmo
        mainViewModel.progressPercentage.observe(this, percentage -> {
            progressIndicator.setProgress(percentage);
            textViewProgressPercentage.setText(String.format("%d%%", percentage));
        });

        mainViewModel.progressSummary.observe(this, summary -> {
            textViewProgressSummary.setText(summary);
        });

        mainViewModel.isLoading.observe(this, isLoading -> {
            swipeRefreshLayout.setRefreshing(isLoading);
        });

        mainViewModel.error.observe(this, error -> {
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
        });

        mainViewModel.updateResult.observe(this, message -> {
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
    // 3. Implementação dos métodos obrigatórios do listener
    @Override
    public void onCollectedClick(Pickup pickup) {
        // Abre o modal de finalização para coletas coletadas
        showFinalizePickupDialog(pickup);
    }

    @Override
    public void onNotCollectedClick(Pickup pickup) {
        showFinalizePickupNotCompletedDialog(pickup);
    }

    @Override
    public void onObservationClick(Pickup pickup) {
        showObservationDialog(pickup);
    }

    // 4. Método que abre o modal de finalização com ocorrência e observação
    private void showFinalizePickupDialog(final Pickup pickup) {
        FinalizePickupDialog dialog = new FinalizePickupDialog(this, pickup, 
            (occurrence, observation) -> {
                // Verificar se occurrence não é null antes de acessar seus métodos
                if (occurrence != null) {
                    // Mostrar informações capturadas (temporário para debug)
                    String message = "Ocorrência: " + occurrence.getName() + 
                                   " (Nº " + occurrence.getOccurrenceNumber() + ")" +
                                   "\nObservação: " + observation;
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                } else {
                    // Caso occurrence seja null, mostrar apenas a observação
                    String message = "Coleta finalizada\nObservação: " + observation;
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
            });
        dialog.show();
    }

    // Método que abre o modal de finalização para NÃO COLETADO com ocorrência e observação
    private void showFinalizePickupNotCompletedDialog(final Pickup pickup) {
        FinalizePickupNotCompletedDialog dialog = new FinalizePickupNotCompletedDialog(this, pickup, 
            (occurrence, observation) -> {
                // O callback agora é executado APÓS a finalização bem-sucedida
                // A chamada real da API já é feita no método finalizePickupWithDetails do diálogo
                Log.d("MainActivity", "Coleta finalizada como NÃO COLETADO com sucesso");
                Log.d("MainActivity", "Ocorrência: " + (occurrence != null ? occurrence.getName() : "N/A"));
                Log.d("MainActivity", "Observação: " + observation);
            });
        dialog.show();
    }

    // 5. Método que cria e exibe o diálogo de confirmação simples (para NÃO COLETADO)
    private void showConfirmationDialog(final Pickup pickup, String actionText, final String statusToSend) {
        new AlertDialog.Builder(this)
                .setTitle("Confirmar Ação")
                .setMessage("Deseja realmente marcar esta coleta como " + actionText + "?")
                .setPositiveButton("Sim", (dialog, which) -> {
                    // Se confirmar, chama o ViewModel para fazer a chamada de API
                    mainViewModel.finalizePickup(pickup.getId(), statusToSend);
                })
                .setNegativeButton("Não", null)
                .show();
    }

    public void startCameraForResult(FinalizePickupDialog dialog) {
        Log.d("MainActivity", "Abrindo câmera para FinalizePickupDialog");
        currentDialog = dialog;
        launchCamera();
    }

    public void startCameraForResult(FinalizePickupNotCompletedDialog dialog) {
        Log.d("MainActivity", "Abrindo câmera para FinalizePickupNotCompletedDialog");
        currentNotCompletedDialog = dialog;
        launchCamera();
    }
    
    private void launchCamera() {
        try {
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (cameraIntent.resolveActivity(getPackageManager()) != null) {
                Log.d("MainActivity", "Lançando intent da câmera");
                cameraLauncher.launch(cameraIntent);
            } else {
                Log.e("MainActivity", "Nenhum app de câmera encontrado");
                Toast.makeText(this, "Câmera não disponível", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Erro ao abrir câmera: " + e.getMessage());
            Toast.makeText(this, "Erro ao abrir câmera", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkCameraPermission() {
        Log.d("MainActivity", "checkCameraPermission() chamado");
        
        // Lista de permissões necessárias
        List<String> permissionsNeeded = new ArrayList<>();
        
        // Verificar permissão da câmera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            Log.d("MainActivity", "Permissão da câmera não concedida");
            permissionsNeeded.add(Manifest.permission.CAMERA);
        } else {
            Log.d("MainActivity", "Permissão da câmera já concedida");
        }
        
        // Verificar permissões de galeria baseado na versão do Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+) - usar READ_MEDIA_IMAGES
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "Permissão READ_MEDIA_IMAGES não concedida");
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES);
            } else {
                Log.d("MainActivity", "Permissão READ_MEDIA_IMAGES já concedida");
            }
        } else {
            // Android 12 e anteriores - usar READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "Permissão READ_EXTERNAL_STORAGE não concedida");
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            } else {
                Log.d("MainActivity", "Permissão READ_EXTERNAL_STORAGE já concedida");
            }
        }
        
        // Solicitar permissões se necessário
        if (!permissionsNeeded.isEmpty()) {
            Log.d("MainActivity", "Solicitando permissões: " + permissionsNeeded.toString());
            String[] permissionsArray = permissionsNeeded.toArray(new String[0]);
            ActivityCompat.requestPermissions(this, permissionsArray, CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            Log.d("MainActivity", "Todas as permissões já concedidas");
        }
    }
    
    public void requestCameraPermission() {
        Log.d("MainActivity", "requestCameraPermission() chamado");
        Log.d("MainActivity", "Verificando se já tem permissão...");
        
        // Verificar se a permissão já foi concedida
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        Log.d("MainActivity", "Status da permissão: " + permissionCheck + " (GRANTED=" + PackageManager.PERMISSION_GRANTED + ")");
        
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            Log.d("MainActivity", "Permissão já concedida, abrindo câmera diretamente");
            // Se já tem permissão, abrir câmera diretamente
            if (currentDialog != null) {
                currentDialog.openCameraAfterPermission();
            } else if (currentNotCompletedDialog != null) {
                currentNotCompletedDialog.openCameraAfterPermission();
            }
            return;
        }
        
        // Verificar se devemos mostrar uma explicação
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            Log.d("MainActivity", "Mostrando explicação da permissão");
            // Mostrar explicação antes de solicitar
            new AlertDialog.Builder(this)
                .setTitle("Permissão da Câmera")
                .setMessage("Este aplicativo precisa acessar a câmera para tirar fotos das coletas.")
                .setPositiveButton("Permitir", (dialog, which) -> {
                    Log.d("MainActivity", "Usuário aceitou explicação, solicitando permissão");
                    ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.CAMERA}, 
                        CAMERA_PERMISSION_REQUEST_CODE);
                })
                .setNegativeButton("Cancelar", (dialog, which) -> {
                    Log.d("MainActivity", "Usuário cancelou a explicação");
                    Toast.makeText(this, "Permissão da câmera é necessária para tirar fotos", Toast.LENGTH_LONG).show();
                })
                .show();
        } else {
            Log.d("MainActivity", "Solicitando permissão da câmera diretamente ao usuário");
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.CAMERA}, 
                CAMERA_PERMISSION_REQUEST_CODE);
        }
    }

    public void startGalleryForResult(FinalizePickupDialog dialog) {
        Log.d("MainActivity", "startGalleryForResult() chamado para FinalizePickupDialog");
        currentDialog = dialog;
        requestStoragePermission();
    }
    
    public void startGalleryForResult(FinalizePickupNotCompletedDialog dialog) {
        Log.d("MainActivity", "startGalleryForResult() chamado para FinalizePickupNotCompletedDialog");
        currentNotCompletedDialog = dialog;
        requestStoragePermission();
    }
    
    private void launchGallery() {
        Log.d("MainActivity", "Abrindo galeria...");
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }
    
    private void checkStoragePermission() {
        Log.d("MainActivity", "checkStoragePermission() chamado");
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
            Log.d("MainActivity", "Permissão de armazenamento não concedida, solicitando...");
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 
                STORAGE_PERMISSION_REQUEST_CODE);
        } else {
            Log.d("MainActivity", "Permissão de armazenamento já concedida");
        }
    }
    
    public void requestStoragePermission() {
        Log.d("MainActivity", "requestStoragePermission() chamado");
        Log.d("MainActivity", "Verificando se já tem permissão de armazenamento...");
        
        boolean hasPermission = false;
        String permissionToRequest;
        
        // Verificar permissão baseado na versão do Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+) - usar READ_MEDIA_IMAGES
            permissionToRequest = Manifest.permission.READ_MEDIA_IMAGES;
            int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES);
            hasPermission = permissionCheck == PackageManager.PERMISSION_GRANTED;
            Log.d("MainActivity", "Status da permissão READ_MEDIA_IMAGES: " + permissionCheck + " (GRANTED=" + PackageManager.PERMISSION_GRANTED + ")");
        } else {
            // Android 12 e anteriores - usar READ_EXTERNAL_STORAGE
            permissionToRequest = Manifest.permission.READ_EXTERNAL_STORAGE;
            int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
            hasPermission = permissionCheck == PackageManager.PERMISSION_GRANTED;
            Log.d("MainActivity", "Status da permissão READ_EXTERNAL_STORAGE: " + permissionCheck + " (GRANTED=" + PackageManager.PERMISSION_GRANTED + ")");
        }
        
        if (hasPermission) {
            Log.d("MainActivity", "Permissão já concedida, abrindo galeria diretamente");
            // Se já tem permissão, abrir galeria diretamente
            launchGallery();
        } else {
            Log.d("MainActivity", "Permissão não concedida, solicitando: " + permissionToRequest);
            // Se não tem permissão, solicitar
            ActivityCompat.requestPermissions(this, 
                new String[]{permissionToRequest}, 
                STORAGE_PERMISSION_REQUEST_CODE);
        }
    }

    private void showObservationDialog(Pickup pickup) {
        if (pickup.getObservation() == null || pickup.getObservation().trim().isEmpty()) {
            Toast.makeText(this, "Nenhuma observação disponível", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Observação")
                .setMessage(pickup.getObservation())
                .setPositiveButton("Fechar", null)
                .show();
    }

    public MainViewModel getMainViewModel() {
        return mainViewModel;
    }
}
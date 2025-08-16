package com.example.zylogi_motoristas;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.TextView;
import android.widget.Toast;

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
    private FinalizePickupDialog currentDialog;
    private FinalizePickupNotCompletedDialog currentNotCompletedDialog;
    
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

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

            setupListeners();
            observeViewModel();
            startTimeUpdates();
            updateLocation();

            mainViewModel.fetchPickups();
            
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
        cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Bundle extras = result.getData().getExtras();
                    Bitmap imageBitmap = (Bitmap) extras.get("data");
                    if (currentDialog != null) {
                        currentDialog.onPhotoTaken(imageBitmap);
                    }
                    if (currentNotCompletedDialog != null) {
                        currentNotCompletedDialog.onPhotoTaken(imageBitmap);
                    }
                }
            }
        );
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
                    textWelcome.setText("Bem-vindo, " + driverName);
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
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
        // Corrigir fuso horário subtraindo 3 horas
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR_OF_DAY, -3);
        String currentDateTime = sdf.format(calendar.getTime());
        textDateTime.setText(currentDateTime);
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
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            
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
        } catch (Exception e) {
            textLocation.setText("📍 Erro ao obter endereço");
        }
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
            progressIndicator.setProgress(percentage, true);
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

    // 4. Método que abre o modal de finalização com ocorrência e observação
    private void showFinalizePickupDialog(final Pickup pickup) {
        FinalizePickupDialog dialog = new FinalizePickupDialog(this, pickup, 
            (occurrence, observation) -> {
                // Aqui você pode usar a ocorrência e observação
                // Por enquanto, vamos finalizar como COMPLETED
                // TODO: Atualizar API para aceitar occurrence e observation
                mainViewModel.finalizePickup(pickup.getId(), "COMPLETED");
                
                // Mostrar informações capturadas (temporário para debug)
                String message = "Ocorrência: " + occurrence.getName() + 
                               " (Nº " + occurrence.getOccurrenceNumber() + ")" +
                               "\nObservação: " + observation;
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            });
        dialog.show();
    }

    // Método que abre o modal de finalização para NÃO COLETADO com ocorrência e observação
    private void showFinalizePickupNotCompletedDialog(final Pickup pickup) {
        FinalizePickupNotCompletedDialog dialog = new FinalizePickupNotCompletedDialog(this, pickup, 
            (occurrence, observation) -> {
                // Mostrar informações capturadas (temporário para debug)
                String message = "Coleta marcada como NÃO COLETADO\n" +
                               "Ocorrência: " + occurrence.getName() + 
                               " (Nº " + occurrence.getOccurrenceNumber() + ")" +
                               "\nObservação: " + observation;
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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
        currentDialog = dialog;
        currentNotCompletedDialog = null;
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraLauncher.launch(cameraIntent);
    }

    public void startCameraForResult(FinalizePickupNotCompletedDialog dialog) {
        currentNotCompletedDialog = dialog;
        currentDialog = null;
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraLauncher.launch(cameraIntent);
    }

    public MainViewModel getMainViewModel() {
        return mainViewModel;
    }
}
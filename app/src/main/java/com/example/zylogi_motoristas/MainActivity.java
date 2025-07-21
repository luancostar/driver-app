package com.example.zylogi_motoristas;

import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupViews();
        setupCarousel();

        mainViewModel = new ViewModelProvider(this).get(MainViewModel.class);

        setupListeners();
        observeViewModel();

        mainViewModel.fetchPickups();
    }

    private void setupViews() {
        carouselRecyclerView = findViewById(R.id.carouselRecyclerView);
        fabSync = findViewById(R.id.fabSync);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        progressIndicator = findViewById(R.id.progressIndicator);
        textViewProgressPercentage = findViewById(R.id.textViewProgressPercentage);
        textViewProgressSummary = findViewById(R.id.textViewProgressSummary);
    }

    private void setupCarousel() {
        carouselRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        // 2. A Activity (this) é passada como o listener para o Adapter
        carouselAdapter = new PickupAdapter(this);
        carouselRecyclerView.setAdapter(carouselAdapter);
        new LinearSnapHelper().attachToRecyclerView(carouselRecyclerView);
    }

    private void setupListeners() {
        fabSync.setOnClickListener(v -> mainViewModel.fetchPickups());
        swipeRefreshLayout.setOnRefreshListener(() -> mainViewModel.fetchPickups());
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
        showConfirmationDialog(pickup, "COLETADO", "COMPLETED");
    }

    @Override
    public void onNotCollectedClick(Pickup pickup) {
        showConfirmationDialog(pickup, "NÃO COLETADO", "NOT_COMPLETED");
    }

    // 4. Método que cria e exibe o diálogo de confirmação
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
}
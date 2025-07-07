package com.example.zylogi_motoristas;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class MainActivity extends AppCompatActivity {

    private MainViewModel mainViewModel;
    private RecyclerView recyclerView;
    private PickupAdapter pickupAdapter;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Associa os componentes visuais
        recyclerView = findViewById(R.id.recyclerViewPickups);
        progressBar = findViewById(R.id.progressBarMain);

        // 2. Configura o RecyclerView
        setupRecyclerView();

        // 3. Pega o ViewModel
        mainViewModel = new ViewModelProvider(this).get(MainViewModel.class);

        // 4. Observa os dados vindos do ViewModel
        observeViewModel();

        // 5. Manda o ViewModel buscar os dados
        mainViewModel.fetchPickups();
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        pickupAdapter = new PickupAdapter();
        recyclerView.setAdapter(pickupAdapter);
    }

    private void observeViewModel() {
        // Observa a lista de coletas
        mainViewModel.pickups.observe(this, pickups -> {
            if (pickups != null && !pickups.isEmpty()) {
                pickupAdapter.setPickups(pickups);
                recyclerView.setVisibility(View.VISIBLE);
            } else {
                // Opcional: mostrar uma mensagem de "nenhuma coleta hoje"
            }
        });

        // Observa o estado de carregamento
        mainViewModel.isLoading.observe(this, isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            if(isLoading) {
                recyclerView.setVisibility(View.GONE);
            }
        });

        // Observa possíveis erros
        mainViewModel.error.observe(this, error -> {
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
        });
    }
}
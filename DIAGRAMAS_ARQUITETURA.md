# 🏗️ Diagramas de Arquitetura - Zylogi Motoristas

*Visualizando a magia por trás do código!* ✨

## 📋 Índice

1. [Arquitetura Geral](#-arquitetura-geral)
2. [Fluxo de Dados](#-fluxo-de-dados)
3. [Sistema Offline](#-sistema-offline)
4. [Ciclo de Vida das Activities](#-ciclo-de-vida-das-activities)
5. [Sincronização](#-sincronização)
6. [Estrutura do Banco de Dados](#-estrutura-do-banco-de-dados)

---

## 🏛️ Arquitetura Geral

### 🎭 Padrão MVVM Detalhado

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           📱 PRESENTATION LAYER                             │
├─────────────────────────────────────────────────────────────────────────────┤
│  🏠 MainActivity        📱 LoginActivity       💬 Dialogs                   │
│  ├─ RecyclerView        ├─ Login Form          ├─ FinalizePickupDialog      │
│  ├─ TopBar              ├─ Biometric Auth      └─ NotCompletedDialog        │
│  ├─ Progress Indicator  └─ Session Management                               │
│  └─ Sync FAB                                                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                            🧠 BUSINESS LOGIC LAYER                          │
├─────────────────────────────────────────────────────────────────────────────┤
│  🎯 MainViewModel       🔐 LoginViewModel      🔄 SyncManager               │
│  ├─ Pickup Management   ├─ Authentication      ├─ WorkManager Integration   │
│  ├─ Progress Tracking   ├─ Session Validation  ├─ Connectivity Monitoring   │
│  ├─ Location Updates    └─ Token Management    └─ Retry Logic               │
│  └─ Cache Coordination                                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                              💾 DATA LAYER                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│  🌐 Remote Data         💾 Local Data          🔄 Synchronization           │
│  ├─ ApiService          ├─ OfflineDatabase     ├─ PendingOperations         │
│  ├─ RetrofitClient      ├─ Room DAOs           ├─ Conflict Resolution       │
│  ├─ AuthInterceptor     ├─ Entities            └─ Background Sync           │
│  └─ Network Monitoring  └─ Converters                                       │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 🌊 Fluxo de Dados

### 📥 Fluxo de Entrada de Dados

```
🌐 API Server
    │
    ▼
📡 RetrofitClient
    │
    ▼
🔐 AuthInterceptor (adiciona JWT)
    │
    ▼
🎯 MainViewModel
    │
    ▼
💾 OfflineRepository
    │
    ├─► 💾 Room Database (cache)
    │
    ▼
📱 MainActivity (UI Update)
    │
    ▼
🎠 RecyclerView (exibe dados)
```

### 📤 Fluxo de Saída de Dados

```
👆 User Action (MainActivity)
    │
    ▼
🎯 MainViewModel (processa ação)
    │
    ▼
💾 OfflineRepository
    │
    ├─► 💾 Room Database (salva localmente)
    │
    ▼
📶 ConnectivityManager (verifica rede)
    │
    ├─► 🔄 SyncManager (se offline)
    │   │
    │   ▼
    │   ⏳ PendingOperations (agenda sync)
    │
    └─► 🌐 ApiService (se online)
        │
        ▼
        ✅ Servidor (confirma operação)
```

---

## 🔄 Sistema Offline Detalhado

### 🧩 Componentes e Interações

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           🔄 OFFLINE ECOSYSTEM                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  📡 ConnectivityManager                                                     │
│  ├─ NetworkCallback ──────────┐                                            │
│  ├─ BroadcastReceiver         │                                            │
│  └─ ConnectionState ──────────┼─► 🔄 SyncManager                           │
│                               │   ├─ WorkManager                           │
│  💾 OfflineDatabase           │   ├─ PeriodicSync                          │
│  ├─ PickupEntity ─────────────┤   ├─ OneTimeSync                           │
│  ├─ OccurrenceEntity          │   └─ RetryPolicy                           │
│  ├─ PendingOperation ─────────┘                                            │
│  └─ Migrations                                                              │
│                                                                             │
│  📦 OfflineRepository                                                       │
│  ├─ Cache Strategy ───────────┐                                            │
│  ├─ Fallback Logic            │                                            │
│  └─ Data Consistency ─────────┼─► 🎯 ViewModels                            │
│                               │   ├─ LiveData Updates                      │
│                               │   ├─ Error Handling                        │
│                               │   └─ UI State Management                   │
│                               │                                            │
│  ⚡ SyncWorker                │                                            │
│  ├─ Background Execution ─────┘                                            │
│  ├─ Constraint Handling                                                     │
│  └─ Progress Reporting                                                      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 🎯 Estratégias de Cache

```
📥 CACHE STRATEGIES

1️⃣ Cache-First (Coletas)
   User Request ──► Cache ──► UI
                     │
                     ▼ (se vazio)
                   API ──► Cache ──► UI

2️⃣ Network-First (Atualizações)
   User Request ──► API ──► Cache ──► UI
                     │
                     ▼ (se falha)
                   Cache ──► UI

3️⃣ Write-Through (Finalizações)
   User Action ──► Cache
                     │
                     ▼
                   API (quando online)

4️⃣ Background Sync (Pendências)
   Offline Actions ──► PendingOperations
                           │
                           ▼ (quando online)
                         API ──► Remove Pending
```

---

## 🔄 Ciclo de Vida das Activities

### 🏠 MainActivity Lifecycle

```
📱 App Launch
    │
    ▼
🎬 SplashActivity
    │
    ▼
🔐 Check Authentication
    │
    ├─► ❌ Not Authenticated ──► 🔑 LoginActivity
    │                               │
    │                               ▼
    │                           ✅ Login Success ──┐
    │                                              │
    └─► ✅ Authenticated ──────────────────────────┘
                                                   │
                                                   ▼
                                               🏠 MainActivity
                                                   │
                                                   ▼
                                               📋 onCreate()
                                               ├─ setupViews()
                                               ├─ setupCarousel()
                                               ├─ setupViewModel()
                                               ├─ setupSyncManager()
                                               └─ fetchPickups()
                                                   │
                                                   ▼
                                               📱 onResume()
                                               ├─ startTimeUpdates()
                                               ├─ updateLocation()
                                               └─ checkPendingSync()
                                                   │
                                                   ▼
                                               ⏸️ onPause()
                                               ├─ stopTimeUpdates()
                                               └─ saveState()
                                                   │
                                                   ▼
                                               🔚 onDestroy()
                                               ├─ cleanup()
                                               └─ unregisterListeners()
```

---

## 🔄 Processo de Sincronização

### ⚡ Sincronização Automática

```
🔄 SYNC TRIGGER EVENTS

📶 Network Available
    │
    ▼
🔍 Check Pending Operations
    │
    ├─► ✅ Has Pending ──► 🚀 Start Sync
    │                        │
    │                        ▼
    │                    📤 Process Queue
    │                        │
    │                        ├─► ✅ Success ──► ✅ Mark Complete
    │                        │
    │                        └─► ❌ Failure ──► ⏳ Retry Later
    │
    └─► ❌ No Pending ──► 😴 Wait for Next Event

⏰ Periodic Sync (15 min)
    │
    ▼
📶 Check Network
    │
    ├─► ✅ Online ──► 🔄 Sync Process
    │
    └─► ❌ Offline ──► ⏳ Schedule Next Check

👆 Manual Sync (FAB)
    │
    ▼
🔄 Force Sync
    │
    ├─► 📶 Online ──► 📤 Immediate Sync
    │
    └─► ❌ Offline ──► 💬 Show Offline Message
```

### 🎯 Algoritmo de Retry

```
❌ Sync Failure
    │
    ▼
🔢 Increment Retry Count
    │
    ▼
⏱️ Calculate Backoff
    │ (2^retryCount * 1000ms)
    │ Max: 5 minutes
    │
    ▼
🔍 Check Retry Count
    │
    ├─► < 5 retries ──► ⏳ Schedule Retry
    │                     │
    │                     ▼
    │                   ⏰ Wait Backoff Time
    │                     │
    │                     ▼
    │                   🔄 Retry Sync
    │
    └─► ≥ 5 retries ──► ❌ Mark as Failed
                          │
                          ▼
                        📝 Log Error
                          │
                          ▼
                        💬 Notify User
```

---

## 🗄️ Estrutura do Banco de Dados

### 📊 Schema do Room Database

```sql
-- 🚚 Tabela de Coletas
CREATE TABLE pickup_cache (
    id INTEGER PRIMARY KEY,
    reference_id TEXT UNIQUE NOT NULL,
    client_name TEXT NOT NULL,
    client_address TEXT NOT NULL,
    status TEXT NOT NULL,
    scheduled_date TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    cached_at TEXT NOT NULL
);

-- 📝 Tabela de Ocorrências
CREATE TABLE cached_occurrences (
    id INTEGER PRIMARY KEY,
    reference_id TEXT UNIQUE NOT NULL,
    occurrence_number TEXT NOT NULL,
    name TEXT NOT NULL,
    is_client_fault BOOLEAN NOT NULL,
    send_to_app BOOLEAN NOT NULL,
    is_activated BOOLEAN NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    cached_at TEXT NOT NULL
);

-- ⏳ Tabela de Operações Pendentes
CREATE TABLE pending_operations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    operation_type TEXT NOT NULL,
    pickup_id TEXT NOT NULL,
    data TEXT NOT NULL,
    retry_count INTEGER DEFAULT 0,
    created_at TEXT NOT NULL,
    last_attempt_at TEXT
);

-- 📊 Índices para Performance
CREATE INDEX idx_pickup_reference ON pickup_cache(reference_id);
CREATE INDEX idx_pickup_status ON pickup_cache(status);
CREATE INDEX idx_occurrence_reference ON cached_occurrences(reference_id);
CREATE INDEX idx_occurrence_activated ON cached_occurrences(is_activated);
CREATE INDEX idx_pending_type ON pending_operations(operation_type);
CREATE INDEX idx_pending_created ON pending_operations(created_at);
```

### 🔄 Migrações do Banco

```
📊 DATABASE MIGRATIONS

Version 1 ──► Version 2
├─ ADD TABLE pickup_cache
├─ ADD TABLE pending_operations
└─ CREATE initial indexes

Version 2 ──► Version 3
├─ ADD TABLE cached_occurrences
├─ ADD COLUMN cached_at to existing tables
└─ CREATE performance indexes

Version 3 ──► Version 4 (Future)
├─ ADD TABLE sync_logs
├─ ADD COLUMN sync_status to pending_operations
└─ OPTIMIZE existing indexes
```

---

## 🎯 Fluxo de Finalização de Coleta

### 📸 Processo Completo

```
👆 User Clicks "Finalizar"
    │
    ▼
💬 Show FinalizePickupDialog
    │
    ├─► 📸 Capture Photo
    │   ├─ Camera Permission Check
    │   ├─ Open Camera Intent
    │   └─ Process Bitmap
    │
    ├─► 📝 Select Occurrence (if needed)
    │   ├─ Load from Cache
    │   ├─ Fallback to API
    │   └─ Show Spinner
    │
    └─► ✅ Confirm Action
        │
        ▼
🎯 MainViewModel.finalizePickup()
    │
    ▼
💾 Save to Local Database
    │
    ├─► 📊 Update Pickup Status
    │
    └─► ⏳ Create Pending Operation
        │
        ▼
📶 Check Network Status
    │
    ├─► ✅ Online ──► 📤 Send to API
    │                  │
    │                  ├─► ✅ Success ──► ✅ Remove Pending
    │                  │
    │                  └─► ❌ Failure ──► ⏳ Keep Pending
    │
    └─► ❌ Offline ──► 💾 Store for Later Sync
        │
        ▼
📱 Update UI
    │
    ├─► 🎠 Refresh Carousel
    ├─► 📊 Update Progress
    └─► 💬 Show Success Message
```

---

## 🔧 Configuração de WorkManager

### ⚡ Background Tasks

```
🔄 WORKMANAGER CONFIGURATION

📅 Periodic Sync Work
├─ Frequency: 15 minutes
├─ Constraints:
│  ├─ Network Required: YES
│  ├─ Battery Not Low: YES
│  └─ Device Idle: NO
├─ Backoff Policy: EXPONENTIAL
└─ Retry Policy: 3 attempts

⚡ One-Time Sync Work
├─ Trigger: Network Available
├─ Constraints:
│  └─ Network Required: YES
├─ Expedited: YES
└─ Priority: HIGH

🔄 Retry Work
├─ Trigger: Failed Operations
├─ Delay: Exponential Backoff
├─ Max Attempts: 5
└─ Constraints:
   └─ Network Required: YES
```

---

## 📊 Monitoramento e Logs

### 📝 Sistema de Logging

```
📊 LOG CATEGORIES

🔄 SyncManager
├─ Sync Started/Completed
├─ Network Status Changes
├─ Retry Attempts
└─ Error Conditions

💾 OfflineRepository
├─ Cache Hits/Misses
├─ Data Persistence
├─ Fallback Strategies
└─ Data Consistency

🎯 ViewModels
├─ User Actions
├─ State Changes
├─ API Calls
└─ Error Handling

📱 UI Components
├─ User Interactions
├─ Navigation Events
├─ Performance Metrics
└─ Crash Reports
```

### 🎯 Debug Commands

```bash
# 📊 Monitor Sync Operations
adb logcat -s SyncManager:D,SyncWorker:D

# 💾 Monitor Database Operations
adb logcat -s OfflineRepository:D,OfflineDatabase:D

# 📱 Monitor UI Updates
adb logcat -s MainViewModel:D,MainActivity:D

# 🌐 Monitor Network Operations
adb logcat -s ApiService:D,RetrofitClient:D

# 🔄 Force Sync Test
adb shell am broadcast -a android.net.conn.CONNECTIVITY_CHANGE

# 💾 Database Inspection
adb shell run-as com.example.zylogi_motoristas
sqlite3 databases/offline_operations.db
.tables
.schema pickup_cache
SELECT * FROM pending_operations;
```

---

*"Um diagrama vale mais que mil linhas de código!"* 📊✨

---

**🔗 Links Relacionados**
- [Documentação Principal](DOCUMENTACAO_TECNICA.md)
- [Guia de Depuração](GUIA_DEPURACAO.md)
- [API Documentation](DOC_APP.md)

---

*Criado com 💙 para desenvolvedores que amam arquitetura limpa!* 🏗️👨‍💻
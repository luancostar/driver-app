# 🔍 Guia de Depuração - Problemas de Conectividade

## 📱 Como Depurar Problemas Sem Internet

### 1. **Verificar Logs do Aplicativo**

Para identificar exatamente onde o erro está ocorrendo, use o comando:

```bash
adb logcat | findstr "MainActivity\|MainViewModel\|AuthInterceptor\|ConnectivityManager"
```

**Ou filtre por tags específicas:**
```bash
adb logcat -s MainActivity MainViewModel AuthInterceptor ConnectivityManager
```

### 2. **Principais Pontos de Falha Identificados e Corrigidos**

#### ✅ **Geocoding (CORRIGIDO)**
- **Problema**: `getNeighborhoodName()` falhava sem internet
- **Solução**: Adicionado tratamento robusto com thread separada
- **Log esperado**: `"📍 Localização offline"` quando sem internet

#### ✅ **Carregamento de Coletas (CORRIGIDO)**
- **Problema**: `fetchPickups()` não tinha fallback adequado
- **Solução**: Tratamento de erro robusto com cache offline
- **Log esperado**: Tentativa de API → Fallback para cache

#### ✅ **Conversão de Dados (CORRIGIDO)**
- **Problema**: `PickupConverter` falhava em conversões
- **Solução**: Método `safeSetField()` e fallback de objetos
- **Log esperado**: Avisos em vez de crashes

### 3. **Teste Passo a Passo**

#### **Cenário 1: Login com Internet → Desconectar → Usar App**
1. Faça login com internet ativa
2. Aguarde carregar as coletas
3. Desative WiFi/dados móveis
4. Navegue pelo app
5. **Resultado esperado**: App funciona com dados em cache

#### **Cenário 2: Verificar Logs Específicos**
```bash
# Terminal 1 - Logs gerais
adb logcat | findstr "ERROR\|WARN\|MainActivity"

# Terminal 2 - Logs de conectividade
adb logcat | findstr "ConnectivityManager\|Geocoding"

# Terminal 3 - Logs de API
adb logcat | findstr "MainViewModel\|ApiService"
```

### 4. **Indicadores de Funcionamento Correto**

#### **✅ Sem Internet - Comportamento Esperado:**
- Localização mostra: `"📍 Localização offline"`
- Coletas carregam do cache local
- Toast: `"Carregando do cache local"`
- Não há crashes ou fechamento do app

#### **✅ Com Internet - Comportamento Esperado:**
- Localização mostra bairro/cidade
- Coletas carregam da API
- Sincronização automática
- Temperatura simulada (20-35°C)

### 5. **Comandos de Depuração Úteis**

#### **Verificar Estado da Conectividade:**
```bash
adb logcat | findstr "Conectividade\|Connectivity"
```

#### **Verificar Chamadas de API:**
```bash
adb logcat | findstr "AuthInterceptor\|RetrofitClient"
```

#### **Verificar Cache Local:**
```bash
adb logcat | findstr "OfflineRepository\|PickupDao"
```

#### **Limpar Logs e Reiniciar:**
```bash
adb logcat -c  # Limpa logs
adb shell am force-stop com.example.zylogi_motoristas  # Fecha app
adb shell am start -n com.example.zylogi_motoristas/.SplashActivity  # Abre app
```

### 6. **Problemas Conhecidos e Soluções**

#### **Problema**: App ainda fecha após login sem internet
**Possíveis causas:**
1. Erro não capturado em outro componente
2. Problema na inicialização de serviços
3. Erro em thread de background

**Solução de depuração:**
```bash
# Capturar TODOS os logs durante o problema
adb logcat > logs_completos.txt

# Filtrar apenas erros críticos
adb logcat | findstr "FATAL\|AndroidRuntime\|CRASH"
```

#### **Problema**: Geocoding ainda falha
**Verificação:**
```bash
adb logcat | findstr "Geocoding\|getNeighborhoodName"
```
**Log esperado:** `"Geocoding falhou - sem conexão"`

### 7. **Teste de Conectividade Manual**

Para testar a detecção de conectividade:

```bash
# Verificar se o app detecta mudanças de rede
adb logcat | findstr "onAvailable\|onLost\|ConnectivityManager"
```

### 8. **Informações do Sistema**

```bash
# Verificar versão do Android
adb shell getprop ro.build.version.release

# Verificar conectividade do sistema
adb shell dumpsys connectivity

# Verificar apps em execução
adb shell ps | findstr zylogi
```

### 9. **Próximos Passos se o Problema Persistir**

1. **Capture logs completos** durante o crash:
   ```bash
   adb logcat -c && adb logcat > crash_logs.txt
   ```

2. **Identifique o stack trace** do erro:
   ```bash
   adb logcat | findstr "at com.example.zylogi"
   ```

3. **Verifique inicializações** na MainActivity:
   ```bash
   adb logcat | findstr "onCreate\|setupViews\|observeViewModel"
   ```

4. **Teste componentes isoladamente**:
   - Comente `updateLocation()` temporariamente
   - Comente `mainViewModel.fetchPickups()` temporariamente
   - Teste cada componente individualmente

---

## 🚀 Melhorias Implementadas

### ✅ **MainActivity.java**
- Geocoding robusto com thread separada
- Tratamento de `IOException` para problemas de rede
- Fallback gracioso para "Localização offline"

### ✅ **MainViewModel.java**
- Tratamento de erro em `fetchPickups()`
- Fallback automático para cache local
- Logs detalhados para depuração

### ✅ **PickupConverter.java**
- Método `safeSetField()` para conversões seguras
- Fallback de objetos em caso de erro
- Logs informativos em vez de crashes

---

**💡 Dica**: Mantenha o `adb logcat` rodando em um terminal separado enquanto testa o app para capturar logs em tempo real.
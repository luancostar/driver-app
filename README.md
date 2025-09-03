# 🚛 Zylogi Motoristas - App Android

> **Sistema completo de gestão de coletas para motoristas com funcionalidades offline avançadas**

## 📱 Sobre o Aplicativo

O **Zylogi Motoristas** é um aplicativo Android robusto desenvolvido para motoristas de coleta, oferecendo:

- 🔄 **Sincronização automática** com sistema offline inteligente
- 📍 **Geolocalização em tempo real** para rastreamento de coletas
- 📷 **Captura de evidências** via câmera ou galeria
- 🔐 **Autenticação segura** com JWT e biometria
- 📊 **Interface intuitiva** com Material Design
- ⚡ **Performance otimizada** com arquitetura MVVM

---

## 📚 Documentação Completa

### 🎯 **Navegação Rápida**

| 📖 Documento | 🎯 Finalidade | 👥 Público |
|--------------|---------------|-------------|
| **[📋 Documentação Técnica](DOCUMENTACAO_TECNICA.md)** | Arquitetura, componentes e funcionamento detalhado | Desenvolvedores |
| **[🏗️ Diagramas de Arquitetura](DIAGRAMAS_ARQUITETURA.md)** | Fluxos, banco de dados e diagramas visuais | Arquitetos/Devs |
| **[🔧 Guia de Depuração](GUIA_DEPURACAO.md)** | Comandos, logs e troubleshooting | DevOps/QA |
| **[🌐 Documentação da API](DOC_APP.md)** | Endpoints, autenticação e integração | Backend/Frontend |

---

## 🚀 Quick Start

### 📋 **Pré-requisitos**
```bash
# Android Studio Arctic Fox ou superior
# JDK 11+
# Android SDK 24+ (API Level 24)
# Gradle 8.0+
```

### ⚡ **Instalação Rápida**
```bash
# 1. Clone o repositório
git clone <url-do-repositorio>
cd driver-app

# 2. Sincronize dependências
.\gradlew build

# 3. Execute no dispositivo/emulador
.\gradlew installDebug
```

### 🔧 **Configuração de Desenvolvimento**
```bash
# Configurar URL da API (app/build.gradle.kts)
API_BASE_URL = "http://192.168.1.11:3001/"

# Habilitar logs de debug
adb shell setprop log.tag.ZylogiApp VERBOSE
```

---

## 🏗️ Arquitetura do Projeto
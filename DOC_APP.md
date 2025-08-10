# Documentação da API - Zylogi Motoristas

## 🌐 Documentação Oficial
**Swagger UI:** https://api.homolog.zylogi.com/documentation

## ✅ Funcionalidades Implementadas

### 📅 Filtro por Data de Agendamento
O app agora filtra e exibe **apenas as coletas agendadas para o dia atual**, garantindo que o motorista veja somente as tarefas relevantes para hoje.

### 🎨 Melhorias na Interface do Usuário
1. **Data de Agendamento Visível**: Cada card agora exibe a data de agendamento formatada (dd/MM/yyyy)
2. **ID da Coleta Visível**: Cada card exibe o ID da coleta no formato "🆔 ID: #<ID>" ou "🆔 ID: Não informado" quando não disponível
3. **Botões Reorganizados**: 
   - ✅ "COLETADO" aparece primeiro (acima)
   - ❌ "NÃO COLETADO" aparece abaixo
   - Botões em layout vertical para melhor usabilidade
4. **Modal de Finalização**: 
   - Modal personalizado ao clicar em "COLETADO"
   - Spinner para seleção de ocorrência (carregado dinamicamente da API `/occurrences`)
   - Campo de texto para observações
   - Botões "Cancelar" e "Finalizar Coleta"
5. **Integração com API de Ocorrências**:
   - Criada classe `Occurrence` para mapear dados da API
   - Adicionado endpoint `/occurrences/driver` no `ApiService`
   - Endpoint já retorna apenas ocorrências disponíveis para motoristas (pré-filtradas)
   - Sistema de fallback com lista predefinida caso a API falhe
   - Loading state durante carregamento das ocorrências
   - Tratamento de erros com mensagens informativas
5. **Visual Aprimorado**: 
   - Ícones nos botões para identificação rápida
   - Cores diferenciadas (verde para coletado, vermelho para não coletado)
   - Data destacada em azul com ícone de calendário
   - Correções na exibição de endereços usando estrutura correta das classes

### 🔧 Implementação Técnica
1. **Modelo Pickup atualizado** com campo `scheduledDate`
2. **Filtragem dupla** no MainViewModel:
   - Primeiro: Filtra por data de agendamento = hoje
   - Segundo: Filtra por status = "PENDING"
3. **Compatibilidade**: Coletas sem data de agendamento são consideradas válidas para hoje
4. **Layout atualizado** com melhor organização visual

### 📊 Fluxo de Filtragem
```
API Response → Filtro Data Agendamento → Filtro Status PENDING → Carrossel UI
```

## Modelos de Dados Atualizados

### Pickup
```json
{
  "id": "string",
  "status": "PENDING" | "COMPLETED" | "NOT_COMPLETED",
  "isFragile": boolean,
  "observation": "string",
  "client": Client,
  "clientAddress": ClientAddress,
  "scheduledDate": "string (yyyy-MM-dd ou yyyy-MM-ddTHH:mm:ss)"
}
```

### Client
```json
{
  "companyName": "string",
  "contactName": "string",
  "phone": "string"
}
```

### ClientAddress
```json
{
  "street": "string",
  "number": "string",
  "neighborhood": Neighborhood,
  "city": City,
  "zipCode": "string"
}
```

## 📋 Estrutura da API Zylogi

### Seções Disponíveis:
- **Auth** - Autenticação
- **Users** - Usuários  
- **drivers** - Motoristas
- **Cidades** - Cidades
- **Bairros** - Bairros
- **Clientes** - Clientes
- **Auditoria de Clientes** - Logs de clientes
- **Endereços de clientes** - Endereços
- **Coletas** - Coletas (pickup)
- **Auditoria de Coletas** - Logs de coletas
- **vehicles** - Veículos
- **Vehicle Types** - Tipos de veículos
- **Packages** - Pacotes
- **Branches** - Filiais
- **Rotas de Coleta** - Rotas
- **Ocorrências** - Ocorrências
- **Contacts** - Contatos

## ✅ Endpoints Implementados

### Autenticação
- `POST /auth/driver/login`
  - Request: `{ cpf: string, password: string }`
  - Response: `{ accessToken: string }`

### Coletas
- `GET /pickups`
  - Query params: `driverId`, `startDate`, `endDate`
  - Response: `Array<Pickup>`
  - **FILTRO ADICIONAL**: Apenas coletas com `scheduledDate` = hoje

- `PATCH /pickups/{id}/driver-finalize`
  - Body: `{ status: "COMPLETED" | "NOT_COMPLETED" }`
  - Response: `Pickup`

## 🚀 Próximas Implementações Sugeridas

1. **Perfil do Motorista** - Tela para visualizar/editar dados
2. **Gestão de Veículos** - Associar e gerenciar veículos
3. **Rotas Otimizadas** - Calcular melhor rota para coletas
4. **Relatório de Ocorrências** - Reportar problemas durante coletas
5. **Histórico de Atividades** - Auditoria das ações do motorista